# Movie Ticket Booking (BookMyShow)

> **Amazon:** ★ booking-with-concurrency is the **most reported LLD cluster** (5 reports: BookMyShow, Meeting Room, Restaurant). The follow-up is almost always *"hold seats while the user pays"*.
>
> **The crux (what's really being tested):**
> 1. **No double-booking:** "is it free?" and "book it" must happen as **one atomic step**.
> 2. **Lock granularity:** lock **one show**, not the whole system and not (yet) each seat.
>
> **Family:** F2 Booking under contention (see [foundations](../../00_AMAZON_LLD_FOUNDATIONS.md)). The same skeleton solves Meeting Room and Restaurant booking (Q10).
> **Pattern:** none needed in the base. The crux is concurrency. Strategy shows up with payment and pricing (Q3, Q8).

---

## 1. Plain-language picture

### The paper seat chart
Picture an old cinema counter. For **each show** there's a paper seat chart. A cashier sells a seat by crossing it out.

```
Show: Inception, 7 PM, Screen 3          Show: Dune, 8 PM, Screen 5
[1][2][3][4][X][X][7]...                 [1][2][3][4][5][6][7]...
       (seats 5, 6 sold)                        (separate chart)
```

**The danger:** two cashiers share the 7 PM chart. Both look at seat 7, both see it free, both sell it. Two people show up for one seat.

**The fix:** **one pen per chart.** To sell a seat on the 7 PM chart you must hold that chart's pen. *Check and cross out happen while holding the pen*, so nobody can sneak in between. That's `synchronized` on the `Showtime`.

**Why one pen per chart, not one for the whole cinema:** the cashier selling Dune 8 PM shouldn't wait for someone buying Inception 7 PM. Different shows, different charts, different pens.

### The coat on the seat (holds, the main follow-up)
Real users pick seats, then spend a minute paying. While they pay, they "put their coat on the seat": the seat is **held**, so nobody else can take it. If they don't come back within 5 minutes, the coat is removed and the seat is free again.

### The building
```
City (Bengaluru) → Theater (PVR Forum) → Screen (Screen 3) → Showtime (Inception, 7 PM) → seats + bookings
Movie (Inception) is separate: one movie plays in many showtimes.
```
**Seats belong to the Showtime, not the Screen.** Screen 3 at 7 PM and Screen 3 at 10 PM are the same chairs but different tickets.

---

## 2. From story to design

| # | Decision | Rejected | Why |
|---|---|---|---|
| D1 | Core API: `book(showtimeId, seatIds) → Booking`, **all-or-nothing** | booking seats one by one | A family booking seats 5–8 doesn't want 3 of 4. If any seat is taken, nothing changes. |
| D2 | Seats live on the **Showtime** | seats on `Screen` | Same physical seat, different show = a different ticket. |
| D3 | A seat is just a **String id** (`"1".."100"`) | a `Seat` class with a `status` field | A `Seat` has no behaviour here. Worse, `Seat.status` + the bookings list would be **two sources of truth** that can disagree. |
| D4 | The **bookings list is the only source of truth**: a seat is taken if some booking contains it | a separate `bookedSeats` set kept in sync | One fact, one place. Scanning ≤100 seats is fast enough. |
| D5 | Lock **per Showtime** (`synchronized` methods on `Showtime`) | a global lock on `BookingSystem` / a lock per seat | Global: every show in the country waits on every other. Per seat: more code + deadlock risk, only worth it at extreme contention (Q5). |
| D6 | Check **and** add inside **one** synchronized `Showtime.book()` | `BookingSystem` calls `isAvailable()` then `book()` | Two separate calls leave a gap between them, which is the race. |
| D7 | Read methods (`isAvailable`, `getAvailableSeats`) are `synchronized` too | only the writer locked | Reading an `ArrayList` while another thread adds to it can throw `ConcurrentModificationException`. |
| D8 | `BookingSystem` keeps an index `showtimesById` | walking City → Theater → Screen on every booking | `book()` is the hot path: O(1) lookup. Search walks the hierarchy, but only inside one city. |
| D9 | Exceptions: `IllegalArgumentException` (bad input), `IllegalStateException` (seat taken), `NoSuchElementException` (unknown id) | returning `null` / `false` | The caller gets the reason, and the HTTP layer maps each to 400 / 409 / 404. |
| D10 | Out of base: holds, payment, cancellation, pricing | building them all | All are follow-ups (§5). Base = what you finish in 35 minutes. |

### Class shape
```
BookingSystem                         ← the service callers use
  Map<String, City> citiesById
  Map<String, Showtime> showtimesById ← index for O(1) booking
  searchMovies(cityId, title) · getAvailableSeats(showtimeId) · book(showtimeId, seatIds)

City → List<Theater> → List<Screen> → List<Showtime>        (thin containers)

Showtime   ← THE class: the crux lives here
  id, screen, movie, datetime
  List<Booking> bookings              ← single source of truth for taken seats
  synchronized book(Booking) · isAvailable(seatId) · getAvailableSeats()

Booking { bookingId, List<String> seatIds }        Movie { id, title }
```

---

## 3. Patterns that earn their place

| Pattern | In the base? | Problem it solves | When it appears |
|---|---|---|---|
| **Strategy** (`PaymentProcessor`) | No | UPI / card / wallet are different ways to do the same `charge()` | Q3: "add payment" |
| **Strategy** (`PricingStrategy`) | No | flat / seat tier / weekend pricing | Q8: "different prices" |
| **Observer** (`BookingListener`) | No | SMS + email + loyalty points react to "booked" without `BookingSystem` knowing them | Q9: "notify the user" |

**Say:** *"The hard part of this problem is concurrency, not patterns. I'll keep the base to plain classes and add Strategy when payment comes in."* Saying this shows judgement; forcing patterns in here looks like over-engineering.

`BookingSystem` is the Facade (the one service class). No need to name it.

**Tempting but wrong here:**
- **State pattern for a seat:** a seat is free / held / booked, but it has no *behaviour* per state. That status is derived from the bookings and holds.
- **Singleton `BookingSystem`:** create it once and inject it.
- **Builder for `Showtime`:** 4 constructor parameters doesn't need one.

---

## 4. The 35-minute Amazon run

### Clarify (min 0–4)
> **You:** Users pick a city, search a movie, choose a showtime, pick specific seats and book. Right?
> **Interviewer:** Yes.
> **You:** Can a booking have several seats, and is it all-or-nothing?
> **Interviewer:** Several seats, all-or-nothing.
> **You:** Many users book at the same time, so no seat can be sold twice?
> **Interviewer:** Correct. That's the important part.
> **You:** Should I include holding seats during payment, and payment itself, or start with direct booking?
> **Interviewer:** Start with booking. We'll discuss holds.
> **You:** Same seat layout for every screen, say 100 numbered seats?
> **Interviewer:** Fine.

```
In scope:  searchMovies(cityId, title) · getAvailableSeats(showtimeId) · book(showtimeId, seatIds)
           multi-seat, all-or-nothing · thread-safe, no double-booking
Out:       holds, payment, cancellation, pricing, seat tiers (follow-ups)
```

### Timeline
| Min | Do |
|---|---|
| 4–8 | Write the class shape. Say the crux: *"Check-and-book must be atomic. I'll lock per showtime."* |
| 8–18 | Code step 1: **`Showtime`**, the crux. `book()` first, then `isAvailable()`, then `getAvailableSeats()`. |
| 18–24 | Code step 2: `Booking`, `Movie` (tiny), then **`BookingSystem`**: the constructor index + `book()`. |
| 24–27 | Code step 3: `searchMovies()` + `getAvailableSeats()`. The thin containers `City`/`Theater`/`Screen`: write one, then say *"the others are the same shape"*. |
| 27–31 | Dry-run the 2-thread race (below). Point at `synchronized`. |
| 31–35 | Follow-ups: holds first (Q2). |

### The code you write, in this order

**Step 1: `Showtime`, where the crux lives** ([model/Showtime.java](model/Showtime.java))
```java
public class Showtime {

    private static final int TOTAL_SEATS = 100;              // seats are "1".."100"

    private final String id;
    private final Screen screen;
    private final Movie movie;
    private final LocalDateTime datetime;
    private final List<Booking> bookings = new ArrayList<>();   // the ONLY record of taken seats

    public Showtime(String id, Screen screen, Movie movie, LocalDateTime datetime) {
        this.id = id;
        this.screen = screen;
        this.movie = movie;
        this.datetime = datetime;
    }

    // check + add under ONE lock: nobody can book between our check and our add
    public synchronized void book(Booking booking) {
        List<String> seatIds = booking.getSeatIds();
        if (seatIds == null || seatIds.isEmpty()) {
            throw new IllegalArgumentException("Must select at least one seat");
        }
        for (String seatId : seatIds) {
            if (!isValidSeatId(seatId)) throw new IllegalArgumentException("Invalid seat: " + seatId);
        }
        if (new HashSet<>(seatIds).size() != seatIds.size()) {       // ["5", "5"]
            throw new IllegalArgumentException("Duplicate seat in request: " + seatIds);
        }
        for (String seatId : seatIds) {                             // all-or-nothing:
            if (!isAvailable(seatId)) {                             // one taken seat → throw, nothing changed
                throw new IllegalStateException("Seat unavailable: " + seatId);
            }
        }
        bookings.add(booking);
    }

    // readers are synchronized too: reading the list while book() adds to it is unsafe
    public synchronized boolean isAvailable(String seatId) {
        for (Booking b : bookings) {
            if (b.getSeatIds().contains(seatId)) return false;
        }
        return true;
    }

    public synchronized List<String> getAvailableSeats() {
        Set<String> booked = new HashSet<>();
        for (Booking b : bookings) booked.addAll(b.getSeatIds());
        List<String> available = new ArrayList<>();
        for (int num = 1; num <= TOTAL_SEATS; num++) {
            String seatId = String.valueOf(num);
            if (!booked.contains(seatId)) available.add(seatId);
        }
        return available;
    }

    private static boolean isValidSeatId(String seatId) {
        try {
            int num = Integer.parseInt(seatId);
            return num >= 1 && num <= TOTAL_SEATS;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // getters: getId(), getScreen(), getMovie(), getDatetime()
}
```
**Shape to remember: lock → validate → check every seat → add. A throw anywhere means nothing changed.**
`isAvailable()` is called from inside `book()` while already holding the lock. That's fine: Java locks are *reentrant*, so the same thread can enter again.

**Step 2: `Booking`, `Movie`** ([model/Booking.java](model/Booking.java), [model/Movie.java](model/Movie.java))
```java
public class Booking {
    private final String bookingId;
    private final List<String> seatIds;

    public Booking(String bookingId, List<String> seatIds) {
        this.bookingId = bookingId;
        this.seatIds = new ArrayList<>(seatIds);             // copy in: caller can't change our list later
    }

    public String getBookingId()     { return bookingId; }
    public List<String> getSeatIds() { return new ArrayList<>(seatIds); }   // copy out
}

public class Movie {
    private final String id;                                 // id, because two movies can share a title
    private final String title;
    public Movie(String id, String title) { this.id = id; this.title = title; }
    public String getTitle() { return title; }
}
```

**Step 3: `BookingSystem`, the service** ([BookingSystem.java](BookingSystem.java))
```java
public class BookingSystem {

    private final Map<String, City> citiesById = new HashMap<>();
    private final Map<String, Showtime> showtimesById = new HashMap<>();   // index: O(1) for book()

    public BookingSystem(List<City> cities) {
        for (City city : cities) {
            citiesById.put(city.getId(), city);
            for (Showtime showtime : city.getAllShowtimes()) {
                showtimesById.put(showtime.getId(), showtime);
            }
        }
    }

    public Booking book(String showtimeId, List<String> seatIds) {
        if (showtimeId == null || seatIds == null || seatIds.isEmpty()) {
            throw new IllegalArgumentException("Invalid booking request");
        }
        Showtime showtime = showtimesById.get(showtimeId);
        if (showtime == null) throw new NoSuchElementException("Showtime not found: " + showtimeId);

        Booking booking = new Booking(UUID.randomUUID().toString(), seatIds);
        showtime.book(booking);                              // atomic; throws if any seat is taken
        return booking;
    }

    public List<String> getAvailableSeats(String showtimeId) {
        Showtime showtime = showtimesById.get(showtimeId);
        if (showtime == null) throw new NoSuchElementException("Showtime not found: " + showtimeId);
        return showtime.getAvailableSeats();
    }

    // city first, like BookMyShow: walk this city's theaters → screens → showtimes
    public List<Showtime> searchMovies(String cityId, String title) {
        City city = citiesById.get(cityId);
        if (city == null || title == null || title.isEmpty()) return new ArrayList<>();
        String query = title.toLowerCase();
        List<Showtime> results = new ArrayList<>();
        for (Theater theater : city.getTheaters()) {
            for (Screen screen : theater.getScreens()) {
                for (Showtime s : screen.getShowtimes()) {
                    if (s.getMovie().getTitle().toLowerCase().contains(query)) results.add(s);
                }
            }
        }
        return results;
    }
}
```
Plain `HashMap`s are fine here: they're filled once in the constructor and only read afterwards. If showtimes get added while serving, switch to `ConcurrentHashMap`.

**Step 4: the thin containers** ([model/City.java](model/City.java), [model/Theater.java](model/Theater.java), [model/Screen.java](model/Screen.java)). Write one; say the others are identical:
```java
public class Screen {
    private final String id;
    private final String name;
    private final List<Showtime> showtimes = new ArrayList<>();

    public Screen(String id, String name) { this.id = id; this.name = name; }

    public void addShowtime(Showtime showtime) { showtimes.add(showtime); }
    public List<Showtime> getShowtimes()        { return showtimes; }
}
// Theater = same shape with List<Screen>;  City = same shape with List<Theater> + getAllShowtimes()
```

### Dry run: the race (say this at the board)
```
Seat 10 is free. Alice and Bob both call book("S2", ["10"]) at the same instant.

Alice: enters Showtime.book() → gets S2's lock
Bob:   enters Showtime.book() → S2 is locked → WAITS
Alice: isAvailable("10") → true → bookings.add(alice) → returns, releases the lock
Bob:   gets the lock → isAvailable("10") → false → IllegalStateException("Seat unavailable: 10")

Meanwhile Carol books show S3 → a different Showtime → a different lock → never waits.
```
The driver proves it: 50 threads race for seat 10, giving exactly 1 success and 49 conflicts.

---

## 5. Follow-ups: answer out loud first, then open

Most likely first: Q1, Q2, Q3, Q4. Q10 matters too: Amazon also asks Meeting Room and Restaurant booking, and it's the same design.

<details>
<summary><b>Q1. Two users click "book" on the same seat at the same moment. Walk me through it.</b></summary>

**Without the lock**, the gap between check and add is the bug:
```
Alice: isAvailable("10") → true
Bob:   isAvailable("10") → true          ← both checked before either added
Alice: bookings.add(alice)
Bob:   bookings.add(bob)                  ← seat 10 sold twice
```
**With `synchronized` on `Showtime.book()`**, check + add run as one step per show (dry run in §4).

**Why lock the Showtime?**
- Not `BookingSystem`: that would make every show in the country wait in one line.
- Not each seat: that's the opening-night upgrade, with more code and a deadlock risk (Q5).
- The Showtime is the **smallest object that contains the whole rule** ("no seat in this show twice").

**Why are the read methods synchronized too?** `getAvailableSeats()` loops over the `bookings` `ArrayList`. If `book()` adds to it mid-loop, Java can throw `ConcurrentModificationException`. Locking only the writer isn't enough: readers must take the same lock.
</details>

<details>
<summary><b>Q2. "Hold the seats while the user pays." (the most likely follow-up)</b></summary>

**Problem:** payment takes 30–60 s. Without a hold, someone else can book the seats meanwhile, and then the payment succeeds for seats you no longer have.
**Fix:** two phases. **Hold** (seats reserved for 5 min) → pay → **confirm** (the hold becomes a booking). A seat is now taken if a booking **or an unexpired hold** has it.

```java
public class SeatHold {
    private final String holdId;
    private final List<String> seatIds;
    private final Instant expiresAt;

    public SeatHold(String holdId, List<String> seatIds, Instant expiresAt) {
        this.holdId = holdId;
        this.seatIds = new ArrayList<>(seatIds);
        this.expiresAt = expiresAt;
    }

    public String getHoldId()        { return holdId; }
    public List<String> getSeatIds() { return seatIds; }
    public boolean isExpired(Instant now) { return !now.isBefore(expiresAt); }
}
```
Additions to `Showtime` (it now gets a `Clock`, so tests can move time):
```java
private final Map<String, SeatHold> holds = new HashMap<>();   // holdId → hold
private final Clock clock;

// Phase 1: reserve the seats for a few minutes while the user pays
public synchronized SeatHold hold(List<String> seatIds, Duration ttl) {
    for (String seatId : seatIds) {
        if (!isAvailable(seatId)) throw new IllegalStateException("Seat unavailable: " + seatId);
    }
    SeatHold hold = new SeatHold(UUID.randomUUID().toString(), seatIds, clock.instant().plus(ttl));
    holds.put(hold.getHoldId(), hold);
    return hold;
}

// Phase 2: payment succeeded → turn the hold into a booking
public synchronized Booking confirm(String holdId) {
    SeatHold hold = holds.remove(holdId);
    if (hold == null) throw new NoSuchElementException("Hold not found or expired: " + holdId);
    if (hold.isExpired(clock.instant())) throw new IllegalStateException("Hold expired: " + holdId);
    Booking booking = new Booking(UUID.randomUUID().toString(), this, hold.getSeatIds());
    bookings.add(booking);
    return booking;
}

// Payment failed or the user closed the page: free the seats now instead of waiting for expiry
public synchronized void release(String holdId) {
    holds.remove(holdId);
}

// A seat is taken if a booking has it OR an unexpired hold has it
public synchronized boolean isAvailable(String seatId) {
    for (Booking b : bookings) {
        if (b.getSeatIds().contains(seatId)) return false;
    }
    Instant now = clock.instant();
    holds.values().removeIf(h -> h.isExpired(now));      // lazy cleanup: no sweeper thread needed
    for (SeatHold h : holds.values()) {
        if (h.getSeatIds().contains(seatId)) return false;
    }
    return true;
}
```
(`Booking` now also stores its `Showtime`; that's used by cancel in Q4.)

**Points that show depth:**
- **Holds use the same per-show lock** as bookings, so there's no new kind of race.
- **Lazy expiry**, like the rate limiter's lazy refill: expired holds are ignored and cleaned up when someone looks. No background thread.
- **Payment happens outside the lock.** Holding a lock during a slow external call would freeze the whole show. That's exactly *why* holds exist (see Q3).
- **Alternative: optimistic.** Let everyone pay and refund the losers. That's cheap at low contention and terrible on opening night.
</details>

<details>
<summary><b>Q3. Add payment: UPI or card, chosen by the user.</b></summary>

**Strategy:** each payment method implements one interface. The user picks one per checkout, so it's passed **per call**.
```java
public interface PaymentProcessor {
    boolean charge(String userId, long amountRupees);
    void refund(String userId, long amountRupees);
}
public class UpiPayment implements PaymentProcessor { ... }
public class CardPayment implements PaymentProcessor { ... }
```
The checkout flow ties holds and payment together:
```java
private static final Duration HOLD_TTL = Duration.ofMinutes(5);

public Booking checkout(String showtimeId, List<String> seatIds, String userId, PaymentProcessor payment) {
    Showtime showtime = getShowtime(showtimeId);
    SeatHold hold = showtime.hold(seatIds, HOLD_TTL);         // 1. reserve: others now see these seats as taken
    long amount = pricing.price(seatIds);                     //    (pricing: Q8; or seatIds.size() * 200)

    if (!payment.charge(userId, amount)) {                     // 2. pay OUTSIDE any lock (slow, external)
        showtime.release(hold.getHoldId());
        throw new IllegalStateException("Payment failed");
    }
    Booking booking;
    try {
        booking = showtime.confirm(hold.getHoldId());          // 3. hold → booking
    } catch (RuntimeException e) {
        payment.refund(userId, amount);                        // hold expired while the user was paying
        throw e;
    }
    bookingsById.put(booking.getBookingId(), booking);
    notifyBooked(booking);                                     // Q9
    return booking;
}
```
**Walk through the 3 failure cases:**
| What fails | What happens |
|---|---|
| Seats taken at hold time | `hold()` throws; nothing charged |
| Payment declined | hold released at once; seats free for others |
| Paid, but the hold expired meanwhile | `confirm()` throws → **refund**. The money never stays without a ticket. |

**Bonus point:** give the charge an **idempotency key** (e.g. the holdId). If the network times out and you retry, the user isn't charged twice.
</details>

<details>
<summary><b>Q4. Add cancellation.</b></summary>

Three small additions:
1. `Booking` stores its `Showtime` (a back-reference), so cancel can find the show in O(1).
2. `Showtime.cancel()`, under the same lock.
3. `BookingSystem` keeps `bookingsById` (filled in `book()`/`checkout()` *after* success).

```java
// Booking: one new field
private final Showtime showtime;
public Booking(String bookingId, Showtime showtime, List<String> seatIds) { ... }

// Showtime
public synchronized void cancel(Booking booking) {
    if (!bookings.remove(booking)) {                         // removing the booking frees its seats
        throw new IllegalStateException("Booking already cancelled: " + booking.getBookingId());
    }
}

// BookingSystem
private final Map<String, Booking> bookingsById = new ConcurrentHashMap<>();

public void cancel(String bookingId) {
    Booking booking = bookingsById.remove(bookingId);         // remove first: a 2nd cancel finds nothing
    if (booking == null) throw new NoSuchElementException("Booking not found: " + bookingId);
    booking.getShowtime().cancel(booking);
}
```
- **Why seats free up automatically:** the bookings list is the only source of truth (D4). Remove the booking and its seats are free. No `Seat.status` to reset. This is the payoff of D3/D4.
- **Double cancel:** `remove()` on a `ConcurrentHashMap` is atomic, so only one of two simultaneous cancels gets the booking.
- **Refunds:** call `payment.refund(...)` after a successful cancel.
</details>

<details>
<summary><b>Q5. "Opening night of a Marvel movie: 50,000 users on one show. The per-show lock is a bottleneck."</b></summary>

First say: *"I'd measure before changing. Each lock is held for microseconds, and 100 seats can only sell 100 times."* Then the upgrade: **a lock per seat**, always taken **in sorted order** so two users can't deadlock.

```java
public class Showtime {
    private final ConcurrentHashMap<String, ReentrantLock> seatLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> seatOwner = new ConcurrentHashMap<>();   // seatId → bookingId

    public void book(String bookingId, List<String> seatIds) {
        List<String> sorted = new ArrayList<>(seatIds);
        Collections.sort(sorted);                         // everyone locks in the same order → no deadlock

        List<ReentrantLock> locks = new ArrayList<>();
        for (String seatId : sorted) {
            locks.add(seatLocks.computeIfAbsent(seatId, k -> new ReentrantLock()));
        }
        for (ReentrantLock lock : locks) lock.lock();
        try {
            for (String seatId : sorted) {
                if (seatOwner.containsKey(seatId)) throw new IllegalStateException("Seat unavailable: " + seatId);
            }
            for (String seatId : sorted) seatOwner.put(seatId, bookingId);
        } finally {
            for (ReentrantLock lock : locks) lock.unlock();   // always unlock, even after a throw
        }
    }
}
```
- **Why sort:** Alice wants `[5, 6]`, Bob wants `[6, 5]`. Alice locks 5, Bob locks 6, each waits for the other forever. That's a deadlock. With sorting, both lock 5 first: one waits and the other finishes. Any consistent order works; here, string order.
- **Trade-off:** users booking *different* seats in the same show no longer wait for each other, but the code is more complex. `seatOwner` becomes the source of truth for seats in this version.
- **Same idea as a bank transfer:** lock both accounts in id order.
</details>

<details>
<summary><b>Q6. Make it work across many servers.</b></summary>

`synchronized` only protects one JVM. Move the "no seat twice" rule into the **database** and let it reject the duplicate:
```sql
CREATE TABLE booked_seat (
    showtime_id VARCHAR(32),
    seat_id     VARCHAR(8),
    booking_id  VARCHAR(64),
    PRIMARY KEY (showtime_id, seat_id)        -- the DB refuses a 2nd row for the same seat
);

-- seats 5 and 6 in one transaction: both inserts succeed, or the duplicate-key error rolls back both
BEGIN;
INSERT INTO booked_seat VALUES ('S1', '5', 'B-77');
INSERT INTO booked_seat VALUES ('S1', '6', 'B-77');
COMMIT;
```
- **The primary key replaces `synchronized`.** Whichever server inserts first wins; the other gets a duplicate-key error and returns "seat unavailable".
- **Holds across servers:** a `seat_hold` table with an `expires_at` column, or Redis `SET hold:S1:5 <userId> NX PX 300000` (`NX` = only if nobody holds it, `PX` = expire after 5 min). Several seats at once needs a Lua script, so they're held all-or-nothing.
- **Shard by showtimeId:** all traffic for one show goes to one place, and different shows scale out.
</details>

<details>
<summary><b>Q7. Show the latest seat map to users who are browsing.</b></summary>

`getAvailableSeats()` is already safe to call any time (synchronized). For live updates, the client polls every few seconds, or the server pushes changes over a WebSocket after each `book`/`hold`/`cancel`. The seat map users see can be a little stale; that's fine because **`book()` re-checks under the lock**. Stale reads are OK; stale writes are not.
</details>

<details>
<summary><b>Q8. Different prices: premium rows cost more, weekends cost more.</b></summary>

**Strategy:** pricing rules change often and are independent of booking logic.
```java
public interface PricingStrategy {
    long price(List<String> seatIds);
}

public class FlatPricing implements PricingStrategy {
    public long price(List<String> seatIds) { return seatIds.size() * 200L; }
}

public class TierPricing implements PricingStrategy {
    // seats 1–20 are PREMIUM (₹400), the rest REGULAR (₹200)
    public long price(List<String> seatIds) {
        long total = 0;
        for (String seatId : seatIds) total += Integer.parseInt(seatId) <= 20 ? 400 : 200;
        return total;
    }
}
// BookingSystem gets one in its constructor: new BookingSystem(cities, new TierPricing())
```
Weekend surge = another class. Coupons or loyalty discounts **stacked** on top of any price = Decorator around `PricingStrategy`. Money is `long` rupees, never `double`.
</details>

<details>
<summary><b>Q9. Send SMS and email when a booking is confirmed.</b></summary>

**Observer:** `BookingSystem` announces "booked"; listeners react. Adding WhatsApp later = one more listener, with no change to booking code.
```java
public interface BookingListener {
    void onBooked(Booking booking);
}

// BookingSystem
private final List<BookingListener> listeners = new CopyOnWriteArrayList<>();

public void addListener(BookingListener listener) { listeners.add(listener); }

private void notifyBooked(Booking booking) {
    for (BookingListener l : listeners) {
        try {
            l.onBooked(booking);
        } catch (Exception e) {                                // a failed SMS must not undo a paid booking
            System.err.println("Listener failed: " + e.getMessage());
        }
    }
}
// usage
system.addListener(b -> smsService.send("Booked seats " + b.getSeatIds()));
```
- **Notify after the booking is committed**, outside the lock.
- In production, put the event on a **queue** so a slow email server never slows down booking.
</details>

<details>
<summary><b>Q10. "Design Meeting Room booking / Restaurant table booking." (same Amazon cluster)</b></summary>

Same skeleton, different "seat":
| | Movie | Meeting room | Restaurant |
|---|---|---|---|
| Thing being booked | seat in a showtime | room | table |
| Conflict when | same seat id | **time ranges overlap** | **time ranges overlap** + party size > table size |
| Lock per | showtime | room | table (or restaurant for small ones) |
| Allocation choice | user picks seats | Strategy: smallest room that fits | Strategy: smallest table that fits |

The new piece is the **overlap check**, using half-open intervals `[start, end)`:
```java
// a meeting ending at 7:00 does NOT clash with one starting at 7:00
boolean overlaps(LocalDateTime aStart, LocalDateTime aEnd, LocalDateTime bStart, LocalDateTime bEnd) {
    return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
}
```
`Room.book(start, end)` is `synchronized`: check overlap against every existing booking, then add. That's exactly `Showtime.book()` with `overlaps()` in place of `contains()`. With many bookings per room, keep them in a `TreeMap<start, booking>` and only check the neighbours (`floorEntry`/`ceilingEntry`): O(log n).
</details>

<details>
<summary><b>Q11. Theaters add new showtimes while the system is live.</b></summary>

Keep the hierarchy (used by search) and the index (used by book) in sync, and make them safe for concurrent use:
```java
// showtimesById becomes a ConcurrentHashMap; Screen.showtimes becomes a CopyOnWriteArrayList
public void addShowtime(Screen screen, Showtime showtime) {
    screen.addShowtime(showtime);                       // the hierarchy (search)
    showtimesById.put(showtime.getId(), showtime);      // the index (book)
}
```
`CopyOnWriteArrayList` suits this: showtimes are added rarely and searched constantly.
</details>

<details>
<summary><b>Q12. How would you know it's working in production?</b></summary>

- **Metrics:** bookings/min, **conflict rate** (409 "seat unavailable"), hold → confirm conversion, holds expired, payment failures, p99 latency of `book()`.
- **Alarms:** **any** double-booked seat (a nightly job comparing bookings; should always be 0); conflict rate spikes (a hot show or a bug); refunds after expired holds rising (TTL too short).
- **Logs:** bookingId + showtimeId + userId on every state change, so one customer complaint can be traced end to end.
</details>

<details>
<summary><b>Q13. How do you test that double-booking can't happen?</b></summary>

50 threads, a start gate, one seat. Exactly 1 must win (this is in the driver):
```java
CountDownLatch fire = new CountDownLatch(1);
AtomicInteger successes = new AtomicInteger();
AtomicInteger conflicts = new AtomicInteger();
ExecutorService pool = Executors.newFixedThreadPool(50);
for (int i = 0; i < 50; i++) {
    pool.submit(() -> {
        try {
            fire.await();                                 // everyone waits at the gate
            system.book("S2", List.of("10"));
            successes.incrementAndGet();
        } catch (IllegalStateException e) {
            conflicts.incrementAndGet();                   // seat taken: expected for 49 threads
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    });
}
fire.countDown();                                         // release all 50 at once
pool.shutdown();
pool.awaitTermination(5, TimeUnit.SECONDS);
// assert successes.get() == 1 && conflicts.get() == 49
```
Also test: all-or-nothing (`["7", "6"]` with 6 taken → 7 stays free), duplicate seats, unknown showtime, and holds expiring exactly at `expiresAt` (with a movable `Clock`).
</details>

---

## 6. Traps that cost points
1. Checking availability in `BookingSystem` and booking in `Showtime`: two calls, so there's a race in the gap.
2. Locking `BookingSystem` (global) or locking nothing.
3. Synchronizing `book()` but not the read methods (`ConcurrentModificationException`).
4. A `Seat` class with a `status` field **plus** a bookings list: two sources of truth.
5. Partial bookings: seats 7 and 8 booked, then seat 6 fails, and 7–8 stay booked.
6. Calling payment **inside** the lock: one slow card network freezes the whole show.
7. Spending 10 minutes on City/Theater/Screen before `Showtime.book()` exists.

---

## 7. Recall check (next day, no peeking)
1. Write the race between Alice and Bob without a lock, step by step.
2. Why lock the `Showtime`? Why not `BookingSystem`, and why not each seat?
3. Why must `isAvailable()` and `getAvailableSeats()` be synchronized too?
4. Why is a seat a `String` and not a class? What goes wrong with `Seat.status`?
5. Draw the hold → pay → confirm flow, and say what happens in each of the 3 failure cases.
6. Per-seat locking: why sort the seat ids first?
7. Across many servers, what replaces `synchronized`?
8. Meeting room: write `overlaps()` and explain `[start, end)`.

**Rebuild in 10 minutes:** `Showtime` with `List<Booking>` + `synchronized book()` (validate → check all → add) + `synchronized isAvailable()` · `Booking(id, seatIds)` · `BookingSystem` with a `showtimesById` index + `book()`.

---

**Files:** `BookingSystem` (service) · `model/` (`Showtime`, `Booking`, `Movie`, `Screen`, `Theater`, `City`) · `BookingSystemDriver` (search, browse, booking, all-or-nothing, invalid/duplicate seats, 50-thread race)
```bash
mvn -q compile exec:java -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.booking.movieticket.BookingSystemDriver
```
