# Amazon Locker

> **Amazon:** ★ reported in 2026 and 2025. Your guide's key points: *locker sizes, assign the smallest fitting slot, OTP pickup, expiry; follow-up: find the nearest locker.*
>
> **The crux (what's really being tested):**
> 1. **Allocation:** the smallest compartment that fits, claimed **atomically** (two drivers, one door).
> 2. **The pickup code:** unique, unguessable, works **exactly once**, and expires.
> 3. **Cleanup:** an expired parcel must free its compartment *and* its code.
>
> **Family:** F1 Allocation. It's the same skeleton as [Parking Lot](../parkinglot/INTERVIEW_WALKTHROUGH.md): read that first and notice how little changes.

---

## 1. Plain-language picture

### The wall of doors
```
┌────┬────┬────────┬──────────────┐
│ S1 │ S2 │   M1   │      L1      │     S = small, M = medium, L = large
└────┴────┴────────┴──────────────┘
Delivery driver: "I have a SMALL parcel" → S1 opens → parcel in → door shuts
Customer gets an SMS: "Your code is 431130, valid 3 days"
Customer types 431130 → S1 opens → parcel out → S1 is free again
```

### Smallest that fits
A small parcel goes in a small door. If all small doors are full, it may use a **medium** one, because a small parcel fits a bigger door. But never put a small parcel in a large door while a small one is free: that wastes the large door for the next large parcel. Sort the free doors that fit by size, and take the first.

### Two drivers at one locker
Both see M1 free at the same moment. Without care, both are told "use M1". The fix is the same as the parking lot: **claim the door in one atomic step** (`tryOccupy()` checks and sets under the door's lock). One driver gets M1, and the other is given the next door.

### The code is a key
- **Unique:** two parcels must never share a code (otherwise you open someone else's door).
- **Unguessable:** generated with `SecureRandom`, not `Random`.
- **One use:** the code is removed on pickup.
- **Expires:** after 3 days, staff reclaim the door and the parcel goes back to the sender.

---

## 2. From story to design

| # | Decision | Rejected | Why |
|---|---|---|---|
| D1 | `Size` with a rank + `canHold(parcel)`; sort candidates by size | exact size match only | A small parcel shouldn't be refused while a medium door is free. |
| D2 | `Compartment.tryOccupy()`: synchronized check-and-claim | `if (c.isAvailable()) c.markOccupied()` from outside | Check-then-act across two calls is the race. |
| D3 | `Map<code, AccessToken>`; the token points at its compartment | searching compartments by code | O(1) pickup. |
| D4 | `putIfAbsent(code, token)` to issue a code | `containsKey` then `put` | Two parcels generating the same code at once: only one can insert it. |
| D5 | `remove(code, token)` on pickup | `get` then `remove` | A code opens the door **once**, even with two simultaneous attempts. |
| D6 | `SecureRandom` for codes | `java.util.Random` | `Random` is predictable from a few outputs; a 6-digit code is effectively a password. |
| D7 | `Clock` injected; expiry `now >= expiresAt` | `Instant.now()` | Tests jump 4 days ahead instantly. |
| D8 | Reclaim frees the door **and** removes the token | freeing the door only | Otherwise the token map grows forever and an old code could match a new parcel's door. |
| D9 | Distinct errors: bad input (`IllegalArgument`), unknown code (`NoSuchElement`), expired (`IllegalState`) | one generic error | The UI shows the right message ("expired, contact support" vs "wrong code"). |

### Class shape
```
AmazonLocker                          ← one locker bank at one location
  List<Compartment> · Map<code, AccessToken> (concurrent) · Clock · SecureRandom
  depositPackage(parcelSize) → code · pickup(code) · openExpiredCompartments() · freeCompartmentsFor(size)

Compartment { id, Size, status }      tryOccupy() (atomic) · markFree · markOutOfService · open()
AccessToken { code, expiresAt, compartment }  isExpired(clock)
Size enum SMALL/MEDIUM/LARGE + canHold      CompartmentStatus AVAILABLE/OCCUPIED/OUT_OF_SERVICE
```

---

## 3. Patterns that earn their place

| Pattern | In the base? | When |
|---|---|---|
| **Strategy** (allocation) | Optional | smallest-fit is the rule today; "near the floor for heavy parcels" or "near the door" are other policies, the same as Parking Lot's `SpotAllocationStrategy`. Mention it; code it if asked. |
| **Observer** (notify customer) | No | Q4: send the code by SMS/email when deposited, and a reminder before expiry. |

**Say:** *"It's the parking-lot allocation pattern: smallest fitting slot, claimed atomically. The new part is the access code: unique, unguessable, one-time, expiring."*

**Tempting but wrong:** Singleton locker (there are thousands), State pattern for a compartment (3 statuses, no per-state behaviour), Factory for compartments.

---

## 4. The 45-minute run

### Clarify (min 0–5)
> **You:** A locker has compartments of 3 sizes. A delivery driver deposits a parcel of some size; the customer gets a code and collects it?
> **Interviewer:** Yes.
> **You:** If no exact-size door is free, can a smaller parcel use a bigger door?
> **Interviewer:** Yes, but use the smallest that fits.
> **You:** Code format and expiry?
> **Interviewer:** 6 digits, 3 days. After that, staff take it out.
> **You:** Several drivers at the same locker at once?
> **Interviewer:** Possible.

```
In scope:  deposit(size) → code (smallest fitting door, atomic claim)
           pickup(code): once, before expiry · reclaim expired · free-door count
Out:       nearest locker across the city, notifications, returns, brute-force lockout
```

### Timeline
| Min | Do |
|---|---|
| 5–9 | Class shape. Say: *"Same as parking: smallest fit, atomic claim. Plus a one-time expiring code."* |
| 9–14 | Code step 1: `Size` (rank, `canHold`), `Compartment` (`tryOccupy`), `AccessToken`. |
| 14–30 | Code step 2: **`AmazonLocker`**: `claimSmallestFitting`, `depositPackage`, `pickup`, `openExpiredCompartments`. |
| 30–35 | Dry run: smalls full → medium; two drivers racing; an expired code. |
| 35–45 | Follow-ups: nearest locker, guessing protection. |

### The code you write, in this order

**Step 1: model** ([model/](model/))
```java
public enum Size {
    SMALL(1), MEDIUM(2), LARGE(3);
    private final int rank;
    Size(int rank) { this.rank = rank; }
    public int getRank() { return rank; }
    public boolean canHold(Size parcel) { return rank >= parcel.rank; }
}

public class Compartment {
    private final String id;
    private final Size size;
    private CompartmentStatus status = CompartmentStatus.AVAILABLE;

    public Compartment(String id, Size size) { this.id = id; this.size = size; }

    public synchronized boolean tryOccupy() {               // check + claim in one step
        if (status != CompartmentStatus.AVAILABLE) return false;
        status = CompartmentStatus.OCCUPIED;
        return true;
    }
    public synchronized void markFree()         { status = CompartmentStatus.AVAILABLE; }
    public synchronized boolean isAvailable()   { return status == CompartmentStatus.AVAILABLE; }
    public void open() { System.out.println("[hardware] " + id + " unlocked"); }
    // + markOutOfService, getters
}

public class AccessToken {
    private final String code;
    private final Instant expiresAt;
    private final Compartment compartment;
    // constructor + getters
    public boolean isExpired(Clock clock) { return !clock.instant().isBefore(expiresAt); }   // now >= expiresAt
}
// enum CompartmentStatus { AVAILABLE, OCCUPIED, OUT_OF_SERVICE }
```

**Step 2: the locker** ([AmazonLocker.java](AmazonLocker.java))
```java
public class AmazonLocker {
    private static final Duration TOKEN_TTL = Duration.ofDays(3);
    private static final int CODE_GEN_MAX_ATTEMPTS = 10;

    private final List<Compartment> compartments;
    private final Map<String, AccessToken> tokensByCode = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Random random;                              // SecureRandom in production

    public AmazonLocker(List<Compartment> compartments, Clock clock, Random random) {
        this.compartments = new ArrayList<>(compartments);
        this.clock = clock;
        this.random = random;
    }

    public String depositPackage(Size parcelSize) {
        Compartment compartment = claimSmallestFitting(parcelSize);
        if (compartment == null) throw new NoSuchElementException("No compartment free for a " + parcelSize + " parcel");

        Instant expiresAt = clock.instant().plus(TOKEN_TTL);
        for (int attempt = 0; attempt < CODE_GEN_MAX_ATTEMPTS; attempt++) {
            String code = String.format("%06d", random.nextInt(1_000_000));
            if (tokensByCode.putIfAbsent(code, new AccessToken(code, expiresAt, compartment)) == null) {
                compartment.open();
                return code;
            }
        }
        compartment.markFree();                               // undo the claim
        throw new IllegalStateException("Could not generate a unique pickup code");
    }

    public void pickup(String code) {
        if (code == null || code.isEmpty()) throw new IllegalArgumentException("Invalid pickup code");
        AccessToken token = tokensByCode.get(code);
        if (token == null) throw new NoSuchElementException("Invalid pickup code");
        if (token.isExpired(clock)) throw new IllegalStateException("Pickup code has expired");
        if (!tokensByCode.remove(code, token)) throw new NoSuchElementException("Invalid pickup code");   // used a moment ago
        token.getCompartment().open();
        token.getCompartment().markFree();
    }

    public int openExpiredCompartments() {                    // staff, daily
        int reclaimed = 0;
        for (AccessToken token : tokensByCode.values()) {
            if (token.isExpired(clock) && tokensByCode.remove(token.getCode(), token)) {
                token.getCompartment().open();
                token.getCompartment().markFree();
                reclaimed++;
            }
        }
        return reclaimed;
    }

    private Compartment claimSmallestFitting(Size parcelSize) {
        List<Compartment> candidates = new ArrayList<>();
        for (Compartment c : compartments) {
            if (c.getSize().canHold(parcelSize) && c.isAvailable()) candidates.add(c);
        }
        candidates.sort(Comparator.comparingInt(c -> c.getSize().getRank()));
        for (Compartment c : candidates) {
            if (c.tryOccupy()) return c;                      // lost the race? try the next one
        }
        return null;
    }
    // + freeCompartmentsFor(size)
}
```
**Shape to remember:** deposit = filter fitting free → sort by size → `tryOccupy` the first you can → `putIfAbsent` a fresh code → open. Pickup = validate → expired? → `remove(code, token)` → open → free.

### Dry run
```
Doors S1 S2 M1 L1.
SMALL → candidates [S1,S2,M1,L1] sorted → S1.tryOccupy ✓ → code 431130
SMALL → S2 ✓        SMALL → smalls taken → [M1, L1] → M1 ✓        MEDIUM → [L1] → L1 ✓
LARGE → no candidates → "No compartment free for a LARGE parcel"

Two drivers, one free door M1, same instant:
  both: candidates [M1] → A: M1.tryOccupy() true ✓   B: M1.tryOccupy() false → no more → "no compartment"
pickup(431130) → remove ✓ → S1 opens, free.  pickup(431130) again → not found → "Invalid pickup code"
```
The driver proves it: 30 drivers racing for 10 doors gives exactly 10 deposits and 10 distinct codes.

---

## 5. Follow-ups: answer out loud first, then open

<details>
<summary><b>Q1. "Find the nearest locker with space" (from your Amazon guide).</b></summary>

A `LockerNetwork` over many locker locations. Sort by distance, skip full ones, and if a deposit loses a race (it filled up between the check and the deposit), try the next:
```java
public String depositNearest(double x, double y, Size parcel) {
    List<LockerLocation> byDistance = new ArrayList<>(locations);
    byDistance.sort(Comparator.comparingDouble(l -> Math.hypot(l.x - x, l.y - y)));
    for (LockerLocation l : byDistance) {
        if (l.locker.freeCompartmentsFor(parcel) == 0) continue;
        try {
            return l.id + ":" + l.locker.depositPackage(parcel);
        } catch (NoSuchElementException e) {
            // filled up between the check and the deposit: try the next locker
        }
    }
    throw new NoSuchElementException("No locker nearby has room for a " + parcel + " parcel");
}
```
At city scale: a geo index (geohash cells / Redis `GEOSEARCH`) to find nearby lockers instead of sorting all of them, plus a cached free count per locker per size.
</details>

<details>
<summary><b>Q2. Someone types random codes until a door opens.</b></summary>

A 6-digit code has a million possibilities. Limit wrong attempts per locker (and per phone/IP):
```java
private static final int MAX_FAILURES = 5;
private final Map<String, AtomicInteger> failuresByLocker = new ConcurrentHashMap<>();

public void pickup(String lockerId, AmazonLocker locker, String code) {
    AtomicInteger failures = failuresByLocker.computeIfAbsent(lockerId, k -> new AtomicInteger());
    if (failures.get() >= MAX_FAILURES) throw new IllegalStateException("Too many wrong codes. Try again in 15 minutes.");
    try {
        locker.pickup(code);
        failures.set(0);
    } catch (NoSuchElementException e) {
        failures.incrementAndGet();
        throw e;
    }
}
```
Reset the counter after the lockout window (store a timestamp with it). Combined with `SecureRandom` and the 3-day expiry, guessing becomes impractical.
</details>

<details>
<summary><b>Q3. A door's hinge breaks.</b></summary>

`compartment.markOutOfService()`. `tryOccupy()` only claims AVAILABLE doors, so it's skipped automatically. If it held a parcel, its token still points at it: staff open it manually, then the code is cancelled and the customer gets a new one or a redelivery. A technician later calls `markFree()`.
</details>

<details>
<summary><b>Q4. Text the customer their code, and remind them before it expires.</b></summary>

**Observer**: publish `PARCEL_DEPOSITED(customerId, lockerId, code, expiresAt)` after a successful deposit; the notification service subscribes (see the deck's Notification problem). For the reminder, a daily job finds tokens expiring within 24 hours and publishes `PICKUP_REMINDER`. Never put the code in logs.
</details>

<details>
<summary><b>Q5. Returns: the customer drops a parcel in to send back.</b></summary>

The same allocation, with the direction reversed: the customer gets a **drop-off** code, the door opens, the parcel goes in, and a **courier** pickup code is issued for the carrier. Give the token a `type` (PICKUP / DROP_OFF / COURIER_COLLECT); the flow is otherwise identical. That's the reuse payoff of the token design.
</details>

<details>
<summary><b>Q6. Thousands of lockers, one backend.</b></summary>

The atomic claim moves into the database:
```sql
-- 1 row updated = this driver got the door
UPDATE compartment SET status = 'OCCUPIED'
WHERE id = ? AND locker_id = ? AND status = 'AVAILABLE';

-- unique pickup codes per locker
CREATE TABLE access_token (code CHAR(6), locker_id ..., compartment_id ..., expires_at ...,
                           PRIMARY KEY (locker_id, code));
```
The physical locker keeps a small offline cache of valid codes, so pickups still work if the network drops. It syncs when back online.
</details>

<details>
<summary><b>Q7. How would you know it's working in production?</b></summary>

- **Metrics:** occupancy % per locker and size, "no space" rejections (a full locker → redirect deliveries), pickup rate within 3 days, expiries per day, wrong-code rate.
- **Alarms:** a locker at 100% for days, a spike in wrong codes at one locker (someone guessing), a door that doesn't report "closed" after opening (hardware).
- **Logs:** deposit/pickup events with lockerId + compartmentId + parcelId. **Never log codes.**
</details>

<details>
<summary><b>Q8. How do you test it?</b></summary>

Smallest fit (smalls full → medium), the full-locker rejection, code once-only, a wrong code, expiry with a movable `Clock` (exactly at `expiresAt` is expired), reclaim frees the door and the token, and 30 concurrent deposits into 10 doors → 10 deposits with 10 distinct codes. All are in the driver.
</details>

---

## 6. Traps
1. Exact-size match only.
2. `isAvailable()` then `markOccupied()` from the service (a race).
3. `containsKey` + `put` for codes (a race); `get` + `remove` on pickup (a race).
4. `java.util.Random` for access codes.
5. Reclaiming the door but leaving the token in the map.
6. Logging the codes.

## 7. Recall check
1. What line stops two drivers getting the same door?
2. Why `putIfAbsent` for codes and `remove(code, token)` for pickup?
3. Why `SecureRandom`?
4. What must `openExpiredCompartments` clean up, and why both?
5. How is this the same as Parking Lot, and what's genuinely new?

**Rebuild in 10 minutes:** `Size.canHold` · `Compartment.tryOccupy` · `AccessToken.isExpired` · `claimSmallestFitting` · `depositPackage` (claim → `putIfAbsent` loop) · `pickup` (validate → `remove(code, token)` → free).

---

**Files:** `AmazonLocker` · `model/` (`Compartment`, `AccessToken`, `Size`, `CompartmentStatus`) · `LockerDriver` (happy path, one-time code, smallest fit, full, expiry + reclaim, 30-thread test)
```bash
mvn -q compile exec:java -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.allocation.amazonlocker.LockerDriver
```
