# Meeting Scheduler — 45-min LLD Interview Walkthrough

**Target role:** SDE‑2 (Amazon, Adobe, Microsoft, Atlassian, etc.)

> Meeting Scheduler is the **canonical "interval-conflict detection + room allocation Strategy" archetype**. Three signals separate senior from mid: (a) **half-open intervals [start, end)** so adjacent meetings (10–11, 11–12) are NOT conflicts, (b) **O(log n) conflict check** using `TreeMap.floorEntry/ceilingEntry` instead of an O(n) linear scan, (c) **per-room synchronization** so concurrent bookings on different rooms proceed in parallel while same-room bookings serialize.

---

## Time budget (45 min)

| Step | Activity                                                                                | Budget   | Cumulative |
| ---- | --------------------------------------------------------------------------------------- | -------- | ---------- |
| 1    | Requirements                                                                            | ~5 min   | 5          |
| 2    | Entities & Relationships                                                                | ~4 min   | 9          |
| 3    | Class Design (Strategy + per-room TreeMap)                                              | ~10 min  | 19         |
| 4    | Implementation (`bookMeeting` w/ floor/ceiling conflict check + dry-run)                | ~17 min  | 36         |
| 5    | Extensibility (recurring meetings, attendee conflicts, multi-room booking, amenities)   | ~8 min   | 44         |
| —    | Wrap & questions                                                                        | ~1 min   | 45         |

Step 4 is the longest — the floor/ceiling conflict-check trick is THE signal.

Watch the clock at minute **5** (Step 1 done), minute **19** (start coding), minute **36** (extensibility).

---

## Mental models — internalize these BEFORE you walk in

### M1. Half-open intervals + the overlap formula

```
   Two intervals [a, b) and [c, d) overlap  iff  a < d  AND  c < b
                                                ^^^^^      ^^^^^
                                                strict less-than (NOT ≤)


   Half-open semantics — adjacency is NOT overlap:
   ------------------------------------------------

     Meeting A: 10:00 ---------- 11:00          (ends at 11:00)
     Meeting B:                  11:00 -------- 12:00   (starts at 11:00)

     overlap? A.start (10:00) < B.end (12:00) ✓
              B.start (11:00) < A.end (11:00) ✗   ← B.start == A.end → NOT strict less-than

     ⇒ NO overlap. The meetings are ADJACENT — back-to-back is fine.

   Closed-interval [a, b] semantics would treat 10–11 and 11–12 as OVERLAPPING
   (both contain the point 11:00). That's wrong for meetings — there's no
   conflict at the instant one ends and the other begins.
```

**Senior soundbite (memorize):** *"I use HALF-OPEN intervals [start, end) — adjacent meetings (one ending exactly when the next begins) are NOT conflicts. The overlap rule is `a.start < b.end && b.start < a.end` — strict less-than on both sides. Closed-interval semantics would falsely conflict every back-to-back meeting."*

### M2. Conflict check in O(log n) via TreeMap floor/ceiling

```
   For ONE room, keep meetings in a TreeMap<Instant, Meeting> keyed by START time.

   Inserting [start, end). Two existing meetings could overlap:
     - the meeting that started AT or BEFORE our start          → floorEntry(start)
     - the meeting that starts AT or AFTER  our start           → ceilingEntry(start)
   Any OTHER existing meeting cannot overlap (verify by case analysis).

   Floor check:
     if floor != null && floor.end > start  →  CONFLICT
       (floor's interval extends INTO our start)

   Ceiling check:
     if ceiling != null && ceiling.start < end  →  CONFLICT
       (ceiling's interval starts BEFORE our end)

   Each check is O(log n) — TreeMap is a red-black tree.

   Why no other entry can overlap:
     - Any meeting starting before floor's start ends BEFORE floor's start, which
       is ≤ our start, so it ends BEFORE our start → no overlap.
     - Any meeting starting after ceiling's start has start ≥ ceiling.start, so
       it starts after our window's right edge if ceiling doesn't overlap.

   Picture:
                            our window
                            [start, end)
     ──────────────────────[──────────)─────────────────────────
        ↑                       ↑              ↑
        floor (started before)  start         ceiling (next or equal start)

   The TWO neighbors are the ONLY potential conflicts. O(log n) instead of O(n).
```

### M3. Per-room synchronization — different rooms run in parallel

```
   The booking method's critical section per room:

     synchronized (calendar) {        // calendar is the TreeMap for this room
         if (hasConflict(calendar, start, end)) continue;
         calendar.put(start, meeting);
         meetingsById.put(meeting.id(), meeting);
         return meeting;
     }

   Lock granularity: ONE lock per room (we synchronize on the TreeMap object
   itself, but it could equivalently be the Room object).

   Two threads booking the SAME room serialize → exactly one wins.
   Two threads booking DIFFERENT rooms run in parallel → no contention.

   Compare to single global lock:
     synchronized(this) { ... } in MeetingScheduler
     → ALL bookings serialize, even on different rooms. Wrong granularity.

   The lock lives with the resource it's protecting — the room's calendar.
   Same per-resource locking pattern as Movie Ticket (Showtime), Inventory
   (Warehouse), Logger (Destination), Rate Limiter (per-key bucket).
```

> **The interview rule (same across 5 problems now):** the lock lives with the shared resource it's protecting. For meeting scheduling, that's the per-room calendar.

---

## STEP 1 — Requirements (~5 min)

### What to say out loud (opener)
> "Meeting scheduler is straightforward in concept but has two correctness traps — interval semantics (half-open vs closed) and the conflict-check algorithm (O(n) scan vs O(log n) tree lookup). Let me clarify scope on both."

### Probe the 4 themes

| Theme               | Question to ask                                                                                              |
| ------------------- | ------------------------------------------------------------------------------------------------------------ |
| Primary capabilities| "Book a meeting given attendees + duration + required capacity? Auto-allocate room? Or caller picks a room?" |
| Rules / completion  | "Half-open intervals — adjacent meetings (10–11, 11–12) are NOT conflicts? Smallest-fit room allocation by default?" |
| Error handling      | "No room available → throw or return null? Invalid interval (end ≤ start) → reject?"                          |
| Concurrency         | "Multiple users booking concurrently — per-room locking? Two-people-can't-double-book?" |
| Scope boundaries    | "Out: recurring meetings, attendee conflict detection across rooms, amenity matching, calendar invites, persistence. Confirm?" |

### What to write on the board

```
Functional Requirements
1. Register N rooms at startup (id, name, capacity, amenities).
2. bookMeeting(organizer, attendees, requiredCapacity, start, end, title)
   → returns Meeting, OR throws if no room available.
3. Half-open intervals [start, end). Adjacent meetings are NOT conflicts.
4. Auto-allocate room — caller specifies capacity, scheduler picks the best room.
   Allocation policy is pluggable (smallest-fit, first-fit, etc.).
5. cancelMeeting(meetingId) — frees the room for that interval.
6. findAvailableRooms(requiredCapacity, start, end) — query helper.
7. Thread-safe — concurrent bookings on the SAME room serialize; DIFFERENT rooms parallel.

Out of Scope
- Recurring meetings (Step 5)
- Cross-room attendee conflict detection (a person being in 2 meetings at once)
- Amenity filtering (extension on allocation Strategy)
- Calendar invites / notifications (Observer in Step 5)
- Persistence / distributed scheduling
- Different time zones / DST handling (use Instant — timezone-agnostic)
```

### Close the step
> "Two load-bearing requirements: the half-open-interval semantics and the per-room locking granularity. Both are where the senior signal lives."

---

## STEP 2 — Entities & Relationships (~4 min)

### What to say out loud
> "Four types: **MeetingScheduler** (orchestrator + facade), **Room** (immutable record), **Meeting** (immutable record with the overlap-helper method), **RoomAllocationStrategy** (interface with smallest-fit and first-fit impls). The per-room calendar is a `TreeMap<Instant, Meeting>` — keyed by start time so floor/ceiling lookups give us O(log n) conflict checks."

### Why no `Calendar` or `TimeInterval` class
> "Calendar is just a `TreeMap<Instant, Meeting>` — wrapping it in our own class would just forward methods. TimeInterval would just be two Instants; the overlap check is a one-line method I'd put on Meeting itself. Both would be ceremony without behavior."

### Why no `User` / `Attendee` class
> "Attendees are external — we accept string ids. The scheduler doesn't manage user state. If we ever need 'check this attendee for cross-meeting conflicts' (Step 5), we'd add user behavior then."

### What to write on the board

```
Entities
- MeetingScheduler          (orchestrator + facade: book / cancel / findAvailable)
- Room                      (immutable record — id, name, capacity, amenities)
- Meeting                   (immutable record — id, organizer, attendees, roomId, start, end, title)
- RoomAllocationStrategy    (interface — Strategy; orders candidate rooms by preference)

Enums  — none needed for v1 (could add MeetingStatus if we tracked CONFIRMED/CANCELLED)

NOT entities
- Calendar                  (TreeMap<Instant, Meeting> does it)
- TimeInterval              (two Instants — overlap is one method on Meeting)
- User / Attendee           (external — opaque string ids)

Relationships
- MeetingScheduler owns:
    ConcurrentHashMap<String, Room>                 rooms                    (registry)
    ConcurrentHashMap<String, TreeMap<Instant, Meeting>>  calendars          (per-room sorted ledger)
    ConcurrentHashMap<String, Meeting>              meetingsById             (lookup by id)
    RoomAllocationStrategy                          allocation               (Strategy, injected)
- Each calendar is the per-room source of truth for booking state.
- TreeMap is keyed by START Instant for O(log n) floor/ceiling queries.
```

### Diagram — boxes and arrows

```
                  +--------------------------------------+
                  |          MeetingScheduler            |   <- orchestrator + facade
                  |   bookMeeting / cancel / findAvail   |
                  +--------------------------------------+
                       |               |             |
                  owns | (3 maps + strategy)         |
                       v               v             v
        Map<String, Room>     Map<roomId, TreeMap<Instant, Meeting>>   Map<id, Meeting>
        rooms                  calendars (the per-room sorted ledger)   meetingsById
                                  |
                                  | each TreeMap is keyed by start time
                                  | enabling O(log n) floor/ceiling conflict check
                                  v
                  TreeMap<Instant, Meeting>:
                        ┌──────────────────────────────────────┐
                        │  start1 → Meeting1                    │
                        │  start2 → Meeting2                    │
                        │  ...                                  │
                        └──────────────────────────────────────┘

  Strategy:
    +-------------------------+         impls:
    | RoomAllocationStrategy  | --- SmallestFitStrategy (default — sorts by capacity asc)
    +-------------------------+      FirstFitStrategy   (insertion order)
    | + orderCandidates(rooms)|
    +-------------------------+
```

---

## STEP 3 — Class Design (~10 min)

### MeetingScheduler — state ↔ requirement table

| Requirement                              | State MeetingScheduler must own                              |
| ---------------------------------------- | ------------------------------------------------------------ |
| Multiple rooms                           | `Map<String, Room> rooms`                                    |
| Per-room booking ledger                  | `Map<String, TreeMap<Instant, Meeting>> calendars`           |
| Lookup by meeting id (for cancel)        | `Map<String, Meeting> meetingsById`                          |
| Pluggable allocation policy              | `RoomAllocationStrategy allocation` (injected)              |

### MeetingScheduler — class outline (write on the board)

```java
public class MeetingScheduler {
    private final Map<String, Room>                       rooms        = new ConcurrentHashMap<>();
    private final Map<String, TreeMap<Instant, Meeting>>  calendars    = new ConcurrentHashMap<>();
    private final Map<String, Meeting>                    meetingsById = new ConcurrentHashMap<>();
    private final RoomAllocationStrategy                  allocation;

    public MeetingScheduler(RoomAllocationStrategy allocation) { this.allocation = allocation; }

    public void           registerRoom(Room room);
    public Meeting        bookMeeting(organizer, attendees, capacity, start, end, title);
    public boolean        cancelMeeting(String meetingId);
    public List<Room>     findAvailableRooms(int capacity, Instant start, Instant end);
    public List<Meeting>  getMeetingsInRoom(String roomId);
}
```

### Room, Meeting — immutable records

```java
public record Room(String id, String name, int capacity, Set<String> amenities) {
    public Room {
        // ... null + capacity checks ...
        amenities = amenities == null ? Set.of() : Set.copyOf(amenities);     // defensive copy
    }
}

public record Meeting(
    String id, String organizerId, List<String> attendeeIds,
    String roomId, Instant start, Instant end, String title) {

    public Meeting {
        if (!end.isAfter(start)) throw new IllegalArgumentException(
            "end must be strictly after start (half-open interval)");
        attendeeIds = attendeeIds == null ? List.of() : List.copyOf(attendeeIds);
    }

    /** Two intervals overlap iff each starts before the other ends. */
    public boolean overlaps(Instant otherStart, Instant otherEnd) {
        return start.isBefore(otherEnd) && otherStart.isBefore(end);
    }
}
```

### Strategy — `RoomAllocationStrategy`

```java
public interface RoomAllocationStrategy {
    List<Room> orderCandidates(List<Room> capacityFiltered);
}

public class SmallestFitStrategy implements RoomAllocationStrategy {
    public List<Room> orderCandidates(List<Room> candidates) {
        List<Room> ordered = new ArrayList<>(candidates);
        ordered.sort(Comparator.comparingInt(Room::capacity)
                              .thenComparing(Room::id));     // deterministic tiebreak
        return ordered;
    }
}

public class FirstFitStrategy implements RoomAllocationStrategy {
    public List<Room> orderCandidates(List<Room> candidates) { return candidates; }
}
```

> **Why tie-break by room id in SmallestFit?** Without it, two rooms with equal capacity sort non-deterministically — tests flake on the ordering. Same lesson as JobScheduler's createdAt tiebreak.

### The principle to verbalize — Strategy + Information Expert
> "Strategy lets the allocation policy vary at construction without touching the scheduler. The per-room calendar lives on the scheduler (one TreeMap per room id) because the scheduler is what enforces the no-conflict invariant — Information Expert. The overlap helper lives on Meeting because Meeting owns its own start/end."

---

## STEP 4 — Implementation (~17 min)

### Open by asking
> "Real Java or pseudo-code? I'll do `bookMeeting` first — the conflict-check trick lives there — then dry-run the adjacency case + the concurrent race scenario."

### 4.1 `bookMeeting` — the canonical method

```java
public Meeting bookMeeting(String organizerId, List<String> attendees,
                           int requiredCapacity, Instant start, Instant end,
                           String title) {
    if (!end.isAfter(start)) {
        throw new IllegalArgumentException("end must be after start");
    }
    if (requiredCapacity <= 0) {
        throw new IllegalArgumentException("capacity must be > 0");
    }

    // Step 1: filter rooms by capacity.
    List<Room> capacityFiltered = new ArrayList<>();
    for (Room r : rooms.values()) {
        if (r.capacity() >= requiredCapacity) capacityFiltered.add(r);
    }
    if (capacityFiltered.isEmpty()) {
        throw new NoSuchElementException("No room with capacity ≥ " + requiredCapacity);
    }

    // Step 2: let the strategy order them (smallest-fit / first-fit / ...).
    List<Room> ordered = allocation.orderCandidates(capacityFiltered);

    // Step 3: try each in order; lock the per-room calendar around check-and-insert.
    for (Room candidate : ordered) {
        TreeMap<Instant, Meeting> calendar = calendars.get(candidate.id());
        synchronized (calendar) {              // ← per-room lock
            if (hasConflict(calendar, start, end)) continue;
            Meeting meeting = new Meeting(UUID.randomUUID().toString(),
                    organizerId, attendees, candidate.id(), start, end, title);
            calendar.put(start, meeting);
            meetingsById.put(meeting.id(), meeting);
            return meeting;
        }
    }
    throw new NoSuchElementException("No room available for " + start + "–" + end);
}
```

**Four callouts to deliver out loud:**

1. *"Two filters in sequence. Capacity comes first because it's cheap (one int compare per room). Time-availability is per-room and locked, so it's the expensive check — do it ONLY on the candidates that passed capacity."*

2. *"`synchronized(calendar)` is the per-room lock. The TreeMap object IS the monitor — different rooms have different TreeMaps, so different rooms run in parallel."*

3. *"The conflict check + insert is atomic under the lock. Without the lock, two threads could both pass `hasConflict` for the same room/interval and both insert. With the lock, exactly one wins."*

4. *"Strategy returns the room ORDER — the scheduler tries them in that order until one's calendar accepts. Smallest-fit means we prefer the smallest sufficient room; if it's busy, we try the next one up."*

### 4.2 `hasConflict` — the O(log n) trick

```java
private boolean hasConflict(TreeMap<Instant, Meeting> calendar, Instant start, Instant end) {
    Map.Entry<Instant, Meeting> floor = calendar.floorEntry(start);
    // floor's interval extends INTO our start iff its end > our start.
    if (floor != null && floor.getValue().end().isAfter(start)) return true;

    Map.Entry<Instant, Meeting> ceiling = calendar.ceilingEntry(start);
    // ceiling's interval starts BEFORE our end iff its start < our end.
    if (ceiling != null && ceiling.getKey().isBefore(end)) return true;

    return false;
}
```

> **Senior callout:** *"For one room with N existing meetings, naïve conflict check is O(N) — iterate every meeting and apply the overlap formula. With the meetings sorted by start time in a TreeMap, only TWO existing meetings can possibly overlap: the one starting at-or-before our start (floorEntry), and the one starting at-or-after our start (ceilingEntry). Both are O(log N). Linear → logarithmic from one data-structure choice."*

> **Why these two are the ONLY possible conflicts** — *"Any meeting starting BEFORE floor ends BEFORE floor's start, which is ≤ our start, so it ends before our start → no overlap. Any meeting starting AFTER ceiling starts at-or-after some time ≥ ceiling.start ≥ start, and the question is whether its start < end. If ceiling.start ≥ end, then everything after ceiling has start ≥ end → no overlap. So only floor and ceiling are candidate conflicts."*

### 4.3 `cancelMeeting` — frees the slot

```java
public boolean cancelMeeting(String meetingId) {
    Meeting m = meetingsById.remove(meetingId);
    if (m == null) return false;
    TreeMap<Instant, Meeting> calendar = calendars.get(m.roomId());
    if (calendar == null) return false;
    synchronized (calendar) {
        calendar.remove(m.start());
    }
    return true;
}
```

> *"Remove from `meetingsById` first — the operation is then idempotent against double-cancel. Then remove from the room calendar under the per-room lock so a concurrent booking can't see a half-removed entry."*

### 4.4 Verification — dry-run adjacency

```
Setup: Room R_4 (cap 4), single calendar = TreeMap<Instant, Meeting>.

bookMeeting(start=10:00, end=11:00):
   calendar is empty → no floor, no ceiling → no conflict
   calendar.put(10:00, meeting1)
   ✓ booked

bookMeeting(start=11:00, end=12:00):
   floor = entry at 10:00, end=11:00
     floor.end (11:00) > our start (11:00)? FALSE — 11:00 is NOT after 11:00
     → no conflict from floor ✓
   ceiling = no entry at-or-after 11:00 (calendar only has 10:00)
     → no conflict from ceiling ✓
   calendar.put(11:00, meeting2)
   ✓ booked — adjacency is NOT a conflict

Now calendar has two entries:
   10:00 → meeting1 (ends 11:00)
   11:00 → meeting2 (ends 12:00)

Attempt bookMeeting(start=10:30, end=11:30) — overlaps BOTH:
   floor = entry at 10:00, end=11:00
     floor.end (11:00) > our start (10:30)? TRUE → CONFLICT
   ✓ correctly rejected (we don't even reach the ceiling check)

Attempt bookMeeting(start=10:45, end=10:55) — overlaps just meeting1:
   floor = entry at 10:00, end=11:00
     floor.end (11:00) > our start (10:45)? TRUE → CONFLICT  ✓
```

### 4.5 Verification — concurrent race for the same slot

```
Setup: Room R_4 (cap 4). 50 threads × bookMeeting(R_4, capacity=4, 10:00–11:00).

All 50 threads await CountDownLatch.fire.
fire.countDown() → all 50 race simultaneously.

For each thread, inside synchronized(calendar):
   - Thread A acquires the calendar lock first.
     hasConflict → calendar is empty → false
     calendar.put(10:00, meetingA)
     return meetingA → successes.incrementAndGet()
   - Threads B–Z block on the calendar lock.
     They acquire one-at-a-time.
     hasConflict for each → finds meetingA, overlap → true
     continue to next room... but only R_4 has capacity 4 → loop exits
     throw NoSuchElementException → conflicts.incrementAndGet()

Final:
   successes = 1
   conflicts = 49
   calendar.size() = 1

The driver runs exactly this:
   successes = 1 (expect 1)
   conflicts = 49 (expect 49)
   ✓ per-room synchronization holds
```

> **This is the empirical proof of correctness.** Mention you'd verify with `CountDownLatch` + `AtomicInteger` and assert (a) exactly 1 success, (b) calendar size == 1.

---

## STEP 5 — Extensibility (~8 min)

### 5.1 "Recurring meetings (weekly standup at 10am)"

> **Problem in current design:** *"One meeting = one entry. Recurring meetings would mean N entries per series — verbose, hard to manage."*
>
> **Pattern as the fix:** *"Two options: (a) expand on submit — generate N concrete Meeting entries and book each; cancel-one-instance just removes that single Meeting. (b) Add a `MeetingSeries` entity with a recurrence rule; the calendar stores series references and computes conflicts by evaluating the rule. Option (a) is simpler — option (b) is what Google Calendar does for very long horizons."*

### 5.2 "Cross-room attendee conflict (a person in two meetings at once)"

> **Problem in current design:** *"We only check ROOM conflicts. Alice could be 'in' two simultaneous meetings in different rooms."*
>
> **Pattern as the fix:** *"Maintain a parallel `Map<UserId, TreeMap<Instant, Meeting>>` — per-user calendar. Same floor/ceiling check, but on the attendee dimension. Conflict iff any attendee has an overlap. This doubles the per-meeting state-update cost but adds correctness."*

### 5.3 "Amenity-aware allocation (need projector + whiteboard)"

> **Problem in current design:** *"SmallestFitStrategy ignores amenities. A 4-person whiteboard meeting could land in a 4-person room without a whiteboard."*
>
> **Pattern as the fix:** *"Either (a) tighten the candidate filter — pre-filter rooms by `amenities ⊇ required`, then apply Strategy ordering — or (b) introduce a richer `AmenityAwareStrategy` that ranks rooms by fit score (capacity slack + amenity match). Tradeoff: (a) is simpler; (b) handles 'fall back to 8-person room with whiteboard over 4-person without' gracefully."*

### 5.4 "Multi-room booking (a 50-person event spanning rooms)"

> **Problem in current design:** *"`bookMeeting` picks ONE room. Big events need adjacent rooms with a divider open."*
>
> **Pattern as the fix:** *"Two passes: (a) capacity filter expanded to room-groups (pre-configured 'mergeable' room sets), (b) acquire ALL the per-room locks in a consistent order (room-id sorted) to avoid deadlock — same ordered-lock-acquisition pattern as Inventory Management's transfer."*

### 5.5 Other "what-if" answers

| Follow-up                                  | Answer                                                                                              |
| ------------------------------------------ | --------------------------------------------------------------------------------------------------- |
| "Send calendar invites on booking"         | Observer — `MeetingListener.onBooked` / `onCancelled`; email/Slack subscribers register independently. |
| "Time zones"                               | All persisted as `Instant` (UTC). Display layer handles user time zone. Don't store local times.   |
| "Buffer time between meetings"             | At conflict check, expand the candidate interval to `[start - buffer, end + buffer)` before checking. |
| "Persistence"                              | Inject `MeetingRepository`; write on every book/cancel; load on boot.                              |
| "Soft holds during checkout"               | Add a third state `TENTATIVE` alongside the calendar; sweeper releases stale holds (same pattern as Movie Ticket's seat-hold extension). |
| "Find any free 30-min slot today"          | Iterate calendar gaps using `TreeMap.tailMap(now).headMap(eod)` — visit consecutive entries; return the first gap ≥ 30 min. O(n) but n is one day's meetings. |

---

## Design Patterns — Hello Interview's canonical 8

> **One pattern earns rent in the base** (Strategy for allocation, justified by R4's pluggable policy). Facade is naturally present.

### How this maps to MeetingScheduler specifically

**Already in the BASE design — call out by name:**

- **Strategy (#1)** ⭐ — `RoomAllocationStrategy` with SmallestFit + FirstFit. Justified by R4. Name it in Step 2.
- **Facade (#8)** — `MeetingScheduler` is the only class application code touches.
- **Information Expert** (GRASP) — per-room calendar lives on the scheduler because conflict-detection is per-room; overlap method on Meeting because Meeting owns start/end.
- **Immutability** (principle) — Room and Meeting are records.

**Reach for these on Step-5 follow-ups:**

| Follow-up                                  | Pattern (HI's 8)             | Your line                                                                                            |
| ------------------------------------------ | ---------------------------- | ---------------------------------------------------------------------------------------------------- |
| "Send invites / notifications"             | **Observer (#2)**            | *"`MeetingListener.onBooked/onCancelled` — email/Slack subscribers register independently."*       |
| "Amenity-aware allocation"                 | **Strategy (#1)** ⭐         | *"`AmenityAwareStrategy` adds amenity matching to the ordering — same Strategy interface, new impl."* |
| "Multi-tenant / per-org rooms"             | (Composite over schedulers)  | *"`Map<OrgId, MeetingScheduler>` keyed by org; routing at the boundary."*                          |
| "Persistence"                              | (Repository — not HI's 8)    | *"Inject `MeetingRepository`; write on every book/cancel."*                                        |
| "Buffer / soft holds"                      | (State extension)            | *"Add TENTATIVE state with TTL; sweeper releases stale (same pattern as Movie Ticket holds)."*    |

**Patterns to actively refuse:**

- **Singleton on MeetingScheduler** — kills tests; DI a single instance.
- **State pattern on Meeting** — no per-state behavior; meetings are immutable once booked.
- **Composite for room hierarchy** — rooms aren't a tree; they're a flat registry. (Multi-room booking groups would be a separate registry.)
- **Builder for the 1-arg `MeetingScheduler(allocation)` ctor** — academic noise.

### One sentence to say at the end of Step 3

> *"The base design names one GoF pattern by name — Strategy on RoomAllocationStrategy — plus Facade on MeetingScheduler. The headline algorithmic insight is the floor/ceiling-based conflict check — O(log n) per booking instead of O(n) — which is what scales this to a calendar with thousands of meetings per room."*

---

## Interview deep-dives — the questions you'll definitely get asked

### 1. Complexity (Big-O)

Let `R` = number of rooms, `M` = meetings in one room's calendar, `K` = capacity-matching rooms.

| Operation                                | Time                                              | Space               | Notes                                                                              |
| ---------------------------------------- | ------------------------------------------------- | ------------------- | ---------------------------------------------------------------------------------- |
| `bookMeeting`                             | **`O(R + K · log M)`**                            | O(K) for sort       | Capacity filter O(R); allocation sort O(K log K); each candidate conflict check O(log M) |
| `cancelMeeting`                           | **`O(log M)`**                                    | O(1)                | TreeMap remove                                                                     |
| `findAvailableRooms`                      | **`O(R · log M)`**                                | O(K) for result list | Walk all rooms, conflict-check each                                                |
| `getMeetingsInRoom`                       | **`O(M)`** (copy)                                 | O(M)                | Defensive copy of the calendar's values                                            |
| `hasConflict`                             | **`O(log M)`**                                    | O(1)                | Two TreeMap entry lookups                                                          |
| Storage — calendars                       | -                                                 | **`O(R · M̄)`**    | R rooms, average M̄ meetings per room                                              |
| Storage — meetingsById                    | -                                                 | **`O(R · M̄)`**    | Same entries indexed by id for cancel lookup                                       |

> **Senior callout:** *"The headline `O(log M)` per conflict check is the whole reason for TreeMap over List. With 1000 meetings per room/day, that's ~10 comparisons vs 1000 for a linear scan — 100× faster on a hot path. Across multiple candidate rooms, the total is `O(K log M)` where K is the number of capacity-matching rooms. The capacity filter itself is `O(R)` but R is small (dozens of rooms) — payload of the inner check dominates."*

### 2. Concurrency / thread-safety

| Approach                                | When to use                                  | Cost                                                              |
| --------------------------------------- | -------------------------------------------- | ----------------------------------------------------------------- |
| **Per-room `synchronized(calendar)`** ⭐ | **Default.** Different rooms parallel; same-room serialized. | Same-room hotspots serialize (acceptable) |
| Global lock on the scheduler             | Anti-pattern. Serializes all bookings.       | Bottleneck across unrelated rooms                                |
| Per-room ReadWriteLock                   | Read-heavy (lots of findAvailableRooms)      | Slight added complexity                                          |
| Optimistic versioned calendar             | Extreme throughput                           | Tricky; only after profiling                                      |

> **The TWO race conditions this prevents:**
> 1. **Same-room concurrent book:** without the lock, two threads pass hasConflict and both insert. With per-room sync, only one wins. *(Driver scenario 7 proves this empirically.)*
> 2. **Cancel during book:** without locking, a cancel could remove the entry that book just observed-as-conflicting. With per-room sync, cancel waits.

### 3. Testing — what to write tests for

| Test category                | Cases to cover                                                                                              |
| ---------------------------- | ----------------------------------------------------------------------------------------------------------- |
| Basic booking                | Capacity filter picks correct room; calendar updated                                                       |
| **Adjacency NOT conflict**   | 10–11 + 11–12 same room → both succeed                                                                     |
| Overlap rejected             | 10–11 + 10:30–11:30 same room → second rejected                                                            |
| **Smallest-fit ordering**    | Capacity 5 with R_4, R_5, R_10 → picks R_5                                                                 |
| Cancel frees room            | Book, cancel, re-book same slot → succeeds                                                                  |
| Find available rooms         | After booking R_5 at 10–11, find at 10:30–11:30 returns only R_10                                          |
| **Concurrent same-slot race**| 50 threads × same room/time → exactly 1 success, 49 conflicts; calendar size == 1                          |
| Concurrent different-rooms   | 2 threads × different rooms / same time → both succeed (no contention)                                     |
| Invalid intervals            | end ≤ start → IllegalArgumentException                                                                    |
| No capacity match            | All rooms < required capacity → throws NoSuchElement                                                        |

```java
@Test
void adjacent_meetings_are_not_conflicts() {
    MeetingScheduler s = new MeetingScheduler(new SmallestFitStrategy());
    s.registerRoom(new Room("R", "R", 4, Set.of()));

    Meeting first = s.bookMeeting("a", List.of(), 4,
        Instant.parse("2026-06-01T10:00:00Z"), Instant.parse("2026-06-01T11:00:00Z"), "first");
    Meeting second = s.bookMeeting("b", List.of(), 4,
        Instant.parse("2026-06-01T11:00:00Z"), Instant.parse("2026-06-01T12:00:00Z"), "second");

    assertNotNull(first);
    assertNotNull(second);
    assertEquals(2, s.getMeetingsInRoom("R").size());
}
```

### 4. SOLID mapping

| Letter                       | Where it shows up                                                                                          |
| ---------------------------- | ---------------------------------------------------------------------------------------------------------- |
| **S** Single Responsibility  | Scheduler = orchestration + conflict invariants. Room = room data. Meeting = booking record + overlap math. Strategy = allocation policy. Four reasons to change, four types. |
| **O** Open/Closed            | New allocation policy = new Strategy class + one constructor arg. Scheduler unchanged. Adding amenity-aware filter = same Strategy interface. |
| **L** Liskov Substitution    | Any RoomAllocationStrategy substitutable behind the interface — same `orderCandidates` contract. New impls must produce a deterministic order. |
| **I** Interface Segregation  | Strategy has ONE method. Scheduler exposes 5 narrow methods. Room/Meeting expose only getters (records). |
| **D** Dependency Inversion   | Scheduler depends on RoomAllocationStrategy interface, not on SmallestFitStrategy concrete. Strategy injected at composition root. |

### 5. "Summarize your design in 30 seconds"

> *"MeetingScheduler is the orchestrator + facade. Three maps: rooms registry, per-room calendars (each a TreeMap<Instant, Meeting> keyed by start time), and a meetings-by-id lookup for cancel. The headline algorithmic insight is the conflict check — `floorEntry(start)` and `ceilingEntry(start)` are the ONLY two existing meetings that could possibly overlap a new interval; each is `O(log n)` via TreeMap. Total conflict check is `O(log n)` per room instead of `O(n)` linear. Intervals are HALF-OPEN [start, end) — adjacent meetings 10–11 and 11–12 are NOT conflicts, the strict-less-than overlap formula `a.start < b.end && b.start < a.end` makes that work. Room allocation is pluggable via Strategy — SmallestFit (default) sorts candidates by capacity ascending, FirstFit returns insertion order. Per-room synchronization via `synchronized(calendar)` — different rooms run in parallel, same room serializes. The 50-thread race test books the same room+time concurrently; exactly 1 succeeds + 49 rejected + calendar size == 1 — empirically proves per-room locking. Extensions: Observer for invites, recurring-meeting expansion, multi-room booking with ordered lock acquisition."*

That's ~55 seconds. Hits: structure + the O(log n) trick + half-open semantics + per-room locking + the empirical concurrency proof.

---

## Closing soundbites (memorize these)

- **Opening:** *"Two correctness traps — interval semantics (half-open vs closed) and O(n) vs O(log n) conflict check. Get those right and the rest is plumbing."*
- **Why half-open [start, end):** *"Adjacent meetings — one ending exactly when the next starts — are NOT conflicts. Closed intervals would falsely flag every back-to-back."*
- **Why TreeMap keyed by start:** *"`floorEntry(start)` and `ceilingEntry(start)` are the ONLY two existing meetings that could overlap. Both are O(log n) via TreeMap. Linear scan → logarithmic."*
- **Why per-room locking:** *"The lock lives with the resource — same pattern as Movie Ticket's Showtime, Inventory's Warehouse, Logger's Destination. Different rooms run in parallel; same-room conflicts serialize."*
- **Why Strategy for allocation:** *"R4 explicitly enumerates 'smallest-fit / first-fit'. Strategy passes the one-sentence test in the base."*
- **Why createdAt / id tiebreak in the Strategy:** *"Deterministic ordering — two rooms with equal capacity must sort to the same order every test run. Otherwise flaky."*
- **On extensibility:** *"Observer for invites, attendee-conflict via per-user TreeMap, multi-room booking via ordered-lock acquisition — all extensions that compose with the existing Strategy interface."*

---

## Top mistakes that lose points

- **Closed intervals [start, end]** — adjacent meetings falsely flagged as conflicts. Use half-open.
- **Linear scan for conflict check** — O(n) per booking; tens of milliseconds for big calendars. Use TreeMap floor/ceiling.
- **Single global lock on the scheduler** — serializes all bookings even on different rooms.
- **No tiebreak in the Strategy comparator** — flaky ordering across tests / threads.
- **Promoting `Calendar` or `TimeInterval` to a class** — ceremony without behavior.
- **Storing local times instead of `Instant`** — DST and time-zone bugs.
- **Mutable Meeting** — same Meeting passed around can be tampered with; immutable record + defensive `List.copyOf` closes it.
- **Allowing `end == start` (zero-duration meetings)** — degenerate intervals that overlap everything. Reject `end > start` strictly.
- **Cancellation without per-room lock** — concurrent book could see a half-removed entry.
- **Not testing the adjacency case** — the most common bug in interval implementations.
- **Skipping the concurrent race test** — empirical proof of per-room locking is the senior signal.

---

## Files in this folder (your reference implementation)

| File                                                      | What it shows                                                                            |
| --------------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| `model/Room.java`                                         | Immutable record + defensive amenities copy                                              |
| `model/Meeting.java`                                      | Immutable record + half-open interval validation + `overlaps` helper                     |
| `allocation/RoomAllocationStrategy.java`                  | Strategy interface — one method, returns ordered candidates                              |
| `allocation/SmallestFitStrategy.java` / `FirstFitStrategy.java` | Two concrete strategies with deterministic tiebreaks                                |
| `MeetingScheduler.java`                                   | **The hot class** — per-room TreeMap + O(log n) floor/ceiling conflict check + per-room sync |
| `MeetingSchedulerDriver.java`                             | 7 scenarios — basic / conflict / adjacency / smallest-fit / cancel / findAvailable / **50-thread concurrent race** |

Run from the project root:

```bash
mvn -q compile exec:java \
  -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.meetingscheduler.MeetingSchedulerDriver
```
