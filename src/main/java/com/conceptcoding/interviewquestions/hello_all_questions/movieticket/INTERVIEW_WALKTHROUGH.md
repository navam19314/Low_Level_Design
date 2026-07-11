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
   Two entry points:
     (a) search by title           (b) browse by theater
              │                             │
              v                             v
     searchMovies("Inception")   getShowtimesAtTheater(amc)
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
                        Reservation
                              │
                              v
                  BookingSystem indexes it
                  (reservationsById for cancel routing)

   Cancel flow:
   confirmationId → reservationsById → Reservation → showtime back-ref → Showtime.cancel
```

### M2. The reservations list IS the seat state (single source of truth)

```
   Naive: Showtime tracks TWO things
              List<Reservation> reservations
              Set<String>       bookedSeats     ← BUG MAGNET (must stay in sync)

   Better: Showtime tracks ONE thing
              List<Reservation> reservations
              A seat is "available" iff no reservation.seatIds contains it.
              Availability is DERIVED, not stored.

                    +--------------------------+
                    |    Showtime.reservations |
                    +--------------------------+
                    | [ Reservation { A5,A6 } ]|
                    | [ Reservation { B1    } ]|
                    | [ Reservation { C10   } ]|
                    +--------------------------+
```

**Senior soundbite:** *"I'm not maintaining a separate `bookedSeats` set — the reservations list IS the seat state. Every booked seat appears in exactly one reservation; availability is derived. One mutable field, one source of truth."*

### M3. The check-then-act race + the fix (THE core signal for this problem)

```
   WITHOUT synchronization — two threads booking seat A5:

   t=0   Thread A                Thread B
   ────  ──────────              ──────────
   t=1   isAvailable(A5) → true
   t=2                           isAvailable(A5) → true   ← BOTH see it free
   t=3   reservations.add(rA)
   t=4                           reservations.add(rB)     ← DOUBLE-BOOKED!


   WITH synchronized(this) on Showtime — only one thread inside at a time:

   t=0   Thread A                Thread B
   ────  ──────────              ──────────
   t=1   enters lock             (waits)
   t=2   isAvailable → true
   t=3   add(rA)
   t=4   exits lock              enters lock
   t=5                           isAvailable(A5) → FALSE  ← sees A's mutation
   t=6                           throws SeatUnavailableException  ✓
```

> **The rule:** the entire *check → validate → add* sequence must be one atomic block. Splitting "first check, then add" is the textbook check-then-act race. This is what the interviewer is looking for.

---

## Step 1 — Requirements (~5 min)

### Clarifying dialogue

**You:** *"Users search by movie title AND browse a specific theater — both flows return showtimes. Right?"*
**Interviewer:** *"Yes, both."*

**You:** *"Are all screens the same layout, or variable? Any seat tiers (premium / recliner)?"*
**Interviewer:** *"Uniform layout across all screens for v1. All seats identical, no tiers."*
> Signals no `Seat` or `Screen` class needed. Layout is a constant.

**You:** *"When users book, they pick specific seat ids from a map — multiple in one booking?"*
**Interviewer:** *"Yes, multiple seats per booking. All-or-nothing if any seat is taken."*
> Signals the atomic multi-seat requirement.

**You:** *"Concurrency — if two users click the same seat at the same instant, exactly one wins?"*
**Interviewer:** *"Exactly one. This is the important requirement."*
> Signals `synchronized` on `Showtime.book` — the load-bearing decision.

**You:** *"Cancellation — cancel-by-confirmation-id, which frees those seats?"*
**Interviewer:** *"Yes. No rescheduling — cancel and rebook if needed."*

**You:** *"Out of scope for v1 — payment processing, pricing / tiers, UI, ranking in search?"*
**Interviewer:** *"Correct. Focus on the booking logic and concurrency."*

### Requirements to write down

```
IN SCOPE
1. Search movies by title (case-insensitive substring).
2. Browse showtimes at a given theater.
3. Uniform seat layout across all screens (e.g. rows A-Z, seats 0-20).
4. Book multiple seats atomically; returns a confirmation id.
5. Concurrent bookings of the same seat: EXACTLY ONE succeeds.
6. Cancel by confirmation id; releases those seats.
7. Filter out past showtimes from search/browse.

OUT OF SCOPE
- Payment processing (assume success)
- Seat tiers / variable pricing (Step 5)
- Variable seat layouts (Step 5)
- Rescheduling (cancel + rebook)
- UI / ranking in search
- Temporary seat holds during checkout (Step 5)
```

---

## Step 2 — Entities & relationships (~4 min)

```
Entities
- BookingSystem   orchestrator + facade — search, browse, book, cancel
- Theater         named location, owns showtimes
- Showtime        THE bookable unit — owns reservations + concurrency control
- Movie           searchable record — id + title
- Reservation     immutable — confirmationId + showtime back-ref + seatIds

NOT entities (string fields instead)
- Seat            no state, no rules      → just `String seatId`
- Screen          uniform layout          → just `String screenLabel`

Relationships
- BookingSystem owns    List<Theater>
- BookingSystem indexes moviesById, showtimesById, showtimesByMovieId, reservationsById
- Theater       owns    List<Showtime>
- Showtime      back-refs Theater (for navigation)
- Showtime      refs    Movie
- Showtime      owns    List<Reservation>       ← SINGLE source of truth for seats
- Reservation   back-refs Showtime              ← cancel routing without scanning
- Reservation   owns    List<String> seatIds
```

### Why no `Seat` class?

> *"Seat has no state we manage and no rules to enforce — it's an identifier, period. A string with built-in equality and hashing is the right choice. If per-seat behavior (tiers, per-seat locking, accessibility flags) comes up later we can promote it — but not before there's a reason."*

### Why no `Screen` class?

> *"Once we've agreed all screens share the same layout, Screen has no state or behavior — it's just a label. `String screenLabel` on Showtime is honest."*

### Class diagram

```
                  +--------------------------------+
                  |        BookingSystem           |   ← orchestrator + facade
                  |  search / browse / book /      |
                  |  cancel                        |
                  +--------------------------------+
                     │        │        │        │
                     │        indexes (4 lookup maps)
                     v        v        v        v
             moviesById  showtimesById  showtimesByMovieId  reservationsById
                     │
                owns │ List<Theater>
                     v
              +-----------+
              |  Theater  |    id, name, showtimes
              +-----------+
                     │  List<Showtime>
                     v
              +------------+
              |  Showtime  |    ← the lock-protected hot spot
              | reservations|
              +------------+
                  /       \
                 v         v
          +---------+  +--------------+
          |  Movie  |  |  Reservation | ← back-ref to Showtime + seatIds
          +---------+  +--------------+
```

---

## Step 3 — Class design (~10 min)

### BookingSystem — state derived from requirements

| Requirement | State BookingSystem must own |
|-------------|------------------------------|
| Search + browse | `List<Theater> theaters` |
| Search by title efficiently | `Map<String, Movie> moviesById` + `Map<String, List<Showtime>> showtimesByMovieId` |
| Route `book` by showtime id | `Map<String, Showtime> showtimesById` |
| Route `cancel` by confirmation id | `Map<String, Reservation> reservationsById` |
| Future-only filter | `Clock clock` (injected for testable time) |

> **Why 4 indexes?** *"Naive `searchMovies` walks `O(theaters × showtimes)` on every call. The indexes turn it into `O(matching movies × showtimes per movie)`. All four are built in ONE pass at construction."*

### BookingSystem — public API (4 methods)

```java
public class BookingSystem {
    private final List<Theater>               theaters;
    private final Map<String, Movie>          moviesById;
    private final Map<String, List<Showtime>> showtimesByMovieId;
    private final Map<String, Showtime>       showtimesById;
    private final Map<String, Reservation>    reservationsById;
    private final Clock                       clock;

    public List<Showtime> searchMovies(String title);
    public List<Showtime> getShowtimesAtTheater(Theater theater);
    public Reservation    book(String showtimeId, List<String> seatIds);
    public void           cancelReservation(String confirmationId);
}
```

### Showtime — THE hot class

| Requirement | State Showtime owns |
|-------------|--------------------|
| Track booked seats | `List<Reservation> reservations` — single source of truth |
| Show what's playing where/when | `Theater theater`, `Movie movie`, `Instant datetime`, `String screenLabel` |
| Concurrency | `synchronized` on `book` / `cancel` |

```java
public class Showtime {
    private final String id, screenLabel;
    private final Theater theater;
    private final Movie movie;
    private final Instant datetime;
    private final List<Reservation> reservations;    // single source of truth

    public boolean            isAvailable(String seatId);
    public List<String>       getAvailableSeats();
    public synchronized void  book(Reservation r);   // atomic check+store
    public synchronized void  cancel(Reservation r);
}
```

### Reservation & Theater & Movie — thin data holders

```java
public class Reservation {                           // immutable
    private final String        confirmationId;
    private final Showtime      showtime;             // back-ref for O(1) cancel routing
    private final List<String>  seatIds;
    // defensive copy in ctor, defensive copy out of getSeatIds
}

public class Theater {                               // named location + showtimes
    private final String id, name;
    private final List<Showtime> showtimes;
    public void addShowtime(Showtime s);              // two-phase setup (Showtime needs back-ref)
}

public class Movie {                                 // immutable record
    private final String id, title;
}
```

### The principle to say aloud

> *"`book` lives on Showtime — Information Expert. Only Showtime knows about its reservations, so it owns the atomic mutation. BookingSystem doesn't reach into the list; it creates a Reservation and TELLS Showtime to store it. Same for `cancel` — Reservation doesn't have a `cancel()` method (it would have to mutate Showtime's state from outside — Tell-Don't-Ask violated)."*

---

## Step 4 — Implementation + dry-run (~16 min)

### 4.1 `Showtime.book` — the atomic check-and-store (write this FIRST)

```java
public synchronized void book(Reservation reservation) {
    List<String> seatIds = reservation.getSeatIds();
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
            throw new SeatUnavailableException("Seat unavailable: " + seatId);
        }
    }
    reservations.add(reservation);
}
```

**Three callouts to deliver out loud while writing this:**

1. *"`synchronized` on `this` — the Showtime. Two threads booking DIFFERENT showtimes proceed in parallel; only same-showtime bookings serialize. Right granularity for typical traffic."*

2. *"All-or-nothing: I check EVERY requested seat before adding. If a 3-seat booking has one unavailable seat, the whole thing fails with no partial state. Caller doesn't clean up half-bookings."*

3. *"Three distinct exceptions: `IllegalArgumentException` for bad input, `SeatUnavailableException` for the domain-specific race loss. The caller can tell them apart."*

### 4.2 `BookingSystem.book` — orchestration

```java
public Reservation book(String showtimeId, List<String> seatIds) {
    if (showtimeId == null || seatIds == null || seatIds.isEmpty()) {
        throw new IllegalArgumentException("Invalid booking request");
    }
    Showtime showtime = showtimesById.get(showtimeId);
    if (showtime == null) {
        throw new NoSuchElementException("Showtime not found: " + showtimeId);
    }
    Reservation reservation = new Reservation(
            UUID.randomUUID().toString(), showtime, seatIds);

    showtime.book(reservation);                             // atomic — may throw

    // ONLY reached on success — never register a rejected reservation.
    reservationsById.put(reservation.getConfirmationId(), reservation);
    return reservation;
}
```

> **Senior callout:** *"Order matters — `showtime.book` runs FIRST under its lock. If it throws, the `put` to `reservationsById` never executes, so we don't leak orphan entries from rejected bookings."*

### 4.3 `cancelReservation` — follow the back-ref

```java
public void cancelReservation(String confirmationId) {
    if (confirmationId == null || confirmationId.isEmpty()) {
        throw new IllegalArgumentException("Invalid confirmation ID");
    }
    Reservation reservation = reservationsById.get(confirmationId);
    if (reservation == null) {
        throw new NoSuchElementException("Reservation not found");
    }
    reservation.getShowtime().cancel(reservation);   // back-ref → O(1) routing
    reservationsById.remove(confirmationId);
}
```

> *"The back-ref on Reservation is what makes cancel O(1) — without it, we'd walk every theater × every showtime × every reservation. One extra reference at booking time saves O(N) on every cancel."*

### 4.4 `searchMovies` — leverage the indexes

```java
public List<Showtime> searchMovies(String title) {
    if (title == null || title.isEmpty()) return new ArrayList<>();
    String searchLower = title.toLowerCase();
    Instant now = clock.instant();
    List<Showtime> results = new ArrayList<>();
    for (Movie movie : moviesById.values()) {
        if (!movie.getTitle().toLowerCase().contains(searchLower)) continue;
        for (Showtime s : showtimesByMovieId.getOrDefault(movie.getId(), List.of())) {
            if (s.getDatetime().isAfter(now)) results.add(s);
        }
    }
    return results;
}
```

> *"Returns showtimes directly — not movies — so the UI doesn't need a follow-up call to ask 'where is each movie playing?' One round-trip, complete result."*

### 4.5 Dry-run — the 2-thread race (say this at the board)

```
Setup: showtime "S1" for Inception at 7pm, all 546 seats free.

Thread A: bookingSystem.book("S1", ["A5"])
Thread B: bookingSystem.book("S1", ["A5"])     ← same seat!

Step 1: Both threads create Reservation objects (different confirmation ids).
        No state change yet — just object construction.

Step 2: Both call showtime.book(reservation). Java's monitor lets ONE inside.

   Thread A wins the lock:
      isAvailable("A5") → true    (reservations empty)
      reservations.add(reservationA)
      exit lock

   Thread B blocked; now acquires the lock:
      isAvailable("A5") → scans reservations, finds A5 → FALSE
      throw SeatUnavailableException("A5")
      exit lock with NO state change                                    ✓

Step 3: Back in BookingSystem.book:
   Thread A: reservationsById.put(A.id, A); return A                    ✓
   Thread B: exception propagates; the put is never reached             ✓

Result:
   1 success, 1 rejection, exactly one reservation stored in both maps.
   ⇒ R6 satisfied: exactly one wins.
```

**Bonus test to mention:** *"The included driver actually runs 50 threads through a `CountDownLatch` racing for the same seat, then asserts `successes = 1, conflicts = 49`. Empirical proof the lock works."*

---

## Step 5 — Extensibility (~9 min)

### E1. "Temporary seat holds during checkout" (the highest-likelihood follow-up)

**Problem:** Real users pick seats, then spend 30–60s entering payment. Another user could grab the same seats meanwhile.

**Fix:** A third state — `HELD`. Two-phase flow: `holdSeats` reserves for ~5 min; `confirmHold` upgrades to a Reservation. A sweeper releases stale holds.

```java
class Showtime {
    private final List<Reservation> reservations;
    private final Map<String, SeatHold> holds;       // NEW

    public synchronized String holdSeats(List<String> seatIds, Duration timeout) {
        for (String s : seatIds) {
            if (!isAvailable(s)) throw new SeatUnavailableException(s);
        }
        SeatHold hold = new SeatHold(UUID.randomUUID().toString(), seatIds,
                                     clock.instant().plus(timeout));
        holds.put(hold.id(), hold);
        return hold.id();
    }

    public synchronized void confirmHold(String holdId, Reservation r) {
        SeatHold hold = holds.get(holdId);
        if (hold == null) throw new NoSuchElementException("hold gone");
        if (clock.instant().isAfter(hold.expiresAt())) {
            holds.remove(holdId);
            throw new HoldExpiredException(holdId);
        }
        holds.remove(holdId);
        reservations.add(r);
    }

    // isAvailable now checks both reservations AND non-expired holds.
}
```

**Say aloud:** *"Fits cleanly under the existing per-showtime lock — holds and bookings serialize on the same monitor, no new race classes. Alternative is optimistic (let everyone book, refund losers) — fine for low contention, falls apart on opening night."*

### E2. "Dynamic add/remove of showtimes"

**Problem:** Indexes are built once in the constructor. Real theaters add showtimes continuously.

```java
public synchronized void addShowtime(Theater theater, Showtime showtime) {
    theater.addShowtime(showtime);
    indexShowtime(showtime);       // the same helper the ctor uses — DRY
}

public synchronized void removeShowtime(String showtimeId) {
    Showtime s = showtimesById.get(showtimeId);
    if (s == null) throw new NoSuchElementException("not found");
    if (!s.getReservations().isEmpty()) {
        throw new IllegalStateException("cancel existing reservations first");
    }
    showtimesById.remove(showtimeId);
    showtimesByMovieId.get(s.getMovie().getId()).remove(s);
    s.getTheater().getShowtimes().remove(s);
}
```

**Say aloud:** *"The senior signal is treating the 4 indexes as a derived invariant that must stay in sync with the source of truth. Extracting `indexShowtime` as a private helper means only one place knows how to add to all four — DRY across construction and dynamic add."*

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
            if (s.isBooked()) throw new SeatUnavailableException(s.id());
        }
        for (Seat s : sorted) s.markBooked(reservation);
    } finally {
        for (Seat s : sorted) s.unlock();
    }
}
```

**Say aloud:** *"Sorted lock acquisition is the deadlock fix — Thread A grabbing `[A5, B5]` and Thread B grabbing `[B5, A5]` would otherwise deadlock. Same pattern generalizes to bank transfers between two accounts."*

**Tradeoff to name:** *"More state (one lock per seat), more careful coding. Real benefit only at extreme contention. Profile before switching."*

### E4. Other one-liners

| Follow-up | Answer |
|-----------|--------|
| "Different seat tiers / pricing" | Promote `Seat` to a class with `tier`. Add `PricingStrategy` — `FlatRate`, `TierBased`, `DynamicSurge`. |
| "Notify user on booking / cancel" | Observer — BookingSystem publishes `BookingConfirmed` / `BookingCancelled`; SMS / Email / Push subscribe. |
| "Loyalty points / coupons" | Decorator on the pricing pipeline — `LoyaltyDiscount(BasePricing)`, stackable. |
| "Persist across restart" | Inject `ReservationRepository`; write on every book/cancel; replay on boot. |
| "Multi-region" | Facade — `MultiRegionBookingSystem` over `Map<region, BookingSystem>`, routes by region. |
| "Cleanup expired showtimes" | Background sweeper — drops showtimes whose `datetime` is in the past. |
| "Variable seat layouts (IMAX 100, intimate 30)" | `ScreenBuilder` OR pass layout dimensions into Showtime; uniform layout was the earlier simplification. |

---

## Design patterns in play (name these out loud in the interview)

### In the BASE design — mention in Step 2 or Step 3

| Pattern / Principle | Where it lives | One-line justification |
|---------------------|----------------|------------------------|
| **Facade** | `BookingSystem` | *"Callers only touch BookingSystem — 4 methods hide 5 collaborators + 4 lookup indexes."* |
| **Information Expert** (GRASP) | `Showtime.book` lives on Showtime | *"Only Showtime knows about its reservations, so it owns the atomic mutation."* |
| **Tell, Don't Ask** | `BookingSystem` doesn't reach into `Showtime.reservations` | *"BookingSystem creates a Reservation and TELLS Showtime to store it. It never mutates the list from outside."* |
| **Single Source of Truth** | `Showtime.reservations` — availability is derived | *"One field. No separate bookedSeats set. No cross-field consistency to maintain."* |
| **Immutability** | `Movie`, `Reservation` | *"Immutable after construction; defensive copies of `seatIds` in both directions."* |
| **Dependency Injection** | `Clock` injected into `BookingSystem` | *"Time is testable — advance the clock to check future-showtime filtering without sleeps."* |

**No Strategy / Factory / Builder in the base — deliberately.** *"The one-sentence test fails: search is locked to substring, pricing isn't variable, allocation is user-driven. Hardcode it; refactor if Step 5 asks."*

### Patterns for Step 5 extensibility — reach for these when the interviewer prompts

| Follow-up trigger | Pattern | The one-line move |
|-------------------|---------|-------------------|
| "Different seat tiers / pricing" | **Strategy** ⭐ | *"Promote `Seat` to a class. Add `PricingStrategy` — `FlatRate`, `TierBased`, `DynamicSurge` implement it."* |
| "Different search algorithms (fuzzy match, Elasticsearch)" | **Strategy** ⭐ | *"`SearchStrategy` interface — `SubstringSearch` (default), `FuzzySearch` (Levenshtein), `ElasticsearchAdapter`."* |
| "Notify on booking / cancellation" | **Observer** | *"BookingSystem publishes `BookingConfirmed` / `BookingCancelled`; SMS / Email / Push / Analytics subscribe independently."* |
| "Loyalty discounts / stackable coupons" | **Decorator** | *"Wrap the pricing pipeline — `LoyaltyDiscount(BasePricing)`, `CouponDiscount(LoyaltyDiscount(BasePricing))` — stackable at runtime."* |
| "Multiple kinds of theaters (IMAX, drive-in)" | **Factory** | *"`TheaterFactory.create(kind)` returns a preconfigured Theater with the right layout."* |
| "Persist across restart" | **Repository** | *"Inject `ReservationRepository`; write on every book/cancel; replay on boot to rebuild indexes."* |
| "Multi-region / multi-country" | **Facade + Composition** | *"`MultiRegionBookingSystem` is a Facade over `Map<region, BookingSystem>` that routes by region."* |
| "Variable seat layouts (IMAX 100 seats vs intimate 30 seats)" | **Builder** | *"`ScreenBuilder` with `.row('A', 20).row('B', 18)…` — each Screen has its own dimensions."* |

### Patterns to actively refuse if the interviewer baits you

- **Singleton on BookingSystem** — kills tests; DI a single instance via constructor instead.
- **State pattern for Reservation** (`PENDING / CONFIRMED / CANCELLED`) — overkill; an enum is fine unless per-state behavior actually diverges.
- **Composite over Theater → Showtime → Seat** — not a tree of uniform nodes; each level has a distinct contract.
- **Builder for the 1-arg `BookingSystem(theaters)` ctor** — academic noise.

### The rule to sound natural

1. **Cap at 2 patterns in the base design.** Movie Ticket lands on 1 (Facade). Anything else is Step 5.
2. **Always pair the pattern name with a concrete win.** *"Strategy — because pricing rules swap at runtime"* > *"I'd use Strategy."*
3. **Never volunteer a pattern without a requirement pressing on it.** Wait for the trigger.

---

## What is expected at each level

### Junior (SDE-1)
- Arrives at 5 entities with a nudge; may promote Seat / Screen to classes without justification.
- Implements happy-path `book` and `cancel`; misses the `synchronized` keyword until prompted.
- May maintain both `reservations` list AND a `bookedSeats` set (two sources of truth).
- No dry-run of the race condition unless explicitly asked.

### Mid-level (SDE-2) — the target
- Reservations list as the single source of truth — no separate booked-seats set.
- `synchronized` on `Showtime.book` from the start; explains the check-then-act race unprompted.
- All-or-nothing multi-seat booking implemented correctly.
- Back-reference on Reservation for O(1) cancel routing.
- Reservation created BEFORE `showtime.book`; `reservationsById.put` only after success.
- Runs the 2-thread dry-run out loud.

### Senior (SDE-3 / SDE-II)
- Everything mid-level, faster, plus proactive tradeoffs.
- Names granularity explicitly: *"Per-showtime lock is the right default; per-seat is a measured optimization."*
- Discusses the deadlock trap in per-seat locking (sorted acquisition) as a preventive design.
- Introduces seat holds as the "realistic checkout flow" pattern before being asked.
- 4 indexes built in one pass, treated as a derived invariant that dynamic ops must maintain — introduces `indexShowtime` helper to keep DRY.
- Would write / describes a `CountDownLatch`-based multi-threaded test for empirical concurrency verification.

---

## Interview deep-dives

### Complexity

Let `S` = total showtimes, `M` = movies, `R` = reservations on one showtime, `K` = seats per booking.

| Operation | Time | Notes |
|-----------|------|-------|
| Constructor (index build) | **O(S)** | One pass over all theaters + showtimes |
| `searchMovies(title)` | **O(M + matches × showtimes/movie)** | Walks movies map, then their showtimes |
| `getShowtimesAtTheater` | **O(showtimes at that theater)** | Single pass + future filter |
| `book(showtimeId, K seats)` | **O(K · R)** | `isAvailable` is O(R) per seat |
| `cancelReservation(id)` | **O(1)** hash lookup + **O(R)** list remove | Back-ref makes lookup O(1) |

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
                bookingSystem.book("S1", List.of("C10"));
                successes.incrementAndGet();
            } catch (SeatUnavailableException ignored) {}
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

> *"Five classes: BookingSystem, Theater, Showtime, Reservation, Movie. Seat and Screen are strings — no state, no rules. BookingSystem is the facade — owns theaters plus four lookup indexes built at construction (movies-by-id, showtimes-by-id, showtimes-by-movie-id, reservations-by-confirmation-id) so every routing operation is O(1). The core architectural call is that `Showtime.reservations` is the SINGLE source of truth for seat state — availability is derived. `Showtime.book` is synchronized, so check-then-add is atomic — that's how exactly one of N concurrent bookings wins. Multi-seat bookings are all-or-nothing — a single unavailable seat throws with no state change. Reservation has a back-ref to its Showtime so cancel is O(1). Extensions: seat holds under the same per-showtime lock, dynamic add/remove via a shared `indexShowtime` helper, per-seat locking with sorted acquisition for extreme scale."*

---

## Top mistakes that lose points

- **Forgetting `synchronized` on `book`** — silent double-booking. The single biggest interview-flunk in this problem.
- **Putting `synchronized` on BookingSystem instead of Showtime** — blocks all bookings system-wide. Granularity should be per-Showtime.
- **Maintaining a separate `bookedSeats: Set<String>` alongside `reservations`** — two sources of truth → consistency bugs.
- **No back-reference on Reservation** — cancel walks every theater × every showtime × every reservation.
- **Doing `reservationsById.put` BEFORE `showtime.book` succeeds** — orphan entries from every rejected booking.
- **A `cancel()` method on Reservation** — Reservation would mutate Showtime state from outside. Cancellation lives on Showtime.
- **Per-seat locking without sorted acquisition** — deadlocks.
- **Promoting Seat to a class without justification** — burns time; if you do it, name the reason (tiers, per-seat locks).

---

## Files in this folder

| File | Purpose |
|------|---------|
| `model/Movie.java` | Immutable — id + title |
| `model/Theater.java` | Named location; owns `List<Showtime>`; two-phase setup |
| `model/Showtime.java` | **The hot class** — synchronized book/cancel; reservations = single source of truth |
| `model/Reservation.java` | Immutable + back-ref to Showtime |
| `exception/SeatUnavailableException.java` | Domain exception distinct from IllegalArgument |
| `BookingSystem.java` | Facade — 4 indexes, injected Clock, 4 public methods |
| `BookingSystemDriver.java` | Scenarios including a real 50-thread race |

Run:
```bash
mvn -q compile exec:java \
  -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.movieticket.BookingSystemDriver
```
