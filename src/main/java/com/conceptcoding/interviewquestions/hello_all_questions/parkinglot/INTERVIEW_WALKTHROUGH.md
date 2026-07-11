# Parking Lot — 45-min LLD Interview Walkthrough

**Target role:** SDE-2 (Amazon, Microsoft, Adobe, Atlassian)

> The most-asked LLD problem in the industry. The interview rewards a small set of crisp decisions: **occupancy lives on the orchestrator (not the spot), money is `long` cents, ticket removal is what makes exit idempotent, and the concurrency race is between "find" and "mark occupied."**

---

## Time budget

| Step | Activity | Budget | Cumulative |
|------|----------|--------|------------|
| 1 | Requirements | ~5 min | 5 |
| 2 | Entities & relationships | ~4 min | 9 |
| 3 | Class design | ~12 min | 21 |
| 4 | Implementation + dry-run | ~16 min | 37 |
| 5 | Extensibility | ~7 min | 44 |
| — | Wrap | ~1 min | 45 |

---

## Mental models — memorize before you walk in

### M1. Two-phase parking session lifecycle

```
   enter(vehicleType)                        exit(ticketId)
        │                                          │
        v                                          v
   +-----------+   parked   +-----------+    +-----------+
   |  ISSUED   | ========>  |  ACTIVE   | -> |  EXITED   |
   |  ticket   | (in lot)   | (driving) |    | (removed) |
   +-----------+            +-----------+    +-----------+

   Enter side:                              Exit side:
     spot = findAvailableSpot(type)           ticket = activeTickets[id]  (null? throw)
     occupiedSpotIds.add(spot.id)             fee = computeFee(entry, now)
     activeTickets[id] = ticket               occupiedSpotIds.remove(spot.id)
                                              activeTickets.remove(id)   ← makes exit idempotent
```

**Invariant:** a spot is in `occupiedSpotIds` iff some live ticket references it. Removing the ticket from the map is what makes double-exit a "not found" error automatically.

### M2. Where does occupancy live? — Relational vs intrinsic (THE key insight)

This is the single most-discussed design decision at SDE-2 for this problem.

```
   intrinsic state                          relational state
   ─────────────────                        ─────────────────
   "property of THIS entity"                "RELATIONSHIP between entities"
   lives ON the entity                      lives ON the orchestrator

   Amazon Locker compartment:               Parking Lot spot:
     boolean isOccupied on the Compartment    Set<String> occupiedSpotIds on ParkingLot
     (package physically inside or not)       (a ticket is assigned to this spot —
                                               occupied the MOMENT we mint the ticket,
                                               before the car even parks)
```

**Senior soundbite:** *"In Locker, occupancy is intrinsic — a package is physically inside or not. In Parking Lot, occupancy is relational — the spot becomes 'occupied' the moment we assign a ticket to it. So I put it where the relationship is managed: on the orchestrator, not the spot."*

### M3. Two lookup directions → two data structures

```
  ENTRY                          EXIT
  ─────                          ────
  Has: a VehicleType             Has: a ticket id (string)
  Needs: any compatible spot     Needs: THE specific Ticket

     linear scan                    hash lookup
     List<ParkingSpot>              Map<String, Ticket>
     O(C)                           O(1)
```

The workflow demands exactly these two structures — nothing more.

---

## Step 1 — Requirements (~5 min)

### Clarifying dialogue

**You:** *"Vehicle enters → system picks a compatible spot → returns a ticket. At exit, ticket → fee + spot freed. Is that the shape?"*
**Interviewer:** *"Yes."*

**You:** *"Vehicle types — I'll assume MOTORCYCLE / CAR / LARGE with matching spot types. Is a strict 1-to-1 mapping OK, or do we allow fallbacks (motorcycle in car spot)?"*
**Interviewer:** *"Strict 1-to-1 for v1."*
> Fallback logic is a Step-5 answer.

**You:** *"Pricing — flat hourly rate for all vehicle types, or per-type? Round up partial hours?"*
**Interviewer:** *"Flat hourly, round up, pay at exit."*
> Simplifies to one `long hourlyRateCents` field. Per-type pricing is a Step-5 Strategy.

**You:** *"Error handling — reject entry if lot is full for that type; reject exit on null / unknown / already-used ticket?"*
**Interviewer:** *"Yes, throw with a clear message."*

**You:** *"Scope — backend logic only, no payment processing, gate hardware, or lost-ticket flows?"*
**Interviewer:** *"Correct."*

**You:** *"Concurrency — can multiple gates admit vehicles simultaneously?"*
**Interviewer:** *"Assume single-gate for v1, but we might discuss multi-gate later."*
> Signals Step-5 concurrency discussion.

### Requirements to write down

```
IN SCOPE
1. Three vehicle types (MOTORCYCLE / CAR / LARGE) with matching spot types.
2. On entry: assign a compatible free spot, issue a ticket.
3. Ticket holds: id, spotId, vehicleType, entryTime.
4. On exit: validate ticket, compute fee (flat hourly, round up), free the spot.
5. Reject entry when no compatible spot is available.
6. Reject exit on null / empty / unknown / already-used ticket.

OUT OF SCOPE
- Payment processing, gate hardware, UI, cameras
- Reservations / lost-ticket recovery
- Multi-floor (Step 5)
- Per-type or surge pricing (Step 5)
- Concurrency (Step 5 discussion)
```

---

## Step 2 — Entities & relationships (~4 min)

```
Entities
- ParkingLot    orchestrator + facade — enter, exit, fee, owns occupancy
- ParkingSpot   physical slot — id + SpotType, no occupancy state
- Ticket        immutable session record — id, spotId, vehicleType, entryTime

Enums
- SpotType    { MOTORCYCLE, CAR, LARGE }
- VehicleType { MOTORCYCLE, CAR, LARGE }    ← separate from SpotType for OCP

Relationships
- ParkingLot owns  List<ParkingSpot>
- ParkingLot owns  Map<String, Ticket> activeTickets
- ParkingLot owns  Set<String> occupiedSpotIds       ← relational state
- Ticket refs      ParkingSpot BY spotId (string), not by object
```

**Not entities:** `Vehicle`, `Customer`, `Gate`, `Cashier` — either actors or things with no state we care about. `VehicleType` is enough.

**Why separate `SpotType` and `VehicleType` enums even though they're identical today?**
> *"If tomorrow the spec says 'motorcycles can use car spots when motorcycle spots are full', the mapping lives in one place — `mapVehicleTypeToSpotType`. The two enums can drift independently. Open/Closed."*

**Why does Ticket store `spotId` (string) not `ParkingSpot` (object)?**
> *"Law of Demeter. Tickets are records — they shouldn't navigate the domain model. Storing the id prevents `ticket.getSpot().setOccupied(true)` style abuse."*

### Class-diagram sketch

```
                  +------------------------------+
                  |          ParkingLot          |   ← orchestrator + facade
                  |  enter / exit                |
                  +------------------------------+
                       │          │          │
                 owns  │   owns   │   owns   │
                       v          v          v
            +------------------+ +--------------------+ +------------------+
            | List<ParkingSpot>| | Map<id, Ticket>    | | Set<spotId>      |
            +------------------+ | activeTickets      | | occupiedSpotIds  |
                  │              +--------------------+ +------------------+
                  v element                  │ value
            +-----------------+               v
            |  ParkingSpot    |         +----------------+
            |  id, spotType   |<--------| Ticket         |   refs by spotId (String),
            +-----------------+  spotId | id, spotId,    |   NOT by object reference
                                        | vehicleType,   |
                                        | entryTime      |
                                        +----------------+
```

---

## Step 3 — Class design (~12 min)

### ParkingLot — state derived from requirements

| Requirement | State ParkingLot must own |
|-------------|--------------------------|
| Assign a compatible spot | `List<ParkingSpot> spots` |
| Track which spots are taken | `Set<String> occupiedSpotIds` (relational) |
| Validate tickets on exit | `Map<String, Ticket> activeTickets` |
| Compute fee | `long hourlyRateCents` |

> **Money is `long` cents, never `double` dollars.** *"$5.47 becomes 547 cents. Floats can't represent 0.10 exactly — we'd accumulate error on every transaction."*

### ParkingLot — public API (just two methods)

| Need | Method |
|------|--------|
| Vehicle enters | `Ticket enter(VehicleType type)` |
| Vehicle exits | `long exit(String ticketId)` — returns fee in cents |

Resist the urge to add `getAvailableSpots()` / `getStatus()` — they leak state and aren't in the requirements.

### ParkingLot — outline to write on the board

```java
public class ParkingLot {
    private final List<ParkingSpot>    spots;
    private final Map<String, Ticket>  activeTickets   = new HashMap<>();
    private final Set<String>          occupiedSpotIds = new HashSet<>();
    private final long                 hourlyRateCents;

    public ParkingLot(List<ParkingSpot> spots, long hourlyRateCents) {
        this.spots = spots;
        this.hourlyRateCents = hourlyRateCents;
    }

    public Ticket enter(VehicleType type) { /* Step 4 */ }
    public long   exit(String ticketId)    { /* Step 4 */ }

    // Internals
    private ParkingSpot findAvailableSpot(VehicleType type);
    private SpotType    mapToSpotType(VehicleType type);
    private long        computeFee(LocalDateTime entry, LocalDateTime exit);
}
```

### ParkingSpot & Ticket — pure data holders

```java
public class ParkingSpot {                       // intrinsic data only — no occupancy field
    private final String   id;
    private final SpotType spotType;
    // ctor + getters
}

public final class Ticket {                      // immutable
    private final String        id;
    private final String        spotId;          // string, not object (Law of Demeter)
    private final VehicleType   vehicleType;
    private final LocalDateTime entryTime;
    // ctor + getters
}
```

### Principle to say out loud

> *"Information Expert — pricing is system policy (on ParkingLot), spot type is intrinsic (on ParkingSpot), occupancy is relational (on ParkingLot, not Spot). That's the opposite of Locker where occupancy is physical."*

---

## Step 4 — Implementation + dry-run (~16 min)

### 4.1 `enter` — find spot, mark occupied, mint ticket

```java
public Ticket enter(VehicleType vehicleType) {
    ParkingSpot spot = findAvailableSpot(vehicleType);
    if (spot == null) {
        throw new NoSuchElementException("No available spot for " + vehicleType);
    }

    String ticketId = UUID.randomUUID().toString();
    Ticket ticket = new Ticket(ticketId, spot.getId(), vehicleType, LocalDateTime.now());
    occupiedSpotIds.add(spot.getId());
    activeTickets.put(ticketId, ticket);
    return ticket;
}

private ParkingSpot findAvailableSpot(VehicleType vehicleType) {
    SpotType required = mapToSpotType(vehicleType);
    for (ParkingSpot spot : spots) {
        if (spot.getSpotType() == required && !occupiedSpotIds.contains(spot.getId())) {
            return spot;                        // first-fit linear scan
        }
    }
    return null;
}
```

> *"All-or-nothing — if `findAvailableSpot` returns null we throw before mutating any state. If we mutated first and rolled back, we'd have to think about partial-failure cleanup."*

### 4.2 `exit` — lookup, compute fee, free spot, REMOVE ticket

```java
public long exit(String ticketId) {
    if (ticketId == null || ticketId.isEmpty()) {
        throw new IllegalArgumentException("Invalid ticket id");
    }
    Ticket ticket = activeTickets.get(ticketId);
    if (ticket == null) {
        throw new NoSuchElementException("Ticket not found or already used");
    }

    long fee = computeFee(ticket.getEntryTime(), LocalDateTime.now());
    occupiedSpotIds.remove(ticket.getSpotId());
    activeTickets.remove(ticketId);              // ← makes exit idempotent
    return fee;
}
```

> **Senior callout:** *"Removing the ticket is what makes the API idempotent against double-exit — the same trick as Locker.pickup. We deliberately don't distinguish 'never existed' from 'already used' — both are 'invalid ticket' to the caller."*

### 4.3 `computeFee` — round up partial hours

```java
private long computeFee(LocalDateTime entryTime, LocalDateTime exitTime) {
    long minutes = Duration.between(entryTime, exitTime).toMinutes();
    long hours = (minutes + 59) / 60;          // ceiling division — 61 min → 2 hours
    if (hours == 0) hours = 1;                 // minimum 1-hour charge
    return hours * hourlyRateCents;
}
```

> **Two soundbites:**
> - *"Ceiling division `(minutes + 59) / 60` is cleaner than a modulo check. 60 min → 1h, 61 min → 2h."*
> - *"The `hours == 0` guard covers a 0-minute exit — minimum 1-hour charge even for instant reversal."*

### 4.4 Dry-run — 2.5-hour stay (say this at the board)

```
Setup: spots = [M1:MOTORCYCLE, C1:CAR, C2:CAR, L1:LARGE]
       hourlyRateCents = 500 (₹5/hr)
       clock @ 08:00

enter(CAR):
   findAvailableSpot(CAR) → C1 (matches, not occupied)
   ticketId = "T1"
   activeTickets = { T1 → (T1, C1, CAR, 08:00) }
   occupiedSpotIds = { "C1" }
   return T1                                                              ✓

[2.5 hours pass — clock @ 10:30]

exit("T1"):
   activeTickets["T1"] → found
   computeFee(08:00, 10:30):
     minutes = 150
     hours = (150 + 59) / 60 = 3     ← ceiling
     fee = 3 * 500 = 1500                                                  ✓
   occupiedSpotIds = {}, activeTickets = {}
   return 1500

exit("T1") again:
   activeTickets["T1"] → null → throw NoSuchElement                        ✓

Fill both car spots, then enter(CAR):
   findAvailableSpot → null → throw NoSuchElement("No available spot")    ✓
```

---

## Step 5 — Extensibility (~7 min)

### E1. "Multi-floor parking garage"

Introduce `ParkingFloor` between Lot and Spot. `ParkingLot` owns `List<ParkingFloor>`; each Floor owns its own spots. Spot id becomes `"floor-section-slot"`, e.g. `"3-A-15"`. `findAvailableSpot` iterates floors in order (or via a `FloorSelectionStrategy` — lowest-first, most-empty-first). Ticket unchanged.

### E2. "Different pricing per vehicle type"

```java
private final Map<VehicleType, Long> hourlyRates;    // replace the single long
// computeFee uses ticket.getVehicleType() → rate lookup
```

If pricing gets complex (surge, discounts, EV-charging), promote to `PricingStrategy`:

```java
public interface PricingStrategy {
    long compute(Ticket ticket, LocalDateTime exit);
}
class FlatHourlyPricing implements PricingStrategy { /* current logic */ }
class SurgePricing       implements PricingStrategy { /* rate × surgeMultiplier */ }
```

For 3 fixed rates, the map is right-sized. Strategy is right at 3+ policies.

### E3. "Concurrent entry — two gates racing"

**The race:** two threads pass `findAvailableSpot` before either calls `add(spotId)` — both get the same spot, second ticket silently overwrites the first.

**Simplest fix (interview-ready):**

```java
public synchronized Ticket enter(VehicleType type) { /* ... */ }
public synchronized long   exit(String ticketId)   { /* ... */ }
```

Operations are short (no I/O), contention is low.

**Higher-throughput alternative — atomic set add:**

```java
private final Set<String> occupiedSpotIds = ConcurrentHashMap.newKeySet();

private ParkingSpot findAvailableSpot(VehicleType type) {
    SpotType required = mapToSpotType(type);
    for (ParkingSpot spot : spots) {
        if (spot.getSpotType() != required) continue;
        if (occupiedSpotIds.add(spot.getId())) {    // atomic claim; false if already taken
            return spot;
        }
    }
    return null;
}
```

**Say aloud:** *"The race we're fixing is the gap between 'check available' and 'mark occupied'. Method-level `synchronized` is correct and simple; the atomic-add version pushes the lock down for higher throughput."*

### E4. "Different allocation rules — best-fit, random, proximity-based"

Extract `SpotLookupStrategy`:

```java
public interface SpotLookupStrategy {
    ParkingSpot findSpot(List<ParkingSpot> spots, Set<String> occupied, SpotType required);
}
class FirstFitLookupStrategy implements SpotLookupStrategy { /* current loop */ }
class RandomLookupStrategy   implements SpotLookupStrategy { /* random pick */ }
class BestFitLookupStrategy  implements SpotLookupStrategy { /* closest to entrance */ }
```

Inject into ParkingLot; default stays first-fit. Adding a 4th policy = 1 new class, no touches to existing ones.

**Say aloud:** *"Enum + switch works for 2-3 policies but violates OCP at 4+, and each policy can't carry its own state (random needs its `Random` instance, best-fit wants an entrance reference). Strategy scales; switch doesn't."*

### E5. Other one-liners

| Follow-up | Answer |
|-----------|--------|
| "Motorcycles can use car spots when full" | `mapToSpotType` returns an ordered fallback list; `findAvailableSpot` iterates. Call out that cars might get locked out — policy choice. |
| "Reserved / paid-monthly spots" | Add `reservedFor: UserId` on Spot OR a `reservedSpotIds` set. Skip in `findAvailableSpot` unless the caller is the reservee. |
| "Lost ticket" | Charge a flat lost-ticket fee, OR look up by license plate if we captured it. |
| "Persist across restart" | Inject a `LotRepository`; write on every enter/exit, replay on boot. |
| "Notify customer when fee is high" | Observer — Lot publishes `VehicleExited(fee)`; listeners subscribe. |

---

## Design patterns in play (name these out loud in the interview)

### In the BASE design — mention in Step 2 or Step 3

| Pattern / Principle | Where it lives | One-line justification |
|---------------------|----------------|------------------------|
| **Facade** | `ParkingLot` | *"Callers only touch ParkingLot — 2 methods hide 3 collaborators (spots list, tickets map, occupancy set)."* |
| **Information Expert** (GRASP) | Pricing on Lot, spot type on Spot | *"Each rule lives where its data lives."* |
| **Tell, Don't Ask** | Lot calls `spot.getSpotType()` | *"Never reads spot internals or mutates them from outside."* |
| **Immutability** | `Ticket`, `ParkingSpot` | *"All fields `final`. Ticket is an immutable session record."* |
| **Relational State** | `occupiedSpotIds` on Lot, not on Spot | *"Occupancy is a relationship (ticket → spot), not intrinsic to the spot — so it lives on the orchestrator. Opposite of Locker."* |

**No Strategy in the base — deliberately.** *"Requirements just say 'assign any compatible spot'. First-fit is right-sized. If Step 5 asks for best-fit / random / proximity, that's a clean SpotLookupStrategy extraction."*

### Patterns for Step 5 extensibility

| Follow-up trigger | Pattern | The one-line move |
|-------------------|---------|-------------------|
| "Different allocation rules (best-fit, random, proximity)" | **Strategy** ⭐ | *"`SpotLookupStrategy` — `FirstFit` (default), `Random`, `BestFit`. Inject at construction; each carries its own state (Random's `Random`, BestFit's entrance ref)."* |
| "Different pricing (per-type, surge, discount)" | **Strategy** ⭐ | *"`PricingStrategy` — `FlatHourly`, `Surge`, `TierBased`. For 2-3 policies a `Map<VehicleType, Long>` is right-sized; Strategy at 3+."* |
| "Multi-floor garage" | **Composition + Facade** | *"Introduce `ParkingFloor` between Lot and Spot. Lot stays the facade; each Floor owns its spots. Optional `FloorSelectionStrategy` (lowest-first, most-empty-first)."* |
| "Notify customers on entry/exit" | **Observer** | *"Lot publishes `VehicleEntered` / `VehicleExited` events; SMS / Email / Analytics subscribe independently."* |
| "Multiple kinds of lots (commercial, residential, EV)" | **Factory** | *"`LotFactory.create(kind)` returns a preconfigured Lot; callers stop depending on concrete types."* |
| "Persist across restart" | **Repository** | *"Inject `LotRepository`; write on every enter/exit; replay on boot to rebuild occupancy."* |
| "Motorcycles can use car spots when full" | Extend `mapToSpotType` | *"Return an ordered fallback list; `findAvailableSpot` iterates. Call out that cars might be locked out — policy choice."* |

### Patterns to actively refuse

- **Singleton on ParkingLot** — kills tests, breaks multi-lot extension. DI a single instance.
- **State pattern on ParkingSpot** — no per-state behavior; an occupied set is correct.
- **Builder for `ParkingLot(spots, rate)`** — 2 args. Academic noise.
- **Factory for `SpotType` / `VehicleType`** — they're enums.
- **Visitor over spots** — homogeneous list, no benefit.

### The rule to sound natural

1. **Cap at 2 patterns in the base.** Parking Lot lands on Facade only. Everything else is Step 5.
2. **Pair the pattern name with a concrete win.** *"Strategy — because allocation rules vary at runtime"* > *"I'd use Strategy."*
3. **Never volunteer a pattern without a requirement pressing on it.**

---

## What is expected at each level

### Junior (SDE-1)
- Arrives at 3 classes (Lot, Spot, Ticket) with a nudge; may put `boolean occupied` on Spot instead of a set on Lot.
- Implements happy-path `enter` and `exit`; may forget to remove the ticket on exit (so double-exit slips through).
- Uses `double` for money until corrected.
- Fee math has an off-by-one at the hour boundary; needs prompting to round up.

### Mid-level (SDE-2) — the target
- Names the relational-vs-intrinsic occupancy insight unprompted; puts occupancy on the orchestrator.
- Money is `long` cents from the start.
- Ceiling division for partial-hour rounding + minimum-1-hour guard.
- Removes the ticket on exit as a deliberate idempotency mechanism, not by accident.
- Separates `SpotType` and `VehicleType` enums with an explicit "for OCP" reasoning.
- Runs the dry-run out loud.

### Senior (SDE-3 / SDE-II)
- Everything mid-level, faster, and with proactive tradeoffs.
- Contrasts with a peer problem: *"Occupancy is relational here; in Locker it's intrinsic on the compartment."*
- Discusses the entry-race (find vs mark-occupied) as a concrete two-thread scenario before the interviewer asks about concurrency.
- Deliberately doesn't extract Strategy in the base — *"Not because I don't know it; because the requirements don't demand it yet."* Extracts it cleanly on the Step-5 prompt.
- Catches subtle bugs: partial-hour boundary, `Set<String>` vs concurrent set for multi-gate, ticket-object-vs-id.
- Finishes early; uses buffer to discuss the two data-structure trade (list scan for entry, hash for exit).

---

## Interview deep-dives

### Complexity

| Operation | Time | Space |
|-----------|------|-------|
| `enter` | **O(C)** — linear scan + O(1) inserts | O(1) |
| `exit` | **O(1)** — map lookup + removes | O(1) |
| `computeFee` | **O(1)** — integer arithmetic | O(1) |
| Storage: spots | — | O(C) |
| Storage: tickets | — | O(T) ≤ O(C) |

> **Senior callout:** *"If C is large and we want `enter` at O(1) too, maintain `Map<SpotType, Queue<String>> freeSpotsByType`. Costs one more invariant to keep in sync."*

### Concurrency — code (already covered in E3)

### One test worth memorizing (boundary case)

```java
@Test
void one_millisecond_past_hour_rounds_up_to_two() {
    // 61 minutes → 2 hours → 2 × 500 = 1000 cents
    Ticket t = lot.enter(VehicleType.CAR);
    // (with an injectable clock, advance 61 minutes)
    assertEquals(1000L, lot.exit(t.getId()));
}
```

*Note: production code uses `LocalDateTime.now()` directly. To make time testable, you'd inject a `Clock` and use `LocalDateTime.now(clock)` — that's a Step-5 refactor, not required in the base.*

---

## 30-second summary (memorize for closing)

> *"Three classes: ParkingLot (orchestrator + facade), ParkingSpot (id + type, no occupancy), Ticket (immutable session record). ParkingLot owns the spots list, a `Set<String>` of occupied ids — RELATIONAL state, distinct from Locker where occupancy is intrinsic on the compartment — and a `Map<id, Ticket>` for O(1) exit lookup. `enter` first-fit-scans for a compatible spot, mints a UUID ticket, adds to both collections. `exit` looks up the ticket, computes fee with ceiling division on minutes, frees the spot, and REMOVES the ticket — that removal is what makes double-exit throw automatically. Money is `long` cents. Separate `SpotType` and `VehicleType` enums for OCP. I deliberately didn't extract Strategy in the base — first-fit is correct for these requirements; if best-fit or surge pricing lands as a follow-up, that's a clean extraction."*

---

## Top mistakes that lose points

- **Money as `double`** — instant red flag. `long` cents always.
- **`boolean occupied` on ParkingSpot** — misses the relational-state insight. If you do this, at least *name* the trade-off.
- **Forgetting to remove the ticket on exit** — double-exit slips through undetected.
- **`getAvailableSpots()` / `getStatus()` methods** — leaks state; not in requirements.
- **Same enum for SpotType and VehicleType** — locks you out of "motorcycles in car spots later."
- **Adding `Vehicle` / `Customer` / `Gate`** — actors or state-free noise, not entities.
- **Pattern-stuffing in Step 3** — Strategy on pricing, Factory on spots, Builder on Ticket. Save patterns for Step 5.
- **No dry-run** — the boundary math (2.5h → 3h charge) is exactly where interviewers probe.

---

## Files in this folder

| File | Purpose |
|------|---------|
| `model/SpotType.java` | Enum — MOTORCYCLE / CAR / LARGE |
| `model/VehicleType.java` | Enum — separate for OCP |
| `model/ParkingSpot.java` | Pure data — id + spotType |
| `model/Ticket.java` | Immutable session record |
| `ParkingLot.java` | Orchestrator + facade — `enter` / `exit` / first-fit lookup / fee |
| `ParkingLotDriver.java` | Scenarios — happy path, double exit, full lot, instant exit, invalid |

Run:
```bash
mvn -q compile exec:java \
  -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.parkinglot.ParkingLotDriver
```
