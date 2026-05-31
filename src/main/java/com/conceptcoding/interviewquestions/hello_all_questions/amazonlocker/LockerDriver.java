package com.conceptcoding.interviewquestions.hello_all_questions.amazonlocker;

import com.conceptcoding.interviewquestions.hello_all_questions.amazonlocker.model.Compartment;
import com.conceptcoding.interviewquestions.hello_all_questions.amazonlocker.model.Size;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class LockerDriver {

    public static void main(String[] args) {
        List<Compartment> compartments = new ArrayList<>();
        compartments.add(new Compartment("S1", Size.SMALL));
        compartments.add(new Compartment("S2", Size.SMALL));
        compartments.add(new Compartment("M1", Size.MEDIUM));
        compartments.add(new Compartment("L1", Size.LARGE));

        MutableClock clock = new MutableClock(Instant.parse("2026-06-01T10:00:00Z"));
        AmazonLocker locker = new AmazonLocker(compartments, clock, new Random(42));

        System.out.println("--- Happy path ---");
        String token1 = locker.depositPackage(Size.SMALL);
        System.out.println("Driver received code: " + token1);
        locker.pickup(token1);
        System.out.println("Customer picked up successfully.");

        System.out.println("\n--- Wrong code ---");
        try {
            locker.pickup("000000");
        } catch (RuntimeException e) {
            System.out.println("Rejected: " + e.getMessage());
        }

        System.out.println("\n--- Full size ---");
        String t1 = locker.depositPackage(Size.SMALL);
        String t2 = locker.depositPackage(Size.SMALL);
        try {
            locker.depositPackage(Size.SMALL);
        } catch (RuntimeException e) {
            System.out.println("Rejected (no SMALL left): " + e.getMessage());
        }

        System.out.println("\n--- Expiration ---");
        String t3 = locker.depositPackage(Size.MEDIUM);
        System.out.println("Deposited MEDIUM with token " + t3);
        clock.advanceDays(8);
        try {
            locker.pickup(t3);
        } catch (RuntimeException e) {
            System.out.println("Rejected (expired): " + e.getMessage());
        }
        System.out.println("Staff reclaims expired compartments:");
        locker.openExpiredCompartments();
        System.out.println("MEDIUM is free again — deposit should now succeed:");
        String t4 = locker.depositPackage(Size.MEDIUM);
        System.out.println("New token: " + t4);
    }

    /** Fast-forwardable clock so we can test expiration deterministically. */
    static final class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant start) { this.now = start; }
        @Override public Instant instant() { return now; }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId z) { return this; }
        void advanceDays(int days) { now = now.plusSeconds(days * 86_400L); }
    }
}
