# Movie Ticket Booking — 45-min LLD Interview Walkthrough

**Target role:** SDE-2 (Amazon, Adobe, Microsoft, Atlassian)

> The **canonical concurrency LLD problem** in Indian SDE-2 onsites. The headline isn't class design (that's straightforward) — it's the **check-then-act race** on `book()` and how you defend "exactly one wins" when two users grab the same seat. Get that right and you're senior.

---

## Time budget

| Step | Activity | Budget | Cumulative |
|------|----------|--------|------------|
| 1 | Requirements | ~5 min | 5 |
| 2 | Entities & relationships | ~4 min | 9 |
| 3 | Class design | ~10 min | 19 |
| 4 | Implementation + dry-run | ~16 min | 35 |
| 5 | Extensibility | ~9 min | 44 |
| — | Wrap | ~1 min | 45 |

Step 5 gets an extra minute because seat-hold extensions are practically guaranteed as a follow-up.

---

## Mental models — memorize before you walk in

### M1. The booking flow (two entry points → one Showtime → atomic mutation)

```
   Two entry points (both after user picks a city):
     (a) search by title                 (b) browse by theater
              │                                   │
              v                                   v
     searchMovies("BLR", "Inception")    getShowtimesAtTheater(amc)
              └────── both return ─────────┘
                       List<Showtime>
                              │
                              v
                    user picks ONE Showtime
                              │
                              v
              +-----------------------------------+
              |       Showtime.book(...)          |   ← THE mutation point
              |   synchronized { check + add }    |     single lock, atomic
              +-----------------------------------+
                              │
                              v
                        Booking (returned to caller)

   (Cancellation is a Step-5 extension — see E0 below.)
```

### M2. The bookings list IS the seat state (single source of truth)

```
   Naive: Showtime tracks TWO things
              List<Booking> bookings
              Set<String>       bookedSeats     ← BUG MAGNET (must stay in sync)

   Better: Showtime tracks ONE thing
              List<Booking> bookings
              A seat is "available" iff no booking.seatIds contains it.
              Availability is DERIVED, not stored.

                    +--------------------------+
                    |   Showtime.bookings      |
                    +--------------------------+
                    | [ Booking { 5, 6 } ]     |
                    | [ Booking { 20   } ]     |
                    | [ Booking { 10   } ]     |
                    +--------------------------+
```

**Senior soundbite:** *"I'm not maintaining a separate `bookedSeats` set — the bookings list IS the seat state. Every booked seat appears in exactly one booking; availability is derived. One mutable field, one source of truth."*

### M3. The check-then-act race + the fix (THE core signal for this problem)

```
   WITHOUT synchronization — two threads booking seat 5:

   t=0   Thread A                Thread B
   ────  ──────────              ──────────
   t=1   isAvailable(5) → true
   t=2                           isAvailable(5) → true   ← BOTH see it free
   t=3   bookings.add(rA)
   t=4                           bookings.add(rB)     ← DOUBLE-BOOKED!


   WITH synchronized(this) on Showtime — only one thread inside at a time:

   t=0   Thread A                Thread B
   ────  ──────────              ──────────
   t=1   enters lock             (waits)
   t=2   isAvailable → true
   t=3   add(rA)
   t=4   exits lock              enters lock
   t=5                           isAvailable(5) → FALSE  ← sees A's mutation
   t=6                           throws IllegalStateException  ✓
```

> **The rule:** the entire *check → validate → add* sequence must be one atomic block. Splitting "first check, then add" is the textbook check-then-act race. This is what the interviewer is looking for.

---

## Step 1 — Requirements (~5 min)

### Clarifying dialogue

**You:** *"BookMyShow-style — users pick a city first, then search movies by title OR browse a specific theater, then book seats?"*
**Interviewer:** *"Yes."*
> Justifies City, Theater, and Movie as entities.

**You:** *"Users book by picking specific seat ids — multiple in one booking, all-or-nothing if any is taken?"*
**Interviewer:** *"Yes, multi-seat atomic. If one seat is taken the whole booking fails."*
> Signals the atomic multi-seat check.

**You:** *"Since the caller picks specific seat ids, they'll need to see which seats are free first — should the system expose that?"*
**Interviewer:** *"Yes, that's needed — the user has to know what's available before picking."*
> Signals `getAvailableSeats(showtimeId)` — not optional, since `book()` requires the caller to already know valid seat ids.

**You:** *"Concurrency — two users click the same seat at the same instant, exactly one wins?"*
**Interviewer:** *"Exactly one. This is the important requirement."*
> Signals `synchronized` on `Showtime.book` — the load-bearing decision.

**You:** *"For v1, is cancellation in scope? And I'll assume uniform seat layout across all screens — no per-screen sizes, no seat tiers?"*
**Interviewer:** *"Focus on booking first — cancellation is a follow-up. Uniform layout is fine."*
> Both simplifications: cancellation → Step 5, uniform layout keeps `Seat` as a string (Seat class is Step-5-if-tiers-arrive).

### Requirements to write down

```
IN SCOPE (all 4 test something the interviewer is grading)
1. List showtimes — search by movie title within a city, or browse a specific theater.
2. View available seats for a showtime — the caller must know valid seat ids before booking.
3. Book multiple seats atomically — all-or-nothing if any seat is taken.
4. Concurrent bookings of the same seat: EXACTLY ONE succeeds.

ASSUMED (not requirements — simplifications we agreed on)
- Uniform seat layout — flat numbered seats "1".."100" — no per-screen sizes.
- All seats identical — no tiers.
- Payment assumed successful.

OUT OF SCOPE (all Step-5 extensions)
- Cancellation
- Variable pricing / seat tiers
- Temporary seat holds during checkout
- UI / search ranking
```

---

## Step 2 — Entities & relationships (~4 min)

```
Entities
- BookingSystem   orchestrator + facade — search, browse, book
- City            groups theaters — id + name + List<Theater>
- Theater         named location — id + name + List<Screen>
- Screen          physical screen inside a theater — id + name + List<Showtime>
- Showtime        THE bookable unit — owns bookings + concurrency control
- Movie           searchable record — id + title
- Booking         immutable — bookingId + seatIds

NOT entities (string field instead)
- Seat            no state, no rules      → just `String seatId`

Relationships (full BookMyShow hierarchy)
- BookingSystem owns    Map<String, City> citiesById     (routes searches by city)
- BookingSystem indexes showtimesById                    (O(1) book resolution)
- City          owns    List<Theater>
- Theater       owns    List<Screen>
- Screen        owns    List<Showtime>
- Showtime      back-refs Screen (for navigation)
- Showtime      refs    Movie
- Showtime      owns    List<Booking>                    ← SINGLE source of truth for seats
- Booking       owns    List<String> seatIds
```

> **Note:** cancellation is a Step-5 extension. When it lands, Booking grows a back-ref to Showtime and BookingSystem grows a `bookingsById` index for O(1) cancel-by-confirmation-id.

### Why no `Seat` class?

> *"Seat has no state we manage — availability is derived from bookings. A string with built-in equality and hashing is the right choice. If tiers (Premium/Recliner) or per-seat locks come up, we can promote it — but even then, keep availability derived."*

### The full BookMyShow hierarchy — and the trap to avoid

We have `City → Theater → Screen → Showtime` in v1 — every level a class. `Seat` is a deliberate exception. Push back if the interviewer asks for `Seat` to own `SeatStatus`:

> *"If Seat owns a `SeatStatus` field, we now have TWO sources of truth — the seat's status AND the bookings list. Every book / cancel has to keep both in sync — the exact bug we solved by making bookings the only source of truth. I'd promote Seat only when a requirement demands it (tiers or per-seat locks), and even then keep availability derived."*

Compact map of what to add and when (all Step-5 additions):

| Requirement that arrives | Add this |
|--------------------------|----------|
| "Different seat tiers (Premium / Recliner)" | Promote `Seat` to a class — but keep availability derived, not on Seat |
| "Variable screen layouts (IMAX 100 vs intimate 30)" | Move rows/cols from Showtime's constants onto `Screen` |
| "Per-seat locking under Marvel-opening-night contention" | Promote `Seat` with its own `ReentrantLock` (sorted acquisition) |

### Class diagram

```
                  +--------------------------------+
                  |        BookingSystem           |   ← orchestrator + facade
                  |  search(city,title) / browse / book |
                  +--------------------------------+
                       │  Map<cityId, City> citiesById
                       │  Map<showtimeId, Showtime> showtimesById  (O(1) book)
                       v
                  +----------+
                  |   City   |    id, name, List<Theater>
                  +----------+
                       │
                       v
                  +-----------+
                  |  Theater  |    id, name, List<Screen>
                  +-----------+
                       │
                       v
                  +----------+
                  |  Screen  |    id, name, List<Showtime>
                  +----------+
                       │
                       v
                  +------------+
                  |  Showtime  |    ← the lock-protected hot spot
                  |  bookings  |
                  +------------+
                     /       \
                    v         v
             +---------+  +---------+
             |  Movie  |  | Booking |    bookingId + seatIds
             +---------+  +---------+
```

---

## Step 3 — Class design (~10 min)

### BookingSystem — state derived from requirements

| Requirement | State BookingSystem must own |
|-------------|------------------------------|
| Route search by city | `Map<String, City> citiesById` |
| Route `book` by showtime id | `Map<String, Showtime> showtimesById` |

> **Why only two indexes?** *"`book` needs O(1) resolution of a showtime id → `showtimesById`. Search starts with a city, so we need `citiesById`. Within a city, we scan its theaters and their showtimes — O(showtimes-in-city), nothing at interview scale. I'd only add a title index if search became a measured hot path."*

### BookingSystem — public API (4 methods)

```java
public class BookingSystem {
    private final Map<String, City>      citiesById;
    private final Map<String, Showtime>  showtimesById;

    public BookingSystem(List<City> cities) {
        // build citiesById; ask each city for its showtimes to build showtimesById (LoD)
    }

    public List<Showtime> searchMovies(String cityId, String title);   // city first, then title
    public List<Showtime> getShowtimesAtTheater(Theater theater);
    public List<String>   getAvailableSeats(String showtimeId);        // caller needs this before book()
    public Booking        book(String showtimeId, List<String> seatIds);
}
```

> **Why `getAvailableSeats` is NOT optional here:** *"`book()`'s signature takes specific `seatIds` — the system doesn't auto-assign a seat like Parking Lot does with spots. That means the caller MUST have a way to discover what's free before calling book. Unlike Parking Lot's `getAvailableSpots()` (which really would be scope creep, since spots are auto-assigned there), this is a hard dependency of our own API design — not a nice-to-have."*

### Showtime — THE hot class

| Requirement | State Showtime owns |
|-------------|--------------------|
| Track booked seats | `List<Booking> bookings` — single source of truth |
| Show what's playing where/when | `Screen screen`, `Movie movie`, `LocalDateTime datetime` |
| Concurrency | `synchronized` on `book` |

```java
public class Showtime {
    private final String id;
    private final Screen screen;                 // back-ref up the hierarchy
    private final Movie movie;
    private final LocalDateTime datetime;
    private final List<Booking> bookings;        // single source of truth

    public synchronized boolean isAvailable(String seatId);
    public synchronized List<String>       getAvailableSeats();
    public synchronized void  book(Booking b);   // atomic check+store
}
```

> **Why is `isAvailable` synchronized too, not just `book`?** *"`book` mutates the `bookings` list under the lock, but a reader calling `isAvailable` or `getAvailableSeats` iterates that SAME list. If reads aren't synchronized, a reader can race a concurrent `book()` and throw `ConcurrentModificationException` — or see a half-written state. All three methods share Showtime's intrinsic lock; Java's reentrant locking means `book()` calling `isAvailable()` internally is still safe."*

### Booking & City & Theater & Screen & Movie — thin data holders

```java
public class Booking {                           // immutable
    private final String        bookingId;
    private final List<String>  seatIds;
    // defensive copy in ctor, defensive copy out of getSeatIds
    // (Step 5 cancellation adds a back-ref to Showtime here)
}

public class City {                              // id, name, List<Theater>
    public void addTheater(Theater t);
    public List<Showtime> getAllShowtimes();      // flatten across theaters
}

public class Theater {                           // id, name, List<Screen>
    public void addScreen(Screen s);
    public List<Showtime> getShowtimes();         // flatten across screens
}

public class Screen {                            // id, name, List<Showtime>
    public void addShowtime(Showtime s);          // two-phase setup
}

public class Movie {                             // id, title — pure data, no behavior
}
```

### The principle to say aloud

> *"`book` lives on Showtime — Information Expert. Only Showtime knows about its bookings, so it owns the atomic mutation. BookingSystem doesn't reach into the list; it creates a Booking and TELLS Showtime to store it. If cancellation lands in Step 5, `cancel` lives on Showtime for the same reason — never as a method on Booking."*

---

## Step 4 — Implementation + dry-run (~16 min)

### 4.1 `Showtime.book` — the atomic check-and-store (write this FIRST)

```java
public synchronized void book(Booking booking) {
    List<String> seatIds = booking.getSeatIds();
    if (seatIds == null || seatIds.isEmpty()) {
        throw new IllegalArgumentException("Must select at least one seat");
    }
    // Validate seat-id format first — fail-fast, no allocation cost.
    for (String seatId : seatIds) {
        if (!isValidSeatId(seatId)) {
            throw new IllegalArgumentException("Invalid seat: " + seatId);
        }
    }
    // Check ALL seats are available BEFORE mutating — all-or-nothing.
    for (String seatId : seatIds) {
        if (!isAvailable(seatId)) {
            throw new IllegalStateException("Seat unavailable: " + seatId);
        }
    }
    bookings.add(booking);
}
```

**Three callouts to deliver out loud while writing this:**

1. *"`synchronized` on `this` — the Showtime. Two threads booking DIFFERENT showtimes proceed in parallel; only same-showtime bookings serialize. Right granularity for typical traffic."*

2. *"All-or-nothing: I check EVERY requested seat before adding. If a 3-seat booking has one unavailable seat, the whole thing fails with no partial state. Caller doesn't clean up half-bookings."*

3. *"Two exception types: `IllegalArgumentException` for bad input (typo in seat id), `IllegalStateException` for race loss (someone else got the seat). Two distinct kinds of caller mistakes."*

### 4.2 `BookingSystem.book` — orchestration

```java
public Booking book(String showtimeId, List<String> seatIds) {
    if (showtimeId == null || seatIds == null || seatIds.isEmpty()) {
        throw new IllegalArgumentException("Invalid booking request");
    }
    Showtime showtime = showtimesById.get(showtimeId);
    if (showtime == null) {
        throw new NoSuchElementException("Showtime not found: " + showtimeId);
    }
    Booking booking = new Booking(UUID.randomUUID().toString(), seatIds);
    showtime.book(booking);                             // atomic — may throw
    return booking;
}
```

> **Senior callout:** *"`showtime.book` runs under its lock. If it throws, the caller sees the exception and no state has changed anywhere. When cancellation is added (Step 5) we'll introduce a `bookingsById` map — and the order will matter then: put ONLY after the book succeeds so orphan entries can't leak in from rejected bookings."*

### 4.3 `searchMovies` — city-scoped title search

```java
public List<Showtime> searchMovies(String cityId, String title) {
    if (title == null || title.isEmpty()) return new ArrayList<>();
    City city = citiesById.get(cityId);
    if (city == null) return new ArrayList<>();

    String query = title.toLowerCase();
    List<Showtime> results = new ArrayList<>();
    for (Theater theater : city.getTheaters()) {
        for (Screen screen : theater.getScreens()) {
            for (Showtime s : screen.getShowtimes()) {
                if (s.getMovie().getTitle().toLowerCase().contains(query)) {
                    results.add(s);
                }
            }
        }
    }
    return results;
}
```

> *"City-first search matches how BookMyShow actually works — you pick a city, then search movies. Unknown city returns empty (not a throw) — the caller may be exploring. This is a direct triple-nested walk down the hierarchy — city → theaters → screens → showtimes — filtering by title inline. Simplest thing that satisfies the requirement."*

### If the interviewer asks about Law of Demeter here

`s.getMovie().getTitle().toLowerCase().contains(query)` is technically a chain through Showtime → Movie → String — three levels of reach. That's a legitimate LoD critique, and it's worth having the fix ready **without pre-building it into the base design**:

> *"Right now `searchMovies` reaches through Showtime into Movie's internals. If that's a concern, I'd push the filter down: `Movie.titleContains(query)`, `Showtime.matchesTitle(query)` delegating to it, and each container level (`Screen`, `Theater`, `City`) exposing its own `findShowtimesByTitle` that delegates to its children. Every method then talks only to its direct collaborator — five one-hop methods instead of one triple-nested loop. I didn't build it that way up front because the base requirement is just 'search by title in a city' — this is a clean refactor to reach for if you want strict LoD, not before."*

```java
// The LoD-clean version, if asked — five methods, each one hop:
public boolean titleContains(String query) {                 // on Movie
    return title.toLowerCase().contains(query.toLowerCase());
}
public boolean matchesTitle(String query) {                  // on Showtime
    return movie.titleContains(query);
}
public List<Showtime> findShowtimesByTitle(String query) {   // on Screen (Theater, City are the same shape)
    List<Showtime> result = new ArrayList<>();
    for (Showtime s : showtimes) if (s.matchesTitle(query)) result.add(s);
    return result;
}
```

**Tradeoff to name:** *"Five small methods across five classes vs. one loop in one place. LoD wins on 'change locality' — if Movie renames `title` to `name`, only Movie's method changes. The loop version wins on 'everything's in one place to read.' For base scope I'd default to the loop; I'd only push it into five methods if the interviewer specifically probes on coupling."*

> *"One scan over showtimes, filter by title. Returns showtimes directly — not movies — so the UI doesn't need a follow-up call to ask 'where is each movie playing?' One round-trip, complete result."*

### 4.4 `getAvailableSeats` — trivial delegation, but not optional

```java
public List<String> getAvailableSeats(String showtimeId) {
    Showtime showtime = showtimesById.get(showtimeId);
    if (showtime == null) {
        throw new NoSuchElementException("Showtime not found: " + showtimeId);
    }
    return showtime.getAvailableSeats();
}
```

> *"This is a one-line delegation — the real logic (layout minus booked seats) already lives on Showtime, computed from the same `bookings` single-source-of-truth. I throw on an unknown showtime here, same as `book()`, because this is an exact-id lookup — not an exploratory query like `searchMovies`, where an empty result for a bad city id is more appropriate than an exception."*

### 4.5 Dry-run — the 2-thread race (say this at the board)

```
Setup: showtime "S1" for Inception at 7pm, all 100 seats free.

Thread A: bookingSystem.book("S1", ["5"])
Thread B: bookingSystem.book("S1", ["5"])     ← same seat!

Step 1: Both threads create Booking objects (different confirmation ids).
        No state change yet — just object construction.

Step 2: Both call showtime.book(booking). Java's monitor lets ONE inside.

   Thread A wins the lock:
      isAvailable("5") → true    (bookings empty)
      bookings.add(bookingA)
      exit lock

   Thread B blocked; now acquires the lock:
      isAvailable("5") → scans bookings, finds 5 → FALSE
      throw IllegalStateException("5")
      exit lock with NO state change                                    ✓

Step 3: Back in BookingSystem.book:
   Thread A: returns Booking A                                      ✓
   Thread B: exception propagates                                        ✓

Result:
   1 success, 1 rejection.  Showtime.bookings contains exactly ONE entry.
   ⇒ R6 satisfied: exactly one wins.
```

**Bonus test to mention:** *"The included driver actually runs 50 threads through a `CountDownLatch` racing for the same seat, then asserts `successes = 1, conflicts = 49`. Empirical proof the lock works."*

---

## Step 5 — Extensibility (~9 min)

### E0. "Add cancellation" (this is v1's most likely first follow-up)

**Three small additions to the base:**

1. Booking grows a back-ref to its Showtime — so `cancel` routes without scanning.
2. Showtime gets a `synchronized cancel(booking)` method.
3. BookingSystem gets a 4th index `bookingsById` and a public `cancelBooking(bookingId)`.

```java
// On Booking — add the back-ref
public class Booking {
    private final String bookingId;
    private final Showtime showtime;                // NEW: back-ref for O(1) routing
    private final List<String> seatIds;
    // updated ctor + getShowtime()
}

// On Showtime — one new synchronized method
public synchronized void cancel(Booking booking) {
    bookings.remove(booking);               // frees seats automatically
}

// On BookingSystem — one new field, one new method, one extra put in book()
private final Map<String, Booking> bookingsById = new HashMap<>();

public Booking book(String showtimeId, List<String> seatIds) {
    // ... existing checks + Booking creation ...
    showtime.book(booking);
    bookingsById.put(booking.getBookingId(), booking);   // NEW
    return booking;
}

public void cancelBooking(String bookingId) {
    if (bookingId == null || bookingId.isEmpty()) {
        throw new IllegalArgumentException("Invalid confirmation ID");
    }
    Booking booking = bookingsById.get(bookingId);
    if (booking == null) {
        throw new NoSuchElementException("Booking not found");
    }
    booking.getShowtime().cancel(booking);   // back-ref → O(1) routing
    bookingsById.remove(bookingId);
}
```

**Say aloud (three callouts):**

1. *"The back-ref on Booking makes cancel O(1) — without it, we'd walk every theater × every showtime × every booking."*

2. *"Order matters in `book`: `showtime.book` runs FIRST under its lock. Only if it succeeds do we `put` into `bookingsById`. Reverse the order and every rejected booking leaks an orphan entry."*

3. *"Removing from `bookingsById` after successful cancel is what makes double-cancel throw automatically — the second call hits `null` and throws NoSuchElement. Same idempotency trick as ticket-removal in Parking Lot."*

---

### E1. "Temporary seat holds during checkout" (the highest-likelihood follow-up)

**Problem:** Real users pick seats, then spend 30–60s entering payment. Another user could grab the same seats meanwhile.

**Fix:** A third state — `HELD`. Two-phase flow: `holdSeats` reserves for ~5 min; `confirmHold` upgrades to a Booking. A sweeper releases stale holds.

```java
class Showtime {
    private final List<Booking> bookings;
    private final Map<String, SeatHold> holds;       // NEW

    public synchronized String holdSeats(List<String> seatIds, Duration timeout) {
        for (String s : seatIds) {
            if (!isAvailable(s)) throw new IllegalStateException(s);
        }
        SeatHold hold = new SeatHold(UUID.randomUUID().toString(), seatIds,
                                     LocalDateTime.now().plus(timeout));
        holds.put(hold.id(), hold);
        return hold.id();
    }

    public synchronized void confirmHold(String holdId, Booking r) {
        SeatHold hold = holds.get(holdId);
        if (hold == null) throw new NoSuchElementException("hold gone");
        if (LocalDateTime.now().isAfter(hold.expiresAt())) {
            holds.remove(holdId);
            throw new HoldExpiredException(holdId);
        }
        holds.remove(holdId);
        bookings.add(r);
    }

    // isAvailable now checks both bookings AND non-expired holds.
}
```

**Say aloud:** *"Fits cleanly under the existing per-showtime lock — holds and bookings serialize on the same monitor, no new race classes. Alternative is optimistic (let everyone book, refund losers) — fine for low contention, falls apart on opening night."*

### E2. "Dynamic add/remove of showtimes"

**Problem:** The index is built once in the constructor. Real theaters add showtimes continuously.

```java
public synchronized void addShowtime(Theater theater, Showtime showtime) {
    theater.addShowtime(showtime);
    showtimesById.put(showtime.getId(), showtime);      // keep the index in sync
}

public synchronized void removeShowtime(String showtimeId) {
    Showtime s = showtimesById.get(showtimeId);
    if (s == null) throw new NoSuchElementException("not found");
    if (!s.getBookings().isEmpty()) {
        throw new IllegalStateException("cancel existing bookings first");
    }
    showtimesById.remove(showtimeId);
    s.getTheater().getShowtimes().remove(s);
}
```

**Say aloud:** *"The senior signal is treating the index as a derived invariant that must stay in sync with the source of truth (the theater → showtimes graph). With one index this is trivial; if search grew a title index too, I'd extract a private `indexShowtime` helper so construction and dynamic-add share one code path — DRY."*

### E3. "Per-seat locking for Marvel-opening-night scale"

**Problem:** Per-showtime locking serializes all bookings for a hot showtime even when users want different seats.

**Fix:** Promote `Seat` to a class with its own `ReentrantLock`. Acquire seat locks in **sorted order** (avoids deadlock), validate, mark booked.

```java
public void book(List<Seat> seats) {
    List<Seat> sorted = seats.stream()
                             .sorted(Comparator.comparing(Seat::id))
                             .collect(Collectors.toList());
    for (Seat s : sorted) s.lock();
    try {
        for (Seat s : sorted) {
            if (s.isBooked()) throw new IllegalStateException(s.id());
        }
        for (Seat s : sorted) s.markBooked(booking);
    } finally {
        for (Seat s : sorted) s.unlock();
    }
}
```

**Say aloud:** *"Sorted lock acquisition is the deadlock fix — Thread A grabbing `["5", "6"]` and Thread B grabbing `["6", "5"]` would otherwise deadlock. Same pattern generalizes to bank transfers between two accounts."*

**Tradeoff to name:** *"More state (one lock per seat), more careful coding. Real benefit only at extreme contention. Profile before switching."*

### E4. Other one-liners

| Follow-up | Answer |
|-----------|--------|
| "Different seat tiers / pricing" | Promote `Seat` to a class with `tier`. Add `PricingStrategy` — `FlatRate`, `TierBased`, `DynamicSurge`. |
| "Notify user on booking / cancel" | Observer — BookingSystem publishes `BookingConfirmed` / `BookingCancelled`; SMS / Email / Push subscribe. |
| "Loyalty points / coupons" | Decorator on the pricing pipeline — `LoyaltyDiscount(BasePricing)`, stackable. |
| "Persist across restart" | Inject `BookingRepository`; write on every book/cancel; replay on boot. |
| "Multi-region" | Facade — `MultiRegionBookingSystem` over `Map<region, BookingSystem>`, routes by region. |
| "Cleanup expired showtimes" | Background sweeper — drops showtimes whose `datetime` is in the past. |
| "Variable seat layouts (IMAX 100, intimate 30)" | `ScreenBuilder` OR pass layout dimensions into Showtime; uniform layout was the earlier simplification. |

---

## Design patterns in play (name these out loud in the interview)

### In the BASE design — mention in Step 2 or Step 3

| Pattern / Principle | Where it lives | One-line justification |
|---------------------|----------------|------------------------|
| **Facade** | `BookingSystem` | *"Callers only touch BookingSystem — 4 methods hide the theaters, the showtime index, and the concurrency-protected Showtimes."* |
| **Information Expert** (GRASP) | `Showtime.book` lives on Showtime | *"Only Showtime knows about its bookings, so it owns the atomic mutation."* |
| **Tell, Don't Ask** | `BookingSystem` doesn't reach into `Showtime.bookings` | *"BookingSystem creates a Booking and TELLS Showtime to store it. It never mutates the list from outside."* |
| **Single Source of Truth** | `Showtime.bookings` — availability is derived | *"One field. No separate bookedSeats set. No cross-field consistency to maintain."* |
| **Immutability** | `Movie`, `Booking` | *"Immutable after construction; defensive copies of `seatIds` in both directions."* |
| **Immutability** | `LocalDateTime datetime` on Showtime is `final` | *"A showtime's scheduled time doesn't change once created."* |

**No Strategy / Factory / Builder in the base — deliberately.** *"The one-sentence test fails: search is locked to substring, pricing isn't variable, allocation is user-driven. Hardcode it; refactor if Step 5 asks."*

### Patterns for Step 5 extensibility — reach for these when the interviewer prompts

| Follow-up trigger | Pattern | The one-line move |
|-------------------|---------|-------------------|
| "Different seat tiers / pricing" | **Strategy** ⭐ | *"Promote `Seat` to a class. Add `PricingStrategy` — `FlatRate`, `TierBased`, `DynamicSurge` implement it."* |
| "Different search algorithms (fuzzy match, Elasticsearch)" | **Strategy** ⭐ | *"`SearchStrategy` interface — `SubstringSearch` (default), `FuzzySearch` (Levenshtein), `ElasticsearchAdapter`."* |
| "Notify on booking / cancellation" | **Observer** | *"BookingSystem publishes `BookingConfirmed` / `BookingCancelled`; SMS / Email / Push / Analytics subscribe independently."* |
| "Loyalty discounts / stackable coupons" | **Decorator** | *"Wrap the pricing pipeline — `LoyaltyDiscount(BasePricing)`, `CouponDiscount(LoyaltyDiscount(BasePricing))` — stackable at runtime."* |
| "Multiple kinds of theaters (IMAX, drive-in)" | **Factory** | *"`TheaterFactory.create(kind)` returns a preconfigured Theater with the right layout."* |
| "Persist across restart" | **Repository** | *"Inject `BookingRepository`; write on every book/cancel; replay on boot to rebuild indexes."* |
| "Multi-region / multi-country" | **Facade + Composition** | *"`MultiRegionBookingSystem` is a Facade over `Map<region, BookingSystem>` that routes by region."* |
| "Variable screen layouts (IMAX 100 vs intimate 30)" | Move constants onto `Screen` | *"Screen is already a class — add `rows` and `cols` fields; Showtime asks its Screen for dimensions."* |
| "That search reaches through 3 objects — Law of Demeter?" | **LoD refactor** | *"Push the filter down: `Movie.titleContains`, `Showtime.matchesTitle` delegates to it, `Screen`/`Theater`/`City` each expose `findShowtimesByTitle` delegating to their children. Five one-hop methods instead of one triple-nested loop — see Step 4.3."* |

### Patterns to actively refuse if the interviewer baits you

- **Singleton on BookingSystem** — kills tests; DI a single instance via constructor instead.
- **State pattern for Booking** (`PENDING / CONFIRMED / CANCELLED`) — overkill; an enum is fine unless per-state behavior actually diverges.
- **Composite over Theater → Showtime → Seat** — not a tree of uniform nodes; each level has a distinct contract.
- **Builder for the 1-arg `BookingSystem(theaters)` ctor** — academic noise.

### The rule to sound natural

1. **Cap at 2 patterns in the base design.** Movie Ticket lands on 1 (Facade). Anything else is Step 5.
2. **Always pair the pattern name with a concrete win.** *"Strategy — because pricing rules swap at runtime"* > *"I'd use Strategy."*
3. **Never volunteer a pattern without a requirement pressing on it.** Wait for the trigger.

---

## What is expected at each level

### Junior (SDE-1)
- Arrives at the domain entities with a nudge; may promote Seat to a class without justification.
- Implements happy-path `book`; misses the `synchronized` keyword until prompted.
- May maintain both `bookings` list AND a `bookedSeats` set (two sources of truth).
- No dry-run of the race condition unless explicitly asked.

### Mid-level (SDE-2) — the target
- Bookings list as the single source of truth — no separate booked-seats set.
- `synchronized` on `Showtime.book` from the start; explains the check-then-act race unprompted.
- All-or-nothing multi-seat booking implemented correctly.
- Runs the 2-thread dry-run out loud.
- Names cancellation as an obvious Step-5 extension and can sketch what changes when it lands (Booking back-ref, `bookingsById` index, order-matters in `book`).

### Senior (SDE-3 / SDE-II)
- Everything mid-level, faster, plus proactive tradeoffs.
- Names granularity explicitly: *"Per-showtime lock is the right default; per-seat is a measured optimization."*
- Discusses the deadlock trap in per-seat locking (sorted acquisition) as a preventive design.
- Introduces seat holds as the "realistic checkout flow" pattern before being asked.
- Keeps just ONE index (`showtimesById`) and can justify it — search-by-scan is fine at interview scale; add a title index only if search is a measured hot path. Restraint over premature optimization.
- Would write / describes a `CountDownLatch`-based multi-threaded test for empirical concurrency verification.

---

## Interview deep-dives

### Complexity

Let `S` = total showtimes, `M` = movies, `R` = bookings on one showtime, `K` = seats per booking.

| Operation | Time | Notes |
|-----------|------|-------|
| Constructor (index build) | **O(S)** | One pass builds `showtimesById` |
| `searchMovies(cityId, title)` | **O(showtimes in city)** | Walk city → theaters → showtimes; filter by title |
| `getShowtimesAtTheater` | **O(showtimes at that theater)** | Single pass |
| `book(showtimeId, K seats)` | **O(K · R)** | `isAvailable` is O(R) per seat |
| `cancelBooking(id)` *(Step-5)* | **O(1)** hash lookup + **O(R)** list remove | Back-ref on Booking makes lookup O(1) |

> **Senior callout:** *"`book` is O(K·R). To make it O(K), maintain a derived `Set<String> bookedSeats` on Showtime — O(1) availability at the cost of one more mutable field to keep in sync. Worth it if R grows to thousands, not before."*

### Concurrency — the three-level menu

| Approach | When | Cost |
|----------|------|------|
| `synchronized` per Showtime | **Default.** Correct, simple. | Same-showtime serializes even for different seats. |
| Per-seat lock (sorted acquisition) | High contention on popular showtimes | More state; must sort ids to avoid deadlock. |
| Optimistic CAS on seat-status field | Extreme throughput, low conflict | Retry storms under hot contention. |

### The 50-thread test (mention this)

```java
@Test
void fifty_threads_race_for_one_seat_exactly_one_wins() throws Exception {
    int N = 50;
    CountDownLatch fire = new CountDownLatch(1);
    AtomicInteger successes = new AtomicInteger();

    for (int i = 0; i < N; i++) {
        pool.submit(() -> {
            try {
                fire.await();
                bookingSystem.book("S1", List.of("10"));
                successes.incrementAndGet();
            } catch (IllegalStateException ignored) {}
        });
    }
    fire.countDown();     // all threads race simultaneously
    pool.awaitTermination(5, TimeUnit.SECONDS);

    assertEquals(1, successes.get());
}
```

*"The `CountDownLatch` fires all threads simultaneously — maximizes real contention rather than serialized execution. This is one of the few LLD tests that's genuinely multi-threaded."*

---

## 30-second summary (memorize for closing)

> *"Full BookMyShow hierarchy: City → Theater → Screen → Showtime, all as classes, plus Booking, Movie, and BookingSystem as the facade. Seat is a string — deliberately — because if Seat owned SeatStatus we'd have two sources of truth. BookingSystem owns `citiesById` for city-scoped search and `showtimesById` for O(1) book resolution. Four public methods: search, browse-by-theater, `getAvailableSeats` — necessary because `book()` takes specific seat ids, so the caller must discover what's free first — and `book` itself. The core architectural call is that `Showtime.bookings` is the SINGLE source of truth for seat state — availability is derived, and `isAvailable`/`getAvailableSeats` are synchronized alongside `book` so reads never race the writer. `Showtime.book` is synchronized, so check-then-add is atomic — exactly one of N concurrent bookings wins. Multi-seat bookings are all-or-nothing — one unavailable seat throws with no state change. Extensions: cancellation via a back-ref on Booking plus a `bookingsById` index; seat holds under the same per-showtime lock; promoting Seat to a class for tiers or per-seat locks."*

---

## Top mistakes that lose points

- **Forgetting `synchronized` on `book`** — silent double-booking. The single biggest interview-flunk in this problem.
- **Putting `synchronized` on BookingSystem instead of Showtime** — blocks all bookings system-wide. Granularity should be per-Showtime.
- **Maintaining a separate `bookedSeats: Set<String>` alongside `bookings`** — two sources of truth → consistency bugs.
- **When cancellation is added: no back-reference on Booking** — cancel walks every theater × every showtime × every booking.
- **When cancellation is added: `bookingsById.put` BEFORE `showtime.book` succeeds** — orphan entries from every rejected booking.
- **A `cancel()` method on Booking** — Booking would mutate Showtime state from outside. Cancellation lives on Showtime.
- **Per-seat locking without sorted acquisition** — deadlocks.
- **Promoting Seat to a class without justification** — burns time; if you do it, name the reason (tiers, per-seat locks).

---

## Files in this folder

| File | Purpose |
|------|---------|
| `model/City.java` | Groups theaters — id + name + `List<Theater>` |
| `model/Movie.java` | Immutable — id + title |
| `model/Theater.java` | Named location — id + name + `List<Screen>`; two-phase setup |
| `model/Screen.java` | Physical screen inside a theater — id + name + `List<Showtime>` |
| `model/Showtime.java` | **The hot class** — synchronized `book`; bookings = single source of truth |
| `model/Booking.java` | Immutable — bookingId + seatIds |
| `BookingSystem.java` | Facade — one `showtimesById` index, 4 public methods |
| `BookingSystemDriver.java` | Scenarios including a real 50-thread race |

Run:
```bash
mvn -q compile exec:java \
  -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.movieticket.BookingSystemDriver
```
