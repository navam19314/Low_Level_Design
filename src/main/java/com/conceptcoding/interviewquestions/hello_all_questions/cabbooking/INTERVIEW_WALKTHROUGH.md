# Cab Booking System — 45-min LLD Interview Walkthrough

**Target role:** SDE‑2 (Amazon India, Adobe, Flipkart, and any Uber/Ola-style ride-hailing round)
**Source method:** Hello Interview *Delivery Framework* applied to the *Cab Booking / Ride-Hailing* problem breakdown.

> Cab Booking is the **flagship multi-pattern problem** of the India interview circuit. The signal is multi-axis: two **Strategy** seams chosen on day one (matching + pricing), an **enum-driven state machine** for the ride lifecycle, and — the part that actually separates SDE-2 from SDE-1 — **concurrency correctness on the AVAILABLE → ON_TRIP race**: two riders must never be matched to the same driver.

> Get three things right and you're senior: (a) `rankCandidates` returns a **ranked list**, not a single driver, (b) `Driver.tryReserve()` is atomic per-driver so the reservation race has exactly one winner, and (c) you can *name* the production spatial-index upgrade even though the base uses a linear scan.

---

## Part 0 — Understand it from scratch (read this first if you're rusty)

*Skip if the design is already fresh; come back to it when it isn't.*

**The real-world picture.** You open a ride-hailing app, pick a pickup and drop-off, and hit "Book." Somewhere on a server, that click has to find a nearby driver, mark that driver as taken *before* anyone else can grab them, walk the ride through pickup → in-progress → drop-off, and work out the fare — correctly, even when thousands of people hit "Book" in the same second.

**The pieces:**
- **CabBookingService** — the dispatcher at a taxi call center. You never talk to drivers directly; you call the dispatcher and she handles everything else.
- **Rider** — you, the passenger. Just a name and an ID.
- **Driver** — a cab: current location, status (available or out on a trip), a rating.
- **Ride** — the booking ticket once one exists: rider, driver, pickup, drop-off, stage, eventually the fare.
- **DriverMatchingStrategy** — the dispatcher's rulebook for *picking* a driver ("nearest wins" today; swappable for "highest-rated wins" tomorrow).
- **PricingStrategy** — the dispatcher's rulebook for *pricing* the trip (base fee + distance, with a surge multiplier on busy days).

**Three tricky parts, explained plainly:**

1. **Don't call just one cab — get a list, and work down it.** If you only reserved the single nearest driver, someone else's request could snap them up a half-second earlier and you'd be stuck. Instead the dispatcher gets a ranked list — nearest first — like a restaurant working down a waitlist. First name taken? Just try the next.

2. **Claiming a cab is "raise your hand first," like the last parking spot.** Two riders can spot the same nearest driver at nearly the same instant. Claiming is one-at-a-time and all-or-nothing: whoever's claim registers first gets a clean "yes, it's yours"; the other's claim bounces back "sorry, taken" and that rider just looks at their own next-best option. Nobody ends up sharing a cab.

3. **A ride can only move forward through its stages, in order — like a boarding pass.** Requested → matched → in progress → completed (or cancelled early). You can't stamp "arrived" before "boarded." If something tried to skip a stage, it's refused outright and nothing changes.

**A typical ride:** tap "Book" → dispatcher ranks available cabs by distance → tries to claim the top one; if it's just been taken, moves to the next name → driver arrives, ride flips to "in progress" → you arrive, fare = base + distance (+ surge), ride flips to "completed," driver becomes available again → if you'd cancelled instead, the driver is released and the ride is marked cancelled. Fifty other riders might be doing this in the same second — the "raise your hand first" rule (point 2) guarantees no two of them ever land on the same driver.

That maps directly onto `requestRide`, `startRide`, `completeRide`, and `cancelRide` on `CabBookingService` — the dispatcher fielding all of it, handing real decisions to `DriverMatchingStrategy` and `PricingStrategy`, with each `Driver`'s own `tryReserve()` settling any race.

---

## Time budget (45 min)

| Step | Activity                                                                                   | Budget  | Cumulative |
| ---- | ------------------------------------------------------------------------------------------- | ------- | ---------- |
| 1    | Requirements                                                                                | ~5 min  | 5          |
| 2    | Entities & Relationships                                                                    | ~5 min  | 10         |
| 3    | Class Design (2 Strategies + enum state machine + the reservation race)                    | ~10 min | 20         |
| 4    | Implementation (`requestRide` optimistic-match loop + dry-run + concurrency scenario)       | ~14 min | 34         |
| 5    | Extensibility (spatial index, driver-accept, surge, cab types)                             | ~10 min | 44         |
| —    | Wrap & questions                                                                            | ~1 min  | 45         |

Step 4 is where the optimistic-match loop and the empirical double-booking proof live — budget accordingly.

Watch the clock at minute **5** (Step 1 done), minute **20** (start coding), minute **34** (extensibility).

---

## Mental models — internalize these BEFORE you walk in

### M1. The dispatch flow — facade → strategy → optimistic reserve loop

```
                        ┌──────────────────────────────────┐
                        │     CabBookingService (Facade)   │
                        │                                  │
   requestRide ────────►│ 1. snapshot AVAILABLE drivers    │
                        │ 2. MatchingStrategy.rank(...)    │──────► [Driver, Driver, Driver, ...]
                        │ 3. for each candidate:           │            (ranked best-first)
                        │       if driver.tryReserve()     │
                        │         AVAILABLE → ON_TRIP      │   ◄── per-driver synchronized CAS
                        │         create Ride(MATCHED)     │
                        │         return                   │
                        │       else continue              │
                        │                                  │
   startRide  ─────────►│ ride.transitionTo(IN_PROGRESS)   │
   completeRide ───────►│ fare = pricing.calc(...)         │
                        │ ride.complete(fare)              │
                        │ driver.releaseFromTrip(dropoff)  │
                        │                                  │
   cancelRide ─────────►│ ride.cancel()                    │
                        │ if matched: release driver       │
                        └──────────────────────────────────┘

   Strategies (injected — Strategy pattern)
   ┌─────────────────────────────┐    ┌─────────────────────────────────┐
   │ DriverMatchingStrategy      │    │ PricingStrategy                 │
   │  • NearestDriverStrategy    │    │  • DistanceBasedPricing         │
   │  • <future: SurgeAware>     │    │    + surge multiplier (bps)     │
   │  • <future: HighRatedFirst> │    │  • <future: FlatRate>           │
   └─────────────────────────────┘    └─────────────────────────────────┘
```

**The one decision that defines the design:** matching strategies return a **ranked list**, not a single driver. The service then optimistically reserves each candidate in order until one wins the AVAILABLE → ON_TRIP race. Without this, two concurrent requests can match the same driver — the classic double-booking bug.

### M2. The reservation race — why `tryReserve` returns boolean, and why matching returns a LIST

```
   Thread A: requestRide(pickup near D1)         Thread B: requestRide(pickup near D1)

   Both rank candidates and get the SAME top pick: D1 (nearest to both pickups)

        t=1  A calls D1.tryReserve()
             status == AVAILABLE → flips to ON_TRIP → returns TRUE
             A creates Ride(MATCHED, driver=D1) — A WINS
                                                       t=2  B calls D1.tryReserve()
                                                            status == ON_TRIP → returns FALSE
                                                            B falls through to ITS next
                                                            ranked candidate (D2, D3, ...)
        ============================================================================
              No double-booking. No global lock. Only D1's own monitor was touched.
```

**Senior soundbite (memorize):** *"`tryReserve()` is `synchronized(this)` on the Driver — the AVAILABLE → ON_TRIP check-and-set is atomic per driver. The matching strategy returns a ranked LIST, not a single driver, because the top pick might lose that race to a concurrent request; the service just falls through to the next candidate. No global lock — contention is scoped to individual drivers, not the whole pool."*

### M3. Ride lifecycle — enum-driven state machine, not class-per-state

```
   REQUESTED ──match──► MATCHED ──start──► IN_PROGRESS ──complete──► COMPLETED
       │                   │
       └──cancel──┬────────┴──cancel──► CANCELLED
```

**Why enum + transition map, not GoF class-per-state?** Every transition here is one line of bookkeeping (set a timestamp, set the fare on `complete`). No state has distinct polymorphic *behavior* — compare with Vending Machine, where `HasCoinState.selectProduct()` behaves completely differently from `NoCoinState.selectProduct()`, which earns class-per-state. **The senior signal is knowing which one to pick, not defaulting to State every time you see the word "lifecycle."**

---

## STEP 1 — Requirements (~5 min)

### What to say out loud (opener)
> "Ride-hailing — the headline correctness concern is that concurrent ride requests must never match two riders to the same driver. There's also a genuine geo/spatial-scale angle. Let me clarify scope before designing."

### Clarifying-questions dialogue

> **You:** "Multiple cab types — Sedan, SUV, Auto — or one type for v1?"
> **Interviewer:** "One type for v1; mention how you'd extend."
>
> **You:** "Can a driver reject a match, or is it auto-accept?"
> **Interviewer:** "Auto-accept for v1; two-phase accept/reject is a good Step-5 topic."
>
> **You:** "Is payment processing in scope?"
> **Interviewer:** "No — assume the fare is computed and stored; payment is a separate service."
>
> **You:** "Multi-city, or single city in-memory?"
> **Interviewer:** "Single city, in-memory is fine."
>
> **You:** "Concurrent requests — do I need to handle multiple riders matching simultaneously without double-booking a driver?"
> **Interviewer:** "Yes — that's the one we'll dig into."

### What to write on the board

```
Functional Requirements
1. Rider requests a ride from pickup → drop-off location.
2. System matches the rider to an AVAILABLE driver near pickup.
3. Driver picks up the rider; ride starts; ride completes at drop-off.
4. Rider can cancel before the ride starts (REQUESTED or MATCHED).
5. Fare = base fare + distance × per-km rate, with a surge multiplier.
6. Thread-safe — concurrent requests must never double-book a driver.

ASSUMED
- Single city, in-memory (no persistence, no multi-region).
- One cab type for v1 (Sedan/SUV/Auto is a Step-5 extension).
- Auto-accept — the driver does not reject a match in v1.
- Fare model is distance + surge only (no time-of-day, tolls, taxes).
- Money is long rupees, never a double.

OUT OF SCOPE
- Payments / wallets / refunds — a separate Payment/Billing service owns these.
- Cancellation fees / driver penalties.
- Driver onboarding / KYC.
- Spatial indexing — punt to linear scan; name the production upgrade in Step 5.
- Driver-accept / reject flow — auto-accept for v1; Step-5 talking point.
```

### Close the step
> "Does this match what you had in mind? Two requirements are load-bearing — 'no double-booking under concurrency' and 'match near pickup'. The first drives the whole `tryReserve` design; the second is why matching is pulled out as a Strategy from day one — an interviewer will always ask 'what about highest-rated?' or 'surge-aware?'."

---

## STEP 2 — Entities & Relationships (~5 min)

### What to say out loud
> "Six types: **CabBookingService** (facade + registries), **Rider** and **Driver** (identity), **Ride** (the booking — joins rider, driver, locations, status, fare), **Location** (an immutable lat/lng value class), and two enums — **DriverStatus** and **RideStatus** — the latter carrying an explicit allowed-transitions map. Two Strategy interfaces sit alongside: **DriverMatchingStrategy** and **PricingStrategy**."

### What to write on the board

```
Entities
- CabBookingService  (facade: registries + injected strategies + ride lifecycle)
- Rider              (identity only; location supplied per request)
- Driver             (identity + current location + status + rating; the contention point)
- Ride                (a booking: rider + driver? + source + destination + status + fare + timestamps)
- Location            (immutable value class: lat, lng, distanceKm)
- DriverStatus        (enum: OFFLINE, AVAILABLE, ON_TRIP)
- RideStatus          (enum: REQUESTED, MATCHED, IN_PROGRESS, COMPLETED, CANCELLED + transition map)

Strategy seams
- DriverMatchingStrategy  (pickup, pool) → ranked List<Driver>
- PricingStrategy         (source, destination, surgeBps) → long fare (rupees)

Relationships
- CabBookingService  owns   Map<String, Driver>, Map<String, Rider>, Map<String, Ride>
- Ride               refs  Rider, Driver (null until MATCHED)
- Driver             owns  its own DriverStatus + Location (mutable, synchronized)
```

### Key invariants
- A Driver in `ON_TRIP` cannot be matched again — enforced by `tryReserve()`'s atomic check-and-set.
- A Ride's status can only change via the allowed-transitions graph (no MATCHED→COMPLETED jump).
- Fare is `long` rupees, set exactly once, on `complete()`.
- A Ride has no Driver until status reaches MATCHED.

### Why no `Product`-style ceremony classes
> "No `Trip` vs `Ride` split, no separate `Fare` class — fare is one field on Ride, set once. Adding classes for single fields with no independent behavior is ceremony, not design."

---

## STEP 3 — Class Design (~10 min)

### `DriverMatchingStrategy` — the design seam

```java
public interface DriverMatchingStrategy {
    List<Driver> rankCandidates(Location pickup, List<Driver> availableDrivers);
}
```

**Why a list, not a single driver?** Because the top choice might lose the AVAILABLE → ON_TRIP race to another concurrent request. The service iterates and tries to reserve each candidate in order. *This is the single most important design call in the problem* (see M2).

**Why Strategy on day one?** Run the one-sentence test: *"Will I have at least two implementations on day one?"* Even if v1 ships with only nearest-driver, the interviewer will almost certainly ask "what about highest-rated?" / "surge-aware?" / "predictive matching?" — pre-baking the seam costs ~3 lines and saves a mid-interview refactor.

### `PricingStrategy` — second Strategy

```java
public interface PricingStrategy {
    long calculateFare(Location source, Location destination, int surgeMultiplierBasisPoints);
}
```

Surge is passed *at call time*, not stored on the strategy — surge changes minute-to-minute based on demand. Basis points (10000 = 1.0×) keep the math integer-friendly. Fare is `long` rupees — never a `double`, same money discipline as every other problem in this deck.

### `RideStatus` — enum-driven state machine

```java
public enum RideStatus {
    REQUESTED, MATCHED, IN_PROGRESS, COMPLETED, CANCELLED;

    private static final Map<RideStatus, Set<RideStatus>> ALLOWED = new EnumMap<>(RideStatus.class);
    static {
        ALLOWED.put(REQUESTED,   EnumSet.of(MATCHED, CANCELLED));
        ALLOWED.put(MATCHED,     EnumSet.of(IN_PROGRESS, CANCELLED));
        ALLOWED.put(IN_PROGRESS, EnumSet.of(COMPLETED));
        ALLOWED.put(COMPLETED,   EnumSet.noneOf(RideStatus.class));
        ALLOWED.put(CANCELLED,   EnumSet.noneOf(RideStatus.class));
    }

    public boolean canTransitionTo(RideStatus next) { return ALLOWED.get(this).contains(next); }
}
```

See M3 for the enum-vs-class-per-state reasoning.

### `Driver` — the contention point

```java
public class Driver {
    private DriverStatus status;
    // ...

    // Atomic AVAILABLE → ON_TRIP. Returns true iff WE won the race.
    public synchronized boolean tryReserve() {
        if (status != DriverStatus.AVAILABLE) return false;
        status = DriverStatus.ON_TRIP;
        return true;
    }
}
```

**Why `synchronized(this)` and not `AtomicReference<DriverStatus>`?** Either works. Synchronized is more readable here because the check-and-set is a single short critical section, and Driver has *other* synchronized methods (`getCurrentLocation`, `getStatus`, `updateLocation`) which already pay the same monitor cost. Keeping one synchronization mechanism per class is cleaner than mixing.

### `CabBookingService` — the facade with the optimistic-match loop

```java
public Ride requestRide(Rider rider, Location pickup, Location dropoff) {
    // 1) Snapshot AVAILABLE drivers
    List<Driver> pool = drivers.values().stream()
            .filter(d -> d.getStatus() == DriverStatus.AVAILABLE)
            .toList();

    // 2) Rank
    List<Driver> ranked = matchingStrategy.rankCandidates(pickup, pool);

    // 3) Optimistic match — first to win the AVAILABLE → ON_TRIP CAS takes the ride
    for (Driver candidate : ranked) {
        if (candidate.tryReserve()) {
            Ride ride = new Ride(id, rider, pickup, dropoff, clock);
            ride.match(candidate);
            rides.put(id, ride);
            return ride;
        }
        // lost the race — candidate was just snatched; loop continues
    }
    throw new IllegalStateException("No drivers available near pickup");
}
```

The loop is the magic. No global lock. No driver gets reserved twice — `tryReserve` is atomic per driver. The only cost under contention is one or two failed `tryReserve` calls. Empirically tested: 50 concurrent riders × 10 drivers → exactly 10 matched, zero double-bookings (Step 4).

### The principle to verbalize — Strategy + Tell-Don't-Ask
> "Matching and pricing are pulled out as Strategies because at least one alternative is guaranteed to come up. Every state mutation — `tryReserve`, `match`, `start`, `complete`, `cancel` — is Tell-Don't-Ask: callers never read status and decide; they call the lifecycle method and handle the exception if it's illegal."

---

## STEP 4 — Implementation (~14 min)

### Open by asking
> "Real Java or pseudo-code? I'll write the enums and `Location` first, then `Driver.tryReserve`, then `CabBookingService.requestRide` since that's where the race lives, then dry-run the nearest-driver scenario and the 50-rider concurrency scenario."

### 4.1 Order to write in (so each step leaves you with something runnable)

1. **Enums** (`DriverStatus`, `RideStatus` with transition map) — 3 min
2. **Value object** `Location` — 1 min
3. **Entities** `Rider`, `Driver` (with `tryReserve`), `Ride` (with `transitionTo`) — 4 min
4. **Strategies** — interfaces + one implementation each — 3 min
5. **CabBookingService** — registries + `requestRide` / `startRide` / `completeRide` / `cancelRide` — 3 min

### 4.2 Verification — dry-run "nearest driver wins"

```
Setup: pickup = (12.97, 77.59). Three AVAILABLE drivers:
   D-close  at (12.975, 77.595)   distanceKm ≈ 0.79
   D-medium at (13.000, 77.595)   distanceKm ≈ 3.34
   D-far    at (13.100, 77.595)   distanceKm ≈ 13.34

rankCandidates(pickup, [close, medium, far]) sorts ascending by distanceKm:
   → [D-close, D-medium, D-far]

requestRide iterates: D-close.tryReserve() → AVAILABLE → true → WINS
   ride.getDriver().getId() == "D-close"                                   ✓
```

### 4.3 Verification — dry-run the 50-rider / 10-driver concurrency scenario

```
Setup: 10 drivers clustered near pickup, all AVAILABLE.
       50 riders submit requestRide simultaneously via a CountDownLatch barrier.

Each rider's ranked list top-picks the same handful of closest drivers.
Only the FIRST tryReserve() on any given driver returns true; every
subsequent tryReserve() on that same driver (from any other thread, on
any other rider's ranked list) returns false and falls through.

Result:
   matched:  10     ✓ (exactly one ride per available driver)
   rejected: 40     ✓ (every other requester exhausts its ranked list)
   any driver double-booked? false   ✓
   drivers ON_TRIP:   10  drivers AVAILABLE: 0   ✓ (accounted for exactly)
```

> **This is the single most-important test in this problem.** Mention you'd verify it with a `CountDownLatch` barrier (all 50 threads start `requestRide` simultaneously) and assert (a) `matched == 10`, (b) the set of matched driver ids has no duplicates, (c) every matched driver is `ON_TRIP` and every other driver is still `AVAILABLE`.

---

## STEP 5 — Extensibility (~10 min)

> This is the part of the round where SDE-2s separate from SDE-1s. Below are the questions you'll definitely be asked.

### 5.1 "Linear scan over all drivers doesn't scale — what's the production answer?"

**Spatial indexing.** Three escalating options:
1. **Uniform grid** — divide the city into 1 km × 1 km cells, `Map<CellId, Set<Driver>>`. Lookup = 9 cells (centered + 8 neighbors). O(k) where k = drivers in those 9 cells. Easiest to implement.
2. **Quadtree** — recursive 4-way split, adapts to driver density (CBD vs. suburbs).
3. **H3 / Geohash** — hex-grid indexing used by Uber. Same shape as #1 but better neighbor properties.

**Where it slots in:** `DriverMatchingStrategy.rankCandidates` already takes a `List<Driver> availableDrivers` — swap the snapshot source for a `SpatialIndex.queryNearby(pickup, radiusKm)` call. **The Strategy interface doesn't change** — that's the payoff of putting Strategy on day one.

### 5.2 "Driver can reject the match — what changes?"

Reservation becomes a two-phase commit:
1. `tryReserve()` → tentative hold (new state `PENDING_CONFIRMATION` between AVAILABLE and ON_TRIP).
2. Push notification to driver; wait N seconds for a response.
3. `accept` → `PENDING_CONFIRMATION` → `ON_TRIP` (ride goes to MATCHED).
4. `reject` or timeout → back to `AVAILABLE`; service moves to the next candidate in the ranked list.

This is also where **Observer** earns its place — drivers/riders subscribe to ride events for push notifications.

### 5.3 "Surge pricing — where does the multiplier come from?"

Out of scope for the matching path, but the design accommodates it:
- A `SurgeMonitor` (separate component) watches demand-vs-supply per zone and publishes `SurgeMultiplier(zone, basisPoints)` events.
- `CabBookingService.currentSurgeBasisPoints` is just a cache of the latest published value for the rider's pickup zone.
- At multi-zone scale, the cache becomes `ConcurrentMap<ZoneId, Integer>`, and `requestRide` looks up by pickup zone.

This is the **Observer / Pub-Sub** seam — surge is decoupled from booking.

### 5.4 "Multiple cab types (Sedan, SUV, Auto) — how do you extend?"

1. `Driver` gains a `CabType` field.
2. `requestRide` takes a `CabType` parameter.
3. `MatchingStrategy` filters the snapshot pool by type before ranking.
4. `PricingStrategy` becomes per-type — easiest path: `Map<CabType, PricingStrategy>` on the service, looked up by type.

No interface changes — every extension lives on the strategies. *That's the payoff for putting Strategy on day one.*

### 5.5 "What if the driver-pool snapshot is stale by the time the strategy ranks it?"

It is — and that's fine. The ranking is a hint, not a contract. By the time we call `tryReserve` on the first candidate, they might already be `ON_TRIP` (won by someone else); the for-loop just falls through to the next candidate. Worst case: we exhaust the ranked list and throw — the rider retries, probably against a fresher snapshot that now includes drivers who finished trips in the meantime.

The system is **eventually consistent on driver availability** — a fundamental property of optimistic concurrency. The alternative (lock the whole driver pool during matching) doesn't scale beyond ~10 req/sec.

### 5.6 "What if I want to refund / cancel after complete?"

Out of scope for the state machine as drawn — `COMPLETED → REFUNDED` would need a new terminal-but-revisable status, or a separate `Refund` entity tied to a Ride. The senior answer: **refunds belong in a separate Payment/Billing service**, not in the ride state machine. The ride is COMPLETED forever; the *payment* attached to it can be REFUNDED. Different aggregates, different lifecycles.

### 5.7 Other "what-if" answers

| Follow-up                                       | Answer                                                                                             |
| ------------------------------------------------ | ---------------------------------------------------------------------------------------------------- |
| "Persist across restart"                        | Inject a `RideRepository`/`DriverRepository`; write on every state-mutating call; load on boot.       |
| "Notify rider when driver arrives"              | **Observer** — `Ride` fires lifecycle events; push-notification, analytics, billing, fraud-detection subscribe independently. |
| "Stop a driver from going offline mid-trip"     | `Driver.goOffline()` already rejects if `status == ON_TRIP` — same Tell-Don't-Ask guard as `transitionTo`. Callers don't ask "can I go offline?"; they call it and handle the exception. |
| "Driver location updates mid-ride"              | `Driver.updateLocation(Location)` is the hook; in production this is a stream, consumed by the spatial index + an ETA service. |

---

## Design patterns in play

### Already in the BASE — call out by name

| Pattern                          | Where                                                        | Why it's in the base                                                                 |
| --------------------------------- | ------------------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| **Strategy** ⭐                    | `DriverMatchingStrategy`, `PricingStrategy`                   | At least one extension is guaranteed on day one (highest-rated matching, cab-type pricing). One-sentence test passes twice. |
| **Facade**                        | `CabBookingService`                                          | Caller wants `requestRide`/`startRide`/`completeRide`/`cancelRide` — doesn't compose registries + strategies itself. |
| **State machine (enum, not GoF State)** | `RideStatus.canTransitionTo`                            | The invalid-transition exception is the value; per-state *behavior* is trivial (1-line bookkeeping). |
| **Tell, Don't Ask**               | `Driver.tryReserve`, `Ride.match/start/complete/cancel`        | Callers mutate via named lifecycle methods; nothing ever sets `status` directly.        |
| **Optimistic concurrency (ranked-list match)** | `requestRide`'s reserve loop                     | Not a GoF pattern, but the single most important idiom in the problem — required for correctness under concurrency. |

### Reach for these on the matching Step-5 follow-up

| Follow-up                                  | Pattern                        | Your line                                                                                            |
| ------------------------------------------ | ------------------------------- | ------------------------------------------------------------------------------------------------------ |
| "Driver can reject the match"              | Two-phase reservation (state extension) | *"New `PENDING_CONFIRMATION` state between AVAILABLE and ON_TRIP; confirm/timeout resolves it. See §5.2."* |
| "Notify rider/driver of ride events"       | **Observer**                    | *"Ride fires lifecycle events; push/analytics/billing/fraud-detection subscribe independently. See §5.7."* |
| "Surge computed elsewhere and published"   | **Observer / Pub-Sub**          | *"`SurgeMonitor` publishes per-zone multipliers; the service just caches the latest value. See §5.3."*  |
| "Multiple cab types"                       | (Strategy composition, not a new pattern) | *"Filter the pool by type before ranking; `Map<CabType, PricingStrategy>` for per-type fares. See §5.4."* |
| "Compose surge on top of time-of-day pricing" | **Decorator**                | *"Wrap `PricingStrategy` in a decorator that adds a second multiplier — stackable, no change to the base interface."* |

### Patterns to actively refuse

- **Class-per-state (GoF State) on `Ride`** — every transition is bookkeeping; no per-state behavior worth its own class. (Contrast with Vending Machine, where it *does* earn its keep.)
- **Singleton on `CabBookingService`** — it's *usually* instantiated once in production, but that's a wiring choice, not a design pattern. Saying "I'd make it a Singleton" is a tell, not a signal.
- **Pre-baked Decorator on `PricingStrategy`** — a single surge multiplier in v1 doesn't need composition yet; introduce it only when a second independent multiplier (e.g. time-of-day) shows up.
- **`tryReserve` returning the `Driver`** — the caller already holds the reference it was trying to reserve; it only needs to know "did *I* win?" — that's a boolean, not an object.

### The rule to sound natural
> *"I pre-bake Strategy for matching and pricing because at least one variant is guaranteed to come up in this problem specifically — that's the one-sentence test. I do NOT pre-bake State-pattern classes, Observer, or Decorator, because none of them have a concrete trigger in the base requirements yet. They come out the moment the interviewer's follow-up names their trigger — driver-reject, ride notifications, or stacked pricing multipliers, respectively."*

---

## What is expected at each level

| Level | What they typically do |
| ----- | ------------------------------------------------------------------------------------------------ |
| **Junior** | Gets the happy path working. Matching strategy returns a single driver, not a ranked list — first pick either always wins (no concurrency test) or the whole matching call is put under one big lock. Fare may be a `double`. State transitions are a raw setter — nothing stops MATCHED → COMPLETED. |
| **Mid (the target)** | Pre-bakes Strategy for matching and pricing with a stated one-sentence justification. `tryReserve` is atomic per driver; matching returns a ranked list so a lost race falls through. Ride state machine uses an enum + transition map, not a raw setter. Names the spatial-index upgrade even without implementing it. Writes (or at least designs) the 50-rider concurrency test. |
| **Senior** | Everything Mid does, plus: articulates *why* per-driver `synchronized` beats a global lock (contention scoped to individual drivers, not the whole pool) and *why* it beats `AtomicReference` here (multiple synchronized methods on Driver already pay the same monitor cost — one mechanism, not two). Frames driver availability as **eventually consistent by design** — the ranked snapshot is a hint, not a contract — and explains why that's the *correct* tradeoff versus a pool-wide lock. Proactively raises the driver-accept two-phase extension and the Observer seam for ride-lifecycle notifications before being asked. |

---

## Interview deep-dives — the questions you'll definitely get asked

### 1. Complexity (Big-O)

Let `D` = number of drivers, `A` = AVAILABLE drivers in the snapshot (A ≤ D).

| Operation                        | Time                         | Space       | Notes                                                                            |
| ---------------------------------- | ------------------------------ | ----------- | ----------------------------------------------------------------------------------- |
| `requestRide` (snapshot + rank)    | **`O(D + A log A)`**           | O(A)        | Linear filter over all drivers, then sort the AVAILABLE ones by distance          |
| `Driver.tryReserve`               | **`O(1)`**                     | O(1)        | Single check-and-set under `synchronized(this)`                                  |
| `startRide` / `completeRide` / `cancelRide` | **`O(1)`**             | O(1)        | Map lookup + one state transition                                                |
| `DistanceBasedPricing.calculateFare` | **`O(1)`**                  | O(1)        | One `distanceKm` call + arithmetic                                              |
| Storage                          | —                              | **`O(D + R)`** | Driver registry + ride registry (R = rides so far, never pruned in this scope)  |

> **Senior callout:** *"The `O(D)` linear scan in `requestRide` is the one that doesn't scale — at 10k+ drivers and hundreds of req/sec that becomes the bottleneck. That's exactly the spatial-index upgrade in §5.1: swap the scan for a grid/quadtree/H3 lookup and the complexity drops to O(k) where k is drivers in the local neighborhood. The Strategy interface absorbs the change with zero callsite impact."*

### 2. Concurrency / thread-safety — the full menu

| Approach                                    | When to use                                  | Cost                                                                 |
| --------------------------------------------- | ----------------------------------------------- | ------------------------------------------------------------------------ |
| **Per-driver `synchronized` `tryReserve`** ⭐ | **Default.** Correct + simple                  | Contention scoped to individual drivers; different drivers never block each other |
| `AtomicReference<DriverStatus>` + CAS       | Equally correct; slightly more ceremony        | No real win here — Driver already has other `synchronized` methods paying the monitor cost |
| Global lock over the driver pool             | Never — kills concurrency                       | Serializes every request in the system; doesn't scale past ~10 req/sec |
| Two-phase reservation (driver-accept)        | When drivers can reject a match (§5.2)          | Adds a `PENDING_CONFIRMATION` state + timeout handling                   |

> **The one race this problem is built around:** two riders' `requestRide` calls both rank the same driver first. Fix: `tryReserve()` is `synchronized(this)` on the Driver — exactly one caller's check-and-set wins; the loser's for-loop falls through to its next candidate. No lock ordering needed (unlike Inventory's `transfer`) because only ONE resource — the driver — is ever locked at a time here; there's no multi-resource acquisition to order.

### 3. Testing — what to write tests for

| Test category                | Cases to cover                                                                                          |
| ------------------------------ | ------------------------------------------------------------------------------------------------------------ |
| Basic invariants              | Happy path request → start → complete returns a positive fare; driver ends `AVAILABLE` again              |
| Nearest-wins                  | Three drivers at increasing distance → matched driver is the closest one                                    |
| Cancellation                  | Cancel after MATCHED releases the driver back to AVAILABLE; ride ends CANCELLED                             |
| No drivers available          | All drivers outside `maxRadiusKm`, or none online → `requestRide` throws, no state mutated                  |
| Surge pricing                 | Same route at 1.0× vs 2.0× surge → fare ratio ≈ 2.0                                                          |
| **Concurrent double-booking** | 50 riders × 10 drivers, all racing → exactly 10 matched, 40 rejected, zero duplicate driver ids in matched rides |
| Invalid transition rejected   | `completeRide` on a MATCHED (not yet started) ride throws                                                    |

```java
@Test
void fifty_riders_ten_drivers_no_double_booking() throws Exception {
    CabBookingService svc = new CabBookingService(
            new NearestDriverStrategy(10.0), new DistanceBasedPricing(50L, 15L), Clock.systemUTC());

    List<Driver> drivers = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
        Driver d = new Driver("D" + i, "D" + i, 47, new Location(12.97 + i * 0.0001, 77.59));
        d.goOnline(d.getCurrentLocation());
        svc.registerDriver(d);
        drivers.add(d);
    }

    int riders = 50;
    ExecutorService pool = Executors.newFixedThreadPool(20);
    CountDownLatch go = new CountDownLatch(1);
    AtomicInteger matched = new AtomicInteger();
    List<Ride> matchedRides = Collections.synchronizedList(new ArrayList<>());

    for (int i = 0; i < riders; i++) {
        Rider r = new Rider("R" + i, "R" + i);
        svc.registerRider(r);
        pool.submit(() -> {
            try {
                go.await();
                Ride ride = svc.requestRide(r, new Location(12.97, 77.59), new Location(13.00, 77.59));
                matchedRides.add(ride);
                matched.incrementAndGet();
            } catch (IllegalStateException e) {
                // rejected — no drivers left; expected for 40 of the 50
            } catch (InterruptedException ignored) {
            }
        });
    }
    go.countDown();
    pool.shutdown();
    assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));

    assertEquals(10, matched.get());
    Set<String> driverIds = new HashSet<>();
    for (Ride r : matchedRides) assertTrue(driverIds.add(r.getDriver().getId()));  // no duplicates
}
```

> **Senior callout:** *"This is THE test for this problem. Without an atomic `tryReserve`, two threads can both read `AVAILABLE`, both write `ON_TRIP`, and the driver ends up on two rides simultaneously. With it, the assertion on distinct driver ids across matched rides is the empirical proof."*

---

## 30-second summary

> *"CabBookingService is a facade over three registries (drivers, riders, rides) plus two injected Strategies — DriverMatchingStrategy and PricingStrategy — pulled out on day one because both are guaranteed to vary (highest-rated matching, surge-aware pricing, per-cab-type fares). The single most important design call is that matching returns a ranked LIST, not one driver: `requestRide` walks the list calling `Driver.tryReserve()`, which is `synchronized` on the driver instance and does an atomic AVAILABLE → ON_TRIP check-and-set — that's the no-double-booking invariant, with contention scoped to individual drivers rather than a global lock. Ride lifecycle is an enum-driven state machine (`RideStatus` + an allowed-transitions map), not class-per-state, because every transition is one line of bookkeeping. Fare is `long` rupees — base + distance × per-km, with a surge multiplier expressed in basis points, passed at call time since surge changes minute-to-minute. The concurrency proof is empirical: 50 riders racing for 10 drivers yields exactly 10 matches and zero duplicate driver ids. For scale, the linear driver scan becomes a grid/quadtree/H3 spatial index — the Strategy interface absorbs that change with zero callsite impact. Extensions: two-phase driver-accept (a new PENDING_CONFIRMATION state), Observer for ride-lifecycle notifications and surge publication, per-cab-type pricing via a `Map<CabType, PricingStrategy>`."*

That's ~60 seconds. Hits: structure, the Strategy + optimistic-match choices, the atomicity argument, the enum-vs-State call, the empirical concurrency proof, and the Step-5 extensions.

---

## Top mistakes that lose points

- **Matching strategy returns one Driver instead of a ranked list** — the first `tryReserve` call might lose the race and there's no fallback. Always return ranked candidates.
- **Holding a lock across `strategy.rankCandidates`** — that serializes all matching. The strategy is pure: it takes a snapshot list and returns a sorted list; no locks needed inside it.
- **Using `double` for fare** — money is `long` rupees, always.
- **Mutating `Ride.status` directly via a setter** — bypasses the state machine. All mutation must go through `transitionTo` (private), called only from named lifecycle methods (`match`, `start`, `complete`, `cancel`).
- **Forgetting to release the driver on cancel** — the driver stays `ON_TRIP` forever and silently disappears from the matching pool.
- **`tryReserve` returning a `Driver` instead of a `boolean`** — the race-loser needs to know "did *I* win?", not a reference it already has.
- **No `maxRadiusKm` cap on `NearestDriverStrategy`** — without it, a driver 500 km away can get matched to a rider in city center. Real apps use 3–5 km caps.
- **Letting a driver go OFFLINE mid-trip** — guard `goOffline()` against `ON_TRIP`. Same idea as everywhere else: Tell, don't ask.
- **Defaulting to a global lock "to be safe"** — kills the whole point of per-driver concurrency; every request in the system serializes.

---

## Files in this folder (your reference implementation)

| File                                       | What it shows                                                                              |
| -------------------------------------------- | ---------------------------------------------------------------------------------------------- |
| `model/Location.java`                       | Immutable value class (lat, lng) + `distanceKm` (flat-Earth approximation)                    |
| `model/Rider.java`                          | Identity-only entity                                                                           |
| `model/Driver.java`                         | **The contention point** — `tryReserve()` atomic check-and-set, `releaseFromTrip`, `updateLocation` |
| `model/DriverStatus.java`                   | OFFLINE / AVAILABLE / ON_TRIP                                                                   |
| `model/Ride.java`                           | Entity + lifecycle methods (`match`/`start`/`complete`/`cancel`) that all route through `transitionTo` |
| `model/RideStatus.java`                     | Enum-driven state machine with an explicit allowed-transitions map                              |
| `matching/DriverMatchingStrategy.java`      | Interface — returns a RANKED LIST                                                               |
| `matching/NearestDriverStrategy.java`       | Euclidean-nearest with an optional `maxRadiusKm` cap                                            |
| `pricing/PricingStrategy.java`              | Interface — surge passed at call time, not stored                                               |
| `pricing/DistanceBasedPricing.java`         | baseFare + distance × perKm × surge, all in `long` rupees                                        |
| `CabBookingService.java`                   | Facade with the optimistic-match loop                                                            |
| `CabBookingDriver.java`                     | 7 scenarios incl. **50-rider / 10-driver concurrency proof** (no double-booking)                 |

Run from the project root:

```bash
mvn -q compile exec:java \
  -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.cabbooking.CabBookingDriver
```
