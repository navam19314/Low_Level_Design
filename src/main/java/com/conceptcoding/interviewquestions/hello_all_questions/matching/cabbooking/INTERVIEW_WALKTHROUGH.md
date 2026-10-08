# Cab Booking / Rider Matching (Uber / Ola)

> **Amazon:** ★ reported twice in 2026 ("rider matching", "quick-commerce delivery assignment"). Your guide's key points: *Driver/Rider entities and states; MatchingStrategy (nearest, rating, least busy); a state machine for the trip; concurrency when two orders want one rider.*
>
> **The crux (what's really being tested):**
> 1. **Matching as a Strategy** that returns a **ranked** list, so if the best driver is taken a microsecond earlier you try the next.
> 2. **The atomic claim:** two riders requesting at once must never get the same driver (`tryReserve()`).
> 3. **The trip lifecycle** as an enum state machine: REQUESTED → MATCHED → IN_PROGRESS → COMPLETED, cancel only before the trip starts.
>
> **Family:** matching = F5 Strategy + concurrency tool B + F3 state machine. **Sister problem:** [Food Delivery](../fooddelivery/INTERVIEW_WALKTHROUGH.md) has the same three ideas (rider assignment, CAS claim, order status). Read them together.

---

## 1. Plain-language picture

### A taxi stand with a dispatcher
```
Asha opens the app at MG Road → "find me a cab"
Dispatcher: free drivers near MG Road, closest first: [Bharat 0.5 km, Chitra 1.2 km, ...]
            → try Bharat: free? yes → he's yours (he's now ON_TRIP; nobody else can get him)
Trip: MATCHED → driver arrives, rider gets in → IN_PROGRESS → drop-off → COMPLETED, fare ₹100
Bharat is AVAILABLE again, at the drop-off location.
```

### Two riders, one nearby driver
Asha and Ravi both request at the same instant, and Bharat is nearest to both. Without care, both are told "Bharat is coming". The fix is the **taxi-door rule**: `tryReserve()` checks "free?" and sets "on trip" in **one locked step**. One of them gets Bharat; the other's request moves on to Chitra.

### The trip is a parcel on a conveyor belt
```
REQUESTED → MATCHED → IN_PROGRESS → COMPLETED
     ↘         ↘
         CANCELLED          (no cancelling once you're in the car)
```
An enum lists the allowed next steps; anything else is rejected. There's no per-state *behaviour* to model, so an enum is enough (not the State pattern).

### Money
Base fare + per km, times a **surge** multiplier when demand > supply. Surge is stored in **basis points** (10000 = 1.0×, 20000 = 2.0×), so all the maths stays in integers.

---

## 2. From story to design

| # | Decision | Rejected | Why |
|---|---|---|---|
| D1 | `DriverMatchingStrategy.rankCandidates()` returns **best-first** | returning just one driver | The best driver can be claimed by another rider in between; just try the next. |
| D2 | `Driver.tryReserve()`: synchronized AVAILABLE → ON_TRIP | `if (d.getStatus() == AVAILABLE) d.setStatus(ON_TRIP)` in the service | Check-then-act across two calls is the race. |
| D3 | `RideStatus` enum with an allowed-transitions table; `Ride` methods synchronized | status checks scattered in the service | Rules in one place; complete vs cancel at the same instant → exactly one wins. |
| D4 | **One active ride per rider** (`activeRideByRider.putIfAbsent`) | no check | A double-tap on "Book" would tie up two drivers. |
| D5 | `PricingStrategy` + surge in **basis points**, integer maths | `double` multipliers | Exact rupees; surge can't silently go below 1.0×. |
| D6 | Driver freed **at the drop-off location** on completion | freed at the old location | The next match must use where the driver actually is. |
| D7 | `Clock` injected; timestamps per transition | `Instant.now()` | Testable; and timestamps feed the cancellation-fee rules (Q4). |
| D8 | Out of base: payment, ETA, ratings after the trip, pooling, driver accept/reject | modelling all | Follow-ups (§5). |

### Class shape
```
CabBookingService                         ← the service the apps call
  drivers · riders · rides · activeRideByRider      all ConcurrentHashMap
  DriverMatchingStrategy · PricingStrategy · Clock · surge (bps)
  requestRide(rider, pickup, drop) → Ride · startRide · completeRide → fare · cancelRide

Driver { id, name, rating, location, DriverStatus }   tryReserve() · releaseFromTrip(at) · goOnline/Offline
Ride   { id, rider, driver, source, destination, RideStatus, fare, timestamps }  match · start · complete · cancel
RideStatus enum + canTransitionTo     DriverStatus { OFFLINE, AVAILABLE, ON_TRIP }
Rider { id, name }    Location { lat, lng, distanceKm }

«interface» DriverMatchingStrategy  rankCandidates(pickup, available) → best first
   └── NearestDriverStrategy (optional max radius)
«interface» PricingStrategy  calculateFare(src, dst, surgeBps)
   └── DistanceBasedPricing (base + per km)
```

---

## 3. Patterns that earn their place

| Pattern | In the base? | Problem it solves | Without it |
|---|---|---|---|
| **Strategy** (`DriverMatchingStrategy`) | **Yes** | nearest / best-rated / least-busy / EV-only policies | the ranking hard-coded in `requestRide` |
| **Strategy** (`PricingStrategy`) | **Yes** | per-km, flat airport fare, time-based | fare maths tangled with the trip flow |
| **Enum state machine** (`RideStatus`) | **Yes** | legal trip transitions | scattered `if`s |
| **Observer** (trip events) | No | notify the rider "driver arriving", receipts, analytics | Q5 |

**Say:** *"Two strategies, matching and pricing, because both are business policies that change. The trip is an enum state machine. The concurrency is one atomic `tryReserve` per driver."*

**Tempting but wrong:** the State pattern for the ride (statuses don't behave differently), a `User` base class for Rider/Driver (no shared behaviour), Singleton service.

---

## 4. The 45-minute run

### Clarify (min 0–5)
> **You:** A rider requests a ride from A to B; we match a free driver; the trip goes matched → started → completed; the fare is computed at the end. Right?
> **Interviewer:** Yes.
> **You:** Match on nearest for now, but swappable?
> **Interviewer:** Yes. Maybe rating later.
> **You:** Many riders request at once, so no driver may get two riders?
> **Interviewer:** Correct.
> **You:** Cancellation: allowed until the trip starts?
> **Interviewer:** Yes.
> **You:** Surge pricing in scope?
> **Interviewer:** A simple multiplier.

```
In scope:  requestRide (rank → atomic claim) · start · complete (fare) · cancel before start
           ride states with legal transitions · one active ride per rider · surge multiplier
Out:       payment, ETA, driver accept/reject, pooling, ratings, real geo index
```

### Timeline
| Min | Do |
|---|---|
| 5–9 | Class shape. Say the crux (ranked strategy, atomic claim, enum trip). |
| 9–13 | Code step 1: `RideStatus` + `DriverStatus`. |
| 13–19 | Code step 2: **`Driver.tryReserve` / `releaseFromTrip`**, then `Ride` with `transitionTo`. |
| 19–24 | Code step 3: both strategy interfaces + `NearestDriverStrategy` + `DistanceBasedPricing`. |
| 24–34 | Code step 4: **`CabBookingService`**: `requestRide`, `completeRide`, `cancelRide`. |
| 34–38 | Dry run: two riders, one nearby driver. |
| 38–45 | Follow-ups. |

### The code you write, in this order

**Step 1: statuses** ([model/RideStatus.java](model/RideStatus.java))
```java
public enum RideStatus {
    REQUESTED, MATCHED, IN_PROGRESS, COMPLETED, CANCELLED;

    private static final Map<RideStatus, Set<RideStatus>> ALLOWED = new EnumMap<>(RideStatus.class);
    static {
        ALLOWED.put(REQUESTED,   EnumSet.of(MATCHED, CANCELLED));
        ALLOWED.put(MATCHED,     EnumSet.of(IN_PROGRESS, CANCELLED));
        ALLOWED.put(IN_PROGRESS, EnumSet.of(COMPLETED));                // no cancel once you're in the car
        ALLOWED.put(COMPLETED,   EnumSet.noneOf(RideStatus.class));
        ALLOWED.put(CANCELLED,   EnumSet.noneOf(RideStatus.class));
    }

    public boolean canTransitionTo(RideStatus next) { return ALLOWED.get(this).contains(next); }
}

public enum DriverStatus { OFFLINE, AVAILABLE, ON_TRIP }
```
(The `switch` version from Food Delivery works just as well; this table is easier to read with more states.)

**Step 2: driver + ride** ([model/Driver.java](model/Driver.java), [model/Ride.java](model/Ride.java))
```java
public class Driver {
    private final String id;
    private final String name;
    private final double ratingTenths;            // 47 = 4.7 stars
    private Location currentLocation;
    private DriverStatus status = DriverStatus.OFFLINE;

    public synchronized boolean tryReserve() {    // the atomic claim
        if (status != DriverStatus.AVAILABLE) return false;
        status = DriverStatus.ON_TRIP;
        return true;
    }

    public synchronized void releaseFromTrip(Location at) {
        if (status != DriverStatus.ON_TRIP) throw new IllegalStateException("Driver not on trip");
        currentLocation = at;                     // free where the trip ended
        status = DriverStatus.AVAILABLE;
    }

    public synchronized void goOnline(Location at) { currentLocation = at; status = DriverStatus.AVAILABLE; }
    public synchronized void goOffline() {
        if (status == DriverStatus.ON_TRIP) throw new IllegalStateException("Cannot go offline mid-trip");
        status = DriverStatus.OFFLINE;
    }
    // + constructor, synchronized getStatus/getCurrentLocation, updateLocation, getters
}

public class Ride {
    private final String id;
    private final Rider rider;
    private final Location source;
    private final Location destination;
    private final Clock clock;
    private Driver driver;                         // null until MATCHED
    private RideStatus status = RideStatus.REQUESTED;
    private long fare = -1;
    private Instant matchedAt, startedAt, completedAt, cancelledAt;

    public synchronized void match(Driver d)      { transitionTo(RideStatus.MATCHED); driver = d; matchedAt = clock.instant(); }
    public synchronized void start()              { transitionTo(RideStatus.IN_PROGRESS); startedAt = clock.instant(); }
    public synchronized void complete(long fare)  { transitionTo(RideStatus.COMPLETED); this.fare = fare; completedAt = clock.instant(); }
    public synchronized void cancel()             { transitionTo(RideStatus.CANCELLED); cancelledAt = clock.instant(); }

    private void transitionTo(RideStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException("Illegal transition " + status + " → " + next + " on ride " + id);
        }
        status = next;
    }
    // + constructor, getters
}
```

**Step 3: strategies** ([matching/](matching/), [pricing/](pricing/))
```java
public interface DriverMatchingStrategy {
    List<Driver> rankCandidates(Location pickup, List<Driver> availableDrivers);   // best first
}

public class NearestDriverStrategy implements DriverMatchingStrategy {
    private final double maxRadiusKm;
    public NearestDriverStrategy(double maxRadiusKm) { this.maxRadiusKm = maxRadiusKm; }

    @Override
    public List<Driver> rankCandidates(Location pickup, List<Driver> available) {
        return available.stream()
                .filter(d -> d.getCurrentLocation().distanceKm(pickup) <= maxRadiusKm)
                .sorted(Comparator.comparingDouble(d -> d.getCurrentLocation().distanceKm(pickup)))
                .toList();
    }
}

public interface PricingStrategy {
    long calculateFare(Location source, Location destination, int surgeMultiplierBasisPoints);
}

public class DistanceBasedPricing implements PricingStrategy {
    private final long baseFare;   // rupees
    private final long perKm;
    public DistanceBasedPricing(long baseFare, long perKm) { this.baseFare = baseFare; this.perKm = perKm; }

    @Override
    public long calculateFare(Location src, Location dst, int surgeBps) {
        long subtotal = baseFare + Math.round(src.distanceKm(dst) * perKm);
        return subtotal * surgeBps / 10_000;                 // 20000 bps = 2.0×; integer maths
    }
}
```

**Step 4: the service** ([CabBookingService.java](CabBookingService.java))
```java
public class CabBookingService {
    private final ConcurrentMap<String, Driver> drivers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Rider> riders = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Ride> rides = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> activeRideByRider = new ConcurrentHashMap<>();   // riderId → rideId
    private final AtomicLong rideSeq = new AtomicLong();
    private final DriverMatchingStrategy matchingStrategy;
    private final PricingStrategy pricingStrategy;
    private final Clock clock;
    private volatile int currentSurgeBasisPoints = 10_000;              // 1.0×

    public Ride requestRide(Rider rider, Location pickup, Location dropoff) {
        String id = "ride-" + rideSeq.incrementAndGet();
        if (activeRideByRider.putIfAbsent(rider.getId(), id) != null) {     // double-tap on "Book"
            throw new IllegalStateException(rider.getId() + " already has an active ride");
        }
        List<Driver> pool = new ArrayList<>();
        for (Driver d : drivers.values()) if (d.getStatus() == DriverStatus.AVAILABLE) pool.add(d);

        for (Driver candidate : matchingStrategy.rankCandidates(pickup, pool)) {
            if (candidate.tryReserve()) {                                   // lost the race? try the next one
                Ride ride = new Ride(id, rider, pickup, dropoff, clock);
                ride.match(candidate);
                rides.put(id, ride);
                return ride;
            }
        }
        activeRideByRider.remove(rider.getId(), id);                        // undo: no ride was created
        throw new IllegalStateException("No drivers available near pickup");
    }

    public void startRide(String rideId) { required(rideId).start(); }

    public long completeRide(String rideId) {
        Ride r = required(rideId);
        long fare = pricingStrategy.calculateFare(r.getSource(), r.getDestination(), currentSurgeBasisPoints);
        r.complete(fare);                                                   // throws if not IN_PROGRESS
        r.getDriver().releaseFromTrip(r.getDestination());
        activeRideByRider.remove(r.getRider().getId(), rideId);
        return fare;
    }

    public void cancelRide(String rideId) {
        Ride r = required(rideId);
        Driver d = r.getDriver();
        r.cancel();                                                         // throws once IN_PROGRESS
        if (d != null) d.releaseFromTrip(d.getCurrentLocation());
        activeRideByRider.remove(r.getRider().getId(), rideId);
    }

    public void setSurgeBasisPoints(int bps) {
        if (bps < 10_000) throw new IllegalArgumentException("Surge cannot reduce price below 1.0×");
        currentSurgeBasisPoints = bps;
    }
    // + registerDriver, registerRider, getters, required(id) → IllegalArgumentException
}
```
**Shape to remember:** request = one-ride check → available pool → rank → `tryReserve` each until one wins → ride MATCHED. Complete = fare → `complete` (state check) → free the driver at the drop-off.

### Dry run: two riders, one close driver
```
Bharat 0.5 km, Chitra 1.2 km, both AVAILABLE. Asha and Ravi request at the same instant.
Asha: rank → [Bharat, Chitra] → Bharat.tryReserve() → true  → ride-1 MATCHED with Bharat
Ravi: rank → [Bharat, Chitra] → Bharat.tryReserve() → false → Chitra.tryReserve() → true → ride-2
Asha double-taps: activeRideByRider has "Asha" → rejected, no third driver tied up.
```
The driver proves it: 50 concurrent requests with 10 drivers gives exactly 10 matched and no driver double-booked.

---

## 5. Follow-ups: answer out loud first, then open

<details>
<summary><b>Q1. Match by rating instead, but only drivers within 3 km.</b></summary>

A new strategy; the service doesn't change:
```java
public class BestRatedNearbyStrategy implements DriverMatchingStrategy {
    private final double maxKm;
    public BestRatedNearbyStrategy(double maxKm) { this.maxKm = maxKm; }

    @Override
    public List<Driver> rankCandidates(Location pickup, List<Driver> available) {
        List<Driver> nearby = new ArrayList<>();
        for (Driver d : available) if (d.getCurrentLocation().distanceKm(pickup) <= maxKm) nearby.add(d);
        nearby.sort(Comparator.comparingDouble(Driver::getRatingTenths).reversed()
                .thenComparingDouble(d -> d.getCurrentLocation().distanceKm(pickup)));   // ties → nearer
        return nearby;
    }
}
```
"Least busy" (fewest trips today, for fairness between drivers) and "EV only" are the same shape.
</details>

<details>
<summary><b>Q2. Compute surge automatically from demand and supply.</b></summary>

Per area: open requests ÷ free drivers, in basis points, with a floor of 1.0× and a cap of 3.0×:
```java
static int surgeBps(int openRequests, int freeDrivers) {
    if (freeDrivers == 0) return 30_000;
    int bps = openRequests * 10_000 / freeDrivers;        // 10 requests / 5 drivers → 20000 (2.0×)
    return Math.max(10_000, Math.min(30_000, bps));
}
```
Recompute every minute per geo cell, and **lock the surge at request time** into the ride (store it on `Ride`), so the price shown is the price charged even if surge changes mid-trip.
</details>

<details>
<summary><b>Q3. The driver can accept or reject; no answer in 15 seconds = reject.</b></summary>

Add `OFFERED` between REQUESTED and MATCHED. Offer the top-ranked driver (`tryReserve` holds them); on reject or timeout (`ScheduledExecutorService.schedule(..., 15, SECONDS)`), release them, remember they declined, and offer the next in the ranked list. The same pattern as Food Delivery's Q6.
</details>

<details>
<summary><b>Q4. Cancellation fee if the rider cancels late.</b></summary>

The per-transition timestamps make this easy:
```java
// free within 2 minutes of matching; after that ₹50 (the driver already started driving to you)
static long fee(Instant matchedAt, Instant cancelledAt) {
    return Duration.between(matchedAt, cancelledAt).toSeconds() <= 120 ? 0 : 50;
}
```
Make it a `CancellationPolicy` Strategy if it differs by city or ride type. Driver-initiated cancels: no rider fee, and re-match automatically.
</details>

<details>
<summary><b>Q5. Notify the rider: "driver assigned", "arriving", "trip complete".</b></summary>

**Observer**: publish an event on each ride transition (`RIDE_MATCHED`, `RIDE_STARTED`, `RIDE_COMPLETED`); the notification service, receipts and analytics subscribe (see the deck's Notification problem). Publish after the transition succeeds, outside any lock.
</details>

<details>
<summary><b>Q6. A city with 50,000 drivers: scanning all of them per request is too slow.</b></summary>

A **geo index**: geohash cells (or Redis `GEOADD`/`GEOSEARCH`, or a quadtree) holding the *available* drivers. Search the pickup's cell + neighbours, and widen if empty. Drivers send GPS every few seconds → move them between cells. Only the strategy's input changes (nearby drivers instead of all), so the interface stays the same.
</details>

<details>
<summary><b>Q7. Many servers: `synchronized tryReserve` only works in one JVM.</b></summary>

A conditional update in the database (or a Redis `SET driver:<id>:trip <rideId> NX`):
```sql
UPDATE driver SET status = 'ON_TRIP', ride_id = 'ride-42'
WHERE id = 'D7' AND status = 'AVAILABLE';      -- 1 row = claimed; 0 rows = someone else got them
```
The trip transitions are the same: `UPDATE ride SET status = 'COMPLETED' WHERE id = ? AND status = 'IN_PROGRESS'`.
</details>

<details>
<summary><b>Q8. Ride pooling (share with strangers going the same way).</b></summary>

A driver can hold up to N riders. Matching becomes "is this new pickup/drop on the way of the driver's current route without adding more than X minutes?". The driver's status becomes a **seat count** instead of AVAILABLE/ON_TRIP, and `tryReserve` becomes "seats > 0 → seats−1" under the same lock. Fares are split by the distance each rider travels.
</details>

<details>
<summary><b>Q9. How would you know it's working in production?</b></summary>

- **Metrics:** time to match (p50/p95), match success rate, cancellation rate (rider vs driver, before/after the fee window), surge level per area, driver utilisation.
- **Alarms:** match rate drops in an area (driver shortage → raise surge, notify drivers), any driver on two active rides (should be 0), and a payment-failure spike.
- **Logs:** rideId on every transition, with the strategy used and the ranked candidates tried.
</details>

<details>
<summary><b>Q10. How do you test it?</b></summary>

Happy path (fare + driver freed at the drop-off), nearest among many, cancel releases the driver, no drivers, surge doubles the fare, an illegal transition (MATCHED → COMPLETED), a rider double-tap, and 50 concurrent requests with 10 drivers → exactly 10 matched with no double booking. All are in the driver.
</details>

---

## 6. Traps
1. Strategy returns one driver: no fallback when they're claimed concurrently.
2. Checking `isAvailable()` in the service, then setting ON_TRIP: a race.
3. Status checks spread across the service instead of one transition table.
4. Freeing the driver at their old location.
5. `double` surge maths; surge allowed below 1.0×.
6. No guard against the same rider booking twice.
7. Jumping into geo indexes and Kafka before `requestRide` works.

## 7. Recall check
1. Why does the strategy return a ranked list?
2. What exactly does `tryReserve()` do, and why must it be synchronized?
3. Draw the ride state diagram. Where is cancel allowed?
4. What stops a double-tap from booking two cabs?
5. Surge in basis points: 15000 bps on a ₹120 subtotal = ?
6. How is this the same as Food Delivery, and what differs?

<details><summary>Answer to 5</summary>

120 × 15000 / 10000 = **₹180**.
</details>

**Rebuild in 12 minutes:** `RideStatus` table · `Driver.tryReserve/releaseFromTrip` · `Ride.transitionTo` · `NearestDriverStrategy.rankCandidates` · `requestRide` (one-ride guard → pool → rank → tryReserve loop) · `completeRide` · `cancelRide`.

---

**Files:** `CabBookingService` · `model/` (`Driver`, `Rider`, `Ride`, `RideStatus`, `DriverStatus`, `Location`) · `matching/` (`DriverMatchingStrategy`, `NearestDriverStrategy`) · `pricing/` (`PricingStrategy`, `DistanceBasedPricing`) · `CabBookingDriver` (happy path, nearest, cancel, no drivers, surge, 50-thread test, illegal transition, double-tap)
```bash
mvn -q compile exec:java -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.matching.cabbooking.CabBookingDriver
```
