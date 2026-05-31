package com.conceptcoding.interviewquestions.hello_all_questions.meetingscheduler;

import com.conceptcoding.interviewquestions.hello_all_questions.meetingscheduler.allocation.SmallestFitStrategy;
import com.conceptcoding.interviewquestions.hello_all_questions.meetingscheduler.model.Meeting;
import com.conceptcoding.interviewquestions.hello_all_questions.meetingscheduler.model.Room;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class MeetingSchedulerDriver {

    public static void main(String[] args) throws Exception {
        scenarioBasicBooking();
        scenarioConflictRejected();
        scenarioAdjacentMeetingsNoConflict();
        scenarioSmallestFitAllocation();
        scenarioCancellationFreesRoom();
        scenarioFindAvailableRooms();
        scenarioConcurrentRaceForSameSlot();
    }

    // ---- 1. Basic booking — verify Meeting stored, queryable ----
    private static void scenarioBasicBooking() {
        System.out.println("=== Scenario 1: basic booking ===");
        MeetingScheduler s = newSchedulerWithRooms();
        Meeting m = s.bookMeeting("alice", List.of("alice", "bob"), 4,
                t("10:00"), t("11:00"), "Sync");
        System.out.println("  booked: roomId=" + m.roomId() + " (expect smallest-fit = R_4)");
        System.out.println("  getMeetingsInRoom(R_4) = " + s.getMeetingsInRoom("R_4").size() + " (expect 1)");
        System.out.println();
    }

    // ---- 2. Same room + overlapping time → reject ----
    private static void scenarioConflictRejected() {
        System.out.println("=== Scenario 2: overlapping booking on same room rejected ===");
        MeetingScheduler s = newSchedulerWithOneRoom("R_4", 4);
        s.bookMeeting("alice", List.of("alice"), 4, t("10:00"), t("11:00"), "first");
        try {
            // Second meeting overlapping [10:30, 11:30) — should be rejected (only room is full)
            s.bookMeeting("bob", List.of("bob"), 4, t("10:30"), t("11:30"), "second");
        } catch (NoSuchElementException e) {
            System.out.println("  rejected: " + e.getMessage());
        }
        System.out.println();
    }

    // ---- 3. Adjacent meetings (a.end == b.start) → NOT a conflict ----
    private static void scenarioAdjacentMeetingsNoConflict() {
        System.out.println("=== Scenario 3: adjacent meetings (10-11, 11-12) → NOT conflict ===");
        MeetingScheduler s = newSchedulerWithOneRoom("R_4", 4);
        s.bookMeeting("alice", List.of("alice"), 4, t("10:00"), t("11:00"), "first");
        Meeting m2 = s.bookMeeting("bob", List.of("bob"), 4, t("11:00"), t("12:00"), "second");
        System.out.println("  second meeting booked: " + (m2 != null ? "yes ✓" : "no"));
        System.out.println("  room calendar size: " + s.getMeetingsInRoom("R_4").size() + " (expect 2)");
        System.out.println();
    }

    // ---- 4. Smallest-fit — capacity 5 should pick R_5, not R_10 ----
    private static void scenarioSmallestFitAllocation() {
        System.out.println("=== Scenario 4: smallest-fit allocation — capacity 5 picks R_5 not R_10 ===");
        MeetingScheduler s = newSchedulerWithRooms();
        Meeting m = s.bookMeeting("alice", List.of(), 5, t("10:00"), t("11:00"), "Demo");
        System.out.println("  booked into: " + m.roomId() + " (expect R_5 — smallest fit ≥ 5)");
        System.out.println();
    }

    // ---- 5. Cancellation frees the room ----
    private static void scenarioCancellationFreesRoom() {
        System.out.println("=== Scenario 5: cancel frees the slot ===");
        MeetingScheduler s = newSchedulerWithOneRoom("R_4", 4);
        Meeting m1 = s.bookMeeting("alice", List.of(), 4, t("10:00"), t("11:00"), "first");
        try { s.bookMeeting("bob", List.of(), 4, t("10:00"), t("11:00"), "conflict"); }
        catch (NoSuchElementException e) { System.out.println("  before cancel: second booking rejected ✓"); }

        s.cancelMeeting(m1.id());
        Meeting m2 = s.bookMeeting("bob", List.of(), 4, t("10:00"), t("11:00"), "now-ok");
        System.out.println("  after cancel: second booking succeeded ✓ (id=" + m2.id().substring(0, 8) + "…)");
        System.out.println();
    }

    // ---- 6. findAvailableRooms with capacity filter ----
    private static void scenarioFindAvailableRooms() {
        System.out.println("=== Scenario 6: findAvailableRooms — capacity 5 ===");
        MeetingScheduler s = newSchedulerWithRooms();
        s.bookMeeting("alice", List.of(), 5, t("10:00"), t("11:00"), "in-R_5");
        // Now query at 10:30 with capacity 5 — R_5 should be out; R_10 should be available
        List<Room> available = s.findAvailableRooms(5, t("10:30"), t("11:30"));
        System.out.print("  available rooms for [10:30,11:30) capacity≥5: ");
        for (Room r : available) System.out.print(r.id() + "(cap=" + r.capacity() + ") ");
        System.out.println(" (expect R_10 only)");
        System.out.println();
    }

    // ---- 7. Concurrent race for same room/time — exactly 1 succeeds ----
    private static void scenarioConcurrentRaceForSameSlot() throws Exception {
        System.out.println("=== Scenario 7: 50 threads race for same room + same time — exactly 1 succeeds ===");
        MeetingScheduler s = newSchedulerWithOneRoom("R_4", 4);
        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch fire  = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    fire.await();
                    s.bookMeeting("u-" + Thread.currentThread().getId(), List.of(), 4,
                            t("10:00"), t("11:00"), "race");
                    successes.incrementAndGet();
                } catch (NoSuchElementException e) { conflicts.incrementAndGet(); }
                catch (InterruptedException e)     { Thread.currentThread().interrupt(); }
            });
        }
        ready.await();
        fire.countDown();
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        System.out.println("  successes = " + successes.get() + "  (expect 1)");
        System.out.println("  conflicts = " + conflicts.get() + "  (expect 49)");
        System.out.println("  calendar size = " + s.getMeetingsInRoom("R_4").size() + "  (expect 1)");
        System.out.println(successes.get() == 1 && conflicts.get() == 49
                ? "  ✓ per-room synchronization holds"
                : "  ✗ RACE — counts wrong");
    }

    // ----- helpers -----

    private static MeetingScheduler newSchedulerWithRooms() {
        MeetingScheduler s = new MeetingScheduler(new SmallestFitStrategy());
        s.registerRoom(new Room("R_4",  "Small",  4,  Set.of("whiteboard")));
        s.registerRoom(new Room("R_5",  "Medium", 5,  Set.of()));
        s.registerRoom(new Room("R_10", "Large",  10, Set.of("projector", "whiteboard")));
        return s;
    }

    private static MeetingScheduler newSchedulerWithOneRoom(String id, int cap) {
        MeetingScheduler s = new MeetingScheduler(new SmallestFitStrategy());
        s.registerRoom(new Room(id, id, cap, Set.of()));
        return s;
    }

    /** Convenience for "today at HH:MM UTC" — keeps the scenario code readable. */
    private static Instant t(String hhmm) {
        return Instant.parse("2026-06-01T" + hhmm + ":00Z");
    }
}
