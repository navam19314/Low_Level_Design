# Parking Lot

> **The classic.** Asked everywhere (Ethos list, Amazon, Adobe). It tests **clean entities, a slot allocation strategy, and pricing**.
>
> **The crux (what's really being tested):**
> 1. **Clean entities:** a spot doesn't know if it's free; the lot owns that fact, in **one** place.
> 2. **Two strategies:** *which spot* (allocation) and *how much* (pricing), both swappable.
> 3. **No double allocation:** two cars at two gates must never get the same spot, without a global lock.
>
> **Family:** F1 Allocation + F5 Swappable policy + concurrency tool B (claim exactly once). See [foundations](../../00_AMAZON_LLD_FOUNDATIONS.md).

---

## 1. Plain-language picture

### The mall parking
```
Entry gate:  car arrives → "you get spot F1-M2" → ticket printed with the entry time
Exit gate:   ticket scanned → "2h10m = 3 hours × ₹50 = ₹150" → barrier opens → spot is free again
```

### Sizes: a bike fits anywhere, a truck doesn't
```
Vehicle:  MOTORCYCLE(1)   CAR(2)    TRUCK(3)
Spot:     SMALL(1)        MEDIUM(2) LARGE(3)
fits if spot size ≥ vehicle size
```
The **allocation strategy** decides *which* fitting spot to give. **Smallest fit** keeps big spots for big vehicles: a bike goes to a bike spot, and takes a car spot only when the bike spots are full. **Lowest floor** puts everyone close to the entrance. Same lot, different policy: that's Strategy.

### The guard's clipboard (the one source of truth)
The painted spot on the floor doesn't know if a car is on it. The **guard's clipboard** does: a list of occupied spot ids. One clipboard means there's never a "spot says free, lot says taken" disagreement.

### Two gates, one spot
Gate A and Gate B both see spot C1 free at the same instant. Without care, both print a ticket for C1. The fix is a clipboard where **writing a spot id succeeds only if it isn't already there**: `occupiedSpotIds.add("C1")` returns `true` for exactly one gate. The other gate tries the next spot. No global lock, so the two gates never wait for each other.

### Pricing
Per **started** hour, minimum 1 hour: 5 min → 1 h, 2h10m → 3 h. Rates differ by vehicle. Offers like "first hour free" or a daily cap wrap around the base price (Decorator, Q3).

---

## 2. From story to design

| # | Decision | Rejected | Why |
|---|---|---|---|
| D1 | `ParkingSpot` is **immutable** (id, floor, type); free/occupied lives in the lot's `occupiedSpotIds` set | `spot.isOccupied` field | One source of truth. Also, an immutable spot needs no locking. |
| D2 | `occupiedSpotIds` = `ConcurrentHashMap.newKeySet()`; claim with `add()` | `synchronized` on the whole lot | `add()` is atomic: exactly one gate wins a spot, and gates never block each other. |
| D3 | `SpotAllocationStrategy.rank()` returns **best-first**; the lot claims the first it can | strategy returns one spot | The best spot may be grabbed a microsecond earlier by another gate; just try the next. |
| D4 | Size as an `int` on the enums + `SpotType.canFit(vehicle)` | a type → type mapping table | A bike in a car spot works automatically; adding a size is one enum value. |
| D5 | `PricingStrategy.fee(vehicleType, duration)` | price math inside `exit()` | Pricing changes constantly (offers, weekends); keep it swappable. |
| D6 | `activeTickets.remove(id)` on exit (atomic) | `get` then `remove` | Two simultaneous scans of one ticket: only one exit, and only one fee. |
| D7 | `platesInside.putIfAbsent(plate, ...)` | no vehicle check | The same car can't hold two spots (a cloned or copied ticket). |
| D8 | `Clock` injected | `LocalDateTime.now()` | The driver can "wait 2h10m" instantly and check the exact fee. |
| D9 | Money as `long` rupees | `double` / cents | Whole-rupee rates; no rounding errors. |
| D10 | **Not** classes: `Floor`, `Gate`, `EntrancePanel` | modelling the building | No behaviour of their own here. Floor is a field on the spot. |

### Class shape
```
ParkingLot                                ← the service the gates call
  List<ParkingSpot> spots
  Set<String> occupiedSpotIds             ← the clipboard (concurrent set)
  Map<ticketId, Ticket> activeTickets
  Map<plate, ticketId> platesInside
  SpotAllocationStrategy · PricingStrategy · Clock
  enter(vehicle) → Ticket · exit(ticketId) → fee · freeSpotsFor(vehicle)

ParkingSpot { id, floor, SpotType }       (immutable)
Vehicle { plate, VehicleType }   Ticket { id, spotId, vehicle, entryTime }
VehicleType(size) MOTORCYCLE/CAR/TRUCK    SpotType(size) SMALL/MEDIUM/LARGE + canFit()

«interface» SpotAllocationStrategy  rank(freeFittingSpots, vehicleType)
   ├── SmallestFitStrategy   └── LowestFloorStrategy
«interface» PricingStrategy  fee(vehicleType, duration)
   └── HourlyPricing
```

---

## 3. Patterns that earn their place

| Pattern | In the base? | Problem it solves | Without it |
|---|---|---|---|
| **Strategy** (`SpotAllocationStrategy`) | **Yes** | smallest-fit vs lowest-floor vs near-the-lift | the spot-choosing loop hard-coded in `enter()`; changing the policy edits the core flow |
| **Strategy** (`PricingStrategy`) | **Yes** | hourly today; weekend, flat event rates tomorrow | fee math tangled with exit logic |
| **Decorator** (around pricing) | No | stack offers: first hour free + daily cap | Q3. Without it, one class per combination. |
| **Strategy** (`PaymentMethod`) | No | cash / card / FASTag / UPI at exit | Q4 |

**Say:** *"Two strategies: one decides which spot, one decides the price. Both change independently of the parking logic."*

**Tempting but wrong here:**
- **Singleton `ParkingLot`:** a city has many lots. Create and inject.
- **State pattern for a spot:** free/occupied is one fact in a set, not behaviour.
- **Subclass per vehicle (`Car extends Vehicle`):** vehicles don't behave differently; a `VehicleType` enum is enough.
- **Factory for spots or vehicles:** construction is trivial.

---

## 4. The 45-minute run

### Clarify (min 0–5)
> **You:** Vehicle types and spot sizes: bike, car, truck, with small/medium/large spots, and a smaller vehicle can use a bigger spot?
> **Interviewer:** Yes.
> **You:** Multiple floors and multiple entry/exit gates?
> **Interviewer:** Yes, several gates working at the same time.
> **You:** Pricing: per started hour, different rates per vehicle type, minimum 1 hour?
> **Interviewer:** Good.
> **You:** Which spot do we give: smallest that fits, or nearest?
> **Interviewer:** Make it configurable.
> **You:** Payment, reservations, lost tickets: out of scope?
> **Interviewer:** For now.

```
In scope:  enter(vehicle) → ticket · exit(ticket) → fee · free-spot count
           3 vehicle types, 3 spot sizes, smaller fits bigger · multiple floors
           allocation strategy (configurable) · pricing strategy (per started hour)
           concurrent gates: no double allocation, no double exit, no duplicate car
Out:       payment, reservations, lost ticket, EV charging
```

### Timeline
| Min | Do |
|---|---|
| 5–9 | Class shape. Say the crux: *"Occupancy lives in one concurrent set; `add()` is the atomic claim. Two strategies."* |
| 9–13 | Code step 1: enums with size + `canFit`, then `Vehicle`, `ParkingSpot`, `Ticket`. |
| 13–18 | Code step 2: both strategy interfaces + `SmallestFitStrategy` + `HourlyPricing`. |
| 18–30 | Code step 3: **`ParkingLot`**: `enter`, `exit`, `freeSpotsFor`. |
| 30–35 | Dry-run: two gates race for one spot; a 2h10m fee. |
| 35–45 | Follow-ups. |

### The code you write, in this order

**Step 1: entities** ([model/](model/))
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

    // a bike fits in a car spot; a truck doesn't fit in a bike spot
    public boolean canFit(VehicleType vehicle) { return size >= vehicle.getSize(); }
}

public class Vehicle {
    private final String licensePlate;
    private final VehicleType type;
    // constructor + getters
}

public class ParkingSpot {                  // immutable: whether it's free lives in the lot
    private final String id;
    private final int floor;
    private final SpotType type;
    // constructor + getters
}

public class Ticket {
    private final String id;
    private final String spotId;
    private final Vehicle vehicle;
    private final Instant entryTime;
    // constructor + getters
}
```

**Step 2: the two strategies** ([allocation/](allocation/), [pricing/](pricing/))
```java
public interface SpotAllocationStrategy {
    List<ParkingSpot> rank(List<ParkingSpot> freeFittingSpots, VehicleType vehicle);   // best first
}

// smallest spot that fits, then lowest floor: keeps big spots for big vehicles
public class SmallestFitStrategy implements SpotAllocationStrategy {
    @Override
    public List<ParkingSpot> rank(List<ParkingSpot> freeFittingSpots, VehicleType vehicle) {
        List<ParkingSpot> ranked = new ArrayList<>(freeFittingSpots);
        ranked.sort(Comparator.comparingInt((ParkingSpot s) -> s.getType().getSize())
                              .thenComparingInt(ParkingSpot::getFloor));
        return ranked;
    }
}
// LowestFloorStrategy: same, sorted by floor only

public interface PricingStrategy {
    long fee(VehicleType vehicle, Duration parked);    // whole rupees
}

public class HourlyPricing implements PricingStrategy {
    private final Map<VehicleType, Long> ratePerHour;
    public HourlyPricing(Map<VehicleType, Long> ratePerHour) { this.ratePerHour = new EnumMap<>(ratePerHour); }

    @Override
    public long fee(VehicleType vehicle, Duration parked) {
        long minutes = parked.toMinutes();
        long hours = Math.max(1, (minutes + 59) / 60);    // round UP; minimum 1 hour
        return hours * ratePerHour.get(vehicle);
    }
}
```
`(minutes + 59) / 60` is integer **ceiling division**: 130 min → 189 / 60 = 3.

**Step 3: the lot** ([ParkingLot.java](ParkingLot.java))
```java
public class ParkingLot {

    private final List<ParkingSpot> spots;
    private final Set<String> occupiedSpotIds = ConcurrentHashMap.newKeySet();     // the clipboard
    private final Map<String, Ticket> activeTickets = new ConcurrentHashMap<>();   // ticketId → ticket
    private final Map<String, String> platesInside = new ConcurrentHashMap<>();    // plate → ticketId
    private final SpotAllocationStrategy allocationStrategy;
    private final PricingStrategy pricingStrategy;
    private final Clock clock;
    private final AtomicLong ticketSeq = new AtomicLong();

    public ParkingLot(List<ParkingSpot> spots, SpotAllocationStrategy allocationStrategy,
                      PricingStrategy pricingStrategy, Clock clock) {
        this.spots = new ArrayList<>(spots);
        this.allocationStrategy = allocationStrategy;
        this.pricingStrategy = pricingStrategy;
        this.clock = clock;
    }

    public Ticket enter(Vehicle vehicle) {
        String ticketId = "T-" + ticketSeq.incrementAndGet();
        if (platesInside.putIfAbsent(vehicle.getLicensePlate(), ticketId) != null) {
            throw new IllegalStateException(vehicle.getLicensePlate() + " is already inside");
        }

        List<ParkingSpot> candidates = new ArrayList<>();
        for (ParkingSpot spot : spots) {
            if (spot.getType().canFit(vehicle.getType()) && !occupiedSpotIds.contains(spot.getId())) {
                candidates.add(spot);
            }
        }
        for (ParkingSpot spot : allocationStrategy.rank(candidates, vehicle.getType())) {
            if (occupiedSpotIds.add(spot.getId())) {         // atomic claim; false = another gate got it
                Ticket ticket = new Ticket(ticketId, spot.getId(), vehicle, clock.instant());
                activeTickets.put(ticketId, ticket);
                return ticket;
            }
        }
        platesInside.remove(vehicle.getLicensePlate());       // undo: the car didn't get in
        throw new IllegalStateException("No spot available for " + vehicle.getType());
    }

    public long exit(String ticketId) {
        Ticket ticket = activeTickets.remove(ticketId);       // atomic: a second exit finds nothing
        if (ticket == null) throw new NoSuchElementException("Ticket not found or already used: " + ticketId);

        Duration parked = Duration.between(ticket.getEntryTime(), clock.instant());
        long fee = pricingStrategy.fee(ticket.getVehicle().getType(), parked);
        occupiedSpotIds.remove(ticket.getSpotId());
        platesInside.remove(ticket.getVehicle().getLicensePlate());
        return fee;
    }

    public int freeSpotsFor(Vehicle vehicle) {               // the display board at the entrance
        int free = 0;
        for (ParkingSpot spot : spots) {
            if (spot.getType().canFit(vehicle.getType()) && !occupiedSpotIds.contains(spot.getId())) free++;
        }
        return free;
    }
}
```
**Shape to remember:** `enter` = plate check → filter free + fitting → rank → `add()` the first you can → ticket. `exit` = `remove` ticket → fee → free the spot.

### Dry run
```
Spots: G-S1 (small), G-M1 (medium), F1-M2 (medium), F1-L1 (large). Smallest-fit.

bike 1 → candidates [all 4] → ranked [G-S1, G-M1, F1-M2, F1-L1] → add(G-S1) ✓
bike 2 → G-S1 taken → ranked [G-M1, F1-M2, F1-L1] → add(G-M1) ✓   (bike spots full → car spot)

Gate A and Gate B, two cars, same instant:
  both: candidates [F1-M2, F1-L1] → ranked [F1-M2, F1-L1]
  A: add(F1-M2) → true  ✓ ticket
  B: add(F1-M2) → false → add(F1-L1) → true ✓ ticket       (no lock held, no waiting)

Car leaves after 2h10m: 130 min → (130+59)/60 = 3 h × ₹50 = ₹150
```
The driver proves it: 50 cars racing for 10 spots gives exactly 10 tickets and 10 distinct spots.

---

## 5. Follow-ups: answer out loud first, then open

<details>
<summary><b>Q1. Two gates, one free spot, same instant. Walk me through it.</b></summary>

Both build the candidate list and both see the spot free; that's fine, it's just a read. The **claim** is `occupiedSpotIds.add(spotId)`. A concurrent set's `add` is atomic, so one gate gets `true` and the other `false`, which then tries its next candidate.

**Why not `synchronized enter()`?** That's correct too, and fine to start with if you say so. But every gate then waits for every other gate. With an atomic claim, gates only "collide" when they want the *same* spot, and even then nobody waits; the loser just moves on.

Same idea for exit: `activeTickets.remove(id)` returns the ticket to exactly one caller, so a double-scanned ticket is charged once.
</details>

<details>
<summary><b>Q2. Scanning every spot on each entry is slow for a 5,000-spot lot.</b></summary>

Keep a **queue of free spots per type**. `poll()` on a `ConcurrentLinkedQueue` is atomic, so it's the claim itself:
```java
private final Map<SpotType, Queue<ParkingSpot>> freeByType = new EnumMap<>(SpotType.class);

public FreeSpotIndex(List<ParkingSpot> spots) {
    for (SpotType t : SpotType.values()) freeByType.put(t, new ConcurrentLinkedQueue<>());
    for (ParkingSpot s : spots) freeByType.get(s.getType()).offer(s);
}

// smallest type that fits first; poll() is atomic, so two cars never get the same spot
public ParkingSpot take(VehicleType vehicle) {
    for (SpotType type : SpotType.values()) {                 // SMALL, MEDIUM, LARGE in size order
        if (!type.canFit(vehicle)) continue;
        ParkingSpot spot = freeByType.get(type).poll();
        if (spot != null) return spot;
    }
    return null;                                              // full
}

public void release(ParkingSpot spot) { freeByType.get(spot.getType()).offer(spot); }
```
O(number of spot types) per entry instead of O(spots). The trade-off: "lowest floor first" now needs a priority queue per type (`PriorityBlockingQueue` ordered by floor) instead of a FIFO queue.
</details>

<details>
<summary><b>Q3. Mall offer: first hour free, and never more than ₹400 a day.</b></summary>

**Decorators** wrap any `PricingStrategy`, so offers stack without new combination classes:
```java
public class FirstHourFree implements PricingStrategy {
    private final PricingStrategy inner;
    public FirstHourFree(PricingStrategy inner) { this.inner = inner; }

    public long fee(VehicleType vehicle, Duration parked) {
        if (parked.toMinutes() <= 60) return 0;
        return inner.fee(vehicle, parked.minusHours(1));      // charge only the time after the free hour
    }
}

public class DailyCap implements PricingStrategy {
    private final PricingStrategy inner;
    private final long capPerDay;
    public DailyCap(PricingStrategy inner, long capPerDay) { this.inner = inner; this.capPerDay = capPerDay; }

    public long fee(VehicleType vehicle, Duration parked) {
        long days = Math.max(1, (parked.toMinutes() + 24 * 60 - 1) / (24 * 60));   // started days
        return Math.min(inner.fee(vehicle, parked), days * capPerDay);
    }
}
// usage
PricingStrategy mall = new DailyCap(new FirstHourFree(new HourlyPricing(rates)), 400);
// 50 min → ₹0 · 2h30m → 1h30m charged → ₹100 · 30 h → ₹1,450 capped at 2 days × ₹400 = ₹800
```
</details>

<details>
<summary><b>Q4. Pay at the exit (cash, card, FASTag). What if payment fails?</b></summary>

`PaymentMethod` Strategy. Claim the exit first, then charge; if the charge fails, **put the ticket back**, because the car is still inside:
```java
public interface PaymentMethod {
    boolean charge(long amountRupees, String idempotencyKey);
}

public long exit(String ticketId, PaymentMethod payment) {
    Ticket ticket = activeTickets.remove(ticketId);            // claim the exit (one gate only)
    if (ticket == null) throw new NoSuchElementException("Ticket not found or already used: " + ticketId);

    long fee = pricingStrategy.fee(ticket.getVehicle().getType(),
                                   Duration.between(ticket.getEntryTime(), clock.instant()));
    if (!payment.charge(fee, ticketId)) {                      // ticketId = idempotency key
        activeTickets.put(ticketId, ticket);                   // still parked: try another method
        throw new IllegalStateException("Payment failed");
    }
    occupiedSpotIds.remove(ticket.getSpotId());
    platesInside.remove(ticket.getVehicle().getLicensePlate());
    return fee;
}
```
</details>

<details>
<summary><b>Q5. "I lost my ticket."</b></summary>

The plate is the backup key: `platesInside.get(plate)` gives the ticketId, then a normal exit. Many lots charge a **lost-ticket fee** (e.g. the full daily rate) as a deterrent: `max(normalFee, lostTicketFee)`. Number-plate cameras (ANPR) at the gate make this automatic.
</details>

<details>
<summary><b>Q6. Display the free spots per floor at the entrance.</b></summary>

Count from the clipboard: group spots by floor and count those not in `occupiedSpotIds`. For a big lot, keep `AtomicInteger freeCount` per (floor, type), decremented on a successful claim and incremented on exit. The display reads it in O(1). It can be briefly stale, and that's OK: `enter()` re-checks atomically anyway.
</details>

<details>
<summary><b>Q7. Add EV charging spots.</b></summary>

`ParkingSpot` gets `boolean hasCharger`, and `Vehicle` gets `boolean electric`. A new `EvFirstStrategy` ranks charger spots first for EVs, and **non**-charger spots first for petrol cars, so chargers stay free for EVs. The pricing decorator adds a per-kWh charge. Nothing in `ParkingLot` changes: that's the Strategy payoff.
</details>

<details>
<summary><b>Q8. Reserve a spot in advance from the app.</b></summary>

This turns into a **booking-over-time** problem (see Movie Ticket Q10 / Meeting Room): a reservation holds a spot for a time range `[start, end)`. The allocation step skips spots with an active reservation, and a no-show releases the hold after a grace period (lazy expiry). Keep reservations separate from occupancy: one is the future, the other is now.
</details>

<details>
<summary><b>Q9. Many lots across the city, one backend.</b></summary>

`Map<lotId, ParkingLot>` behind a `ParkingService`. A "find a lot near me with free car spots" query uses the per-lot free counts (Q6). The atomic claim moves to the DB:
```sql
-- 1 row inserted = spot claimed; duplicate key = taken
INSERT INTO occupied_spot (lot_id, spot_id, ticket_id) VALUES ('L1', 'F1-M2', 'T-77');
-- PRIMARY KEY (lot_id, spot_id)
```
</details>

<details>
<summary><b>Q10. How would you know it's working in production?</b></summary>

- **Metrics:** occupancy % per lot/floor/type, entries/exits per gate, average stay, revenue per day, payment failures.
- **Alarms:** occupancy at 100% for long periods (raise prices or redirect), a gate with zero traffic (broken barrier), and occupied count ≠ active tickets (a leak).
- **Logs:** ticketId + plate + spot + gate on every enter/exit.
</details>

<details>
<summary><b>Q11. How do you test it?</b></summary>

- **Pricing:** a movable `Clock`: enter, advance 130 minutes, exit, expect ₹150. Also 5 min → minimum ₹50, and exactly 60 min → 1 hour.
- **Allocation:** bike goes to small first, then medium when small is full; a truck only fits large.
- **Concurrency:** 50 threads entering at once with 10 spots → exactly 10 tickets, 10 distinct spots (in the driver).
- **Edge cases:** double exit, unknown ticket, the same plate twice.
</details>

---

## 6. Traps that cost points
1. `spot.isOccupied` **and** a lot-level map: two sources of truth.
2. `if (isFree(spot)) occupy(spot)` in two steps without an atomic claim.
3. A bike refused because "bike spots are full" while car spots are free.
4. Pricing math inside `exit()` with hard-coded rates.
5. `double` money; `minutes / 60` (rounds **down**: 2h10m → 2 h).
6. `LocalDateTime.now()` everywhere: fees can't be tested.
7. `Floor`, `Gate`, `Panel`, `Building` classes with no behaviour, eating 10 minutes.

---

## 7. Recall check (next day, no peeking)
1. Where does "spot C1 is taken" live, and why not on the spot?
2. What single line stops two gates getting the same spot?
3. Why does the allocation strategy return a ranked list rather than one spot?
4. Fee for a car (₹50/h) parked 61 minutes? 2h59m?
5. How do "first hour free" and "daily cap" stack without new classes per combination?
6. Exit with payment: what happens if the payment fails, and why put the ticket back?

<details><summary>Answer to 4</summary>

61 min → (61 + 59) / 60 = 2 h → ₹100. 179 min → 238 / 60 = 3 h → ₹150.
</details>

**Rebuild in 10 minutes:** `VehicleType`/`SpotType` with size + `canFit` · immutable `ParkingSpot` · `SpotAllocationStrategy.rank` + `SmallestFit` · `PricingStrategy` + `HourlyPricing` · `ParkingLot.enter` (filter → rank → `add()`) and `exit` (`remove` → fee → free).

---

**Files:** `ParkingLot` (service) · `model/` (`VehicleType`, `SpotType`, `Vehicle`, `ParkingSpot`, `Ticket`) · `allocation/` (`SpotAllocationStrategy`, `SmallestFitStrategy`, `LowestFloorStrategy`) · `pricing/` (`PricingStrategy`, `HourlyPricing`) · `ParkingLotDriver` (smallest fit, truck, duplicate plate, full lot, fees with a movable clock, double exit, strategy swap, 50-thread test)
```bash
mvn -q compile exec:java -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot.ParkingLotDriver
```
