package com.conceptcoding.interviewquestions.hello_all_questions.movieticket;

import com.conceptcoding.interviewquestions.hello_all_questions.movieticket.exception.SeatUnavailableException;
import com.conceptcoding.interviewquestions.hello_all_questions.movieticket.model.Movie;
import com.conceptcoding.interviewquestions.hello_all_questions.movieticket.model.Reservation;
import com.conceptcoding.interviewquestions.hello_all_questions.movieticket.model.Showtime;
import com.conceptcoding.interviewquestions.hello_all_questions.movieticket.model.Theater;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class BookingSystemDriver {

    public static void main(String[] args) throws Exception {
        // Fix time at 2026-06-01 10:00 UTC so "future" showtimes are deterministic.
        MutableClock clock = new MutableClock(Instant.parse("2026-06-01T10:00:00Z"));

        // ---- Build catalog: 1 theater, 2 movies, 3 showtimes ----
        Movie inception = new Movie("M1", "Inception");
        Movie dune      = new Movie("M2", "Dune");

        Theater amc = new Theater("T1", "AMC");
        amc.addShowtime(new Showtime("S1", amc, inception, Instant.parse("2026-06-01T19:00:00Z"), "Screen 3"));
        amc.addShowtime(new Showtime("S2", amc, inception, Instant.parse("2026-06-01T22:00:00Z"), "Screen 3"));
        amc.addShowtime(new Showtime("S3", amc, dune,      Instant.parse("2026-06-01T20:00:00Z"), "Screen 5"));

        BookingSystem system = new BookingSystem(List.of(amc), clock);

        System.out.println("--- Search 'inception' ---");
        for (Showtime s : system.searchMovies("inception")) {
            System.out.println("  " + s.getMovie().getTitle() + " @ " + s.getDatetime()
                    + " in " + s.getScreenLabel());
        }

        System.out.println("\n--- Browse AMC ---");
        for (Showtime s : system.getShowtimesAtTheater(amc)) {
            System.out.println("  " + s.getMovie().getTitle() + " @ " + s.getDatetime());
        }

        System.out.println("\n--- Happy-path booking ---");
        Reservation r1 = system.book("S1", List.of("A5", "A6"));
        System.out.println("Confirmation: " + r1.getConfirmationId());

        System.out.println("\n--- Same seats again → SeatUnavailableException ---");
        try { system.book("S1", List.of("A5")); }
        catch (SeatUnavailableException e) { System.out.println("Rejected: " + e.getMessage()); }

        System.out.println("\n--- Partial booking is atomic: A6 taken → whole booking fails ---");
        try { system.book("S1", List.of("A7", "A6", "A8")); }
        catch (SeatUnavailableException e) {
            System.out.println("Rejected: " + e.getMessage());
            System.out.println("A7 still available? " + system.book("S1", List.of("A7")).getConfirmationId().substring(0, 8) + "...");
        }

        System.out.println("\n--- Cancel r1 and rebook A5 ---");
        system.cancelReservation(r1.getConfirmationId());
        Reservation r3 = system.book("S1", List.of("A5"));
        System.out.println("Re-booked A5 with confirmation: " + r3.getConfirmationId().substring(0, 8) + "...");

        System.out.println("\n--- Invalid seat id ---");
        try { system.book("S1", List.of("ZZZ-bad")); }
        catch (IllegalArgumentException e) { System.out.println("Rejected: " + e.getMessage()); }

        System.out.println("\n--- Unknown showtime / confirmation ---");
        try { system.book("nope", List.of("B1")); }
        catch (NoSuchElementException e) { System.out.println("Rejected: " + e.getMessage()); }
        try { system.cancelReservation("00000000-0000-0000-0000-000000000000"); }
        catch (NoSuchElementException e) { System.out.println("Rejected: " + e.getMessage()); }

        System.out.println("\n--- Concurrent booking on S2: 50 threads race for seat 'C10' ---");
        concurrentRace(system, "S2", "C10", /* threadCount */ 50);
    }

    private static void concurrentRace(BookingSystem system, String showtimeId,
                                       String seat, int threadCount) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch fire  = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        List<Throwable> unexpected = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    fire.await();
                    system.book(showtimeId, List.of(seat));
                    successes.incrementAndGet();
                } catch (SeatUnavailableException e) {
                    conflicts.incrementAndGet();
                } catch (Throwable t) {
                    synchronized (unexpected) { unexpected.add(t); }
                }
            });
        }

        ready.await();                  // all threads are at the barrier
        fire.countDown();               // GO!
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        System.out.println("  successes = " + successes.get() + "  (expect 1)");
        System.out.println("  conflicts = " + conflicts.get() + "  (expect " + (threadCount - 1) + ")");
        System.out.println("  unexpected = " + unexpected.size() + "  (expect 0)");
    }

    static final class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant now) { this.now = now; }
        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId z) { return this; }
    }
}
