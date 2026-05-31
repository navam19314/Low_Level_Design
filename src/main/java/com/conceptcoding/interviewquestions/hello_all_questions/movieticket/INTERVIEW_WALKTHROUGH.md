# Movie Ticket Booking — 45-min LLD Interview Walkthrough

**Target role:** SDE‑2 (Amazon, Adobe, Microsoft, Atlassian, etc.)
**Source method:** Hello Interview *Delivery Framework* applied to the *Movie Ticket Booking* problem breakdown.

> Movie Ticket Booking is the **canonical concurrency LLD problem** in Indian SDE‑2 onsites. The headline isn't class design (that's straightforward); it's the **check-then-act race condition** on `book()` and how you defend "exactly one succeeds" when two users grab the same seat. Get that right and you're senior.

---

## Time budget (45 min)

| Step | Activity                                                                  | Budget   | Cumulative |
| ---- | ------------------------------------------------------------------------- | -------- | ---------- |
| 1    | Requirements                                                              | ~5 min   | 5          |
| 2    | Entities & Relationships                                                  | ~4 min   | 9          |
| 3    | Class Design (the "reservations IS the seat state" insight)               | ~10 min  | 19         |
| 4    | Implementation (`book` synchronized + dry-run the race)                    | ~16 min  | 35         |
| 5    | Extensibility (seat holds, dynamic schedule, per-seat locking)             | ~9 min   | 44         |
| —    | Wrap & questions                                                          | ~1 min   | 45         |

Step 5 gets an extra minute here because seat-hold extensions are practically guaranteed as a follow-up at SDE‑2 level — that's where the "real-world checkout flow" conversation lives.

Watch the clock at minute **5** (Step 1 done), minute **19** (start coding), minute **35** (extensibility).

---

## Mental models — internalize these BEFORE you walk in

Three pictures unlock the implementation.

### M1. The booking flow (two entry points → one Showtime → atomic mutation)

```
   Two entry points into the system:
   ----------------------------------
            (a) search by title             (b) browse by theater
                  |                               |
                  v                               v
         searchMovies("Inception")     getShowtimesAtTheater(amc)
                  |                               |
                  +---------- both return --------+
                              List<Showtime>
                                    |
                                    v
                       user picks ONE Showtime
                                    |
                                    v
                +-----------------------------------+
                |       Showtime.book(...)          |   <-- the mutation point
                |   synchronized { check + add }    |       single mutation point
                +-----------------------------------+
                                    |
                              Reservation
                                    |
                                    v
                        BookingSystem indexes
                        reservationsById for cancel routing


   Cancel flow:
   confirmationId -> reservationsById -> Reservation -> showtime back-ref -> Showtime.cancel
```

### M2. The reservations list IS the seat state (single source of truth)

```
   Naive design:           One showtime tracks TWO things:
                              - List<Reservation>    (booking records)
                              - Set<String> booked   (which seats are taken)
                           Now every book/cancel has to keep both in sync. Bug magnet.

   Better design:          One showtime tracks ONE thing:
                              - List<Reservation>
                           Every booked seat is referenced by EXACTLY one reservation.
                           Seat A5 is "available"   iff   no reservation.seatIds contains "A5".
                           availability is DERIVED, not stored.

                                +--------------------------+
                                |    Showtime.reservations |
                                +--------------------------+
                                | [ Reservation { A5,A6 } ]|
                                | [ Reservation { B1    } ]|
                                | [ Reservation { C10   } ]|
                                +--------------------------+
                                          ^
                                          | book(r)      adds entry
                                          | cancel(r)    removes entry
                                          | isAvailable  scans entries
```

**Senior soundbite (memorize):** *"I'm not maintaining a separate `bookedSeats` set — the reservations list IS the seat state. Every booked seat appears in exactly one reservation; availability is derived from a scan. One mutable field, one source of truth, no cross-field consistency to worry about."*

### M3. The check-then-act race + the fix

```
   WITHOUT synchronization — two threads booking the same seat A5:

   t=0   Thread A          Thread B
   ----  -----------       -----------
   t=1   isAvailable(A5)
   t=2   -> true
   t=3                     isAvailable(A5)
   t=4                     -> true        <-- both saw it free
   t=5   reservations.add(rA)
   t=6                     reservations.add(rB)   <-- DOUBLE-BOOKED!


   WITH synchronized(this) on the Showtime — only one thread inside at a time:

   t=0   Thread A         Thread B
   ----  -----------      -----------
   t=1   enters lock      ...
   t=2   isAvailable -> true
   t=3   add(rA)
   t=4   exits lock       enters lock
   t=5                    isAvailable(A5) -> false  <-- sees A's mutation
   t=6                    throws SeatUnavailableException
                                                    ✓ R6: exactly one succeeds
```

> **The interview rule:** the entire check-and-store sequence must be one atomic block. Splitting it into "first check, then store" is the textbook check-then-act race. *This is the single biggest thing the interviewer is looking for in this problem.*

---

## STEP 1 — Requirements (~5 min)

### What to say out loud (opener)
> "BookMyShow-style problems hide a lot of behind the prompt — let me clarify scope and the concurrency expectations before designing."

### Probe the 4 themes

| Theme               | Question to ask                                                                                              |
| ------------------- | ------------------------------------------------------------------------------------------------------------ |
| Primary capabilities| "Search by title and browse by theater — both supported? User picks specific seats from a map, multiple per booking?" |
| Rules / completion  | "Standardized seat layout across all screens? Cancel-only, no rescheduling? All seats identical, no tiers?"  |
| Error handling      | "Invalid seat id, unknown showtime, unknown confirmation, taken seat — what exception each?"                 |
| Concurrency         | "Two users grab the same seat at the same time — exactly one wins? (R6 — this is THE requirement.)"          |
| Scope boundaries    | "Out: payment, seat tiers, rescheduling, UI, ranking. Confirm?"                                              |

### What to write on the board

```
Functional Requirements
1. Search movies by title (case-insensitive substring match).
2. Browse showtimes at a given theater.
3. Theaters have multiple screens; ALL screens share the same layout (rows A-Z, seats 0-20).
4. View available seats for a showtime; pick specific seat IDs.
5. Book multiple seats in one transaction; returns a confirmation id.
6. Concurrent bookings of the same seat: EXACTLY ONE succeeds.
7. Cancel by confirmation id; releases those seats.

Out of Scope
- Payment processing (assume success)
- Seat tiers / variable pricing
- Variable seat layouts
- Rescheduling (cancel + rebook instead)
- UI / rendering
- Ranking / fuzzy match in search
```

### Close the step
> "Does this match what you had in mind? The concurrency requirement is the load-bearing one — I'll handle it explicitly in Step 4."

---

## STEP 2 — Entities & Relationships (~4 min)

### What to say out loud
> "Five entities: **BookingSystem** (orchestrator + facade — owns theaters, provides search/browse, routes book/cancel), **Theater** (named location, owns its showtimes), **Showtime** (the bookable unit — owns reservations + concurrency control), **Movie** (searchable record), **Reservation** (immutable booking record with a back-reference to its Showtime for cancel routing). Seat is **not** a class — a string `'A5'` carries all the identity, no behavior, no state."

### Why no `Seat` class
> "Seat has no state we manage and no rules to enforce — it's an identifier, period. Promoting it to a class would buy us nothing and force us to either intern or `equals`-compare instances. A string with built-in equality and hashing is the right choice. *Some interviewers will push back on this — defend it by asking 'what would the Seat class actually do?' If they want per-seat behavior (locking, tiering, accessibility flags) we can promote it later.*"

### Why no `Screen` class either
> "Same logic. Once we agreed all screens share the same layout, Screen has no state or behavior — it's just a label. `String screenLabel` on Showtime is the honest representation."

### What to write on the board

```
Entities
- BookingSystem    (orchestrator + facade: search, browse, book, cancel)
- Theater          (named location, owns showtimes)
- Showtime         (bookable unit: reservations + synchronized book/cancel)
- Movie            (searchable record: id + title)
- Reservation      (immutable: confirmationId + showtime back-ref + seatIds)

NOT entities (string fields / value types instead)
- Seat             (no behavior, no state)              -> just `String seatId`
- Screen           (uniform layout)                     -> just `String screenLabel`

Relationships
- BookingSystem owns       List<Theater>
- BookingSystem indexes    moviesById, showtimesById, showtimesByMovieId, reservationsById
- Theater       owns       List<Showtime>
- Showtime      back-refs  Theater     (navigation: reservation -> showtime -> theater)
- Showtime      refs       Movie
- Showtime      owns       List<Reservation>            <-- SINGLE source of truth for seats
- Reservation   back-refs  Showtime    (cancel routing without scanning)
- Reservation   owns       List<String> seatIds
```

### Diagram — boxes and arrows

```
                  +--------------------------------+
                  |        BookingSystem           |   <- orchestrator + facade
                  |   search / book / cancel       |
                  +--------------------------------+
                          |        |        |        |
                          | indexes (4 lookup maps)  |
                          v        v        v        v
              moviesById       showtimesById     showtimesByMovieId    reservationsById
                                                              (populated on each book)
                          |
                     owns | List<Theater>
                          v
                    +------------+
                    |  Theater   |    name, showtimes
                    +------------+
                          |
                          | List<Showtime>
                          v
                    +------------+
                    |  Showtime  |    <-- the lock-protected hot spot
                    | reservations|
                    +------------+
                       /        \
                      v          v
              +-----------+  +-----------+
              | Movie     |  | Reservation|<-- back-ref to Showtime + seatIds
              +-----------+  +-----------+
```

---

## STEP 3 — Class Design (~10 min)

### Work top-down: BookingSystem → Theater → Showtime → Movie → Reservation.

### BookingSystem — state ↔ requirement table

| Requirement                              | State BookingSystem must own                                            |
| ---------------------------------------- | ----------------------------------------------------------------------- |
| Search movies + browse theaters          | `List<Theater> theaters`                                                |
| Search by title efficiently              | `Map<String, Movie> moviesById` + `Map<String, List<Showtime>> showtimesByMovieId` |
| Route `book` by showtime id              | `Map<String, Showtime> showtimesById`                                   |
| Route `cancel` by confirmation id        | `Map<String, Reservation> reservationsById`                             |
| Future-only filter on search/browse      | `Clock clock` (injected — makes tests deterministic)                    |

> **Why 4 indexes?** A naive `searchMovies` walks `O(theaters × showtimes)` on every call. The indexes turn that into `O(matching movies × showtimes per movie)` plus the lookup. Same idea for `book` (id → showtime) and `cancel` (id → reservation). All four are built in **one pass at construction**.

### BookingSystem — behavior table

| Need from requirements              | Method                                                |
| ----------------------------------- | ----------------------------------------------------- |
| Search by title                     | `List<Showtime> searchMovies(String title)`           |
| Browse by theater                   | `List<Showtime> getShowtimesAtTheater(Theater t)`     |
| Book seats                          | `Reservation book(String showtimeId, List<String> seats)` |
| Cancel by confirmation              | `void cancelReservation(String confirmationId)`       |

That's it — 4 public methods.

### Showtime — state ↔ requirement table  *(THE hot class)*

| Requirement                              | State Showtime must own                                       |
| ---------------------------------------- | ------------------------------------------------------------- |
| Track booked seats                       | `List<Reservation> reservations`  ← SINGLE source of truth     |
| Show what's playing where/when           | `Theater theater`, `Movie movie`, `Instant datetime`, `String screenLabel` |
| Concurrency: exactly one wins            | (handled with `synchronized` on `book`/`cancel`)              |

### Showtime — behavior table

| Need from requirements              | Method                                                                          |
| ----------------------------------- | ------------------------------------------------------------------------------- |
| Show available seats to UI          | `List<String> getAvailableSeats()` — layout minus booked                        |
| Cheap "is seat free?" check         | `boolean isAvailable(String seatId)`                                            |
| Book atomically                     | `synchronized void book(Reservation r)` — validate, check, store IN ONE BLOCK   |
| Cancel atomically                   | `synchronized void cancel(Reservation r)`                                       |

### Class outlines (write these on the board)

```java
public class BookingSystem {
    private final List<Theater> theaters;
    private final Map<String, Movie>          moviesById;
    private final Map<String, List<Showtime>> showtimesByMovieId;
    private final Map<String, Showtime>       showtimesById;
    private final Map<String, Reservation>    reservationsById;
    private final Clock                        clock;        // injected — testability

    public List<Showtime> searchMovies(String title)                    { /* Step 4 */ }
    public List<Showtime> getShowtimesAtTheater(Theater theater)        { /* Step 4 */ }
    public Reservation    book(String showtimeId, List<String> seatIds) { /* Step 4 */ }
    public void           cancelReservation(String confirmationId)      { /* Step 4 */ }
}

public class Showtime {
    private final String id, screenLabel;
    private final Theater theater;
    private final Movie movie;
    private final Instant datetime;
    private final List<Reservation> reservations;            // single source of truth

    public boolean      isAvailable(String seatId);
    public List<String> getAvailableSeats();                  // layout minus booked
    public synchronized void book(Reservation r);             // atomic check+store
    public synchronized void cancel(Reservation r);
}

public class Reservation {                                    // immutable record
    private final String confirmationId;
    private final Showtime showtime;                          // back-ref for cancel routing
    private final List<String> seatIds;
    // ctor takes defensive copy in, getters return defensive copies out
}

public class Theater {                                        // simple container
    private final String id, name;
    private final List<Showtime> showtimes;
    public void addShowtime(Showtime s);                      // two-phase setup (back-ref)
    public List<Showtime> getShowtimesForMovie(Movie movie);  // convenience filter
}

public class Movie {                                          // immutable record
    private final String id, title;
}
```

### Diagram — class cards

```
+-------------------------------------+   +------------------------------+   +-------------------+
|           BookingSystem              |   |          Showtime            |   |     Theater       |
+-------------------------------------+   +------------------------------+   +-------------------+
| - theaters: List<Theater>            |   | - id, screenLabel: String    |   | - id, name        |
| - moviesById, showtimesById,         |   | - theater: Theater           |   | - showtimes: List |
|   showtimesByMovieId,                |   | - movie: Movie               |   +-------------------+
|   reservationsById: Map<...>         |   | - datetime: Instant          |   | + getShowtimes    |
| - clock: Clock                       |   | - reservations: List<R>      |   | + addShowtime     |
+-------------------------------------+   +------------------------------+   | + getShowtimes    |
| + searchMovies(title): List<S>       |   | + isAvailable(seat): bool    |   |     ForMovie(m)   |
| + getShowtimesAtTheater(t): List<S>  |   | + getAvailableSeats(): List  |   +-------------------+
| + book(showtimeId, seats): Reserv.   |   | + synchronized book(r)       |
| + cancelReservation(confId)          |   | + synchronized cancel(r)     |
+-------------------------------------+   +------------------------------+

+---------------------------+   +-------------------+
|       Reservation         |   |       Movie       |
+---------------------------+   +-------------------+
| - confirmationId: String  |   | - id, title       |
| - showtime: Showtime  ----+   +-------------------+
|     (back-ref for cancel) |   | + getters         |
| - seatIds: List<String>   |   +-------------------+
+---------------------------+
| + getters (immutable)     |
+---------------------------+
```

### The principle to verbalize — Information Expert + Tell-Don't-Ask
> "`book` lives on `Showtime` because only Showtime knows about its reservations. `BookingSystem` doesn't reach in and modify the list — it creates a Reservation and *tells* Showtime to store it. Same for `cancel` — Reservation doesn't have a `cancel()` method on itself; it would have to mutate Showtime's state from the outside, which is exactly what Tell-Don't-Ask prevents. The state's owner does the work."

---

## STEP 4 — Implementation (~16 min)

### Open by asking
> "Real Java or pseudo-code? I'll do `Showtime.book` first — that's where the concurrency lives — then `BookingSystem.book` for the orchestration, then dry-run a 2-thread race."

### 4.1 `Showtime.book` — the atomic check-and-store

```java
// SYNCHRONIZED so the check-then-act is atomic. Without the lock, two threads
// could both pass isAvailable() for the same seat and both add their reservation
// — silent double-booking (R6 violated). All-or-nothing: a single unavailable
// seat aborts the whole booking with no state change.
public synchronized void book(Reservation reservation) {
    List<String> seatIds = reservation.getSeatIds();
    if (seatIds == null || seatIds.isEmpty()) {
        throw new IllegalArgumentException("Must select at least one seat");
    }
    // Fail-fast: validate seat-id format first (cheap, no state to undo).
    for (String seatId : seatIds) {
        if (!isValidSeatId(seatId)) {
            throw new IllegalArgumentException("Invalid seat: " + seatId);
        }
    }
    // Check ALL seats are available — all-or-nothing semantics.
    for (String seatId : seatIds) {
        if (!isAvailable(seatId)) {
            throw new SeatUnavailableException("Seat unavailable: " + seatId);
        }
    }
    reservations.add(reservation);
}
```

**Three callouts to deliver out loud while writing this:**

1. *"`synchronized` on the instance — `this` is the Showtime. Two threads booking different showtimes proceed in parallel; two threads booking the same showtime serialize. Right granularity for opening-night traffic? Probably. For Marvel-opening-night-scale you'd push down to per-seat locking — but that's a measured optimization, not a default."*

2. *"All-or-nothing: I check ALL requested seats before adding. If a 3-seat booking has even one unavailable seat, the whole thing fails — no partial state. Caller doesn't have to clean up half-bookings."*

3. *"Three distinct exceptions — `IllegalArgumentException` for malformed input, `SeatUnavailableException` for the domain-specific 'someone else got it first'. Callers can tell apart 'you typed wrong' from 'unlucky timing'."*

### 4.2 `BookingSystem.book` — the orchestration

```java
public Reservation book(String showtimeId, List<String> seatIds) {
    if (showtimeId == null || seatIds == null || seatIds.isEmpty()) {
        throw new IllegalArgumentException("Invalid booking request");
    }
    Showtime showtime = showtimesById.get(showtimeId);
    if (showtime == null) {
        throw new NoSuchElementException("Showtime not found: " + showtimeId);
    }
    // Create reservation up front — just a data object, no state change yet.
    Reservation reservation = new Reservation(
            UUID.randomUUID().toString(),
            showtime,
            seatIds);

    // Atomic check+store on Showtime; throws if any seat is unavailable.
    showtime.book(reservation);

    // ONLY reaches here on success — never register a reservation that didn't book.
    reservationsById.put(reservation.getConfirmationId(), reservation);
    return reservation;
}
```

> **Senior callout:** *"The order matters — `showtime.book` runs first under its lock. If it throws, the `put` to `reservationsById` never executes, so we don't leak rejected reservations into the routing index. If I'd done it in reverse, I'd have orphaned entries from every rejected booking."*

### 4.3 `BookingSystem.cancelReservation` — follow the back-ref

```java
public void cancelReservation(String confirmationId) {
    if (confirmationId == null || confirmationId.isEmpty()) {
        throw new IllegalArgumentException("Invalid confirmation ID");
    }
    Reservation reservation = reservationsById.get(confirmationId);
    if (reservation == null) {
        throw new NoSuchElementException("Reservation not found: " + confirmationId);
    }
    // Back-ref: avoid scanning every theater/showtime/reservation.
    reservation.getShowtime().cancel(reservation);
    reservationsById.remove(confirmationId);
}
```

> **Senior callout:** *"The back-reference on Reservation is what makes cancel O(1) — without it, I'd have to walk every theater × every showtime × every reservation looking for the confirmation id. Storing one extra reference at booking time saves O(N) on every cancel."*

### 4.4 `searchMovies` + `getShowtimesAtTheater` — leverage the indexes

```java
public List<Showtime> searchMovies(String title) {
    if (title == null || title.isEmpty()) return new ArrayList<>();
    String searchLower = title.toLowerCase();
    Instant now = clock.instant();
    List<Showtime> results = new ArrayList<>();
    for (Movie movie : moviesById.values()) {
        if (!movie.getTitle().toLowerCase().contains(searchLower)) continue;
        List<Showtime> forMovie = showtimesByMovieId.get(movie.getId());
        if (forMovie == null) continue;
        for (Showtime s : forMovie) {
            if (s.getDatetime().isAfter(now)) results.add(s);
        }
    }
    return results;
}
```

> **Senior callout:** *"Walks the movie index (a few hundred entries) instead of every showtime in the system. Returns showtimes directly — not movies — so the UI doesn't need an N+1 follow-up call to ask 'where is each movie playing?' One round-trip, complete actionable result."*

### 4.5 Verification — dry-run the 2-thread race

```
Setup: showtime "S1" for Inception at 7pm, all 546 seats free.

Thread A: bookingSystem.book("S1", ["A5"])
Thread B: bookingSystem.book("S1", ["A5"])     <-- same seat!

Step 1: Both threads create their own Reservation objects (different confirmation ids).
        At this point: NO state change in the system. Pure object construction.

Step 2: Both threads call showtime.book(reservation). Both enter the
        synchronized(this) block — but Java guarantees only ONE at a time.

   Assume Thread A wins the lock:
      isAvailable("A5") -> true    (reservations is still empty)
      reservations.add(reservationA)
      exit lock

   Thread B was blocked. Now it acquires the lock:
      isAvailable("A5") -> scans reservations, finds A5 in reservationA -> FALSE
      throw SeatUnavailableException("A5")
      exit lock with NO state change                                            ✓

Step 3: Back in BookingSystem.book:
   Thread A continues: reservationsById.put(A.confirmationId, A); return A    ✓
   Thread B's exception propagates; the `put` is never reached.                 ✓

Result:
   Thread A: returns a valid Reservation
   Thread B: receives SeatUnavailableException
   Showtime.reservations contains exactly ONE reservation (A's)
   reservationsById contains exactly ONE entry (A's)
   ⇒ R6 satisfied: exactly one succeeds.
```

> **Bonus** — the included driver actually exercises this with 50 real threads. After they all race for the same seat, the assertions print `successes = 1`, `conflicts = 49`, `unexpected = 0`. This is the test you should mention to the interviewer: *"I'd verify with a multi-threaded JUnit test where N threads `await()` a CountDownLatch then all race for the same seat — synchronized correctness shows up empirically as 1 success and N-1 SeatUnavailableExceptions."*

---

## STEP 5 — Extensibility (~9 min)

This is where seat-holds, dynamic schedule, and per-seat locking come up. Cover the **first two** in depth — they're the highest-likelihood follow-ups.

### 5.1 "Temporary seat holds during checkout" — the realistic flow

> **Problem in current design:** *"Right now booking is atomic but instantaneous. In reality, the user picks seats and then spends 30–60 seconds entering payment. During that window another user could grab the same seats and the first user fills the form only to get rejected at the end. Bad UX."*
>
> **Pattern as the fix:** *"Introduce a third state for seats: `HELD`. The flow becomes two-phase: `holdSeats` reserves them for ~5 min with a hold id; `confirmHold` upgrades the hold to a confirmed Reservation. An expiry sweeper releases stale holds. `isAvailable` now returns false if a seat is booked OR held."*
>
> **Alternative + tradeoff:** *"Alternative is optimistic — let everyone book, refund losers. Works for low contention; falls apart on opening night. Pessimistic holding scales to that traffic at the cost of a `holds` map + background cleanup."*

```java
class Showtime {
    private final List<Reservation> reservations;
    private final Map<String, SeatHold> holds;     // NEW: holdId -> SeatHold

    public synchronized String holdSeats(List<String> seatIds, Duration timeout) {
        for (String s : seatIds) if (!isAvailable(s))
            throw new SeatUnavailableException(s);
        SeatHold hold = new SeatHold(UUID.randomUUID().toString(), seatIds,
                                     clock.instant().plus(timeout));
        holds.put(hold.id(), hold);
        return hold.id();
    }

    public synchronized void confirmHold(String holdId, Reservation reservation) {
        SeatHold hold = holds.get(holdId);
        if (hold == null)                          throw new NoSuchElementException("hold gone");
        if (clock.instant().isAfter(hold.expiresAt())) {
            holds.remove(holdId);
            throw new HoldExpiredException(holdId);
        }
        holds.remove(holdId);
        reservations.add(reservation);
    }

    @Override public boolean isAvailable(String seatId) {
        // Seat is unavailable if booked OR held by a non-expired hold.
        for (Reservation r : reservations) if (r.getSeatIds().contains(seatId)) return false;
        Instant now = clock.instant();
        for (SeatHold h : holds.values())
            if (h.expiresAt().isAfter(now) && h.seatIds().contains(seatId)) return false;
        return true;
    }
}
```

> *"This fits cleanly into the existing synchronization model — holds and bookings both serialize on the same per-showtime lock, so no new race classes appear."*

### 5.2 "Dynamic add/remove of showtimes" — the index-invariant story

> **Problem in current design:** *"Indexes are built once in the constructor. Real theaters add showtimes constantly; we can't restart the system every time a new screening is scheduled."*
>
> **Pattern as the fix:** *"Add `addShowtime(theater, showtime)` to `BookingSystem` that updates ALL FOUR indexes the constructor populates: theaters' list, `moviesById`, `showtimesById`, `showtimesByMovieId`."*

```java
public synchronized void addShowtime(Theater theater, Showtime showtime) {
    theater.addShowtime(showtime);
    indexShowtime(showtime);    // the same helper the ctor uses — DRY
}

public synchronized void removeShowtime(String showtimeId) {
    Showtime s = showtimesById.get(showtimeId);
    if (s == null) throw new NoSuchElementException("not found");
    if (!s.getReservations().isEmpty())
        throw new IllegalStateException("cancel existing reservations first");
    // Un-index from all four.
    showtimesById.remove(showtimeId);
    Optional.ofNullable(showtimesByMovieId.get(s.getMovie().getId()))
            .ifPresent(list -> list.remove(s));
    s.getTheater().getShowtimes().remove(s);
}
```

> *"The senior signal here is treating the indexes as a derived invariant — they have to stay in sync with the source of truth (the theater→showtimes graph). Extracting `indexShowtime` as a private helper means there's only one place that knows how to add to all four — DRY across construction and dynamic add."*

### 5.3 Per-seat locking — when the simple lock isn't enough

> **Problem in current design:** *"Per-showtime locking is the right default, but on a Marvel-opening-night showing with 1000+ users all hammering the same showtime, every booking serializes on one lock — even when users want completely different seats."*
>
> **Pattern as the fix:** *"Promote Seat from a String to a class that owns a per-seat ReentrantLock. `book` acquires the per-seat locks in **sorted order** (avoids deadlock), validates availability, then atomically marks them booked."*
>
> **Tradeoffs:** *"More mutable state (one lock per seat). More careful coding (sorted lock acquisition). Real benefit only at extreme contention. I'd profile first."*

### 5.4 Other "what-if" answers

| Follow-up                                  | Answer                                                                                              |
| ------------------------------------------ | --------------------------------------------------------------------------------------------------- |
| "Different seat tiers / pricing"           | Promote Seat to a class with `tier`. Add `PricingStrategy` (Strategy pattern) — `FlatRate`, `TierBased`, `DynamicSurge`. |
| "Notify user when their booking succeeds"  | Observer — BookingSystem publishes `BookingConfirmed` / `BookingCancelled` events; SMS / Email / Push subscribe. |
| "Loyalty points / discount codes"          | Decorator on the pricing path — `LoyaltyDiscount(BasePricing)`, etc.                                |
| "Multi-currency / multi-region"            | Inject a `RegionConfig`; or `Factory` for region-specific BookingSystems.                            |
| "Persist across restart"                   | Inject a `ReservationRepository`; write on every book/cancel; replay on boot.                       |
| "Cleanup old expired showtimes"            | Background sweeper that drops showtimes whose `datetime` is in the past (in-memory only — real systems use a DB). |
| "Search across multiple regions"           | Facade — `MultiRegionBookingSystem` over `Map<region, BookingSystem>` routing by region.             |

---

## Design Patterns — Hello Interview's canonical 8

> **Hello Interview's stance:** *"Patterns arise from good design decisions, not the other way around. Most interview designs use zero to two patterns maximum."*
>
> **India-based interview note:** name patterns explicitly when they fit. Don't pre-bake.

### The 5-step timing rule

| Step                       | Use a pattern here?                                                                 |
| -------------------------- | ----------------------------------------------------------------------------------- |
| **1. Requirements**        | **Never.**                                                                          |
| **2. Entities**            | **Sometimes** — if a clear seam exists (declare the interface as an entity).        |
| **3. Class Design**        | **YES, when you can state the design pressure in one sentence.**                    |
| **4. Implementation**      | **No new patterns.**                                                                |
| **5. Extensibility**       | **YES — for additional patterns triggered by follow-up prompts.**                   |

> **Movie Ticket Booking specifically:** the one-sentence test fails for *all* patterns at base level — search is hardcoded "case-insensitive substring", pricing is "all seats identical", allocation is "user picks". So the base design has **no Strategy / Factory / Builder**, just clean OO with Facade. The patterns in the canonical 8 land **only** when extensibility prompts come up.

### Hello Interview's canonical 8 × interviewer trigger

| # | Pattern              | Category   | Trigger phrase                                                                | One-line response                                                                                       |
| - | -------------------- | ---------- | ------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------- |
| 1 | **Strategy** ⭐       | Behavioral | "different rules" · "variants" · "swap at runtime"                              | *"Promote X to an interface; inject the concrete implementation."*                                       |
| 2 | **Observer**         | Behavioral | "notify multiple" · "broadcast" · "event"                                       | *"X publishes events; subscribers register independently."*                                              |
| 3 | **State Machine**    | Behavioral | "behavior depends on state" · "complex transitions"                             | *"Each state is its own class with its own transitions."*                                                |
| 4 | **Factory** (Method) | Creational | "support different types" · "multiple variants"                                 | *"Centralize creation behind a method."*                                                                  |
| 5 | **Builder**          | Creational | "many optional fields"                                                          | *"Builder collects fields incrementally; `build()` validates."*                                          |
| 6 | **Singleton**        | Creational | "exactly one"                                                                    | *"I'd resist textbook Singleton — DI a single instance instead."*                                         |
| 7 | **Decorator**        | Structural | "optional features" · "stack behaviors"                                          | *"Wrap X in decorators, each adding one concern."*                                                       |
| 8 | **Facade**           | Structural | "hide complexity" · "single entry point"                                         | *"Orchestrators usually ARE facades."*                                                                    |

### Three rules to sound natural

1. **Cap at 2 patterns total** in one interview.
2. **Always name the concrete win in the same breath.**
3. **Never volunteer a pattern without a trigger.**

### How this maps to Movie Ticket Booking specifically

**Naturally present in the BASE design — call out by principle and the one pattern:**

- **Facade (#8)** — `BookingSystem` IS a facade over `List<Theater>` + 4 routing maps + concurrency-protected Showtimes. Name it once in Step 2.
- **Information Expert** (GRASP principle) — `book` lives on Showtime because only Showtime knows about its reservations.
- **Tell, Don't Ask** (principle) — BookingSystem creates a Reservation and *tells* Showtime to store it; never reaches into the reservations list.
- **Dependency Injection** (principle) — `Clock` injected for testable "future showtime" filtering.
- **Immutability** (principle) — Reservation and Movie are immutable; defensive copies on `seatIds` both directions.

> **Why no Strategy in the base?** The one-sentence test fails: search is locked to substring match, pricing isn't variable, allocation is user-driven. *Hardcode it; refactor in Step 5 if asked.*

**Reach for these on the matching Step-5 follow-up — use 3-beat phrasing (problem → pattern → tradeoff):**

| Follow-up                                  | Pattern (HI's 8)             | Your line                                                                                            |
| ------------------------------------------ | ---------------------------- | ---------------------------------------------------------------------------------------------------- |
| "Different seat tiers / pricing"           | **Strategy (#1)** ⭐         | *"Currently flat-rate / no pricing. To support tiers + surge, promote `Seat` to a class and add `PricingStrategy` interface; `FlatRate`, `TierBased`, `DynamicSurge` implement it. Inject at BookingSystem construction."* |
| "Different search algorithms"              | **Strategy (#1)** ⭐         | *"Currently `searchMovies` hardcodes substring match. `SearchStrategy` interface — `SubstringSearch` (default), `FuzzySearch` (Levenshtein), `ElasticsearchAdapter`."* |
| "Notify on booking / cancellation"         | **Observer (#2)**            | *"BookingSystem publishes `BookingConfirmed` / `BookingCancelled` events; SMS, Email, Push, Analytics subscribe independently."* |
| "Multiple kinds of theaters (IMAX, drive-in)" | **Factory (#4)**          | *"`TheaterFactory.create(TheaterKind)` returns the right preconfigured Theater with the appropriate screen layout — pairs with the existing layout extension."* |
| "Loyalty discounts on price"               | **Decorator (#7)**           | *"Wrap the pricing pipeline — `LoyaltyDiscount(BasePricing)`, `CouponDiscount(LoyaltyDiscount(BasePricing))` — stackable at runtime."* |
| "Multi-region / multi-currency"            | **Facade (#8)** + composition | *"`MultiRegionBookingSystem` is a Facade over `Map<region, BookingSystem>` that routes by region."* |
| "Variable seat layouts (IMAX 100 seats, intimate 30 seats)" | (Builder #5)| *"`ScreenBuilder` with `.row('A', 20).row('B', 18)…` lets each Screen have its own dimensions. Only worth it once layouts truly vary — uniform layout doesn't need it."* |

**Patterns to actively refuse if interviewer baits you:**

- **Singleton on BookingSystem** — kills tests; just DI a single instance.
- **Composite for Theater→Showtime→Seat** — not a tree of uniform nodes; each level has a distinct contract.
- **State pattern for Reservation** ("PENDING / CONFIRMED / CANCELLED") — overkill until per-state behavior actually diverges; an enum is fine.
- **Builder for the 1-arg `BookingSystem(theaters)`** — academic noise.

### One sentence to say at the end of Step 3

> *"The base design names two patterns out loud: Facade (BookingSystem orchestrates 5 collaborators behind 4 public methods) and the Information-Expert principle (book lives on Showtime). No Strategy yet — search, pricing, and allocation are all single-policy in the requirements. If extensibility brings pricing tiers, fuzzy search, or notifications, those will earn their own patterns."*

---

## Interview deep-dives — the questions you'll definitely get asked

### 1. Complexity (Big-O)

Let `T` = theaters, `S` = total showtimes, `R` = reservations on one showtime, `M` = movies, `K` = seats per booking.

| Operation                                | Time                                              | Space     | Notes                                                                              |
| ---------------------------------------- | ------------------------------------------------- | --------- | ---------------------------------------------------------------------------------- |
| Constructor (index build)                | **`O(S)`**                                        | `O(S + M)`| One pass over all theaters + showtimes                                             |
| `searchMovies(title)`                    | **`O(M + matching·S_perMovie)`**                  | O(results)| Walks small movie map, then their showtimes. Way better than naïve O(T × S).        |
| `getShowtimesAtTheater(t)`               | **`O(S_t)`** where `S_t` = showtimes at theater   | O(results)| Single pass + future filter                                                        |
| `book(showtimeId, seats)`                | **`O(K·R)`** scan-per-seat for availability check | O(1)      | Could be O(K) with a per-showtime `bookedSeats: Set<String>` (space-time tradeoff) |
| `Showtime.isAvailable(s)`                | **`O(R)`** (linear scan of reservations)          | O(1)      | Trivial to make O(1) with a derived set — call out the option                      |
| `Showtime.getAvailableSeats()`           | **`O(R + 546)`** — build booked set, scan layout   | O(546)    | Called once per page load; 546 is the constant 26 × 21 seat layout                 |
| `cancelReservation(confId)`              | **`O(1)`** lookup + `O(R)` list remove            | O(1)      | Hash lookup + ArrayList remove                                                     |

> **Senior callout:** *"`book` is `O(K·R)` because `isAvailable` is `O(R)`. If R grows large or K grows large, I'd maintain a derived `bookedSeats` `Set<String>` on Showtime — `O(K)` book and `O(1)` availability — at the cost of one extra mutable field to keep in sync. Tradeoff: more state, more places to introduce bugs. At realistic showtime scale (few hundred reservations per showtime), the scan is fine."*

### 2. Concurrency / thread-safety — the full story

Already covered in Step 5.3 — the three-level menu:

| Approach                       | When to use                              | Cost                                                                |
| ------------------------------ | ---------------------------------------- | ------------------------------------------------------------------- |
| `synchronized` method per Showtime | **Default.** Correct, simple, low contention | Two threads booking the same showtime serialize even on different seats |
| Per-seat lock (sorted acquisition) | High contention on popular showtimes | More state; must acquire locks in sorted order to avoid deadlock     |
| Optimistic CAS on a seat-status field | Extreme throughput, low conflict | Retry storm under hot contention; sorted ordering still needed       |

> **The deadlock trap with per-seat locking:** if you don't sort the seat ids before acquiring locks, Thread A grabbing `[A5, B5]` and Thread B grabbing `[B5, A5]` deadlock each other. Sorted lock acquisition is the canonical fix. Same idea generalizes to multi-folder operations in File System — see the lock-ordering pattern there.

### 3. Testing — what to write tests for

The injected `Clock` makes time deterministic. The synchronized model makes concurrency *testable* without flaky retries.

| Test category                | Cases to cover                                                                                              |
| ---------------------------- | ----------------------------------------------------------------------------------------------------------- |
| Happy path                   | Build catalog → search → browse → book → cancel → rebook same seat                                          |
| Past-showtime filter         | Advance Clock past a showtime; search/browse no longer return it                                            |
| Atomic multi-seat booking    | Book [A5, A6, A7] where A6 is taken → throws, A5 and A7 NOT booked                                          |
| All distinct exception types | Invalid input → IllegalArgument; unknown id → NoSuchElement; taken seat → SeatUnavailable                   |
| Cancel idempotency           | Cancel by id → then cancel same id again → throws NoSuchElement (already removed)                           |
| **Concurrency: exactly one** | 50 threads race for the same seat using CountDownLatch — assert 1 success + 49 conflicts + 0 unexpected     |

```java
@Test
void fifty_threads_race_for_one_seat_exactly_one_wins() throws Exception {
    BookingSystem fs = setupOneShowtime();
    int N = 50;
    ExecutorService pool = Executors.newFixedThreadPool(N);
    CountDownLatch ready = new CountDownLatch(N);
    CountDownLatch fire = new CountDownLatch(1);
    AtomicInteger successes = new AtomicInteger();
    AtomicInteger conflicts = new AtomicInteger();

    for (int i = 0; i < N; i++) {
        pool.submit(() -> {
            ready.countDown();
            try {
                fire.await();
                fs.book("S1", List.of("C10"));
                successes.incrementAndGet();
            } catch (SeatUnavailableException e) {
                conflicts.incrementAndGet();
            }
        });
    }
    ready.await();
    fire.countDown();        // all threads race simultaneously
    pool.shutdown();
    pool.awaitTermination(5, TimeUnit.SECONDS);

    assertEquals(1, successes.get());
    assertEquals(N - 1, conflicts.get());
}
```

> **Senior callout:** *"This is one of the few tests in LLD interviews that's actually multi-threaded. The CountDownLatch pattern (`ready` for barrier, `fire` for the GO signal) maximizes the chance of true contention rather than serialized execution. The driver in this folder actually runs it — 50 threads, 1 success, 49 conflicts, 0 unexpected."*

### 4. SOLID mapping

| Letter                       | Where it shows up                                                                                          |
| ---------------------------- | ---------------------------------------------------------------------------------------------------------- |
| **S** Single Responsibility  | BookingSystem = orchestration + routing. Showtime = seat state + concurrency. Reservation = data record. Theater = container. Movie = identity. Five reasons to change → five classes. |
| **O** Open/Closed            | Adding `PricingStrategy` later doesn't touch Showtime's `book`. Adding observer subscribers doesn't touch BookingSystem's `book`. Adding holds adds a new field on Showtime without changing the `book`/`cancel` contract. |
| **L** Liskov Substitution    | Currently no polymorphism in the base. If we extracted `SearchStrategy` or `PricingStrategy`, all impls must honor the contract — same input shape, same exception types. |
| **I** Interface Segregation  | BookingSystem exposes 4 narrow methods, not one fat `query()`. Showtime's mutators (`book`, `cancel`) are separate from its readers (`isAvailable`, `getAvailableSeats`) — caller asks for what they need. |
| **D** Dependency Inversion   | BookingSystem depends on `Clock` (abstraction), not `Instant.now()`. When `ReservationRepository` lands, BookingSystem depends on the interface, not concrete persistence. |

### 5. "Summarize your design in 30 seconds"

> *"Five classes: BookingSystem, Theater, Showtime, Reservation, Movie. Seat is a string, Screen is a string field — neither has state or behavior. BookingSystem is the facade — it owns theaters and four lookup indexes built at construction (movies-by-id, showtimes-by-id, showtimes-by-movie-id, reservations-by-confirmation-id) so every routing operation is O(1). The core architectural call is that `Showtime.reservations` is the SINGLE source of truth for seat state — every booked seat appears in exactly one reservation, availability is derived. `Showtime.book` and `Showtime.cancel` are synchronized, so the check-then-act sequence is atomic — that's how exactly one of N concurrent bookings for the same seat wins. Multi-seat bookings are all-or-nothing — a single unavailable seat throws and leaves no state change. Reservation has a back-reference to its Showtime so cancel-by-confirmation-id is O(1) — no scanning. Extensions: temporary seat holds with the same per-showtime lock; dynamic add/remove of showtimes by updating all four indexes; per-seat locking with sorted acquisition for opening-night-of-Marvel-scale traffic."*

That's ~50 seconds. Hits: structure, the indexes-at-construction call, the "reservations IS the seat state" architectural insight, the synchronized atomic check-then-act (R6), all-or-nothing multi-seat, the back-reference trick, and the headline extensions.

---

## Closing soundbites (memorize these)

- **Opening:** *"BookMyShow-style problems hide a lot behind the prompt — let me clarify scope and concurrency before designing."*
- **Why no Seat / Screen class:** *"Neither has state or rules to enforce — string ids carry all the identity. If per-seat behavior comes up later, we promote then."*
- **Why reservations are the source of truth:** *"Every booked seat appears in exactly ONE reservation. Availability is derived from a scan — no separate `bookedSeats` set to keep in sync, no cross-field consistency bugs."*
- **The synchronized rationale:** *"The check-then-act sequence must be atomic. Without the lock, two threads both pass `isAvailable` for the same seat and both add their reservation — silent double-booking. Per-showtime lock is the right default."*
- **All-or-nothing multi-seat:** *"I check ALL seats before adding. If one is taken, the whole booking fails with no partial state. Caller never sees half-bookings."*
- **The back-reference:** *"Reservation knows its Showtime so cancel-by-confirmation-id is O(1). Without the back-ref, every cancel walks the whole tree."*
- **On the per-seat-locking question:** *"Per-showtime is the right default — short critical sections, no deadlock. Per-seat scales further but adds sorted-lock-acquisition complexity. I'd profile before switching."*
- **On extensibility:** *"Seat holds fit cleanly under the existing per-showtime lock. Dynamic showtime add/remove updates the same four indexes the constructor builds — DRY via a single `indexShowtime` helper."*

---

## Top mistakes that lose points

- **Promoting Seat to a class without justification** — burns time; you'd need a real reason like per-seat tiers or per-seat locking. (You CAN promote it — just justify why.)
- **Maintaining a separate `bookedSeats: Set<String>` alongside `reservations`** — two sources of truth → consistency bugs on every book/cancel.
- **Forgetting `synchronized` on `book`** — silent double-booking. The single biggest interview-flunk in this problem.
- **Putting the synchronized OUTSIDE Showtime (on BookingSystem)** — blocks all bookings system-wide. Granularity should be per-Showtime.
- **`cancel()` method on Reservation** — Reservation would have to mutate Showtime's state from outside. Cancellation lives on Showtime.
- **No back-reference on Reservation** — every cancel walks every theater → every showtime → every reservation. O(N) on every call.
- **Building indexes lazily ("just-in-time on first search")** — first user pays O(S); also harder to reason about race-with-add. Build once in the constructor, mutate alongside `addShowtime`.
- **Per-seat locking without sorted acquisition** — deadlocks. Per-seat is fine, but commit to the ordering rule.
- **Doing `reservationsById.put` BEFORE `showtime.book` succeeds** — orphan entries from rejected bookings.
- **Treating "user picks seats" as a Strategy in the base** — there's no strategy to pick; the user told us the seats. Save Strategy for actual variation (tiers, pricing, search).
- **Skipping the dry run** — the 50-thread race is exactly the kind of thing the interviewer wants to see you mentally trace.

---

## Files in this folder (your reference implementation)

| File                                       | What it shows                                                                            |
| ------------------------------------------ | ---------------------------------------------------------------------------------------- |
| `model/Movie.java`                         | Immutable identity (id + title)                                                          |
| `model/Theater.java`                       | Named location; owns its `List<Showtime>`; two-phase setup via `addShowtime`             |
| `model/Showtime.java`                      | **The hot class.** Synchronized `book`/`cancel`; reservations is the single source of truth |
| `model/Reservation.java`                   | Immutable record + back-reference to Showtime (cancel routing)                           |
| `exception/SeatUnavailableException.java`  | Domain-specific exception — distinguishes "taken" from "invalid input"                  |
| `BookingSystem.java`                       | Orchestrator + facade — 4 indexes, injected Clock, 4 public methods                      |
| `BookingSystemDriver.java`                 | Scenario harness — happy path / atomic-failure / cancel-rebook / **50-thread race**      |

Run from the project root:

```bash
mvn -q compile exec:java \
  -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.movieticket.BookingSystemDriver
```
