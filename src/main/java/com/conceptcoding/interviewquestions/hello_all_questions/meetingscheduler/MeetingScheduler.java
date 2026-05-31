package com.conceptcoding.interviewquestions.hello_all_questions.meetingscheduler;

import com.conceptcoding.interviewquestions.hello_all_questions.meetingscheduler.allocation.RoomAllocationStrategy;
import com.conceptcoding.interviewquestions.hello_all_questions.meetingscheduler.allocation.SmallestFitStrategy;
import com.conceptcoding.interviewquestions.hello_all_questions.meetingscheduler.model.Meeting;
import com.conceptcoding.interviewquestions.hello_all_questions.meetingscheduler.model.Room;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrator + facade. Four load-bearing concerns:
 *
 * <ol>
 *   <li><b>Per-room calendar</b> — each room owns a {@code TreeMap<Instant, Meeting>}
 *       keyed by meeting start. Conflict check via {@code floorEntry}/{@code ceilingEntry}
 *       is O(log n) instead of O(n) for a linear scan.</li>
 *   <li><b>Half-open intervals</b> — [start, end). Adjacent meetings (one ends
 *       exactly when the next starts) are NOT conflicts.</li>
 *   <li><b>Strategy</b> for allocation order — try smallest-fit first, first-fit
 *       second, etc. The scheduler asks the strategy to ORDER candidates; it
 *       then attempts each in turn until one's calendar accepts the booking.</li>
 *   <li><b>Per-room synchronization</b> — concurrent bookings on the SAME room
 *       serialize on a per-room calendar lock; concurrent bookings on DIFFERENT
 *       rooms proceed in parallel.</li>
 * </ol>
 */
public class MeetingScheduler {

    private final Map<String, Room>                   rooms        = new ConcurrentHashMap<>();
    private final Map<String, TreeMap<Instant, Meeting>> calendars  = new ConcurrentHashMap<>();
    private final Map<String, Meeting>                meetingsById = new ConcurrentHashMap<>();
    private final RoomAllocationStrategy              allocation;

    public MeetingScheduler() { this(new SmallestFitStrategy()); }

    public MeetingScheduler(RoomAllocationStrategy allocation) {
        this.allocation = allocation;
    }

    public void registerRoom(Room room) {
        rooms.put(room.id(), room);
        calendars.putIfAbsent(room.id(), new TreeMap<>());
    }

    /**
     * Try to book a meeting. The strategy orders the capacity-filtered candidate
     * rooms; we try each in order, holding the per-room lock during the conflict
     * check + insert (so the race between two threads is bounded).
     *
     * @throws NoSuchElementException if no room satisfies capacity + availability
     * @throws IllegalArgumentException on invalid inputs (end ≤ start, etc.)
     */
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

        // Step 2: let the strategy order them.
        List<Room> ordered = allocation.orderCandidates(capacityFiltered);

        // Step 3: try each in order; lock the room's calendar around check-and-insert.
        for (Room candidate : ordered) {
            TreeMap<Instant, Meeting> calendar = calendars.get(candidate.id());
            synchronized (calendar) {                              // per-room lock
                if (hasConflict(calendar, start, end)) continue;

                Meeting meeting = new Meeting(
                        UUID.randomUUID().toString(),
                        organizerId, attendees,
                        candidate.id(), start, end, title);
                calendar.put(start, meeting);
                meetingsById.put(meeting.id(), meeting);
                return meeting;
            }
        }
        throw new NoSuchElementException(
                "No room available with capacity ≥ " + requiredCapacity
                + " between " + start + " and " + end);
    }

    /** Cancel — frees the room for that interval. */
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

    /** Returns rooms that satisfy capacity AND have no conflict for [start, end). */
    public List<Room> findAvailableRooms(int requiredCapacity, Instant start, Instant end) {
        if (!end.isAfter(start)) throw new IllegalArgumentException("end must be after start");
        List<Room> available = new ArrayList<>();
        for (Room r : rooms.values()) {
            if (r.capacity() < requiredCapacity) continue;
            TreeMap<Instant, Meeting> cal = calendars.get(r.id());
            synchronized (cal) {
                if (!hasConflict(cal, start, end)) available.add(r);
            }
        }
        return available;
    }

    public Meeting getMeeting(String meetingId) {
        Meeting m = meetingsById.get(meetingId);
        if (m == null) throw new NoSuchElementException("Unknown meeting id: " + meetingId);
        return m;
    }

    public List<Meeting> getMeetingsInRoom(String roomId) {
        TreeMap<Instant, Meeting> cal = calendars.get(roomId);
        if (cal == null) return List.of();
        synchronized (cal) {
            return new ArrayList<>(cal.values());      // defensive copy
        }
    }

    // ----- internals -----

    /**
     * O(log n) conflict check using TreeMap's floor/ceiling lookups.
     *
     * <p>For a candidate interval [start, end), only TWO existing meetings can possibly
     * overlap: the one starting at-or-before our start (floorEntry) and the one starting
     * at-or-after our start (ceilingEntry). Both are O(log n) to find.
     *
     * @return true if any existing meeting in this calendar overlaps [start, end)
     */
    private boolean hasConflict(TreeMap<Instant, Meeting> calendar, Instant start, Instant end) {
        Map.Entry<Instant, Meeting> floor = calendar.floorEntry(start);
        // floor.end > start means floor's interval extends INTO our start → overlap
        if (floor != null && floor.getValue().end().isAfter(start)) return true;

        Map.Entry<Instant, Meeting> ceiling = calendar.ceilingEntry(start);
        // ceiling.start < end means ceiling's interval starts BEFORE our end → overlap
        if (ceiling != null && ceiling.getKey().isBefore(end)) return true;

        return false;
    }
}
