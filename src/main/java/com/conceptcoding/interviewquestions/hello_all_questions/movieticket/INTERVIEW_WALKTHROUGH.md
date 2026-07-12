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
                              v
                        Reservation (returned to caller)

   (Cancellation is a Step-5 extension — see E0 below.)
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
   t=6                           throws IllegalStateException  ✓
```

> **The rule:** the entire *check → validate → add* sequence must be one atomic block. Splitting "first check, then add" is the textbook check-then-act race. This is what the interviewer is looking for.

---

## Step 1 — Requirements (~5 min)

### Clarifying dialogue

**You:** *"Users need to list showtimes — by movie title or by theater — then book seats. Right?"*
**Interviewer:** *"Yes."*
> One "list showtimes" concept, two entry points. Justifies Movie + Theater as entities.

**You:** *"Users book by picking specific seat ids — multiple in one booking, all-or-nothing if any is taken?"*
**Interviewer:** *"Yes, multi-seat atomic. If one seat is taken the whole booking fails."*
> Signals the atomic multi-seat check.

**You:** *"Concurrency — two users click the same seat at the same instant, exactly one wins?"*
**Interviewer:** *"Exactly one. This is the important requirement."*
> Signals `synchronized` on `Showtime.book` — the load-bearing decision.

**You:** *"For v1, is cancellation in scope? And I'll assume uniform seat layout across screens — no per-screen sizes, no seat tiers?"*
**Interviewer:** *"Focus on booking first — cancellation is a follow-up. Uniform layout is fine."*
> Both simplifications: cancellation → Step 5, uniform layout means no `Seat`/`Screen` class needed.

### Requirements to write down

```
IN SCOPE (all 3 test something the interviewer is grading)
1. List showtimes — by movie title or by theater.
2. Book multiple seats atomically — all-or-nothing if any seat is taken.
3. Concurrent bookings of the same seat: EXACTLY ONE succeeds.

ASSUMED (not requirements — simplifications we agreed on)
- Uniform seat layout (rows A-Z, seats 0-20) — no per-screen sizes.
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
- Theater         named location, owns showtimes
- Showtime        THE bookable unit — owns reservations + concurrency control
- Movie           searchable record — id + title
- Reservation     immutable — confirmationId + seatIds

NOT entities (string fields instead)
- Seat            no state, no rules      → just `String seatId`
- Screen          uniform layout          → just `String screenLabel`

Relationships
- BookingSystem owns    List<Theater>
- BookingSystem indexes showtimesById            (ONE index — needed for O(1) book)
- Theater       owns    List<Showtime>
- Showtime      back-refs Theater (for navigation)
- Showtime      refs    Movie
- Showtime      owns    List<Reservation>       ← SINGLE source of truth for seats
- Reservation   owns    List<String> seatIds
```

> **Note:** cancellation is a Step-5 extension. When it lands, Reservation grows a back-ref to Showtime and BookingSystem grows a `reservationsById` index for O(1) cancel-by-confirmation-id.

### Why no `Seat` class?

> *"Seat has no state we manage and no rules to enforce — it's an identifier, period. A string with built-in equality and hashing is the right choice. If per-seat behavior (tiers, per-seat locking, accessibility flags) comes up later we can promote it — but not before there's a reason."*

### Why no `Screen` class?

> *"Once we've agreed all screens share the same layout, Screen has no state or behavior — it's just a label. `String screenLabel` on Showtime is honest."*

### If the interviewer wants a deeper hierarchy (`City → Theater → Screen → Show → Seat`)

Some interviewers push for the BookMyShow-textbook hierarchy — every level a class, `Seat` owning `SeatStatus`. Push back with the concurrency argument:

> *"If Seat owns a `SeatStatus` field, we now have TWO sources of truth — the seat's status AND the reservations list. Every book / cancel has to keep both in sync — that's the exact bug we solved by making reservations the only source of truth. I'd add `City` if we need location-based search, and promote `Seat` to a class if we need tiers or per-seat locks. But neither adds signal at base level — they land in Step 5 when a requirement pushes for them."*

Compact map of what to add and when:

| Requirement that arrives | Add this class |
|--------------------------|----------------|
| "Search by city, not just title" | `City` — owns `List<Theater>`; `BookingSystem` becomes `Map<cityId, City>` |
| "Different seat tiers (Premium / Recliner)" | Promote `Seat` to a class — but keep availability derived, not on Seat |
| "Different screen layouts (IMAX 100 vs intimate 30)" | Promote `Screen` to a class with layout dimensions |
| "Per-seat locking under Marvel-opening-night contention" | Promote `Seat` with its own `ReentrantLock` (sorted acquisition) |

### Class diagram

```
                  +--------------------------------+
                  |        BookingSystem           |   ← orchestrator + facade
                  |  search / browse / book        |
                  +--------------------------------+
                       │
                       │  showtimesById  (one index — O(1) book)
                       v
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
          |  Movie  |  |  Reservation | ← confirmationId + seatIds
          +---------+  +--------------+
```

---

## Step 3 — Class design (~10 min)

### BookingSystem — state derived from requirements

| Requirement | State BookingSystem must own |
|-------------|------------------------------|
| Search + browse | `List<Theater> theaters` |
| Route `book` by showtime id | `Map<String, Showtime> showtimesById` |

> **Why only one index?** *"`book` needs O(1) resolution of a showtime id → keep `showtimesById`. Search just scans all showtimes and filters by title — O(S), and at interview scale (hundreds of showtimes) that's nothing. I'd only add a title index if search became a measured hot path. Fewer indexes = fewer invariants to keep in sync."*

### BookingSystem — public API (3 methods)

```java
public class BookingSystem {
    private final List<Theater>          theaters;
    private final Map<String, Showtime>  showtimesById;   // the only index

    public List<Showtime> searchMovies(String title);
    public List<Showtime> getShowtimesAtTheater(Theater theater);
    public Reservation    book(String showtimeId, List<String> seatIds);
}
```

### Showtime — THE hot class

| Requirement | State Showtime owns |
|-------------|--------------------|
| Track booked seats | `List<Reservation> reservations` — single source of truth |
| Show what's playing where/when | `Theater theater`, `Movie movie`, `LocalDateTime datetime`, `String screenLabel` |
| Concurrency | `synchronized` on `book` |

```java
public class Showtime {
    private final String id, screenLabel;
    private final Theater theater;
    private final Movie movie;
    private final LocalDateTime datetime;
    private final List<Reservation> reservations;    // single source of truth

    public boolean            isAvailable(String seatId);
    public List<String>       getAvailableSeats();
    public synchronized void  book(Reservation r);   // atomic check+store
}
```

### Reservation & Theater & Movie — thin data holders

```java
public class Reservation {                           // immutable
    private final String        confirmationId;
    private final List<String>  seatIds;
    // defensive copy in ctor, defensive copy out of getSeatIds
    // (Step 5 cancellation adds a back-ref to Showtime here)
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

> *"`book` lives on Showtime — Information Expert. Only Showtime knows about its reservations, so it owns the atomic mutation. BookingSystem doesn't reach into the list; it creates a Reservation and TELLS Showtime to store it. If cancellation lands in Step 5, `cancel` lives on Showtime for the same reason — never as a method on Reservation."*

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
            throw new IllegalStateException("Seat unavailable: " + seatId);
        }
    }
    reservations.add(reservation);
}
```

**Three callouts to deliver out loud while writing this:**

1. *"`synchronized` on `this` — the Showtime. Two threads booking DIFFERENT showtimes proceed in parallel; only same-showtime bookings serialize. Right granularity for typical traffic."*

2. *"All-or-nothing: I check EVERY requested seat before adding. If a 3-seat booking has one unavailable seat, the whole thing fails with no partial state. Caller doesn't clean up half-bookings."*

3. *"Two exception types: `IllegalArgumentException` for bad input (typo in seat id), `IllegalStateException` for race loss (someone else got the seat). Two distinct kinds of caller mistakes."*

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
    Reservation reservation = new Reservation(UUID.randomUUID().toString(), seatIds);
    showtime.book(reservation);                             // atomic — may throw
    return reservation;
}
```

> **Senior callout:** *"`showtime.book` runs under its lock. If it throws, the caller sees the exception and no state has changed anywhere. When cancellation is added (Step 5) we'll introduce a `reservationsById` map — and the order will matter then: put ONLY after the book succeeds so orphan entries can't leak in from rejected bookings."*

### 4.3 `searchMovies` — leverage the indexes

```java
public List<Showtime> searchMovies(String title) {
    if (title == null || title.isEmpty()) return new ArrayList<>();
    String query = title.toLowerCase();
    List<Showtime> results = new ArrayList<>();
    for (Showtime s : showtimesById.values()) {
        if (s.getMovie().getTitle().toLowerCase().contains(query)) {
            results.add(s);
        }
    }
    return results;
}
```

> *"One scan over showtimes, filter by title. Returns showtimes directly — not movies — so the UI doesn't need a follow-up call to ask 'where is each movie playing?' One round-trip, complete result."*

### 4.4 Dry-run — the 2-thread race (say this at the board)

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
      throw IllegalStateException("A5")
      exit lock with NO state change                                    ✓

Step 3: Back in BookingSystem.book:
   Thread A: returns Reservation A                                      ✓
   Thread B: exception propagates                                        ✓

Result:
   1 success, 1 rejection.  Showtime.reservations contains exactly ONE entry.
   ⇒ R6 satisfied: exactly one wins.
```

**Bonus test to mention:** *"The included driver actually runs 50 threads through a `CountDownLatch` racing for the same seat, then asserts `successes = 1, conflicts = 49`. Empirical proof the lock works."*

---

## Step 5 — Extensibility (~9 min)

### E0. "Add cancellation" (this is v1's most likely first follow-up)

**Three small additions to the base:**

1. Reservation grows a back-ref to its Showtime — so `cancel` routes without scanning.
2. Showtime gets a `synchronized cancel(reservation)` method.
3. BookingSystem gets a 4th index `reservationsById` and a public `cancelReservation(confirmationId)`.

```java
// On Reservation — add the back-ref
public class Reservation {
    private final String confirmationId;
    private final Showtime showtime;                // NEW: back-ref for O(1) routing
    private final List<String> seatIds;
    // updated ctor + getShowtime()
}

// On Showtime — one new synchronized method
public synchronized void cancel(Reservation reservation) {
    reservations.remove(reservation);               // frees seats automatically
}

// On BookingSystem — one new field, one new method, one extra put in book()
private final Map<String, Reservation> reservationsById = new HashMap<>();

public Reservation book(String showtimeId, List<String> seatIds) {
    // ... existing checks + Reservation creation ...
    showtime.book(reservation);
    reservationsById.put(reservation.getConfirmationId(), reservation);   // NEW
    return reservation;
}

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

**Say aloud (three callouts):**

1. *"The back-ref on Reservation makes cancel O(1) — without it, we'd walk every theater × every showtime × every reservation."*

2. *"Order matters in `book`: `showtime.book` runs FIRST under its lock. Only if it succeeds do we `put` into `reservationsById`. Reverse the order and every rejected booking leaks an orphan entry."*

3. *"Removing from `reservationsById` after successful cancel is what makes double-cancel throw automatically — the second call hits `null` and throws NoSuchElement. Same idempotency trick as ticket-removal in Parking Lot."*

---

### E1. "Temporary seat holds during checkout" (the highest-likelihood follow-up)

**Problem:** Real users pick seats, then spend 30–60s entering payment. Another user could grab the same seats meanwhile.

**Fix:** A third state — `HELD`. Two-phase flow: `holdSeats` reserves for ~5 min; `confirmHold` upgrades to a Reservation. A sweeper releases stale holds.

```java
class Showtime {
    private final List<Reservation> reservations;
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

    public synchronized void confirmHold(String holdId, Reservation r) {
        SeatHold hold = holds.get(holdId);
        if (hold == null) throw new NoSuchElementException("hold gone");
        if (LocalDateTime.now().isAfter(hold.expiresAt())) {
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

**Problem:** The index is built once in the constructor. Real theaters add showtimes continuously.

```java
public synchronized void addShowtime(Theater theater, Showtime showtime) {
    theater.addShowtime(showtime);
    showtimesById.put(showtime.getId(), showtime);      // keep the index in sync
}

public synchronized void removeShowtime(String showtimeId) {
    Showtime s = showtimesById.get(showtimeId);
    if (s == null) throw new NoSuchElementException("not found");
    if (!s.getReservations().isEmpty()) {
        throw new IllegalStateException("cancel existing reservations first");
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
| **Facade** | `BookingSystem` | *"Callers only touch BookingSystem — 3 methods hide the theaters, the showtime index, and the concurrency-protected Showtimes."* |
| **Information Expert** (GRASP) | `Showtime.book` lives on Showtime | *"Only Showtime knows about its reservations, so it owns the atomic mutation."* |
| **Tell, Don't Ask** | `BookingSystem` doesn't reach into `Showtime.reservations` | *"BookingSystem creates a Reservation and TELLS Showtime to store it. It never mutates the list from outside."* |
| **Single Source of Truth** | `Showtime.reservations` — availability is derived | *"One field. No separate bookedSeats set. No cross-field consistency to maintain."* |
| **Immutability** | `Movie`, `Reservation` | *"Immutable after construction; defensive copies of `seatIds` in both directions."* |
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
- Implements happy-path `book`; misses the `synchronized` keyword until prompted.
- May maintain both `reservations` list AND a `bookedSeats` set (two sources of truth).
- No dry-run of the race condition unless explicitly asked.

### Mid-level (SDE-2) — the target
- Reservations list as the single source of truth — no separate booked-seats set.
- `synchronized` on `Showtime.book` from the start; explains the check-then-act race unprompted.
- All-or-nothing multi-seat booking implemented correctly.
- Runs the 2-thread dry-run out loud.
- Names cancellation as an obvious Step-5 extension and can sketch what changes when it lands (Reservation back-ref, `reservationsById` index, order-matters in `book`).

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

Let `S` = total showtimes, `M` = movies, `R` = reservations on one showtime, `K` = seats per booking.

| Operation | Time | Notes |
|-----------|------|-------|
| Constructor (index build) | **O(S)** | One pass builds `showtimesById` |
| `searchMovies(title)` | **O(S)** | Scan all showtimes, filter by title + future |
| `getShowtimesAtTheater` | **O(showtimes at that theater)** | Single pass |
| `book(showtimeId, K seats)` | **O(K · R)** | `isAvailable` is O(R) per seat |
| `cancelReservation(id)` *(Step-5)* | **O(1)** hash lookup + **O(R)** list remove | Back-ref on Reservation makes lookup O(1) |

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

> *"Five classes: BookingSystem, Theater, Showtime, Reservation, Movie. Seat and Screen are strings — no state, no rules. BookingSystem is the facade — owns theaters plus one index, `showtimesById`, for O(1) booking. Search just scans showtimes and filters by title — fine at interview scale. The core architectural call is that `Showtime.reservations` is the SINGLE source of truth for seat state — availability is derived, no separate booked-set to keep in sync. `Showtime.book` is synchronized, so check-then-add is atomic — that's how exactly one of N concurrent bookings wins. Multi-seat bookings are all-or-nothing — one unavailable seat throws with no state change. Extensions: cancellation via a back-ref on Reservation plus a `reservationsById` index; seat holds under the same per-showtime lock; per-seat locking with sorted acquisition for opening-night-of-Marvel scale."*

---

## Top mistakes that lose points

- **Forgetting `synchronized` on `book`** — silent double-booking. The single biggest interview-flunk in this problem.
- **Putting `synchronized` on BookingSystem instead of Showtime** — blocks all bookings system-wide. Granularity should be per-Showtime.
- **Maintaining a separate `bookedSeats: Set<String>` alongside `reservations`** — two sources of truth → consistency bugs.
- **When cancellation is added: no back-reference on Reservation** — cancel walks every theater × every showtime × every reservation.
- **When cancellation is added: `reservationsById.put` BEFORE `showtime.book` succeeds** — orphan entries from every rejected booking.
- **A `cancel()` method on Reservation** — Reservation would mutate Showtime state from outside. Cancellation lives on Showtime.
- **Per-seat locking without sorted acquisition** — deadlocks.
- **Promoting Seat to a class without justification** — burns time; if you do it, name the reason (tiers, per-seat locks).

---

## Files in this folder

| File | Purpose |
|------|---------|
| `model/Movie.java` | Immutable — id + title |
| `model/Theater.java` | Named location; owns `List<Showtime>`; two-phase setup |
| `model/Showtime.java` | **The hot class** — synchronized `book`; reservations = single source of truth |
| `model/Reservation.java` | Immutable — confirmationId + seatIds |
| `BookingSystem.java` | Facade — one `showtimesById` index, 3 public methods |
| `BookingSystemDriver.java` | Scenarios including a real 50-thread race |

Run:
```bash
mvn -q compile exec:java \
  -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.movieticket.BookingSystemDriver
```
