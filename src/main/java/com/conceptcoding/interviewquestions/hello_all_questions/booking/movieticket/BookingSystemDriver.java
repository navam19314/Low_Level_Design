package com.conceptcoding.interviewquestions.hello_all_questions.booking.movieticket;

import com.conceptcoding.interviewquestions.hello_all_questions.booking.movieticket.model.Booking;
import com.conceptcoding.interviewquestions.hello_all_questions.booking.movieticket.model.Movie;
import com.conceptcoding.interviewquestions.hello_all_questions.booking.movieticket.model.Showtime;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class BookingSystemDriver {

    public static void main(String[] args) throws Exception {
        Movie inception = new Movie("M1", "Inception");
        BookingSystem system = new BookingSystem();
        system.addShowtime(new Showtime("S1", inception, LocalDateTime.of(2026, 10, 10, 19, 0), 10));   // 7 PM
        system.addShowtime(new Showtime("S2", inception, LocalDateTime.of(2026, 10, 10, 22, 0), 10));   // 10 PM

        System.out.println("=== Book seats 3 and 4 ===");
        Booking b1 = system.book("S1", List.of(3, 4));
        System.out.println("  " + b1.getId() + " → seats " + b1.getSeats() + " (expect B-1 → [3, 4])");
        System.out.println("  free on S1: " + system.getAvailableSeats("S1") + " (no 3, 4)");

        System.out.println("\n=== Rejections ===");
        tryIt("seat 4 again", () -> system.book("S1", List.of(4)));
        tryIt("seats 5, 4, 6 (4 is taken)", () -> system.book("S1", List.of(5, 4, 6)));
        System.out.println("  seat 5 still free after that? " + system.getAvailableSeats("S1").contains(5) + " (expect true: all or nothing)");
        tryIt("seat 11 on a 10-seat show", () -> system.book("S1", List.of(11)));
        tryIt("seat 7 twice", () -> system.book("S1", List.of(7, 7)));
        tryIt("no seats", () -> system.book("S1", List.of()));
        tryIt("unknown show", () -> system.book("S9", List.of(1)));

        System.out.println("\n=== Different show, same seat number ===");
        System.out.println("  S2 seat 3 → " + system.book("S2", List.of(3)).getId() + " (allowed: different show)");

        System.out.println("\n=== Concurrency: 50 people grab seat 1 on S1 at the same instant ===");
        ExecutorService pool = Executors.newFixedThreadPool(50);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger won = new AtomicInteger(), lost = new AtomicInteger(), other = new AtomicInteger();
        for (int i = 0; i < 50; i++) {
            pool.submit(() -> {
                try {
                    go.await();
                    system.book("S1", List.of(1));
                    won.incrementAndGet();
                } catch (IllegalStateException e) {
                    lost.incrementAndGet();
                } catch (Exception e) {
                    other.incrementAndGet();
                }
            });
        }
        go.countDown();
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println("  won = " + won + " (expect 1), lost = " + lost + " (expect 49), other errors = " + other + " (expect 0)");
    }

    private static void tryIt(String label, Runnable action) {
        try {
            action.run();
            System.out.println("  " + label + ": ALLOWED ✗");
        } catch (RuntimeException e) {
            System.out.println("  " + label + ": rejected → " + e.getMessage());
        }
    }
}
