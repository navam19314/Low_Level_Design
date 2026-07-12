package com.conceptcoding.interviewquestions.hello_all_questions.movieticket;

import com.conceptcoding.interviewquestions.hello_all_questions.movieticket.model.Movie;
import com.conceptcoding.interviewquestions.hello_all_questions.movieticket.model.Booking;
import com.conceptcoding.interviewquestions.hello_all_questions.movieticket.model.Showtime;
import com.conceptcoding.interviewquestions.hello_all_questions.movieticket.model.Theater;

import java.time.LocalDateTime;
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
        // ---- Build catalog: 1 theater, 2 movies, 3 showtimes ----
        Movie inception = new Movie("M1", "Inception");
        Movie dune      = new Movie("M2", "Dune");

        Theater amc = new Theater("T1", "AMC");
        amc.addShowtime(new Showtime("S1", amc, inception, LocalDateTime.of(2026, 7, 1, 19, 0), "Screen 3"));
        amc.addShowtime(new Showtime("S2", amc, inception, LocalDateTime.of(2026, 7, 1, 22, 0), "Screen 3"));
        amc.addShowtime(new Showtime("S3", amc, dune,      LocalDateTime.of(2026, 7, 1, 20, 0), "Screen 5"));

        BookingSystem system = new BookingSystem(List.of(amc));

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
        Booking b1 = system.book("S1", List.of("A5", "A6"));
        System.out.println("Booking id: " + b1.getBookingId());

        System.out.println("\n--- Same seats again → IllegalStateException ---");
        try { system.book("S1", List.of("A5")); }
        catch (IllegalStateException e) { System.out.println("Rejected: " + e.getMessage()); }

        System.out.println("\n--- Partial booking is atomic: A6 taken → whole booking fails ---");
        try { system.book("S1", List.of("A7", "A6", "A8")); }
        catch (IllegalStateException e) {
            System.out.println("Rejected: " + e.getMessage());
            System.out.println("A7 still available? " + system.book("S1", List.of("A7")).getBookingId().substring(0, 8) + "...");
        }

        System.out.println("\n--- Invalid seat id ---");
        try { system.book("S1", List.of("ZZZ-bad")); }
        catch (IllegalArgumentException e) { System.out.println("Rejected: " + e.getMessage()); }

        System.out.println("\n--- Unknown showtime ---");
        try { system.book("nope", List.of("B1")); }
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
                } catch (IllegalStateException e) {
                    conflicts.incrementAndGet();
                } catch (Throwable t) {
                    synchronized (unexpected) { unexpected.add(t); }
                }
            });
        }

        ready.await();                  // all threads at the barrier
        fire.countDown();               // GO!
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        System.out.println("  successes = " + successes.get() + "  (expect 1)");
        System.out.println("  conflicts = " + conflicts.get() + "  (expect " + (threadCount - 1) + ")");
        System.out.println("  unexpected = " + unexpected.size() + "  (expect 0)");
    }
}
