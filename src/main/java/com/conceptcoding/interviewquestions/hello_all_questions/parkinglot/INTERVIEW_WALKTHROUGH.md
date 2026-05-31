# Parking Lot — 45-min LLD Interview Walkthrough

**Target role:** SDE‑2 (Amazon, Adobe, Microsoft, Atlassian, etc.)
**Source method:** Hello Interview *Delivery Framework* applied to the *Parking Lot* problem breakdown.

> Parking Lot is the most-asked LLD problem in the industry, period. The interview rewards a small set of crisp decisions: where occupancy lives, money as cents (never floats), single-mutation entry/exit methods, and an honest concurrency discussion. Get these right and the rest is easy.

---

## Time budget (45 min)

| Step | Activity                                                  | Budget   | Cumulative |
| ---- | --------------------------------------------------------- | -------- | ---------- |
| 1    | Requirements                                              | ~5 min   | 5          |
| 2    | Entities & Relationships                                  | ~4 min   | 9          |
| 3    | Class Design (state + behavior)                           | ~12 min  | 21         |
| 4    | Implementation (`enter`, `exit`, fee, dry run)            | ~16 min  | 37         |
| 5    | Extensibility (3–4 follow-ups)                            | ~7 min   | 44         |
| —    | Wrap & questions                                          | ~1 min   | 45         |

Watch the clock at minute **5** (Step 1 done), minute **21** (start coding), minute **37** (extensibility).

---

## Mental models — internalize these BEFORE you walk in

Three pictures unlock this problem. If you can draw them from memory, the code falls out.

### M1. The two-phase parking session lifecycle

```
   enter(vehicleType)                        exit(ticketId)
        |                                          |
        v                                          v
   +-----------+   parked   +-----------+    +-----------+
   |  ISSUED   | ========>  |  ACTIVE   | -> |  EXITED   |
   |  ticket   | (in lot)   | (driving) |    | (removed) |
   +-----------+            +-----------+    +-----------+
        |                                          |
        | spotId, vehicleType, entryTimeMs         |
        |                                          v
        |                                  fee = computeFee(entry, now)
        |                                  occupiedSpotIds.remove(spotId)
        v                                  activeTickets.remove(ticketId)
   activeTickets[id] = ticket
   occupiedSpotIds.add(spotId)


   Invariant: a spot is in occupiedSpotIds  iff  some live ticket references it.
   Removing the ticket from the map is what makes "exit" idempotent — a second
   exit with the same id falls through the "not found" branch.
```

### M2. Where does occupancy live? (Relational vs. intrinsic state)

This is the single most-discussed design decision at SDE‑2 level for this problem. Memorize the answer.

```
   intrinsic state                          relational state
   ----------------                          ----------------
   "is property of THIS entity"             "is a RELATIONSHIP between entities"
   lives ON the entity                       lives in the orchestrator
                                            
   Amazon Locker compartment:               Parking Lot spot:
     boolean isOccupied                       NOT on ParkingSpot
     (physical state — pkg                    Set<String> occupiedSpotIds
     is or isn't in the box)                  ON ParkingLot
                                              (assignment — a ticket
                                              references this spot)
```

**Senior soundbite (memorize):** *"In Locker, occupancy is intrinsic — a package is physically inside or not. In Parking Lot, occupancy is relational — the spot is 'occupied' the moment we assign a ticket to it, before the car even parks. So I put it where the relationship is managed: on the orchestrator, not the spot."*

### M3. The two lookup directions

```
  Entry flow                vs.          Exit flow
  -----------                              -----------
  Has: a VehicleType                       Has: a ticket id (string)
  Needs: any compatible spot               Needs: the specific Ticket

       linear scan                              hash lookup
       List<ParkingSpot>                        Map<String, Ticket>
       O(C)                                     O(1)
```

This is the same shape as the Amazon Locker pattern: list-scanned by category, hash-looked up by id. Two data structures, exactly the ones the workflow demands.

---

## STEP 1 — Requirements (~5 min)

### What to say out loud (opener)
> "Before I start designing, let me clarify scope and rules so we're aligned on what 'done' looks like."

### Probe the 4 themes

| Theme               | Question to ask                                                                                              |
| ------------------- | ------------------------------------------------------------------------------------------------------------ |
| Primary capabilities| "Vehicle enters → system picks a compatible spot → returns a ticket. At exit, ticket → fee + spot freed. Right?" |
| Rules / completion  | "Pricing: hourly, same rate for all sizes, round up to the next hour, pay at exit?"                          |
| Error handling      | "Reject entry if lot is full for that vehicle type; reject exit on null / unknown / already-used ticket?"    |
| Scope boundaries    | "Backend logic only — no payment processing, no gate hardware, no UI, no lost-ticket recovery — confirm?"    |

### What to write on the board

```
Functional Requirements
1. Three vehicle types: MOTORCYCLE, CAR, LARGE; matching spot types.
2. On entry, system assigns an available compatible spot and issues a ticket.
3. Ticket holds: ticket id, spot id, vehicle type, entry timestamp.
4. On exit, system validates the ticket, computes fee (hourly, round up), frees the spot.
5. Hourly rate is the SAME for all vehicle types (keep it simple).
6. Reject entry if no compatible spot is available.
7. Reject exit if ticket is null, empty, unknown, or already used.

Out of Scope
- Payment processing
- Physical entry/exit gates and hardware
- Cameras / sensors / monitoring
- UI / display
- Reservations or pre-booking
- Lost-ticket recovery
- Surge / discount pricing (keep simple)
```

### Close the step
> "Does this match what you had in mind? Anything you'd add before I move to entities?"

---

## STEP 2 — Entities & Relationships (~4 min)

### What to say out loud
> "From these requirements, three classes: **ParkingLot**, **ParkingSpot**, **Ticket**. ParkingLot is the orchestrator and effectively a facade — it owns spots, tracks occupancy, mints tickets, calculates fees. ParkingSpot is the physical slot — id and type, no occupancy state. Ticket is the immutable record of a parking session."

### Why no `Vehicle` class
> "I considered Vehicle, but it has no state we care about and no rules to enforce — the driver just tells us a type. So `VehicleType` is an enum, not a class. Same reasoning rules out `Customer`, `Gate`, and `Cashier` — actors, not entities."

This is the explicit mid/senior signal: arrive at the 3-class model by *reasoning*, not by pre-building empty classes.

### What to write on the board

```
Entities
- ParkingLot    (orchestrator: enter, exit, fee, occupancy)
- ParkingSpot   (physical slot: id + SpotType)         <- intrinsic state only
- Ticket        (session record: id, spotId, VehicleType, entryTimeMs)

Enums
- SpotType    { MOTORCYCLE, CAR, LARGE }
- VehicleType { MOTORCYCLE, CAR, LARGE }     <- separate from SpotType for OCP

Relationships
- ParkingLot owns       List<ParkingSpot>
- ParkingLot owns       Map<String, Ticket>   (ticket id -> ticket)
- ParkingLot owns       Set<String>           (occupiedSpotIds — RELATIONAL state)
- Ticket    references  ParkingSpot          (by spotId STRING, not object — LoD)
```

> **Should I pre-bake a Strategy for spot lookup?** Apply the one-sentence test: *"Can I state the concrete design pressure that motivates the pattern, right now, in one sentence?"* For Parking Lot, the answer is **no** — the requirements just say "assign any compatible spot", with no signal of multiple policies. So we hardcode first-fit linear scan in the base. **If** the interviewer prompts "what about different allocation rules?" in Step 5, that's the moment to extract `SpotLookupStrategy`. See §5.4 for the refactor sketch.

### Diagram — boxes and arrows

```
                  +------------------------------+
                  |          ParkingLot          |   <- orchestrator + facade
                  |  enter / exit / fee          |
                  +------------------------------+
                       |          |          |
                 owns  |   owns   |   owns   |
                       v          v          v
            +------------------+ +------------------------+ +------------------+
            | List<ParkingSpot>| | Map<id, Ticket>        | | Set<spotId>      |
            +------------------+ | (activeTickets)        | | (occupiedSpotIds)|
                  |              +------------------------+ +------------------+
                  | element                |
                  v                        | value
            +-----------------+            v
            |  ParkingSpot    |     +----------------+
            |  id, spotType   |<----| Ticket         |   refs by id (string),
            +-----------------+ id  | id, spotId,    |   not by object reference
                                    | vehicleType,   |
                                    | entryTimeMs    |
                                    +----------------+
```

> **Why separate `SpotType` and `VehicleType` enums?** They look identical today, but they're *semantically distinct*. If tomorrow the spec says *"motorcycles can use car spots when motorcycle spots are full"*, the mapping lives in one place (`mapVehicleTypeToSpotType`) — and the two enums can drift independently. **Open/Closed.**

> **Why does Ticket store `spotId` (string) and not `ParkingSpot` (object)?** Law of Demeter. Tickets are records — they shouldn't navigate the domain model. Storing the id prevents `ticket.getSpot().setOccupied(true)` style abuse.

---

## STEP 3 — Class Design (~12 min)

### Work top-down: ParkingLot → ParkingSpot → Ticket.

### ParkingLot — state ↔ requirement table

| Requirement                              | State ParkingLot must own                              |
| ---------------------------------------- | ------------------------------------------------------ |
| Assign compatible spot                   | `List<ParkingSpot> spots`                              |
| Track which spots are taken              | `Set<String> occupiedSpotIds` (relational)             |
| Validate tickets on exit                 | `Map<String, Ticket> activeTickets`                    |
| Compute fee                              | `long hourlyRateCents` (pricing is system policy)      |
| Compute fee depends on "now"             | `Clock clock` (injected — makes time testable)         |

> **Money is `long cents`, never `double dollars`.** Floats can't represent `0.10` exactly. Memorize this line: *"$5.47 becomes 547 cents. All arithmetic stays exact."*

### ParkingLot — behavior table

| Need from requirements              | Method on ParkingLot                  |
| ----------------------------------- | ------------------------------------- |
| Vehicle enters                      | `Ticket enter(VehicleType type)`     |
| Vehicle exits                       | `long exit(String ticketId)` (returns fee in cents) |

That's the **entire** public API. Two methods. Resist the urge to add `getAvailableSpots()` / `getStatus()` — they leak state and aren't in the requirements.

### ParkingLot — class outline (write this on the board)

```java
public class ParkingLot {
    // ----- State -----
    private final List<ParkingSpot>      spots;
    private final Set<String>             occupiedSpotIds;
    private final Map<String, Ticket>    activeTickets;
    private final long                    hourlyRateCents;
    private final Clock                   clock;        // injected — testability

    // ----- Behavior -----
    public Ticket enter(VehicleType type) { /* Step 4 */ }
    public long   exit(String ticketId)   { /* Step 4 */ }
}
```

> **Note on Strategy:** `findAvailableSpot` is hardcoded to first-fit linear scan in the base. The pattern (`SpotLookupStrategy`) lands only if the interviewer asks for variants in Step 5 — see §5.4 for the refactor sketch. The senior signal here is *restraint*: not over-engineering until pressure shows up.

### ParkingSpot — outline

```java
public class ParkingSpot {
    private final String   id;
    private final SpotType spotType;
    // ctor + getters only — pure data holder
}
```

### Ticket — outline (immutable)

```java
public final class Ticket {
    private final String      id;
    private final String      spotId;
    private final VehicleType vehicleType;
    private final long        entryTimeMs;
    // ctor + getters only — immutable record
}
```

### Diagram — class cards

```
+----------------------------------+   +-------------------+   +-----------------------+
|            ParkingLot            |   |   ParkingSpot     |   |       Ticket          |
+----------------------------------+   +-------------------+   +-----------------------+
| - spots: List<ParkingSpot>       |   | - id: String      |   | - id: String          |
| - occupiedSpotIds: Set<String>   |   | - spotType:       |   | - spotId: String      |
| - activeTickets: Map<id, Ticket> |   |     SpotType      |   | - vehicleType: VType  |
| - hourlyRateCents: long          |   +-------------------+   | - entryTimeMs: long   |
| - clock: Clock                   |   | + getId()         |   +-----------------------+
+----------------------------------+   | + getSpotType()   |   | (immutable — getters) |
| + enter(type): Ticket            |   +-------------------+   +-----------------------+
| + exit(ticketId): long           |
+----------------------------------+

ParkingLot --owns--> spots, activeTickets, occupiedSpotIds
Ticket    --refs--> ParkingSpot (by spotId STRING, not by object — Law of Demeter)
```

### The principle to verbalize — Information Expert
> "Pricing is system policy, so it lives on ParkingLot. Spot type is intrinsic to a spot, so it lives on ParkingSpot. Occupancy is relational — 'a ticket is currently assigned to this spot' — so it lives on ParkingLot, not on ParkingSpot. That's the **opposite** of the Locker design where occupancy was physical, and I'd call out the difference in the interview."

---

## STEP 4 — Implementation (~16 min)

### Open by asking
> "Real Java or pseudo-code? I'd like to walk through `enter`, then `exit`, then the fee math, then dry-run a 2.5-hour stay."

### 4.1 `enter` — flow + code

```
   enter(vehicleType)
        |
        v
   +----------------------------+
   | findAvailableSpot(type)    |
   +----------------------------+
                | null -> throw NoSuchElement("no spot for ...")
                v
   +----------------------------+
   | occupiedSpotIds.add(id)    |
   | ticket = new Ticket(...)   |
   | activeTickets.put(id, t)   |
   +----------------------------+
                v
   return ticket
```

```java
public Ticket enter(VehicleType vehicleType) {
    ParkingSpot spot = findAvailableSpot(vehicleType);
    if (spot == null) {
        throw new NoSuchElementException("No available spot for vehicle type " + vehicleType);
    }
    occupiedSpotIds.add(spot.getId());
    String ticketId = UUID.randomUUID().toString();
    Ticket ticket = new Ticket(ticketId, spot.getId(), vehicleType, clock.millis());
    activeTickets.put(ticketId, ticket);
    return ticket;
}

// First-fit linear scan — simplest correct allocation. If the requirements grow to
// "support multiple lookup policies", extract a SpotLookupStrategy (see §5.4).
private ParkingSpot findAvailableSpot(VehicleType type) {
    SpotType required = mapVehicleTypeToSpotType(type);
    for (ParkingSpot spot : spots) {
        if (spot.getSpotType() == required && !occupiedSpotIds.contains(spot.getId())) {
            return spot;
        }
    }
    return null;
}
```

> **Senior callout:** *"All-or-nothing — if `findAvailableSpot` returns null we throw before mutating any state. If we made the failure path mutate first and rollback, we'd have to think about partial-failure cleanup."*

### 4.2 `exit` — flow + code

```
   exit(ticketId)
        |
        v
   +----------------------------+
   | null / empty ticketId ?    |--yes--> throw IllegalArgument
   +----------------------------+
                | no
                v
   +----------------------------+
   | ticket = activeTickets[id] |
   | null ?                     |--yes--> throw NoSuchElement (covers "invalid"
   +----------------------------+         AND "already used" — they're the same
                | no                       to the caller)
                v
   +----------------------------+
   | fee = computeFee(...)      |
   | occupiedSpotIds.remove(...)|
   | activeTickets.remove(id)   |
   +----------------------------+
                v
   return fee
```

```java
public long exit(String ticketId) {
    if (ticketId == null || ticketId.isEmpty()) {
        throw new IllegalArgumentException("Invalid ticket id");
    }
    Ticket ticket = activeTickets.get(ticketId);
    if (ticket == null) {
        throw new NoSuchElementException("Ticket not found or already used");
    }
    long fee = computeFee(ticket.getEntryTimeMs(), clock.millis());
    occupiedSpotIds.remove(ticket.getSpotId());
    activeTickets.remove(ticketId);
    return fee;
}
```

> **Senior callout:** *"Removing the ticket from the map is what makes the API idempotent against double-exit — exact same trick as Locker.pickup. We deliberately don't distinguish 'never existed' from 'already used' — both are 'invalid ticket' to the caller. If product wants the distinction, we'd add a `usedTickets` set; for interview scope this is right-sized."*

### 4.3 `computeFee` — round up, never trust floats

```java
private static final long MILLIS_PER_HOUR = 60L * 60L * 1000L;

private long computeFee(long entryTimeMs, long exitTimeMs) {
    long durationMs = exitTimeMs - entryTimeMs;
    long hours = durationMs / MILLIS_PER_HOUR;
    if (durationMs % MILLIS_PER_HOUR > 0) hours++;        // round up partial hour
    if (hours == 0) hours = 1;                            // minimum 1-hour charge
    return hours * hourlyRateCents;
}
```

> **The two soundbites here:**
> - *"All in cents — long, not double. Floats can't represent 0.10 exactly; we'd accumulate error on every transaction."*
> - *"Round up means anyone parking 5 minutes pays for 1 hour. The mod check handles it without a separate minimum-charge branch — the `hours == 0` guard is just defensive for the 0-millisecond exit case."*

### 4.4 Verification — dry-run a 2.5-hour stay

```
Setup: spots = [M1:MOTORCYCLE, C1:CAR, C2:CAR, L1:LARGE]
       occupiedSpotIds = {}, activeTickets = {}
       hourlyRateCents = 500 ($5/hr), clock @ 08:00:00 UTC

enter(CAR):
  findAvailableSpot(CAR) -> C1 (type matches, not occupied)
  occupiedSpotIds = {"C1"}
  ticketId = "T1", entryTimeMs = 08:00:00
  activeTickets = { "T1" -> ticket(T1, C1, CAR, 08:00) }
  return ticket(T1)                                                      ✓

[clock advances 2h 30m] -> now = 10:30:00

exit("T1"):
  ticket found
  computeFee(08:00, 10:30): durationMs = 9_000_000
    hours = 9_000_000 / 3_600_000 = 2
    9_000_000 % 3_600_000 = 1_800_000 > 0  -> hours++ -> 3
    return 3 * 500 = 1500 cents                                          ✓
  occupiedSpotIds = {}, activeTickets = {}
  return 1500                                                             ✓

exit("T1") again:
  activeTickets["T1"] -> null
  throw NoSuchElement("Ticket not found or already used")                 ✓

enter(CAR), enter(CAR), enter(CAR):
  1st -> C1, 2nd -> C2, 3rd -> findAvailableSpot returns null
                              -> throw NoSuchElement("No available spot") ✓
```

---

## STEP 5 — Extensibility (~7 min)

You're **pointing, not rewriting** — name the small additions; don't draft full classes unless asked.

### 5.1 "Multi-floor parking garage"

> *"Introduce a `ParkingFloor` between Lot and Spot. ParkingLot owns `List<ParkingFloor>`; each Floor owns its own list of spots. Spot id becomes 'floor-section-slot', e.g. '3-A-15'. `findAvailableSpot` iterates floors-in-order for simplicity, or uses a `FloorSelectionStrategy` (lowest-first, most-empty-first, proximity-to-destination) when the requirements get more nuanced. Ticket doesn't change — `spotId` is still a string."*

### 5.2 "Different pricing per vehicle type"

> *"Simplest: replace `hourlyRateCents` with `Map<VehicleType, Long> hourlyRates`. `computeFee` looks up the rate using `ticket.getVehicleType()`. If pricing gets complex — surge, discounts, EV-charging — promote to a `PricingStrategy` interface. For 3 fixed rates, the map is right-sized."*

### 5.3 "Concurrent entry — two gates, race condition"

> *"The race is on `findAvailableSpot → add(spotId)` — two threads can both pass the check before either marks it occupied, then both vehicles get the same spot. Simplest fix is `synchronized` on `enter`/`exit`. For higher throughput, push the lock down to a `ConcurrentHashMap` + `add()` returns-boolean — `findAvailableSpot` calls `occupiedSpotIds.add(spot.id)` and trusts the atomic result; if `false`, retry to the next candidate spot."* (See deep-dive section below for code.)

### 5.4 "Support different allocation rules (best-fit, random, proximity-based)"

This is the **Strategy refactor** — but introduced *now*, with the 3-beat phrasing:

> **Problem in current design:** *"Right now `findAvailableSpot` hardcodes first-fit linear scan. If we want best-fit or random distribution, we'd have to either branch inside the method or duplicate the method body — both bad."*
>
> **Pattern as the fix:** *"Extract a `SpotLookupStrategy` interface with one method `findSpot(spots, occupied, required) → ParkingSpot`. Implement `FirstFitLookupStrategy` (current behavior), `RandomLookupStrategy` (uniform distribution to spread wear), `BestFitLookupStrategy` (closest to entrance — requires spot coordinates). Inject into ParkingLot via constructor; default stays first-fit."*
>
> **Alternative + tradeoff:** *"Alternative is an enum + switch inside `findAvailableSpot`. That works for 2-3 policies but violates Open/Closed once you have more, and each policy can't carry its own private state (random needs its `Random` instance, best-fit might want an entrance reference). Strategy scales; switch doesn't."*

Minimal sketch (don't write all of this; just show the shape):

```java
public interface SpotLookupStrategy {
    ParkingSpot findSpot(List<ParkingSpot> spots, Set<String> occupied, SpotType required);
}

public class FirstFitLookupStrategy implements SpotLookupStrategy { /* current loop */ }
public class RandomLookupStrategy   implements SpotLookupStrategy { /* random pick */ }

// In ParkingLot — one new field, one constructor overload, one delegation:
private final SpotLookupStrategy lookupStrategy;       // defaults to new FirstFit()
private ParkingSpot findAvailableSpot(VehicleType t) {
    return lookupStrategy.findSpot(spots, occupiedSpotIds, mapVehicleTypeToSpotType(t));
}
```

### 5.5 Other "what-if" answers (have one-liners ready)

| Follow-up                                  | Answer                                                                                              |
| ------------------------------------------ | --------------------------------------------------------------------------------------------------- |
| "Motorcycles can use car spots when full"  | Tweak `mapVehicleTypeToSpotType` to return an ordered fallback list; `findAvailableSpot` iterates the list. Tradeoff: cars later might be locked out — call out the policy choice. |
| "Reserved / paid-monthly spots"            | Add a `reservedFor: UserId?` field on Spot or a separate `reservedSpotIds` set on Lot. Skip these in `findAvailableSpot` unless the caller is the assigned user. |
| "Lost ticket"                              | Move ticket id from random UUID to `(plate, entryTime)` derivable key; lookup by plate at exit. Or charge a flat lost-ticket fee. |
| "Persist across restart"                   | Inject `LotRepository`; write on every `enter`/`exit`, load on boot. In-memory impl for tests.      |
| "Multiple lots in a city"                  | A `ParkingNetwork` facade routes by location; each `ParkingLot` is self-contained.                  |
| "Notify customer when fee is high"         | Observer: Lot publishes `VehicleExited(fee)`; SMS/Email/Analytics subscribe.                        |
| "Dashboard: real-time occupancy"           | Expose a `getStatus()` method (justified by the new requirement) that returns counts per type. Don't add proactively. |

---

## Design Patterns — Hello Interview's canonical 8, and WHEN to mention each

The single biggest pattern mistake at SDE‑2 level isn't *not knowing* patterns — it's **forcing them into the wrong step**. Patterns volunteered in Step 1, 2, or 3 sound rehearsed; the same patterns named in Step 5 sound senior.

> **Hello Interview's stance:** *"Patterns arise from good design decisions, not the other way around. Most interview designs use zero to two patterns maximum."*
>
> **Geography note (matters for you):** India-based interviews expect candidates to identify patterns by name. Err on the side of **explicitly naming** when it fits.

### The 5-step timing rule

| Step                       | Use a pattern here?                                                                 |
| -------------------------- | ----------------------------------------------------------------------------------- |
| **1. Requirements**        | **Never.** You're scoping — patterns are an implementation concern.                |
| **2. Entities**            | **Sometimes** — if you already see a clear Strategy seam (e.g., allocation policy), declare the interface as one of the entities. *That's what we did here with `SpotLookupStrategy`.* |
| **3. Class Design**        | **YES, when the pattern earns rent in the base.** Name it explicitly — India-based interviews expect candidates to identify patterns by name when applied. Don't artificially defer to Step 5 if the design genuinely needs the seam now. |
| **4. Implementation**      | **No new patterns.** Implement what Step 3 designed — adding a pattern mid-coding is over-engineering. |
| **5. Extensibility**       | **YES — for the *additional* patterns the interviewer's follow-up prompts trigger.** Also where you defend why your Step-3 patterns absorb each change cleanly. |

### Hello Interview's canonical 8 × interviewer trigger

| # | Pattern              | Category   | Trigger phrase                                                                | One-line response                                                                                       |
| - | -------------------- | ---------- | ------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------- |
| 1 | **Strategy** ⭐       | Behavioral | "different rules" · "variants" · "swap at runtime" · "piles of if/else by type" | *"Promote X to an interface; inject the concrete implementation. Replaces conditionals with polymorphism."* |
| 2 | **Observer**         | Behavioral | "notify multiple" · "broadcast" · "event"                                       | *"X publishes events; subscribers register independently. Decouples X from how the world reacts."*       |
| 3 | **State Machine**    | Behavioral | "behavior depends on state" · "complex transitions"                             | *"Each state is its own class with its own transitions; I'd draw the state diagram on the board."*       |
| 4 | **Factory** (Method) | Creational | "support different types" · "multiple variants"                                 | *"Centralize creation; callers stop depending on concrete classes."*                                      |
| 5 | **Builder**          | Creational | "many optional fields" · "complicated construction"                             | *"Builder collects fields incrementally; `build()` validates. Beats a 12-arg constructor."*              |
| 6 | **Singleton**        | Creational | "exactly one" · "global"                                                         | *"I'd resist textbook Singleton — DI a single instance instead."*                                         |
| 7 | **Decorator**        | Structural | "optional features" · "stack behaviors"                                          | *"Wrap X in decorators, each adding one concern. Avoids subclass explosion."*                            |
| 8 | **Facade**           | Structural | "hide complexity" · "single entry point"                                         | *"Orchestrators usually ARE facades — you're already building one."*                                     |

> **⭐ Strategy is the #1 priority pattern.** Hello Interview: *"If you learn one pattern from this page, make it this one."*

### Three rules that make pattern mentions sound natural

1. **Cap at 2 patterns total** in one interview.
2. **Always name the concrete win in the same breath.** *"Strategy here because pricing rules swap at runtime"* > *"I'd use Strategy."*
3. **Never volunteer a pattern without a trigger** — interviewer phrase or concrete need in your design.

### How this maps to Parking Lot specifically

**Naturally present in the BASE design — call out by principle, plus one pattern:**

- **Facade (#8)** — *ParkingLot IS a facade* over the spots list, the tickets map, and the occupancy set. Hello Interview explicitly notes orchestrators are facades. **Name it once in Step 2.**
- **Information Expert** (GRASP principle) — pricing on Lot, spot type on Spot. Each rule lives where its data lives.
- **Tell, Don't Ask** (principle) — Lot calls `spot.getSpotType()`; never reads spot internals.
- **Dependency Injection** (principle) — `Clock` injected for testable time.
- **Immutability** (principle) — `Ticket` and `ParkingSpot` fields are all `final`.

> **Why no Strategy in the base?** Apply the one-sentence test: *can I state in one specific sentence the design pressure that motivates Strategy now?* For Parking Lot, no — the requirements just say "assign any compatible spot." Hardcoding first-fit is the honest baseline. If the interviewer asks for variants in Step 5, *that's* the moment to introduce Strategy with the 3-beat phrasing (see §5.4).

**Reach for these on the matching Step-5 follow-up — use 3-beat phrasing (problem → pattern → tradeoff):**

| Follow-up                                  | Pattern (HI's 8)             | Your line                                                                                            |
| ------------------------------------------ | ---------------------------- | ---------------------------------------------------------------------------------------------------- |
| "Different allocation rules (best-fit, random, proximity)" | **Strategy (#1)** ⭐ | *"Currently `findAvailableSpot` hardcodes first-fit. To support multiple policies, extract `SpotLookupStrategy`. Alternative is enum + switch but it violates OCP at 4+ policies — Strategy scales."* |
| "Different pricing rules (surge, discount, EV)" | **Strategy (#1)** ⭐     | *"Currently `computeFee` hardcodes flat hourly. Extract `PricingStrategy` — `FlatHourly`, `Surge`, `DiscountedFirstHour`. Tradeoff: enum-switch is fine for 2 modes, Strategy is right at 3+."* |
| "Multi-floor garage"                       | **Facade (#8)** + composition | *"Introduce `ParkingFloor` as an intermediate aggregate; Lot stays the facade over many floors."* |
| "Notify customers / send receipts"         | **Observer (#2)**            | *"Lot publishes `VehicleExited` / `VehicleEntered` events. SMS / Email / Analytics subscribe independently. Alternative is direct dependency on each subscriber — couples Lot to the world."* |
| "Multiple kinds of lots (commercial / residential / EV-charging)" | **Factory (#4)** | *"`LotFactory.create(LotKind)` returns the right pre-configured Lot. Callers stop depending on concrete types."* |
| "Persist across restart"                   | (Repository — not in HI's 8) | Describe the technique without naming: *"Inject a storage interface; write on every enter/exit, read on boot."* Name "Repository" only if the interviewer prompts. |

**Patterns to actively refuse (Parking Lot traps):**

- **Singleton on ParkingLot** — kills tests, breaks multi-lot extension. *"Pass a single instance via constructor instead."*
- **Builder for the 2-arg `ParkingLot(spots, rate)`** — academic noise.
- **Factory for `SpotType` / `VehicleType`** — they're enums.
- **State pattern on ParkingSpot** — there's no per-state behavior; an occupied set is correct.
- **Visitor over spots** — homogeneous list, no benefit.

### One sentence to say at the end of Step 3

> *"The base design relies on Facade (the orchestrator), Information Expert, and Tell-Don't-Ask — no GoF Strategy yet because the requirements don't mandate multiple allocation policies. When extensibility prompts come (best-fit lookup, surge pricing, notifications), I'll point out where Strategy and Observer would earn their place."*

---

## Interview deep-dives — the questions you'll definitely get asked

### 1. Complexity (Big-O)

Let `C` = #compartments... sorry, #spots; `T` = active tickets; `F` = floors (if extended).

| Operation              | Time                                                       | Space            | Notes                                                                              |
| ---------------------- | ---------------------------------------------------------- | ---------------- | ---------------------------------------------------------------------------------- |
| `enter(type)`          | `O(C)` scan + `O(1)` map/set insert = **`O(C)`**           | O(1) per call    | Linear scan to find first matching free spot                                       |
| `exit(ticketId)`       | **`O(1)`**                                                 | O(1) per call    | Map lookup + set/map remove                                                        |
| `findAvailableSpot`    | `O(C)`                                                     | O(1)             | Could be `O(1)` with a per-`SpotType` queue of free spots                          |
| `computeFee`           | **`O(1)`**                                                 | O(1)             | Pure integer arithmetic                                                            |
| Storage — spots        | —                                                          | **`O(C)`**       | Fixed at construction                                                              |
| Storage — tickets      | —                                                          | **`O(T)`** ≤ `O(C)` | Bounded — 1 active ticket per occupied spot                                       |

> **Senior callout:** *"`enter` is `O(C)`, `exit` is `O(1)`. If `C` grows to thousands and we want `enter` to be `O(1)` too, I'd maintain `Map<SpotType, Queue<String>> freeSpotsByType` — costs one extra invariant to keep in sync."*

### 2. Concurrency / thread-safety

**The race:** two vehicles entering simultaneously from two gates can both pass `findAvailableSpot` before either calls `add(spotId)` — same spot handed to both, second ticket silently overwrites the first.

**Simplest correct fix (interview-ready):**

```java
public class ParkingLot {
    public synchronized Ticket enter(VehicleType type) { /* ... */ }
    public synchronized long   exit(String ticketId)   { /* ... */ }
}
```

Method-level lock — operations are short (no I/O on the hot path), contention low.

**Higher-throughput alternative — atomic set add:**

```java
private final Set<String> occupiedSpotIds = ConcurrentHashMap.newKeySet();

private ParkingSpot findAvailableSpot(VehicleType type) {
    SpotType required = mapVehicleTypeToSpotType(type);
    for (ParkingSpot spot : spots) {
        if (spot.getSpotType() != required) continue;
        // add() returns true only if NEWLY added; effectively an atomic claim.
        if (occupiedSpotIds.add(spot.getId())) {
            return spot;
        }
    }
    return null;
}
```

> **Senior callout:** *"Method-level `synchronized` is the right default — simple, correct, low contention. For multi-gate scale I'd push down to an atomic `add` on a concurrent set so claim becomes a single CAS. Either way the race we're fixing is the gap between 'check available' and 'mark occupied'."*

### 3. Testing — what to write tests for

The injected `Clock` is the entire reason fee math is testable.

| Test category        | Cases to cover                                                                                              |
| -------------------- | ----------------------------------------------------------------------------------------------------------- |
| Happy path           | enter CAR → fee for 2.5h is 1500c (round up to 3h); spot freed; ticket map empty                            |
| Fee boundaries       | 0ms stay → 1h charge (minimum); exactly 3600000ms → 1h; 3600001ms → 2h (round up boundary)                  |
| Type matching        | enter MOTORCYCLE doesn't take a CAR spot                                                                    |
| Capacity             | Exhaust all CAR spots → next CAR `enter` throws `NoSuchElement`                                             |
| Invalid ticket       | `null` → IllegalArgument; `""` → IllegalArgument; unknown → NoSuchElement (three **distinct** exceptions)   |
| Double exit          | Same ticket id twice → second is rejected                                                                   |
| Spot reuse           | Enter → exit → re-enter — same spot is allocatable again                                                    |

```java
@Test
void exit_at_two_and_a_half_hours_charges_three_hours() {
    MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    ParkingLot lot = new ParkingLot(
            List.of(new ParkingSpot("C1", SpotType.CAR)),
            /* rate */ 500, clock);

    Ticket t = lot.enter(VehicleType.CAR);
    clock.advanceMinutes(150);
    assertEquals(1500L, lot.exit(t.getId()));
}

@Test
void exit_boundary_at_exactly_one_hour_charges_one_hour() {
    /* setup ... */
    Ticket t = lot.enter(VehicleType.CAR);
    clock.advanceMs(MILLIS_PER_HOUR);
    assertEquals(500L, lot.exit(t.getId()));
}

@Test
void exit_boundary_one_ms_past_hour_charges_two_hours() {
    /* setup ... */
    Ticket t = lot.enter(VehicleType.CAR);
    clock.advanceMs(MILLIS_PER_HOUR + 1);
    assertEquals(1000L, lot.exit(t.getId()));
}

@Test
void double_exit_is_rejected() {
    /* setup ... */
    Ticket t = lot.enter(VehicleType.CAR);
    lot.exit(t.getId());
    assertThrows(NoSuchElementException.class, () -> lot.exit(t.getId()));
}
```

### 4. SOLID mapping

| Letter                       | Where it shows up                                                                                          |
| ---------------------------- | ---------------------------------------------------------------------------------------------------------- |
| **S** Single Responsibility  | ParkingLot = orchestration + pricing policy. ParkingSpot = intrinsic spot data. Ticket = session record. Three reasons to change → three classes. |
| **O** Open/Closed            | `computeFee` is closed for modification but open for extension via a future `PricingStrategy`. `mapVehicleTypeToSpotType` ditto for compatibility rules (e.g., motorcycles in car spots). |
| **L** Liskov Substitution    | Spots of a given `SpotType` are interchangeable. If `ParkingSpot` becomes polymorphic (e.g., `EVSpot` extends), they must honor the same contract. |
| **I** Interface Segregation  | ParkingLot exposes 2 narrow methods; doesn't dump a giant `getStatus()`. ParkingSpot exposes 2 read-only methods, no state mutation. |
| **D** Dependency Inversion   | ParkingLot depends on `Clock` (abstraction), not on `System.currentTimeMillis()`. When `PricingStrategy` lands, Lot depends on the interface, not concrete pricing. |

### 5. "Summarize your design in 30 seconds"

> *"Three classes: ParkingLot, ParkingSpot, Ticket. ParkingLot is the orchestrator and a facade — it owns the spots, a `Set<String>` of occupied spot ids (relational state — distinct from how Locker tracks intrinsic occupancy), and a `Map<id, Ticket>` for O(1) exit lookup. `enter` finds a compatible spot via first-fit linear scan, mints a UUID ticket, and inserts both. `exit` looks up the ticket, computes the fee rounding partial hours up, frees the spot, and removes the ticket — removing is what makes the API idempotent against double-exit. Money is always `long` cents, never `double` — floats can't represent 0.10 exactly. `Clock` is injected so fee math is fully testable. I deliberately didn't pre-bake a Strategy for allocation — first-fit is correct for the stated requirements; if the interviewer wants best-fit or random distribution, that's a clean Step-5 extraction. Same for pricing — flat hourly today, PricingStrategy if surge/discount lands."*

That's ~40 seconds. Hits: structure, the relational-state insight, money discipline, testability, the senior trap (ticket removal = idempotency), and the explicit choice not to over-engineer.

---

## Closing soundbites (memorize these)

- **Opening:** *"Before I design, let me clarify scope and rules."*
- **Why no Vehicle class:** *"Vehicle is external — no state we manage, no rules to enforce. Just a classification → enum."*
- **Money discipline:** *"All in cents — long, not double. Floats can't represent 0.10 exactly."*
- **Occupancy choice:** *"Occupancy is relational here — 'a ticket is assigned to this spot'. So it lives on the orchestrator, not on the spot. Opposite of Locker's intrinsic occupancy."*
- **Defending tell-don't-ask:** *"Lot calls `spot.getSpotType()` — never peeks inside Spot's internals."*
- **Before coding:** *"Real Java or pseudo-code? I'll do `enter`, then `exit`, then the fee math, then a dry-run."*
- **Idempotent exit:** *"Removing the ticket on exit is what blocks double-exit — same trick as Locker.pickup."*
- **On testability:** *"`Clock` is injected so fee math is unit-testable without sleeps or static mocking."*
- **On extensibility:** *"Pricing and floor-selection both want to become Strategy interfaces the moment requirements grow."*

---

## Top mistakes that lose points

- **Adding a `Vehicle` class** with `licensePlate`, `make`, `model` — not in scope, pure noise.
- **Adding `Customer` / `Cashier` / `Gate`** classes — actors, not entities.
- **Money as `double` / `float`** — instant lose. Use `long` cents.
- **`boolean occupied` on ParkingSpot** — works, but you miss the "relational state" insight. If you go this way, *call out the trade-off explicitly* — both are defensible, what isn't defensible is making the choice without seeing it.
- **`getAvailableSpots()`/`getStatus()` on ParkingLot** — leaks internal state, isn't in requirements.
- **Forgetting to remove the ticket on exit** — double-exit slips through.
- **Using `System.currentTimeMillis()` directly** — untestable.
- **Not handling the partial-hour boundary** — 1ms past the hour should charge 2 hours; many candidates forget the round-up.
- **Same enum for SpotType and VehicleType** — works today, locks you out of "motorcycles in car spots" later.
- **Pattern-stuffing in Step 3** — Strategy on pricing, Factory on spots, Builder on Ticket. Save patterns for Step 5.
- **Skipping the dry run.**

---

## Files in this folder (your reference implementation)

| File                                       | What it shows                                                              |
| ------------------------------------------ | -------------------------------------------------------------------------- |
| `model/SpotType.java`                      | Enum — MOTORCYCLE / CAR / LARGE                                            |
| `model/VehicleType.java`                   | Enum — separate from SpotType for OCP                                      |
| `model/ParkingSpot.java`                   | Pure data holder — id + spotType (intrinsic state only)                    |
| `model/Ticket.java`                        | Immutable session record — id, spotId, vehicleType, entryTimeMs            |
| `ParkingLot.java`                          | Orchestrator + facade — `enter`/`exit`, occupancy set, tickets map, fee, hardcoded first-fit lookup |
| `ParkingLotDriver.java`                    | Scenario harness — happy path / double exit / full lot / instant exit / invalid |

Run from the project root:

```bash
mvn -q compile exec:java \
  -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.parkinglot.ParkingLotDriver
```
