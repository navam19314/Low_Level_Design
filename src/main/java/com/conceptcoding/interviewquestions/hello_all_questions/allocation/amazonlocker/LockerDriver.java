package com.conceptcoding.interviewquestions.hello_all_questions.allocation.amazonlocker;

import com.conceptcoding.interviewquestions.hello_all_questions.allocation.amazonlocker.model.Compartment;
import com.conceptcoding.interviewquestions.hello_all_questions.allocation.amazonlocker.model.Size;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class LockerDriver {

    public static void main(String[] args) throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-01T10:00:00Z"));
        AmazonLocker locker = new AmazonLocker(List.of(
                new Compartment("S1", Size.SMALL), new Compartment("S2", Size.SMALL),
                new Compartment("M1", Size.MEDIUM), new Compartment("L1", Size.LARGE)), clock, new Random(42));

        System.out.println("=== Happy path ===");
        String code = locker.depositPackage(Size.SMALL);
        System.out.println("  customer gets code " + code);
        locker.pickup(code);
        tryIt("use the same code again", () -> locker.pickup(code));
        tryIt("wrong code", () -> locker.pickup("000000"));

        System.out.println("\n=== Smallest fitting compartment ===");
        locker.depositPackage(Size.SMALL);                       // S1
        locker.depositPackage(Size.SMALL);                       // S2
        System.out.println("  smalls full; next SMALL parcel goes to the medium:");
        locker.depositPackage(Size.SMALL);                       // M1
        tryIt("MEDIUM parcel (only L1 left, it fits)", () -> locker.depositPackage(Size.MEDIUM));
        tryIt("LARGE parcel with everything full", () -> locker.depositPackage(Size.LARGE));

        System.out.println("\n=== Expiry (3 days) ===");
        AmazonLocker locker2 = new AmazonLocker(List.of(new Compartment("M9", Size.MEDIUM)), clock, new Random(7));
        String old = locker2.depositPackage(Size.MEDIUM);
        clock.advanceDays(4);
        tryIt("pick up after 4 days", () -> locker2.pickup(old));
        System.out.println("  staff reclaimed " + locker2.openExpiredCompartments() + " compartment(s)");
        System.out.println("  free for a MEDIUM now: " + locker2.freeCompartmentsFor(Size.MEDIUM) + " (expect 1)");

        concurrentDeliveries();
    }

    // 30 drivers deposit at the same instant into 10 compartments: exactly 10 succeed, 10 distinct doors, 10 distinct codes.
    private static void concurrentDeliveries() throws Exception {
        System.out.println("\n=== Concurrency: 30 drivers, 10 compartments ===");
        List<Compartment> doors = new ArrayList<>();
        for (int i = 0; i < 10; i++) doors.add(new Compartment("C" + i, Size.MEDIUM));
        AmazonLocker locker = new AmazonLocker(doors, Clock.systemUTC(), new Random(1));
        List<String> codes = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(30);
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < 30; i++) {
            pool.submit(() -> {
                start.await();
                try { codes.add(locker.depositPackage(Size.SMALL)); } catch (RuntimeException e) { /* full */ }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
        Set<String> distinct = new HashSet<>(codes);
        System.out.println("  deposits = " + codes.size() + " (expect 10), distinct codes = " + distinct.size()
                + " (expect 10), free left = " + locker.freeCompartmentsFor(Size.SMALL) + " (expect 0)");
    }

    private static void tryIt(String label, Runnable action) {
        try {
            action.run();
            System.out.println("  " + label + ": ALLOWED");
        } catch (RuntimeException e) {
            System.out.println("  " + label + ": rejected → " + e.getMessage());
        }
    }

    // fast-forwardable clock for expiry tests
    static final class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant start) { this.now = start; }
        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId z) { return this; }
        void advanceDays(int days) { now = now.plusSeconds(days * 86_400L); }
    }
}
