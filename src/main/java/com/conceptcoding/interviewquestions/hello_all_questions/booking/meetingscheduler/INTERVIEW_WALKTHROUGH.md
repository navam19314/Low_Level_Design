# Meeting Room Scheduler

> **Amazon:** ★ part of the booking cluster (BookMyShow / Meeting Room / Restaurant, 5 reports in 2026). Read [Movie Ticket](../movieticket/INTERVIEW_WALKTHROUGH.md) first: this is the same "two people want the same thing" problem, with **time ranges** instead of seat numbers.
>
> **The crux (what's really being tested):**
> 1. **Overlap of time ranges**, done right: half-open `[start, end)`, so 10–11 and 11–12 don't clash.
> 2. **Fast conflict check:** a `TreeMap` per room, so you only look at the two neighbouring meetings (O(log n)).
> 3. **Per-room locking + an allocation Strategy** (smallest room that fits).
>
> **Family:** F2 Booking under contention. See [foundations](../../00_AMAZON_LLD_FOUNDATIONS.md).

---

## 1. Plain-language picture

### The room's diary
Each room has a diary of bookings sorted by start time:
```
Room "Everest" (8 seats):   09:00–10:00 standup   |   11:00–12:00 design review   |   14:00–15:00 1:1
New request: 10:30–11:30 → clashes with 11:00–12:00 → refuse (try another room)
New request: 10:00–11:00 → fits exactly between → OK
```

### What "overlap" means: half-open intervals
A meeting `[10:00, 11:00)` includes 10:00 but **not** 11:00. So a meeting ending at 11:00 and one starting at 11:00 **don't** clash, which is what humans expect.
```
A and B overlap  ⇔  A.start < B.end  AND  B.start < A.end
```

### Only two neighbours can clash
The diary is sorted. For a new meeting starting at `s`, only two existing meetings can possibly overlap:
- the one starting **at or just before** `s` (does it run past `s`?), and
- the one starting **at or just after** `s` (does it start before our end?).

A `TreeMap` gives both in O(log n) (`floorEntry`, `ceilingEntry`). There's no need to scan the whole diary.

### Which room?
"Book a room for 4 people": try the **smallest room that fits** first, to keep the 20-seat boardroom free. That ordering is a Strategy.

### Two people booking at once
Both want Everest at 10:00. Each room's diary has its own lock: check + insert happen under it, so one wins and the other moves on to the next room. Bookings in *different* rooms never wait for each other.

---

## 2. From story to design

| # | Decision | Rejected | Why |
|---|---|---|---|
| D1 | Half-open `[start, end)` everywhere | closed intervals | Back-to-back meetings are allowed; one rule, no `<=` vs `<` bugs. |
| D2 | `TreeMap<Instant start, Meeting>` per room | a `List` scanned each time | O(log n) conflict check with `floorEntry` / `ceilingEntry`. |
| D3 | Lock **per room's calendar** around check + insert | one global lock / no lock | Two bookings in the same room serialise; different rooms run in parallel. |
| D4 | `RoomAllocationStrategy.orderCandidates()` returns an **ordered** list; the scheduler tries each | the strategy picking one room | The preferred room may be taken; just try the next. |
| D5 | Filter by capacity first, then the strategy orders | strategy sees every room | Cheap filter first; strategies stay simple. |
| D6 | `meetingsById` index for cancel | searching all calendars | O(1) cancel. |
| D7 | Validate `end > start`, capacity > 0 | trusting input | A zero-length or inverted meeting breaks the overlap maths. |
| D8 | Normal immutable classes (`Room`, `Meeting`) | records | Deck convention (whiteboard-friendly). |

### Class shape
```
MeetingScheduler                              ← the service
  Map<roomId, Room> · Map<roomId, TreeMap<start, Meeting>> calendars · Map<meetingId, Meeting>
  RoomAllocationStrategy
  bookMeeting(organizer, attendees, capacity, start, end, title) · cancelMeeting · findAvailableRooms

Room { id, name, capacity, amenities }       Meeting { id, organizer, attendees, roomId, start, end, title } + overlaps()

«interface» RoomAllocationStrategy  orderCandidates(capacityFiltered)
  ├── SmallestFitStrategy (capacity, then id)
  └── FirstFitStrategy
```

---

## 3. Patterns that earn their place

| Pattern | In the base? | Problem it solves | Without it |
|---|---|---|---|
| **Strategy** (`RoomAllocationStrategy`) | **Yes** | smallest-fit vs first-fit vs amenity-aware vs "same floor as organiser" | ordering logic inside `bookMeeting` |
| **Observer** (invites) | No | calendar invites, reminders | Q5 |

**Say:** *"The crux is the overlap check: half-open intervals and a TreeMap per room, so a conflict check is O(log n). A lock per room for check-and-insert. Room choice is a Strategy."*

**Tempting but wrong:** Singleton scheduler; a `TimeSlot` grid of 15-minute cells (wastes memory and can't do 10:07–10:52); State pattern for meetings (booked/cancelled is just presence in the map).

---

## 4. The 45-minute run

### Clarify (min 0–5)
> **You:** Rooms have a capacity. Users book a room for a time range and a number of people. Any free room that fits?
> **Interviewer:** Yes, prefer the smallest that fits.
> **You:** Back-to-back meetings (10–11 then 11–12) are fine?
> **Interviewer:** Yes.
> **You:** Cancel, and "which rooms are free from 2 to 3"?
> **Interviewer:** Yes.
> **You:** Concurrent bookings?
> **Interviewer:** Yes, many people at once.
> **You:** Attendee calendars, recurring meetings, amenities: later?
> **Interviewer:** Later.

```
In scope:  book (capacity + time, smallest fit) · cancel · find free rooms · half-open intervals
           O(log n) conflict check · per-room locking
Out:       attendee double-booking, recurring meetings, amenities, invites
```

### Timeline
| Min | Do |
|---|---|
| 5–9 | Draw a room's diary and the two-neighbour picture. Write the overlap rule. |
| 9–13 | Code step 1: `Room`, `Meeting` (+ `overlaps`), the strategy + `SmallestFitStrategy`. |
| 13–20 | Code step 2: **`hasConflict`** with `floorEntry` / `ceilingEntry`. |
| 20–30 | Code step 3: **`bookMeeting`** (validate → filter → order → per-room lock → check → insert), `cancelMeeting`, `findAvailableRooms`. |
| 30–35 | Dry run, including the boundary case 11:00. |
| 35–45 | Follow-ups: attendee clashes, find a slot for everyone. |

### The code you write, in this order

**Step 1: model + strategy** ([model/](model/), [allocation/](allocation/))
```java
public class Room {
    private final String id;
    private final String name;
    private final int capacity;
    private final Set<String> amenities;
    // constructor validates capacity > 0; getters
}

public class Meeting {                                  // [start, end): half-open
    private final String id, organizerId, roomId, title;
    private final List<String> attendeeIds;
    private final Instant start, end;
    // constructor validates end > start; getters

    public boolean overlaps(Instant otherStart, Instant otherEnd) {
        return start.isBefore(otherEnd) && otherStart.isBefore(end);
    }
}

public interface RoomAllocationStrategy {
    List<Room> orderCandidates(List<Room> capacityFiltered);     // first = tried first
}

public class SmallestFitStrategy implements RoomAllocationStrategy {
    public List<Room> orderCandidates(List<Room> capacityFiltered) {
        List<Room> ordered = new ArrayList<>(capacityFiltered);
        ordered.sort(Comparator.comparingInt(Room::getCapacity).thenComparing(Room::getId));   // id = stable ties
        return ordered;
    }
}
```

**Step 2: the conflict check, the crux** ([MeetingScheduler.java](MeetingScheduler.java))
```java
// only the meeting starting at-or-before us and the one starting at-or-after us can overlap
private boolean hasConflict(TreeMap<Instant, Meeting> calendar, Instant start, Instant end) {
    Map.Entry<Instant, Meeting> floor = calendar.floorEntry(start);
    if (floor != null && floor.getValue().getEnd().isAfter(start)) return true;    // runs into our start

    Map.Entry<Instant, Meeting> ceiling = calendar.ceilingEntry(start);
    if (ceiling != null && ceiling.getKey().isBefore(end)) return true;           // starts before our end

    return false;
}
```
**Why only two?** Meetings in one room never overlap each other (we refused those), so the diary is a sorted chain of non-overlapping blocks. Anything earlier than `floor` ends before `floor` starts; anything later than `ceiling` starts after `ceiling` starts.

**Step 3: book / cancel / find**
```java
public class MeetingScheduler {
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final Map<String, TreeMap<Instant, Meeting>> calendars = new ConcurrentHashMap<>();
    private final Map<String, Meeting> meetingsById = new ConcurrentHashMap<>();
    private final RoomAllocationStrategy allocation;

    public void registerRoom(Room room) {
        rooms.put(room.getId(), room);
        calendars.putIfAbsent(room.getId(), new TreeMap<>());
    }

    public Meeting bookMeeting(String organizerId, List<String> attendees, int requiredCapacity,
                               Instant start, Instant end, String title) {
        if (!end.isAfter(start)) throw new IllegalArgumentException("end must be after start");
        if (requiredCapacity <= 0) throw new IllegalArgumentException("capacity must be > 0");

        List<Room> capacityFiltered = new ArrayList<>();
        for (Room r : rooms.values()) if (r.getCapacity() >= requiredCapacity) capacityFiltered.add(r);
        if (capacityFiltered.isEmpty()) throw new NoSuchElementException("No room with capacity ≥ " + requiredCapacity);

        for (Room candidate : allocation.orderCandidates(capacityFiltered)) {
            TreeMap<Instant, Meeting> calendar = calendars.get(candidate.getId());
            synchronized (calendar) {                              // per-room lock: check + insert together
                if (hasConflict(calendar, start, end)) continue;   // taken: try the next room
                Meeting meeting = new Meeting(UUID.randomUUID().toString(), organizerId, attendees,
                                              candidate.getId(), start, end, title);
                calendar.put(start, meeting);
                meetingsById.put(meeting.getId(), meeting);
                return meeting;
            }
        }
        throw new NoSuchElementException("No room available with capacity ≥ " + requiredCapacity
                + " between " + start + " and " + end);
    }

    public boolean cancelMeeting(String meetingId) {
        Meeting m = meetingsById.remove(meetingId);               // atomic: a double cancel finds nothing
        if (m == null) return false;
        TreeMap<Instant, Meeting> calendar = calendars.get(m.getRoomId());
        synchronized (calendar) { calendar.remove(m.getStart()); }
        return true;
    }

    public List<Room> findAvailableRooms(int requiredCapacity, Instant start, Instant end) {
        List<Room> available = new ArrayList<>();
        for (Room r : rooms.values()) {
            if (r.getCapacity() < requiredCapacity) continue;
            TreeMap<Instant, Meeting> cal = calendars.get(r.getId());
            synchronized (cal) { if (!hasConflict(cal, start, end)) available.add(r); }
        }
        return available;
    }
}
```
**Shape to remember:** validate → filter by capacity → strategy orders → for each room: lock → `hasConflict`? next : insert.

### Dry run
```
Everest diary: [10:00–11:00]
book 10:30–11:30: floor(10:30) = 10:00–11:00, ends 11:00 > 10:30 → CONFLICT → next room
book 11:00–12:00: floor(11:00) = 10:00–11:00, ends 11:00 > 11:00? NO
                  ceiling(11:00) = none → free → booked   (back-to-back works: half-open)
book 09:00–10:00: floor(09:00) = none; ceiling(09:00) = 10:00 start, 10:00 < 10:00? NO → free → booked
```
The driver also races 50 threads for the same room and time: exactly 1 succeeds.

---

## 5. Follow-ups: answer out loud first, then open

<details>
<summary><b>Q1. Don't double-book a person: Priya can't be in two meetings at 10:00.</b></summary>

Give every **person** a calendar too (`TreeMap` per user, same `hasConflict`). Booking must now lock the room **and** every attendee, which means several locks, so take them in a **fixed order** to avoid deadlock (sort the ids):
```java
// sketch
List<Object> locks = new ArrayList<>();
List<String> people = new ArrayList<>(attendees);
Collections.sort(people);                                      // fixed order: no deadlock
for (String p : people) locks.add(personCalendar(p));
locks.add(calendars.get(roomId));
// lock them all in that order (nested synchronized, or ReentrantLocks in a loop)
// → check the room AND every person for a conflict → insert into all of them → unlock in reverse
```
Same lock-ordering rule as the deck's Splitwise transfer and per-seat booking. In real calendars (Google/Outlook) people are often only **warned** about clashes, not blocked; ask which the product wants.
</details>

<details>
<summary><b>Q2. "Find a 1-hour slot when everyone is free" (very common).</b></summary>

Collect everyone's busy intervals, sort by start, and walk them with a cursor; the gaps are the free slots:
```java
static List<Instant[]> freeSlots(List<Instant[]> busy, Instant dayStart, Instant dayEnd, Duration duration) {
    List<Instant[]> sorted = new ArrayList<>(busy);
    sorted.sort(Comparator.comparing(b -> b[0]));
    List<Instant[]> free = new ArrayList<>();
    Instant cursor = dayStart;                                  // everyone is free from here
    for (Instant[] b : sorted) {
        if (!Duration.between(cursor, b[0]).minus(duration).isNegative()) free.add(new Instant[] { cursor, b[0] });
        if (b[1].isAfter(cursor)) cursor = b[1];                // overlapping busy blocks merge naturally
    }
    if (!Duration.between(cursor, dayEnd).minus(duration).isNegative()) free.add(new Instant[] { cursor, dayEnd });
    return free;
}
// busy: 9–10, 9:30–11, 11:30–12, 14–15  (9–17, 1 h) → [12:00–14:00, 15:00–17:00]   (11:00–11:30 is too short)
```
O(n log n) for the sort. Then intersect with rooms that are free in that slot.
</details>

<details>
<summary><b>Q3. Rooms need a projector or a whiteboard.</b></summary>

A new strategy: filter by amenities, then smallest first. `bookMeeting` doesn't change:
```java
public class AmenityAwareStrategy implements RoomAllocationStrategy {
    private final Set<String> required;
    public AmenityAwareStrategy(Set<String> required) { this.required = Set.copyOf(required); }

    public List<Room> orderCandidates(List<Room> capacityFiltered) {
        List<Room> ok = new ArrayList<>();
        for (Room r : capacityFiltered) if (r.getAmenities().containsAll(required)) ok.add(r);
        ok.sort(Comparator.comparingInt(Room::getCapacity).thenComparing(Room::getId));
        return ok;
    }
}
```
If amenities vary per request, pass them as a parameter to `bookMeeting` and filter there, before the strategy.
</details>

<details>
<summary><b>Q4. Recurring meetings: every Monday 10–11 for 12 weeks.</b></summary>

Expand into 12 concrete occurrences and book them **all-or-nothing** in the same room: lock that room, check all 12 for conflicts, insert all 12, unlock. If any one clashes, either reject the series or ask the user (book 11, skip one). Store a `seriesId` on each occurrence so "cancel the series" removes them all. Don't store a rule and compute clashes lazily: it makes every conflict check expensive.
</details>

<details>
<summary><b>Q5. Send calendar invites and reminders.</b></summary>

**Observer**: publish `MEETING_BOOKED` / `MEETING_CANCELLED`; email (an `.ics` invite) and Slack subscribe. Reminders: a scheduled job finds meetings starting in 10 minutes. Publish after the booking is committed, outside the room lock.
</details>

<details>
<summary><b>Q6. Many offices, one backend.</b></summary>

In a database, the per-room lock becomes a transaction with a conflict check:
```sql
-- inside a transaction; lock the room's row first so two bookings serialise per room
SELECT id FROM room WHERE id = ? FOR UPDATE;
SELECT 1 FROM meeting WHERE room_id = ? AND start_at < :end AND end_at > :start;   -- any overlap?
INSERT INTO meeting (...) VALUES (...);                                            -- only if none
```
(PostgreSQL can enforce this directly with an exclusion constraint on a `tstzrange`.) Index `(room_id, start_at)`.
</details>

<details>
<summary><b>Q7. How do you test it?</b></summary>

The basic booking, an overlap rejected, **back-to-back allowed** (the half-open boundary), smallest fit, cancel frees the slot, find available rooms, and 50 threads racing for one room and time → exactly 1 success. All are in the driver. Also test the edges: zero-length meetings rejected; a meeting that fully contains another; one that's fully inside another.
</details>

---

## 6. Traps
1. Closed intervals: back-to-back meetings wrongly clash.
2. Scanning every meeting instead of the two TreeMap neighbours.
3. Check and insert not under the same lock.
4. A global lock: every room's bookings wait on each other.
5. Fixed 15/30-minute slot grids.
6. Locking room + people in arbitrary order (deadlock) in the attendee follow-up.

## 7. Recall check
1. Write the overlap condition. Why half-open?
2. Why do `floorEntry` and `ceilingEntry` suffice? What exactly does each check?
3. Where is the lock, and what does it cover?
4. Walk through a free-slot search for: busy 9–10, 9:30–11, 14–15; day 9–17; 1 hour.
5. Attendee double-booking: which locks, in what order?

**Rebuild in 10 minutes:** `Meeting.overlaps` · `hasConflict` (floor + ceiling) · `bookMeeting` (validate → filter → order → lock → check → insert) · `SmallestFitStrategy`.

---

**Files:** `MeetingScheduler` · `model/` (`Room`, `Meeting`) · `allocation/` (`RoomAllocationStrategy`, `SmallestFitStrategy`, `FirstFitStrategy`) · `MeetingSchedulerDriver` (basic, overlap, back-to-back, smallest fit, cancel, find available, 50-thread race)
```bash
mvn -q compile exec:java -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.booking.meetingscheduler.MeetingSchedulerDriver
```
