# Movie Ticket Booking (BookMyShow)

> 30–40 min round. Amazon ★ (booking under contention is the most reported LLD cluster). Phases: **Requirements (5) → Entities (3) → Class Design (10–15) → Implementation (10) → Extensibility (5)**.
> Sister problems: [Meeting Room](../meetingscheduler/INTERVIEW_WALKTHROUGH.md) and [Inventory](../inventory/INTERVIEW_WALKTHROUGH.md), the same "check and claim in one step" idea.

---

## 0. The problem in plain words (read first, don't say it in the interview)

**Picture an old cinema counter.** Each show (Inception, 7 PM) has its own **paper seat chart**. To sell a seat, the cashier crosses it out.

**The danger:** two cashiers look at the 7 PM chart together. Both see seat 5 free, and both sell it. Two people turn up for one seat.

**The fix:** **one pen per chart.** You must hold the pen to look **and** cross out, so nobody can sneak in between. The Dune 8 PM chart has its own pen, so its buyers never wait for Inception's.

**The whole system is 2 actions:**
```
See seats  →  getAvailableSeats(show)   →  which seats are still free
Book       →  book(show, [5, 6])        →  all the seats, or none of them
```

**Real world → code**
| In the real world | In the code |
|---|---|
| The movie | `Movie` (id, title) |
| One screening, "Inception 7 PM" + its seat chart | `Showtime`: the seats live here |
| "One pen per chart" | `synchronized` methods on `Showtime` |
| A seat | just a number, 1..N (no class) |
| Your ticket | `Booking` (id, show, seats) |
| The app's backend | `BookingSystem`: finds the show, the show does the booking |

**Only 2 things are tricky:**
1. **Check and book must be one step**, under the show's lock. "Is it free?" followed by a separate "book it" leaves a gap, and that gap is the race.
2. **All or nothing.** A family booking seats 5, 6, 7 doesn't want 2 of the 3. Check **every** seat first, then book them all.

**Picture this run before you start coding** (a 10-seat show):
```
book [3, 4]       → B-1               free: 1 2 5 6 7 8 9 10
book [4]          → "already booked"
book [5, 4, 6]    → "already booked"  → seat 5 is still free (nothing changed)
book [11]         → "no such seat"     book [7, 7] → "same seat twice"
10 PM show, [3]   → fine (a different show is a different chart)
50 people grab seat 1 at once → exactly 1 wins
```
If you can explain this run, you understand the problem.

---

## 1. Requirements (~5 min)

**Ask:** pick exact seats, or "any 2"? Several seats at once, all or nothing? Hold seats while paying? Many people on one show at once?

```
1. getAvailableSeats(showId) → the free seat numbers
2. book(showId, seats) → a Booking: all the seats or none
3. Reject: a taken seat, a seat that doesn't exist, the same seat twice, an unknown show
4. Safe with many people booking the same show at once (no seat sold twice)

Out of scope: holds while paying, payment, cancellation, search, prices
```

---

## 2. Entities & Relationships (~3 min)

```
BookingSystem
   └── Showtime (many)   movie, start time, seat count, booked seats  ← the lock lives here
          └── Movie
Booking   (id, showId, seats): returned to the user
```
- **Seats belong to the show, not the hall.** Seat 5 at 7 PM and seat 5 at 10 PM are different tickets.
- **A seat is a number, not a class.** It has no behaviour, and a `Seat.status` field would be a second source of truth.
- **No City / Theater / Screen in the base.** They have no behaviour until search is asked for (Extension 4).

---

## 3. Class Design (~10 min)

```
BookingSystem
  state:     showtimes (id → Showtime), booking counter
  behaviour: addShowtime(show) · getAvailableSeats(showId) · book(showId, seats) → Booking

Showtime   id, movie, startTime, totalSeats, bookedSeats      synchronized getAvailableSeats(), synchronized book(seats)
Booking    id, showtimeId, seats                               (getters only)
Movie      id, title
```
- **The lock is per show.** A global lock would make every show in the country wait for every other. A lock per seat is more code, and only worth it for a huge show (Extension 6).
- **`BookingSystem` has no lock.** It only looks up the show, so its map is a `ConcurrentHashMap` and its counter an `AtomicInteger`.
- **No patterns in the base.** The crux is concurrency. Strategy comes with payment and pricing (Extensions 2, 5).

---

## 4. Implementation (~10 min)

**Write it in this order:** `Showtime.book()` (the crux) → `getAvailableSeats()` → `Booking`, `Movie` → `BookingSystem`.

```java
public class Showtime {
    private final String id;
    private final Movie movie;
    private final LocalDateTime startTime;
    private final int totalSeats;                              // seats are 1..totalSeats
    private final Set<Integer> bookedSeats = new HashSet<>();

    // constructor, getters

    public synchronized List<Integer> getAvailableSeats() {
        List<Integer> free = new ArrayList<>();
        for (int seat = 1; seat <= totalSeats; seat++) {
            if (!bookedSeats.contains(seat)) free.add(seat);
        }
        return free;
    }

    // All or nothing: check EVERY seat first, then book them all
    public synchronized void book(List<Integer> seats) {
        if (seats.isEmpty()) throw new IllegalArgumentException("Pick at least one seat");
        if (new HashSet<>(seats).size() != seats.size()) {
            throw new IllegalArgumentException("Same seat twice: " + seats);
        }
        for (int seat : seats) {
            if (seat < 1 || seat > totalSeats) throw new IllegalArgumentException("No such seat: " + seat);
            if (bookedSeats.contains(seat)) throw new IllegalStateException("Seat already booked: " + seat);
        }
        bookedSeats.addAll(seats);
    }
}

public class Booking { private final String id, showtimeId; private final List<Integer> seats; /* ctor copies the list + getters */ }
public class Movie   { private final String id, title; /* ctor + getters */ }
```

```java
public class BookingSystem {
    private final Map<String, Showtime> showtimes = new ConcurrentHashMap<>();
    private final AtomicInteger nextBookingNumber = new AtomicInteger(1);

    public void addShowtime(Showtime showtime) {
        showtimes.put(showtime.getId(), showtime);
    }

    public List<Integer> getAvailableSeats(String showtimeId) {
        return findShowtime(showtimeId).getAvailableSeats();
    }

    public Booking book(String showtimeId, List<Integer> seats) {
        Showtime showtime = findShowtime(showtimeId);
        showtime.book(seats);                                  // atomic; throws if any seat is taken
        return new Booking("B-" + nextBookingNumber.getAndIncrement(), showtimeId, seats);
    }

    private Showtime findShowtime(String showtimeId) {
        Showtime showtime = showtimes.get(showtimeId);
        if (showtime == null) throw new NoSuchElementException("Unknown show " + showtimeId);
        return showtime;
    }
}
```

**Verify out loud:** walk through the run from section 0. For the race, say: *"Alice and Bob both call `book([5])`. Alice gets the show's lock, sees 5 free, and adds it. Bob waits, then gets the lock, sees 5 booked, and gets an error. Without `synchronized`, both could see it free before either adds it."*

---

## 5. Extensibility (~5 min)

Name **which class changes**, write the few new lines, and don't redesign the rest.

**Quick revision** (read this table on the day of the interview):

| # | Interviewer asks | One-line answer | What changes |
|---|---|---|---|
| 1 | "Hold seats while the user pays" (the most likely one) | A `SeatHold` with an expiry time; a held seat counts as taken | `SeatHold` + 3 methods on `Showtime` |
| 2 | "Add payment: UPI or card" | hold → pay **outside the lock** → confirm; refund if the hold expired | `PaymentProcessor` (Strategy) + `checkout()` |
| 3 | "Cancel a booking" | Remove the booking, free its seats | 1 map + `cancel()` |
| 4 | "Search by city and movie" | A `city` on the show + a loop | 1 field + `search()` |
| 5 | "Premium seats, weekend prices" | `PricingStrategy`; weekend wraps it (Decorator) | 2–3 new classes |
| 6 | "50,000 people on one show" | A lock per seat, always taken in sorted order | `Showtime.book()` |
| 7 | "Many servers" | The database's primary key replaces `synchronized` | the database table |

---

### 1. "Hold the seats while the user pays"
**Idea:** payment takes a minute. During that minute, the seats must look taken to everyone else ("a coat on the seat"). If the user doesn't pay within 5 minutes, the hold expires and the seats are free again.
```java
public class SeatHold {
    private final String id;
    private final List<Integer> seats;
    private final Instant expiresAt;
    // constructor, getId(), getSeats()
    public boolean isExpired(Instant now) { return !now.isBefore(expiresAt); }
}

// Showtime: new fields (it gets a Clock, so tests can move time)
private final Map<String, SeatHold> holds = new HashMap<>();
private final Clock clock;
private int nextHoldNumber = 1;

// a seat is taken if it's booked OR held
private boolean isFree(int seat) {
    if (bookedSeats.contains(seat)) return false;
    for (SeatHold h : holds.values()) {
        if (h.getSeats().contains(seat)) return false;
    }
    return true;
}

public synchronized SeatHold hold(List<Integer> seats, Duration holdFor) {
    dropExpiredHolds();
    // same checks as book(), but using isFree(seat)
    SeatHold hold = new SeatHold(id + "-H" + nextHoldNumber++, seats, clock.instant().plus(holdFor));
    holds.put(hold.getId(), hold);
    return hold;
}

public synchronized void confirm(String holdId) {               // paid: the hold becomes a booking
    SeatHold hold = holds.remove(holdId);
    if (hold == null || hold.isExpired(clock.instant())) throw new IllegalStateException("Hold expired: " + holdId);
    bookedSeats.addAll(hold.getSeats());
}

public synchronized void release(String holdId) {               // payment failed: free the seats now
    holds.remove(holdId);
}

private void dropExpiredHolds() {                               // also called at the start of book() and getAvailableSeats()
    Iterator<SeatHold> it = holds.values().iterator();
    while (it.hasNext()) {
        if (it.next().isExpired(clock.instant())) it.remove();
    }
}
```
**Check:** hold [3, 4] → booking seat 4 fails · 5 min later → seats 3, 4 are free again · confirm after expiry → error.
**Say:** *"Holds use the same per-show lock, so there's no new race. Expiry is lazy: expired holds are dropped whenever someone looks, so there's no background thread."*

---

### 2. "Add payment: UPI or card"
**Idea:** never call payment while holding the show's lock, because a slow bank would freeze the whole show. Hold first, pay with no lock held, then confirm.
```java
public interface PaymentProcessor {                             // UpiPayment, CardPayment: one class each
    boolean charge(String userId, long amount);
    void refund(String userId, long amount);
}

// BookingSystem
private static final Duration HOLD_TIME = Duration.ofMinutes(5);

public Booking checkout(String showtimeId, List<Integer> seats, String userId, PaymentProcessor payment) {
    Showtime showtime = findShowtime(showtimeId);
    SeatHold hold = showtime.hold(seats, HOLD_TIME);           // 1. seats look taken to everyone else
    long amount = seats.size() * 200L;                         //    (or pricing.price(...), Extension 5)

    if (!payment.charge(userId, amount)) {                      // 2. slow call, no lock held
        showtime.release(hold.getId());
        throw new IllegalStateException("Payment failed");
    }
    try {
        showtime.confirm(hold.getId());                         // 3. hold → booked
    } catch (IllegalStateException e) {
        payment.refund(userId, amount);                         // the hold expired while they paid
        throw e;
    }
    return new Booking("B-" + nextBookingNumber.getAndIncrement(), showtimeId, seats);
}
```
**The 3 failure cases:** seats taken → `hold` throws, nothing is charged · payment declined → the hold is released at once · paid too late → `confirm` throws → **refund**.
**Say:** *"Money is never kept without a ticket. I'd also pass the holdId as an idempotency key, so a retried charge isn't taken twice."*

---

### 3. "Cancel a booking"
```java
// BookingSystem
private final Map<String, Booking> bookings = new ConcurrentHashMap<>();
// book() and checkout(): bookings.put(booking.getId(), booking);

public void cancel(String bookingId) {
    Booking booking = bookings.remove(bookingId);                // atomic: a 2nd cancel finds nothing
    if (booking == null) throw new NoSuchElementException("Booking not found or already cancelled: " + bookingId);
    findShowtime(booking.getShowtimeId()).cancelSeats(booking.getSeats());
}

// Showtime
public synchronized void cancelSeats(List<Integer> seats) {
    bookedSeats.removeAll(seats);
}
```
**Say:** *"`remove` on a ConcurrentHashMap is atomic, so two cancels at the same moment free the seats once. Then I'd refund through the same `PaymentProcessor`."*

---

### 4. "Search by city and movie"
**Idea:** give the show a `city` (and a theatre name). A loop is enough for an LLD round.
```java
// Showtime: + private final String city;   + getCity()

// BookingSystem
public List<Showtime> search(String city, String title) {
    List<Showtime> result = new ArrayList<>();
    for (Showtime s : showtimes.values()) {
        if (s.getCity().equalsIgnoreCase(city) && s.getMovie().getTitle().equalsIgnoreCase(title)) {
            result.add(s);
        }
    }
    return result;
}
```
**Say:** *"`City`, `Theater` and `Screen` earn their own classes once they have behaviour, like a theatre's opening hours or a screen's seat layout. At scale, search is a separate read service with an index keyed by city."*

---

### 5. "Premium seats cost more, weekends cost more"
```java
public interface PricingStrategy {
    long price(Showtime show, List<Integer> seats);
}

public class TieredPricing implements PricingStrategy {           // back rows are premium
    private final int normalSeats;
    private final long normalPrice, premiumPrice;
    // constructor sets all three

    public long price(Showtime show, List<Integer> seats) {
        long total = 0;
        for (int seat : seats) total += seat > normalSeats ? premiumPrice : normalPrice;
        return total;
    }
}

public class WeekendPricing implements PricingStrategy {          // wraps any pricing (Decorator)
    private final PricingStrategy inner;
    private final int percent;                                    // 120 = +20%
    // constructor sets both

    public long price(Showtime show, List<Integer> seats) {
        long base = inner.price(show, seats);
        DayOfWeek day = show.getStartTime().getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) return base * percent / 100;
        return base;
    }
}
// new WeekendPricing(new TieredPricing(6, 200, 350), 120)
```
**Check:** seats 1, 2, 9 on a Thursday → 200 + 200 + 350 = ₹750; on a Saturday → ₹900.

---

### 6. "Opening night: 50,000 people on one show, the per-show lock is slow"
**First say:** *"Each lock is held for microseconds, and a 300-seat show can only sell 300 times. I'd measure first."* If they still want it:

**Idea:** a lock **per seat**, so people booking different seats don't wait. Always lock the seats **in sorted order**, or two people can deadlock.
```java
private final Map<Integer, ReentrantLock> seatLocks = new ConcurrentHashMap<>();
private final Set<Integer> bookedSeats = ConcurrentHashMap.newKeySet();

public void book(List<Integer> seats) {                          // no synchronized
    List<Integer> sorted = new ArrayList<>(seats);
    Collections.sort(sorted);                                    // everyone locks in the same order

    List<ReentrantLock> locks = new ArrayList<>();
    for (int seat : sorted) locks.add(seatLocks.computeIfAbsent(seat, k -> new ReentrantLock()));
    for (ReentrantLock lock : locks) lock.lock();
    try {
        for (int seat : sorted) {
            if (bookedSeats.contains(seat)) throw new IllegalStateException("Seat not available: " + seat);
        }
        bookedSeats.addAll(sorted);
    } finally {
        for (ReentrantLock lock : locks) lock.unlock();          // always unlock, even after a throw
    }
}
```
**Why sort:** Alice wants [5, 6] and Bob wants [6, 5]. Alice locks 5, Bob locks 6, and each waits for the other forever. With sorting, both lock 5 first: one waits, the other finishes.
**Check:** 100 threads, half asking for [1, 2] and half for [2, 1] → exactly 1 wins, with no deadlock.

---

### 7. "Many servers"
**Say:** *"`synchronized` only protects one JVM. Across servers, I let the database refuse the second booking:"*
```sql
CREATE TABLE booked_seat (
    showtime_id VARCHAR(32),
    seat        INT,
    booking_id  VARCHAR(64),
    PRIMARY KEY (showtime_id, seat)        -- a 2nd row for the same seat is rejected
);
-- all seats of one booking in ONE transaction: all inserts succeed, or all roll back
```
*"Holds go in a table with `expires_at`, or in Redis with `SET hold:S1:5 user NX PX 300000` (only if nobody holds it, expires after 5 min)."*

---

```bash
mvn -q compile exec:java -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.booking.movieticket.BookingSystemDriver
```
