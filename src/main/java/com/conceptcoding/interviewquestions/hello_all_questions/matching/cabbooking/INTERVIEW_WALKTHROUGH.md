# Cab Booking / Rider Matching

> 30–40 min round. Amazon ★ (reported twice in 2026). Phases: **Requirements (5) → Entities (3) → Class Design (10–15) → Implementation (10) → Extensibility (5)**.
> Sister problem: [Food Delivery](../fooddelivery/INTERVIEW_WALKTHROUGH.md) (the same match → claim → lifecycle idea).

---

## 0. The problem in plain words (read first, don't say it in the interview)

**Picture Uber or Ola.** You book a cab. The app finds the **nearest free driver**. The driver picks you up, drops you off, you pay, and the driver is free for the next person. You can cancel while the driver is on the way, but not once you're in the car.

**The whole system is 4 actions:**
```
Book       →  requestRide()   →  pick the nearest free driver, mark them busy
Pick up    →  startRide()     →  the trip has started
Drop off   →  completeRide()  →  tell the fare, the driver is free again
Cancel     →  cancelRide()    →  only before pickup; the driver is free again
```

**Real world → code**
| In the real world | In the code |
|---|---|
| A driver | `Driver`: where they are, free or busy |
| One booking | `Ride`: rider, driver, from, to, status |
| "On the way" → "On trip" → "Done" / "Cancelled" | `RideStatus`: MATCHED → IN_PROGRESS → COMPLETED, or CANCELLED |
| A place on the map | `Location`: x, y in km |
| How the app picks your driver | `DriverMatchingStrategy` (nearest for now) |
| The app's backend | `CabBookingService` |

**Only 2 things are tricky:**
1. **Two people book at the same moment, and one driver is nearest to both.** Only one may get that driver, so the service methods are `synchronized`.
2. **A trip only moves forward.** You can't finish a trip that never started, or cancel one you're sitting in. `RideStatus` knows the legal next steps.

**Picture this run before you start coding** (fare = ₹50 + ₹12 per km):
```
Bharat is 1 km away, Chitra 5 km away
Asha books Home → Airport (25 km)  → Bharat (nearest)        → R-1
Ravi books                         → Chitra (the only free one) → R-2
Meera books                        → nobody free → "No drivers available"
Finish R-1 before it starts        → rejected
Start R-1, finish R-1              → ₹50 + 25 × ₹12 = ₹350, Bharat is free (now at the airport)
Ravi cancels R-2                   → Chitra is free
```
If you can explain this run, you understand the problem.

---

## 1. Requirements (~5 min)

**Ask:** how do we pick a driver (nearest)? Which trip states? Can the rider cancel, and until when? How is the fare calculated? Many riders at once?

```
1. requestRide(riderId, pickup, drop) → nearest free driver (error if none)
2. Trip: MATCHED → IN_PROGRESS → COMPLETED; cancel only before it starts
3. completeRide → fare = ₹50 + ₹12/km; the driver is free again at the drop point
4. Safe with many riders at once (no driver gets two rides)

Out of scope: payment, surge, ratings, driver accept/reject, live tracking
```

---

## 2. Entities & Relationships (~3 min)

```
CabBookingService
   ├── Driver   (location, available)
   ├── Ride     (riderId, driver, pickup, drop, status)
   └── DriverMatchingStrategy   "which driver"
```
- **Legal trip moves** live in `RideStatus.canMoveTo`, and `Ride.moveTo` enforces them.
- **No Rider class:** a rider has no behaviour here, so a `riderId` string is enough.
- **No pricing class:** the fare is one formula for now. It becomes a Strategy when the rules multiply (Extension 1).

---

## 3. Class Design (~10 min)

```
CabBookingService
  state:     drivers, rides, matchingStrategy, ride counter
  behaviour: requestRide(riderId, pickup, drop) → Ride · startRide(id) · completeRide(id) → fare · cancelRide(id)

Driver       id, name, location, available        finishTrip(dropPoint)
Ride         id, riderId, driver, pickup, drop, status        moveTo(next)
RideStatus   MATCHED, IN_PROGRESS, COMPLETED, CANCELLED      canMoveTo(next)
Location     x, y                                             distanceTo(other)

DriverMatchingStrategy   match(pickup, availableDrivers) → Driver or null   ← NearestDriverStrategy
```
- **One Strategy:** "which driver" is the rule interviewers change most (rating, radius...).
- **Status is an enum** with `canMoveTo`, not the State pattern: the statuses only limit the next move.
- **Thread safety:** every service method is `synchronized`, so two riders can't get the same driver.

---

## 4. Implementation (~10 min)

**Write it in this order:** `RideStatus` → `Location`, `Driver`, `Ride` → strategy → service.

```java
public enum RideStatus {
    MATCHED, IN_PROGRESS, COMPLETED, CANCELLED;

    public boolean canMoveTo(RideStatus next) {
        switch (this) {
            case MATCHED:     return next == IN_PROGRESS || next == CANCELLED;
            case IN_PROGRESS: return next == COMPLETED;
            default:          return false;                // COMPLETED, CANCELLED are final
        }
    }
}

public class Location {
    private final double x, y;                             // km on a simple grid
    public Location(double x, double y) { this.x = x; this.y = y; }
    public double distanceTo(Location other) { return Math.hypot(x - other.x, y - other.y); }
}

public class Driver {
    private final String id, name;
    private Location location;
    private boolean available = true;

    public void finishTrip(Location dropPoint) { location = dropPoint; available = true; }
    // constructor, getters, setAvailable
}

public class Ride {
    private final String id, riderId;
    private final Driver driver;
    private final Location pickup, drop;
    private RideStatus status = RideStatus.MATCHED;        // created only once a driver is found

    public void moveTo(RideStatus next) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("Ride " + id + ": cannot go from " + status + " to " + next);
        }
        status = next;
    }
    // constructor, getters
}
```

```java
public interface DriverMatchingStrategy {
    Driver match(Location pickup, List<Driver> availableDrivers);       // null = none
}

public class NearestDriverStrategy implements DriverMatchingStrategy {
    public Driver match(Location pickup, List<Driver> availableDrivers) {
        Driver nearest = null;
        for (Driver d : availableDrivers) {
            if (nearest == null || d.getLocation().distanceTo(pickup) < nearest.getLocation().distanceTo(pickup)) {
                nearest = d;                                   // closest so far
            }
        }
        return nearest;
    }
}
```

```java
public class CabBookingService {
    private static final long BASE_FARE = 50, PER_KM = 12;
    private final Map<String, Driver> drivers = new HashMap<>();
    private final Map<String, Ride> rides = new HashMap<>();
    private final DriverMatchingStrategy matchingStrategy;
    private int nextRideNumber = 1;

    // constructor sets the strategy; addDriver(driver) puts it in the map

    public synchronized Ride requestRide(String riderId, Location pickup, Location drop) {
        List<Driver> available = new ArrayList<>();
        for (Driver d : drivers.values()) {
            if (d.isAvailable()) available.add(d);
        }
        Driver driver = matchingStrategy.match(pickup, available);
        if (driver == null) {
            throw new IllegalStateException("No drivers available");
        }
        driver.setAvailable(false);
        Ride ride = new Ride("R-" + nextRideNumber++, riderId, driver, pickup, drop);
        rides.put(ride.getId(), ride);
        return ride;
    }

    public synchronized void startRide(String rideId) {
        findRide(rideId).moveTo(RideStatus.IN_PROGRESS);
    }

    public synchronized long completeRide(String rideId) {
        Ride ride = findRide(rideId);
        ride.moveTo(RideStatus.COMPLETED);                     // throws if it never started
        ride.getDriver().finishTrip(ride.getDrop());           // free again, at the drop point
        return BASE_FARE + Math.round(ride.getPickup().distanceTo(ride.getDrop()) * PER_KM);
    }

    public synchronized void cancelRide(String rideId) {
        Ride ride = findRide(rideId);
        ride.moveTo(RideStatus.CANCELLED);                     // throws once the trip has started
        ride.getDriver().setAvailable(true);
    }

    private Ride findRide(String rideId) {
        Ride ride = rides.get(rideId);
        if (ride == null) throw new NoSuchElementException("Unknown ride " + rideId);
        return ride;
    }
}
```

**Verify out loud:** walk through the run from section 0. Then add: *"after R-1, Kiran books from Home and gets Chitra, because Bharat is now 25 km away at the airport."*

---

## 5. Extensibility (~5 min)

Name **which class changes**, write the few new lines, and don't redesign the rest.

**Quick revision** (read this table on the day of the interview):

| # | Interviewer asks | One-line answer | What changes |
|---|---|---|---|
| 1 | "Surge pricing / different fares" | Pull the fare out into a `PricingStrategy`; surge wraps it (Decorator) | 3 new classes, 1 line in `completeRide` |
| 2 | "Pick the best-rated driver" | One more matching strategy | `rating` on `Driver` + 1 new class |
| 3 | "Only drivers within 3 km" | Skip far drivers inside the strategy | 1 new class |
| 4 | "A rider taps Book twice" | Remember each rider's active ride | 1 map in the service |
| 5 | "The driver can reject" | A new `OFFERED` status; on reject, offer to the next driver | `RideStatus`, `Ride`, 2 service methods |
| 6 | "Many riders, the lock is slow" | `Driver.tryReserve()` claims a driver atomically | `Driver` + `requestRide` |
| 7 | "Real map, 50,000 drivers" | Real lat/lng distance + a grid index of nearby drivers | say it, no code |

---

### 1. "Surge pricing" / "airport flat fare"
**Idea:** once there's more than one fare rule, move the formula into a `PricingStrategy`. Surge **wraps** the normal price, so it works on top of any rule.
```java
public interface PricingStrategy {
    long fare(Location pickup, Location drop);
}

public class DistancePricing implements PricingStrategy {
    private final long baseFare, perKm;
    public DistancePricing(long baseFare, long perKm) { this.baseFare = baseFare; this.perKm = perKm; }
    public long fare(Location pickup, Location drop) { return baseFare + Math.round(pickup.distanceTo(drop) * perKm); }
}

public class SurgePricing implements PricingStrategy {
    private final PricingStrategy inner;
    private final int percent;                             // 150 = 1.5×
    public SurgePricing(PricingStrategy inner, int percent) { this.inner = inner; this.percent = percent; }
    public long fare(Location pickup, Location drop) { return inner.fare(pickup, drop) * percent / 100; }
}

// completeRide: return pricingStrategy.fare(ride.getPickup(), ride.getDrop());
// new CabBookingService(new NearestDriverStrategy(), new SurgePricing(new DistancePricing(50, 12), 150))
```
**Check:** 25 km → ₹350 normally, ₹525 at 1.5×.
**Say:** *"I kept the fare inline while there was one rule. With two rules it earns a Strategy, and surge is a Decorator, so it stacks on any price."*

---

### 2. "Pick the best-rated driver"
```java
// Driver: private double rating;  + getRating()

public class BestRatedStrategy implements DriverMatchingStrategy {
    public Driver match(Location pickup, List<Driver> availableDrivers) {
        Driver best = null;
        for (Driver d : availableDrivers) {
            if (best == null || d.getRating() > best.getRating()) best = d;
        }
        return best;
    }
}
```
**Say:** *"A new rule is a new class. The service doesn't change, which is the reason matching is a Strategy."*

---

### 3. "Only drivers within 3 km"
```java
public class NearestWithinStrategy implements DriverMatchingStrategy {
    private final double maxKm;
    public NearestWithinStrategy(double maxKm) { this.maxKm = maxKm; }

    public Driver match(Location pickup, List<Driver> availableDrivers) {
        Driver nearest = null;
        for (Driver d : availableDrivers) {
            double km = d.getLocation().distanceTo(pickup);
            if (km > maxKm) continue;                          // too far: skip
            if (nearest == null || km < nearest.getLocation().distanceTo(pickup)) nearest = d;
        }
        return nearest;                                        // null → "No drivers available"
    }
}
```
**Check:** Bharat 1 km, Chitra 5 km → Bharat. Only Chitra free → nobody.

---

### 4. "The rider taps Book twice"
**Idea:** without a guard, a double tap books two drivers. Remember each rider's open ride.
```java
private final Map<String, String> activeRideByRider = new HashMap<>();     // riderId → rideId

// requestRide(): first line
if (activeRideByRider.containsKey(riderId)) {
    throw new IllegalStateException(riderId + " already has ride " + activeRideByRider.get(riderId));
}
// requestRide(): after creating the ride
activeRideByRider.put(riderId, ride.getId());

// completeRide() and cancelRide(): at the end
activeRideByRider.remove(ride.getRiderId());
```
**Say:** *"The check and the put are both inside the synchronized method, so two taps at the same instant still give one ride."*

---

### 5. "The driver can accept or reject"
**Idea:** a ride starts as `OFFERED`. Accept → MATCHED. Reject → free that driver and offer the ride to the next one, skipping drivers who already said no. If nobody is left, cancel it.
```java
// RideStatus: add OFFERED first
case OFFERED: return next == MATCHED || next == CANCELLED;

// Ride: driver is no longer final + remembers who said no
private final Set<String> rejectedBy = new HashSet<>();
public void addRejectedBy(String driverId)     { rejectedBy.add(driverId); }
public boolean wasRejectedBy(String driverId)  { return rejectedBy.contains(driverId); }

// CabBookingService (requestRide now creates the ride as OFFERED)
public synchronized void acceptRide(String rideId) {
    findRide(rideId).moveTo(RideStatus.MATCHED);
}

public synchronized void rejectRide(String rideId) {
    Ride ride = findRide(rideId);
    if (ride.getStatus() != RideStatus.OFFERED) throw new IllegalStateException("Ride " + rideId + " is not waiting for an answer");
    ride.getDriver().setAvailable(true);
    ride.addRejectedBy(ride.getDriver().getId());

    List<Driver> candidates = new ArrayList<>();
    for (Driver d : drivers.values()) {
        if (d.isAvailable() && !ride.wasRejectedBy(d.getId())) candidates.add(d);
    }
    Driver next = matchingStrategy.match(ride.getPickup(), candidates);
    if (next == null) {
        ride.moveTo(RideStatus.CANCELLED);                     // nobody left
        return;
    }
    next.setAvailable(false);
    ride.setDriver(next);
}
```
**Check:** Bharat rejects → Chitra is offered, Bharat is free · Chitra rejects → the ride is CANCELLED.
**Say:** *"The same matching strategy picks the next driver. I only filter out the drivers who said no. A timeout (no answer in 15 s) would call `rejectRide`."*

---

### 6. "Many riders, the single lock is slow"
**First say:** *"Each call holds the lock for microseconds. I'd measure first."* If they still want it:

**Idea:** lock **one driver**, not the whole service. `tryReserve()` checks and claims in one step. If another rider got the driver first, pick again.
```java
// Driver
public synchronized boolean tryReserve() {
    if (!available) return false;
    available = false;
    return true;
}

// CabBookingService: maps become ConcurrentHashMap, counter becomes AtomicInteger, no synchronized
public Ride requestRide(String riderId, Location pickup, Location drop) {
    while (true) {
        Driver driver = matchingStrategy.match(pickup, availableDrivers());
        if (driver == null) throw new IllegalStateException("No drivers available");
        if (driver.tryReserve()) {                             // true for exactly one rider
            Ride ride = new Ride("R-" + rideSeq.incrementAndGet(), riderId, driver, pickup, drop);
            rides.put(ride.getId(), ride);
            return ride;
        }
        // someone else just took this driver: loop and pick again
    }
}
```
**Check:** 50 riders, 10 drivers at the same instant → exactly 10 rides, 10 different drivers.
**Say:** *"The strategy doesn't change. Only the claim moved from a service lock to a per-driver one."*

---

### 7. "A real city: lat/lng and 50,000 drivers"
**Say, don't code:** *"`distanceTo` becomes the haversine formula on lat/lng. Looping over 50,000 drivers per request is too slow, so I'd split the map into grid cells (or geohashes) and keep a `Map<cell, Set<Driver>>`. A request checks only the pickup's cell and its neighbours, and a driver moves between cells as their location updates. The strategy interface stays the same: it just gets a shorter list."*

---

```bash
mvn -q compile exec:java -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.matching.cabbooking.CabBookingDriver
```
