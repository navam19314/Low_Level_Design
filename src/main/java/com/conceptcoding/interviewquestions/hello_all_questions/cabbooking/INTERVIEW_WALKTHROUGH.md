# Cab Booking System — Interview Walkthrough

A 45-minute SDE-2 LLD round. This is the **flagship multi-pattern problem** of the India interview circuit — Amazon India + Adobe + Flipkart all ask it. The signal here is multi-axis:
- **Strategy** seams (matching + pricing) chosen on day one because variants are guaranteed
- **State machine** (Ride lifecycle) — enum-driven, not class-per-state
- **Concurrency correctness** — the AVAILABLE → ON_TRIP race is the interesting part; two riders must not get the same driver
- **Spatial / geo design** — even if you punt to "linear scan, mention quadtree", you must *name* the production answer

---

## Mental Model

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

   Ride state machine (enum-driven)
   REQUESTED ──match──► MATCHED ──start──► IN_PROGRESS ──complete──► COMPLETED
       │                  │
       └─cancel─┬─────────┴──cancel──► CANCELLED
```

**The one decision that defines the design:** matching strategies return a **ranked list**, not a single driver. The service then optimistically reserves each candidate in order until one wins the AVAILABLE → ON_TRIP race. Without this, two concurrent requests can match the same driver — the classic double-booking bug.

---

## Step 1 — Requirements (5 min)

**Functional:**
1. A rider requests a ride from pickup → drop-off location.
2. System matches the rider to an available driver near pickup.
3. Driver picks up the rider, ride starts, ride completes at drop-off.
4. Rider can cancel before ride starts.
5. Fare calculation includes base fare, distance, and surge multiplier.

**Clarify out loud:**
- Multiple cab types (Sedan/SUV/Auto)? → Out of scope for v1; mention as extension (matching strategy + per-type pricing)
- Driver can reject a match? → No for v1 (auto-accept); mention as extension
- Payment processing? → Out of scope — assume fare is computed and stored
- Real-time location updates mid-ride? → Out of scope — Driver.updateLocation exists as a hook
- Geographic scope? → Single city, in-memory

**Non-functional:**
- **Concurrent** — multiple riders + multiple drivers + multiple matches happening simultaneously
- **No driver double-booking** — the must-not-violate invariant
- **Money discipline** — `long cents` everywhere; basis points for percentages
- **Testability** — strategies injected, Clock injected for deterministic timestamps

**Out of scope** — state explicitly:
- Persistence (we're in-memory)
- Payments / wallets / refunds
- Cancellation fees / driver penalties
- Driver onboarding / KYC
- Pricing models beyond distance + surge (time-of-day, tolls, taxes)
- Spatial indexing — *we'll punt to linear scan and call out the upgrade in Step 5*

---

## Step 2 — Entities (5 min)

| Entity         | Purpose | Lifetime |
|----------------|---------|----------|
| `Rider`        | Identity. Location supplied per ride request. | persistent |
| `Driver`       | Identity + current location + status + rating. | persistent |
| `Ride`         | A booking. Joins rider + driver + locations + status + fare. | per-trip |
| `Location`     | Lat/lng record with `distanceKm`. | value object |
| `DriverStatus` | enum: OFFLINE, AVAILABLE, ON_TRIP | — |
| `RideStatus`   | enum: REQUESTED, MATCHED, IN_PROGRESS, COMPLETED, CANCELLED — with allowed-transitions map | — |

**Key invariants:**
- A Driver in `ON_TRIP` cannot be matched again.
- A Ride's status can only change via the allowed-transitions graph (no MATCHED→COMPLETED jump).
- Fare is `long cents`, set exactly once on `complete()`.
- A Ride has no Driver until status reaches MATCHED.

---

## Step 3 — Class Design (10 min)

### `DriverMatchingStrategy` — the design seam

```java
public interface DriverMatchingStrategy {
    List<Driver> rankCandidates(Location pickup, List<Driver> availableDrivers);
}
```

**Why a list, not a single driver?** Because the top choice might lose the AVAILABLE → ON_TRIP race to another concurrent request. The service iterates and tries to reserve each candidate in order. *This is the single most important design call in the problem.*

**Why Strategy on day one?** Run the one-sentence test: *"Will I have at least two implementations on day one?"* Even if v1 ships with only nearest-driver, the interviewer will absolutely ask "what about highest-rated?" / "surge-aware?" / "predictive matching?" — and you need a clean answer. Pre-baking the seam costs ~3 lines and saves a refactor in Step 5.

### `PricingStrategy` — second Strategy

```java
public interface PricingStrategy {
    long calculateFareCents(Location source, Location destination, int surgeMultiplierBasisPoints);
}
```

Surge is passed *at call time*, not stored on the strategy — surge changes minute-to-minute based on demand. Basis points (10000 = 1.0×) keep the math integer-friendly.

### `RideStatus` — enum-driven state machine

```java
public enum RideStatus {
    REQUESTED, MATCHED, IN_PROGRESS, COMPLETED, CANCELLED;

    private static final Map<RideStatus, Set<RideStatus>> ALLOWED = new EnumMap<>(...);
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

**Why enum-state-machine and not class-per-state?** Each state's transition is a 1-line bookkeeping operation (set timestamp, set fare on complete). No state needs its own polymorphic *behavior*. Compare with Vending Machine where `HasCoinState.selectProduct` differs entirely from `NoCoinState.selectProduct` — that earns class-per-state. Here, an enum + transition map is the right granularity. **The senior signal is knowing which one to pick.**

### `Driver` — the contention point

```java
public class Driver {
    private DriverStatus status;
    // ...

    /** Atomic AVAILABLE → ON_TRIP. Returns true iff WE won the race. */
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
            Ride ride = new Ride(...); ride.match(candidate); rides.put(id, ride); return ride;
        }
    }
    throw new IllegalStateException("No drivers available near pickup");
}
```

The loop is the magic. No global lock. No driver gets reserved twice — `tryReserve` is atomic per driver. The only thing wasted is one or two failed `tryReserve` calls in extreme contention. Empirically tested: 50 concurrent riders × 10 drivers → exactly 10 matched, zero double-bookings.

---

## Step 4 — Implementation (15 min)

**Order to write in (so each step leaves you with something runnable):**

1. **Enums** (`DriverStatus`, `RideStatus` with transition map) — 3 min
2. **Value object** `Location` — 1 min
3. **Entities** `Rider`, `Driver` (with `tryReserve`), `Ride` (with `transitionTo`) — 4 min
4. **Strategies** — interfaces + one implementation each — 3 min
5. **CabBookingService** — registries + `requestRide` / `startRide` / `completeRide` / `cancelRide` — 4 min

Driver scenarios you'd actually code in interview (3 minimum):
- happy path (request → start → complete + fare)
- nearest-driver-wins-with-three-drivers (proves Strategy seam works)
- concurrent-50-requests-10-drivers-no-double-booking (the senior-signal scenario)

---

## Step 5 — Extensibility / Deep-Dive (10 min)

> This is the part of the round where SDE-2s separate from SDE-1s. Below are the questions you'll definitely be asked.

### Q1. *Linear scan over all drivers doesn't scale. What's the production answer?*

**Spatial indexing.** Three escalating options:
1. **Uniform grid** — divide the city into 1 km × 1 km cells. Map<CellId, Set<Driver>>. Lookup = 9 cells (centered + 8 neighbors). O(k) where k = drivers in 9 cells. Easiest to implement.
2. **Quadtree** — recursive 4-way split, adapts to driver density. Better when drivers cluster (CBD vs. suburbs).
3. **H3 / Geohash** — hex-grid indexing used by Uber. Same shape as #1 but better neighbor properties.

**Where it slots in:** `DriverMatchingStrategy.rankCandidates` already takes `List<Driver> availableDrivers` — replace that with a `SpatialIndex.queryNearby(pickup, radiusKm)` call. The Strategy interface doesn't change.

### Q2. *Driver can reject the match — what changes?*

Reservation becomes a two-phase commit:
1. `tryReserve()` → tentative hold (new state PENDING_CONFIRMATION between AVAILABLE and ON_TRIP)
2. Send push to driver, wait N seconds for response
3. If `accept` → PENDING_CONFIRMATION → ON_TRIP (ride goes to MATCHED)
4. If `reject` or timeout → back to AVAILABLE, service moves to next candidate in the ranked list

This is also where Observer pattern earns its place — drivers/riders subscribe to ride events for push notifications.

### Q3. *Surge pricing — where does the multiplier come from?*

Out of scope for the matching path, but the design accommodates it:
- A `SurgeMonitor` (separate component) watches demand-vs-supply per zone, publishes `SurgeMultiplier(zone, basisPoints)` events
- `CabBookingService.currentSurgeBasisPoints` is just a cache of the latest published value for the rider's pickup zone
- For multi-zone scale, the cache becomes `ConcurrentMap<ZoneId, Integer>` and `requestRide` looks up by pickup zone

This is the **Observer/Pub-Sub** seam — surge is decoupled from booking.

### Q4. *Multiple cab types (Sedan, SUV, Auto) — how do you extend?*

Three layers of change:
1. `Driver` gains a `CabType` enum field
2. `requestRide` takes a `CabType` parameter
3. `MatchingStrategy` filters the snapshot pool by type before ranking
4. `PricingStrategy` becomes per-type — easiest path: `Map<CabType, PricingStrategy>` on the service, lookup by type

No interface changes — every extension lives on the strategies. *That's the payoff for putting Strategy on day one.*

### Q5. *Show me the concurrency proof.*

That's exactly what scenario 6 in the driver demonstrates:
- 10 drivers, 50 riders
- All 50 requests submitted simultaneously through a 20-thread pool
- `CountDownLatch` releases them in one burst
- Result: exactly 10 matched, exactly 40 rejected, **zero driver appears in two rides**

The invariant holds because `tryReserve()` is `synchronized(this)` and does compareAndSet semantics — only one caller transitions AVAILABLE → ON_TRIP.

### Q6. *What if the driver-pool snapshot is stale by the time the strategy ranks it?*

It is — and that's fine. The ranking is a hint, not a contract. By the time we're trying `tryReserve` on the first candidate, they might already be ON_TRIP (won by someone else). The for-loop just falls through to the next candidate. Worst case: we exhaust the ranked list and throw — rider retries (probably with a new snapshot that includes drivers who finished trips in the meantime).

The system is **eventually consistent on driver availability** — a fundamental property of optimistic concurrency. The alternative (lock the whole driver pool during matching) doesn't scale beyond ~10 req/sec.

### Q7. *Where does Observer / pub-sub fit?*

Three places — all *would* be Observer if the interviewer asks, but none in v1:
1. **Surge updates** — `SurgeMonitor` publishes, `CabBookingService` subscribes per zone.
2. **Ride lifecycle events** — Ride state changes fire events; subscribers include push-notification, analytics, billing, fraud-detection.
3. **Driver location updates** — published from driver apps, consumed by spatial index + ETA service.

Don't pre-bake any of this. Introduce when the interviewer asks "how do we notify the rider when the driver arrives?".

### Q8. *Test for the double-booking race — what would you write?*

Show scenario 6 verbatim. Senior-signal beats: 
- using `CountDownLatch` to synchronize the *burst* (not just `ExecutorService.submit` which lets the early threads finish before late ones start)
- asserting two invariants: count-matched AND uniqueness-of-driver-id-set
- counting `driver.getStatus()` over the full pool — if any "matched" driver shows AVAILABLE, the release path has a bug

### Q9. *What if I want to refund / cancel after complete?*

Out of scope for the state machine as drawn — COMPLETED → REFUNDED would need a new terminal-but-revisable status, or a separate Refund entity tied to a Ride. The senior answer: **Refunds belong in a separate Payment/Billing service**, not in the ride state machine. The ride is COMPLETED forever; the *payment* attached to it can be REFUNDED. Different aggregates, different lifecycles.

### Q10. *How do you stop a driver from going offline mid-trip?*

`Driver.goOffline()` rejects if `status == ON_TRIP` (already implemented). The senior beat: this is the same pattern as Ride's `transitionTo` — guards on state-mutating methods, not on getters. **Tell, don't ask.** Callers don't check "can I go offline?" — they just call `goOffline()` and handle the exception.

---

## Patterns Used (with timing)

| Pattern | Where | When introduced | Why |
|---------|-------|----------------|-----|
| **Strategy** | `DriverMatchingStrategy`, `PricingStrategy` | Day 1 | At least one extension is guaranteed (highest-rated, cab-type pricing). One-sentence test passes. |
| **Facade** | `CabBookingService` | Day 1 | Caller wants `requestRide` — doesn't compose registries + strategies themselves. |
| **State machine (enum)** | `RideStatus.canTransitionTo` | Day 1 | The invalid-transition exception is the value; per-state behavior is trivial. |
| **Tell-Don't-Ask** | `Driver.tryReserve`, `Ride.match/start/complete/cancel` | Day 1 | Callers mutate via lifecycle methods, never by setting status directly. |
| **Optimistic locking (ranked-list match)** | `requestRide` loop | Day 1 | Required for correctness under concurrency. Not a GoF pattern but the most important idiom in the problem. |

**Patterns I did NOT pre-bake:**
- **Class-per-state (GoF State)** — every Ride state transition is bookkeeping; no per-state behavior worth its own class
- **Observer** — only the surge/location/lifecycle pub-sub features need it. Introduce when those features come up.
- **Decorator** — would wrap PricingStrategy if we needed dynamic surge-on-top-of-base composition. Single multiplier in v1 → not yet.
- **Singleton** — `CabBookingService` is *usually* a singleton, but that's an instantiation choice, not a design pattern. Don't say "I'd make it a Singleton" — that's a tell.

---

## Top Mistakes to Avoid

1. **Matching strategy returns one Driver instead of a list.** The first call to `tryReserve` might fail and you have no fallback. **Always return ranked candidates.**
2. **Holding a lock across the strategy.rankCandidates call.** That serializes all matching. The strategy is pure — it takes a snapshot list and returns a sorted list. No locks needed inside it.
3. **Using `double` for fare.** Money is integer cents, always.
4. **Mutating `Ride.status` directly via a setter.** Then the state machine doesn't guard you. Make all mutation go through `transitionTo` (private) called from named lifecycle methods (`match`, `start`, `complete`, `cancel`).
5. **Forgetting to release the driver on cancel.** Driver stays ON_TRIP forever; goes invisible from the matching pool. Easy to miss.
6. **`tryReserve` returning a Driver instead of boolean.** The race-loser needs to know "did *I* win?" — that's a boolean. The Driver they were trying to reserve, they already have a reference to.
7. **No `maxRadiusKm` cap on NearestDriverStrategy.** Without it, a driver 500 km away gets matched to a rider in city center. Real apps use 3–5 km caps.
8. **Letting the driver go OFFLINE mid-trip.** Guard `goOffline()` against ON_TRIP status. Same idea — Tell, don't ask.

---

## Closing Soundbite (60 seconds)

> "The two design seams I pre-bake on day one are matching and pricing as Strategies — at least one extension is guaranteed in any cab-booking interview (surge, cab-types, rating-weighted). The single most important call is making `MatchingStrategy.rankCandidates` return a *list*, not a single driver — that's what lets `requestRide` walk the list calling `Driver.tryReserve()` until one wins the AVAILABLE → ON_TRIP race. `tryReserve` is `synchronized` on the driver instance and gives compareAndSet semantics — that's the no-double-booking invariant. Ride lifecycle is an enum-driven state machine, not class-per-state, because every transition is one line of bookkeeping. Surge is a basis-points integer passed at call time, not stored. Concurrency proof is empirical — 50 riders × 10 drivers in the driver scenario gives 10 matched, 40 rejected, zero duplicate driver-ids. For scale, the linear scan over AVAILABLE drivers becomes a quadtree or H3 spatial index — the Strategy interface absorbs that change with zero callsite impact."

---

## File Index

```
cabbooking/
├── model/
│   ├── Location.java               # record (lat, lng) + distanceKm
│   ├── Rider.java                  # entity
│   ├── Driver.java                 # entity + tryReserve() + releaseFromTrip()
│   ├── DriverStatus.java           # OFFLINE / AVAILABLE / ON_TRIP
│   ├── Ride.java                   # entity + lifecycle methods using transitionTo
│   └── RideStatus.java             # state machine: REQUESTED / MATCHED / IN_PROGRESS / COMPLETED / CANCELLED
├── matching/
│   ├── DriverMatchingStrategy.java # interface — returns RANKED LIST
│   └── NearestDriverStrategy.java  # Euclidean nearest with maxRadiusKm cap
├── pricing/
│   ├── PricingStrategy.java        # interface — surge passed at call time
│   └── DistanceBasedPricing.java   # baseFare + distance × perKm × surge
├── CabBookingService.java          # facade with optimistic-match loop
├── CabBookingDriver.java           # 7 scenarios incl. 50-rider concurrency proof
└── INTERVIEW_WALKTHROUGH.md        # this file
```
