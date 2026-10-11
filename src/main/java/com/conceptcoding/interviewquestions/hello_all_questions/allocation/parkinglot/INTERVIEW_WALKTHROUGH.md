# Parking Lot

> 35–40 min round. Phases: **Requirements (5) → Entities (3) → Class Design (10–15) → Implementation (10) → Extensibility (5)**.

---

## 0. The problem in plain words (read first, don't say it in the interview)

**Picture a mall parking lot.** You drive up, the gate gives you a ticket that says "go to spot M2", you park, go shopping, come back, hand in the ticket, pay for the hours, and the gate opens. That's the whole system.

**What the system really does: just 2 actions**
```
Car arrives  →  enter()  →  "find a free spot that fits, mark it taken, give a ticket"
Car leaves   →  exit()   →  "take the ticket back, free the spot, tell the fee"
```
Everything else (floors, boards, payments, EVs) is a follow-up built on these two.

**Real world → code**
| In the real world | In the code |
|---|---|
| Bike / car / truck | `VehicleType` with a size (1, 2, 3) |
| Small / medium / large spot | `SpotType` with a size; fits if spot size ≥ vehicle size |
| A painted spot on floor 1 | `ParkingSpot` (id, floor, type): it never changes |
| The paper ticket | `Ticket` (which spot, which vehicle, when it entered) |
| The guard deciding where you go | `SpotAllocationStrategy` |
| The rate card at the exit | `PricingStrategy` |
| The whole lot + its gates | `ParkingLot` |

**The 3 things that make it a real question**
1. **Don't waste big spots.** A bike *can* park in a truck spot, but then the next truck has nowhere to go. So give the **smallest spot that fits**.
2. **Two gates, one last spot.** Two cars enter at the same moment, and both see M2 free. Only one may get it, so `enter` and `exit` are `synchronized`.
3. **Rules keep changing.** Today it's "₹50 per hour"; next month it's "first hour free". The rule for choosing a spot can change too. So both are **Strategies** (swap the class, and the lot code stays the same).

**One subtle choice:** "is this spot taken?" is stored in **one place**, the lot's `occupiedSpotIds`, not as a flag on each spot. With one place, the spot and the lot can never disagree.

**Before you start coding, picture this run:**
```
Spots: S1 small, M1 medium, M2 medium, L1 large
bike  → S1          (smallest that fits)
bike  → M1          (small spots full, a medium is the next smallest)
truck → L1
car   → M2
car   → "No spot available"
car exits after 2h10m → 3 started hours × ₹50 = ₹150, M2 is free again
```
If you can explain this run, you understand the problem. The rest of this file is how to write it.

---

## 1. Requirements (~5 min)

**Ask:** which vehicle and spot sizes? Can a small vehicle use a bigger spot? How is the fee calculated? What if there's no spot, or the ticket is wrong? Several gates at once?

```
1. enter(vehicleType) → assigns the smallest free spot that fits, returns a ticket (bike/car/truck; a bike can use a car spot)
2. exit(ticketId) → frees the spot, returns the fee (per started hour, by vehicle type, min 1 hour)
3. Reject: no fitting spot, unknown or already-used ticket
4. Safe with several gates at once

Out of scope: payment, UI, cameras, reservations
```

---

## 2. Entities & Relationships (~3 min)

```
ParkingLot (orchestrator)
   ├── ParkingSpot  (id, floor, type: immutable)
   ├── Ticket       (active tickets)
   ├── SpotAllocationStrategy   "which spot"
   └── PricingStrategy          "how much"
```
- **"Spot is taken"** lives in `ParkingLot` (a set of occupied ids), not on the spot: one source of truth.
- **"Does it fit?"** lives on `SpotType`, because it owns the size.
- **No class** for Vehicle, Floor or Gate: they have no behaviour (floor is a field on the spot).

---

## 3. Class Design (~10–15 min)

```
ParkingLot
  state:     spots, occupiedSpotIds, activeTickets, allocationStrategy, pricingStrategy, clock
  behaviour: enter(vehicleType) → Ticket,  exit(ticketId) → fee

ParkingSpot   id, floor, spotType                    (getters only)
Ticket        id, spotId, vehicleType, entryTime     (getters only)
VehicleType   MOTORCYCLE(1), CAR(2), TRUCK(3)
SpotType      SMALL(1), MEDIUM(2), LARGE(3)  +  canFit(vehicleType)

SpotAllocationStrategy   choose(freeSpots, vehicleType)   ← SmallestFitStrategy
PricingStrategy          fee(vehicleType, duration)       ← HourlyPricing
```
- **Two Strategies**, because spot choice and pricing change independently of the parking flow.
- **Thread safety:** `enter`/`exit` are `synchronized`. One lock is simple and correct.

### Design patterns: the problem → the fix (how to say it at Amazon)

**The 4-step script for any pattern:** *the problem* → *what goes wrong without it* → *how the pattern fixes it* → *the cost*. Amazon interviewers want to hear **why**, not the pattern's name. Never say "I'll use Strategy here" with no reason.

#### Strategy for choosing a spot (in the base)
**Problem:** the rule for choosing a spot changes: smallest fit today, lowest floor tomorrow, near the lift for VIPs.
**Without it:** every new rule is another `if` inside `ParkingLot`, so you edit and re-test the core class each time.
```java
if (mode == SMALLEST_FIT)      { /* loop A */ }
else if (mode == LOWEST_FLOOR) { /* loop B */ }
else if (mode == NEAR_LIFT)    { /* loop C */ }     // keeps growing
```
**With it:** `ParkingLot` only calls `allocationStrategy.choose(freeSpots, vehicle)`. A new rule is a new class, and `ParkingLot` doesn't change (the Open/Closed principle). Each rule can be tested on its own with a plain list of spots.
**Say:** *"The spot rule is the part most likely to change, so I put it behind an interface. `ParkingLot` decides **when** to choose a spot; the strategy decides **which** one."*

#### Strategy for pricing (in the base)
**Problem:** the rate card changes on its own schedule (per hour, flat per day, weekend rates), separately from how parking works.
**Without it:** `exit()` fills up with pricing `if`s mixed with the ticket and spot logic.
**With it:** `exit()` asks `pricingStrategy.fee(vehicle, duration)`. Finance can change pricing without touching the gates.
**Say:** *"Spot choice and pricing change for different reasons and at different times, so they're two separate strategies."*

#### Decorator for stacking pricing rules (Extension 1)
**Problem:** the rules **combine**: "first hour free", "max ₹400 a day", "both".
**Without it:** a class for every combination (`FirstHourFreeWithCapPricing`...), and the count grows exponentially.
**With it:** each rule wraps another pricing object and adjusts its answer: `new DailyCap(new FirstHourFree(new HourlyPricing(rates)), 400)`. Two rules, two classes, in any order.
**Say:** *"Strategy picks one rule. Decorator lets rules stack on top of each other."*

#### Observer for display boards (Extension 3)
**Problem:** when a spot changes, several things care: the floor board, the entrance board, the mobile app.
**Without it:** `ParkingLot` calls `board.update()`, `app.push()`... directly, so it knows about every screen, and a new screen means editing `ParkingLot`.
**With it:** `ParkingLot` only says "spot changed" to a list of `SpotListener`s, and each listener decides what to do.
**Say:** *"The lot announces the event and doesn't care who's listening. A new screen is a new listener."*

#### Patterns I deliberately **don't** use (say this when asked; it shows judgement)
| Pattern | Why not here |
|---|---|
| **Singleton** `ParkingLot` | It hides a global and makes testing hard (no fresh lot per test). There's one lot because the app **creates** one and passes it around (dependency injection). |
| **Factory** for vehicles | `VehicleType` is an enum with a size; there are no vehicle objects to create. Add one when vehicle types need different classes. |
| **State** pattern for spots | A spot is just free or taken: a set of ids. State pays off when behaviour changes per state (see Vending Machine). |

**Say:** *"I add a pattern only when it removes a real problem I can point to. Otherwise it's just extra classes."*

### ACID in this problem (if they ask "is enter/exit transactional?")
| Property | Means | Where it shows in the parking lot |
|---|---|---|
| **Atomicity** | all or nothing | `enter`: mark the spot taken **and** create the ticket, or neither (the "no spot" error is thrown before anything changes). `exit`: compute the fee **first**, then remove the ticket and free the spot, so a pricing error changes nothing. |
| **Consistency** | the rules always hold | one active ticket per spot · a taken spot always has a ticket · a ticket can be used only once |
| **Isolation** | no one sees half-done work | `synchronized`: a second gate never sees "spot taken but ticket not created yet" |
| **Durability** | survives a crash | **not** given: everything is in memory. Say: *"In production, tickets and occupied spots go in a database, and enter/exit becomes one DB transaction."* |

---

## 4. Implementation (~10 min)

**enter:** free spots → strategy picks → mark occupied → create and store a ticket. *Edge case:* nothing fits → error.
**exit:** find the ticket → compute the fee → *then* remove the ticket and free the spot. *Edge case:* ticket missing or already used → error. *Why the fee comes first:* if pricing throws, nothing has changed yet (see ACID below).

```java
public enum VehicleType {
    MOTORCYCLE(1), CAR(2), TRUCK(3);
    private final int size;
    VehicleType(int size) { this.size = size; }
    public int getSize() { return size; }
}

public enum SpotType {
    SMALL(1), MEDIUM(2), LARGE(3);
    private final int size;
    SpotType(int size) { this.size = size; }
    public int getSize() { return size; }
    public boolean canFit(VehicleType v) { return size >= v.getSize(); }
}

public class ParkingSpot { private final String id; private final int floor; private final SpotType type; /* ctor + getters */ }
public class Ticket { private final String id, spotId; private final VehicleType vehicleType; private final Instant entryTime; /* ctor + getters */ }
```

```java
public interface SpotAllocationStrategy {
    ParkingSpot choose(List<ParkingSpot> freeSpots, VehicleType vehicle);     // null = nothing fits
}

public class SmallestFitStrategy implements SpotAllocationStrategy {
    public ParkingSpot choose(List<ParkingSpot> freeSpots, VehicleType vehicle) {
        ParkingSpot best = null;
        for (ParkingSpot spot : freeSpots) {
            if (!spot.getType().canFit(vehicle)) continue;                    // too small: skip
            if (best == null || spot.getType().getSize() < best.getType().getSize()) {
                best = spot;                                                  // smallest so far
            }
        }
        return best;                                                          // null = nothing fits
    }
}

public interface PricingStrategy {
    long fee(VehicleType vehicle, Duration parked);
}

public class HourlyPricing implements PricingStrategy {
    private final Map<VehicleType, Long> ratePerHour;
    public HourlyPricing(Map<VehicleType, Long> ratePerHour) { this.ratePerHour = new EnumMap<>(ratePerHour); }

    public long fee(VehicleType vehicle, Duration parked) {
        long hours = Math.max(1, (parked.toMinutes() + 59) / 60);          // round UP, minimum 1
        return hours * ratePerHour.get(vehicle);
    }
}
```

```java
public class ParkingLot {
    private final List<ParkingSpot> spots;
    private final Set<String> occupiedSpotIds = new HashSet<>();
    private final Map<String, Ticket> activeTickets = new HashMap<>();
    private final SpotAllocationStrategy allocationStrategy;
    private final PricingStrategy pricingStrategy;
    private final Clock clock;
    private int nextTicketNumber = 1;

    // constructor sets spots, both strategies and the clock

    public synchronized Ticket enter(VehicleType vehicle) {
        ParkingSpot spot = allocationStrategy.choose(freeSpots(), vehicle);
        if (spot == null) {
            throw new IllegalStateException("No spot available for " + vehicle);
        }
        occupiedSpotIds.add(spot.getId());
        Ticket ticket = new Ticket("T-" + nextTicketNumber++, spot.getId(), vehicle, clock.instant());
        activeTickets.put(ticket.getId(), ticket);
        return ticket;
    }

    public synchronized long exit(String ticketId) {
        Ticket ticket = activeTickets.get(ticketId);
        if (ticket == null) throw new NoSuchElementException("Ticket not found or already used: " + ticketId);
        long fee = pricingStrategy.fee(ticket.getVehicleType(), Duration.between(ticket.getEntryTime(), clock.instant()));
        activeTickets.remove(ticketId);                                   // only now: it can't exit twice
        occupiedSpotIds.remove(ticket.getSpotId());
        return fee;                       // fee FIRST: if pricing throws, nothing has changed (atomic)
    }

    private List<ParkingSpot> freeSpots() {
        List<ParkingSpot> free = new ArrayList<>();
        for (ParkingSpot s : spots) if (!occupiedSpotIds.contains(s.getId())) free.add(s);
        return free;
    }
}
```

**Verify out loud** (spots: S1 small, M1 medium, M2 medium, L1 large; car ₹50/h):
```
bike → S1     bike → M1 (smalls full)     truck → L1     car → M2     car → "No spot available"
exit(car ticket) after 2h10m → M2 freed → (130 + 59) / 60 = 3 h × ₹50 = ₹150
exit(same ticket) again → "Ticket not found or already used"
```

---

## 5. Extensibility (~5 min)

Name **which class changes**, write the few new lines, and don't redesign the rest.

**Quick revision** (read this table on the day of the interview):

| # | Interviewer asks | One-line answer | What changes |
|---|---|---|---|
| 1 | "First hour free, max ₹400 a day" | Pricing classes that wrap pricing (Decorator) | 2 new classes |
| 2 | "Park near the lift / lowest floor" | One more allocation strategy | 1 new class |
| 3 | "Show free spots on each floor's board" | The lot tells its boards when a spot changes (Observer) | 1 interface, 1 class, 3 lines in `ParkingLot` |
| 4 | "Add EV charging spots" | One more value in each enum, `canFit` checks for it | the 2 enums |
| 5 | "Pay at the exit" | Free the spot only after payment succeeds | `exit` |
| 6 | "Many gates, the lock is slow" | Concurrent set; `add()` claims the spot | `ParkingLot` fields + `enter` |

---

### 1. "First hour free, and max ₹400 a day"
**Idea:** don't touch `HourlyPricing`. Wrap it in a class that adjusts its answer. The wrappers stack.
```java
public class FirstHourFree implements PricingStrategy {
    private final PricingStrategy inner;
    public FirstHourFree(PricingStrategy inner) { this.inner = inner; }

    public long fee(VehicleType v, Duration parked) {
        if (parked.toMinutes() <= 60) return 0;
        return inner.fee(v, parked.minusHours(1));            // charge the rest normally
    }
}

public class DailyCap implements PricingStrategy {
    private final PricingStrategy inner;
    private final long capPerDay;
    public DailyCap(PricingStrategy inner, long capPerDay) { this.inner = inner; this.capPerDay = capPerDay; }

    public long fee(VehicleType v, Duration parked) {
        long days = Math.max(1, (parked.toMinutes() + 1439) / 1440);   // started days
        return Math.min(inner.fee(v, parked), days * capPerDay);
    }
}

// new DailyCap(new FirstHourFree(new HourlyPricing(rates)), 400)
```
**Check** (car ₹50/h): 45 min → ₹0 · 2h10m → 1h10m charged → ₹100 · 10 h → ₹450, capped at ₹400.
**Say:** *"Pricing rules stack, so each one wraps the previous one. `ParkingLot` doesn't change, only the object I pass in."*

---

### 2. "Park near the lift / lowest floor first"
**Idea:** the same loop as `SmallestFitStrategy`, comparing the floor instead of the size.
```java
public class LowestFloorStrategy implements SpotAllocationStrategy {
    public ParkingSpot choose(List<ParkingSpot> freeSpots, VehicleType vehicle) {
        ParkingSpot best = null;
        for (ParkingSpot spot : freeSpots) {
            if (!spot.getType().canFit(vehicle)) continue;
            if (best == null || spot.getFloor() < best.getFloor()) best = spot;
        }
        return best;
    }
}
// new ParkingLot(spots, new LowestFloorStrategy(), pricing, clock)
```
**Say:** *"That's why allocation is a Strategy: a new rule is a new class, and the lot code doesn't change."*

---

### 3. "Each floor has a board showing free spots"
**Idea:** boards shouldn't keep asking the lot. The lot **tells** every listener when a spot is taken or freed (Observer).
```java
public interface SpotListener {
    void spotChanged(ParkingSpot spot, boolean nowFree);
}

public class FloorDisplayBoard implements SpotListener {
    private final Map<Integer, Integer> freeByFloor = new HashMap<>();

    public FloorDisplayBoard(List<ParkingSpot> spots) {          // all free at the start
        for (ParkingSpot s : spots) freeByFloor.put(s.getFloor(), freeByFloor.getOrDefault(s.getFloor(), 0) + 1);
    }
    public void spotChanged(ParkingSpot spot, boolean nowFree) {
        int change = nowFree ? 1 : -1;
        freeByFloor.put(spot.getFloor(), freeByFloor.get(spot.getFloor()) + change);
    }
    public int freeOn(int floor) { return freeByFloor.getOrDefault(floor, 0); }
}

// ParkingLot: new fields
private final List<SpotListener> listeners = new ArrayList<>();
private final Map<String, ParkingSpot> spotById = new HashMap<>();   // filled in the constructor

// enter(): after occupiedSpotIds.add(...)
notifyListeners(spot, false);
// exit(): after occupiedSpotIds.remove(...)
notifyListeners(spotById.get(ticket.getSpotId()), true);

private void notifyListeners(ParkingSpot spot, boolean nowFree) {
    for (SpotListener l : listeners) l.spotChanged(spot, nowFree);
}
```
**Say:** *"The board just counts. Listeners run inside the lock, so they must be quick. A slow one, like an SMS, I'd call after the lock is released."*

---

### 4. "Add EV charging spots"
**Idea:** the size rule stays. The charger spot is the only exception, and it's reserved for EVs.
```java
public enum VehicleType { MOTORCYCLE(1), CAR(2), ELECTRIC_CAR(2), TRUCK(3); /* same as before */ }

public enum SpotType {
    SMALL(1), MEDIUM(2), EV_CHARGING(2), LARGE(3);
    // ...
    public boolean canFit(VehicleType v) {
        if (this == EV_CHARGING) return v == VehicleType.ELECTRIC_CAR;   // charger kept for EVs
        return size >= v.getSize();                                      // everything else as before
    }
}
```
**Check:** a car skips the EV spot · an EV fits both EV and MEDIUM spots (same size, so smallest-fit takes whichever comes first; to always prefer the charger, the strategy checks `EV_CHARGING` first).
**Don't forget:** add an `ELECTRIC_CAR` rate to the pricing map, or `fee()` throws a NullPointerException.
**Say:** *"Only the two enums change. The strategies and `ParkingLot` already ask `canFit`."*

---

### 5. "Pay at the exit"
**Idea:** the same "check first, change state last" order as the base `exit`, with one more step: charge before freeing the spot. If payment fails, the car is still inside, so the ticket must stay valid.
```java
public interface PaymentProcessor {                 // UPI, card, cash: one class each
    boolean charge(String ticketId, long amount);
}

public synchronized long exit(String ticketId, PaymentProcessor payment) {
    Ticket ticket = activeTickets.get(ticketId);                     // get, not remove
    if (ticket == null) throw new NoSuchElementException("Ticket not found or already used: " + ticketId);
    long fee = pricingStrategy.fee(ticket.getVehicleType(), Duration.between(ticket.getEntryTime(), clock.instant()));

    if (!payment.charge(ticketId, fee)) {
        throw new IllegalStateException("Payment failed for " + ticketId + ", gate stays closed");
    }
    activeTickets.remove(ticketId);                                  // paid → now it's used
    occupiedSpotIds.remove(ticket.getSpotId());
    return fee;
}
```
**Say:** *"If a real bank call takes seconds, holding the lot's lock blocks every gate. Then I'd split it: quote the fee, pay outside the lock, then call a short synchronized release."*

---

### 6. "Many gates, the single lock is slow"
**First say:** *"Each call holds the lock for microseconds. I'd measure before changing it."* If they still want it:

**Idea:** a concurrent set's `add()` returns `true` for **exactly one** gate. That's the claim. If another gate got the spot first, choose again.
```java
private final Set<String> occupiedSpotIds = ConcurrentHashMap.newKeySet();
private final Map<String, Ticket> activeTickets = new ConcurrentHashMap<>();
private final AtomicLong ticketSeq = new AtomicLong();

public Ticket enter(VehicleType vehicle) {                           // no synchronized
    while (true) {
        ParkingSpot spot = allocationStrategy.choose(freeSpots(), vehicle);
        if (spot == null) throw new IllegalStateException("No spot available for " + vehicle);
        if (occupiedSpotIds.add(spot.getId())) {                     // true for exactly one gate
            Ticket t = new Ticket("T-" + ticketSeq.incrementAndGet(), spot.getId(), vehicle, clock.instant());
            activeTickets.put(t.getId(), t);
            return t;
        }
        // another gate took it between choose and add: loop and choose again
    }
}
// exit(): activeTickets.remove(id) is atomic too, so a ticket still can't be used twice
```
**Check:** 100 cars at 20 spots → exactly 20 parked, 20 different spots, 80 rejected.
**Say:** *"The strategy doesn't change. Only the claim moved from a lock to an atomic `add`."*

---

```bash
mvn -q compile exec:java -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot.ParkingLotDriver
```
