package com.conceptcoding.interviewquestions.hello_all_questions.parkinglot;

import com.conceptcoding.interviewquestions.hello_all_questions.parkinglot.model.ParkingSpot;
import com.conceptcoding.interviewquestions.hello_all_questions.parkinglot.model.SpotType;
import com.conceptcoding.interviewquestions.hello_all_questions.parkinglot.model.Ticket;
import com.conceptcoding.interviewquestions.hello_all_questions.parkinglot.model.VehicleType;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public class ParkingLotDriver {

    public static void main(String[] args) {
        List<ParkingSpot> spots = new ArrayList<>();
        spots.add(new ParkingSpot("M1", SpotType.MOTORCYCLE));
        spots.add(new ParkingSpot("C1", SpotType.CAR));
        spots.add(new ParkingSpot("C2", SpotType.CAR));
        spots.add(new ParkingSpot("L1", SpotType.LARGE));

        MutableClock clock = new MutableClock(Instant.parse("2026-06-01T08:00:00Z"));
        ParkingLot lot = new ParkingLot(spots, /* hourlyRateCents */ 500, clock);

        System.out.println("--- Happy path: car parks for 2.5h, charged 3h ---");
        Ticket t1 = lot.enter(VehicleType.CAR);
        System.out.println("Issued ticket " + t1.getId() + " for spot " + t1.getSpotId());
        clock.advanceMinutes(150);
        long fee = lot.exit(t1.getId());
        System.out.println("Fee: " + fee + " cents (expected 1500)");

        System.out.println("\n--- Double exit rejected ---");
        try {
            lot.exit(t1.getId());
        } catch (RuntimeException e) {
            System.out.println("Rejected: " + e.getMessage());
        }

        System.out.println("\n--- Lot full for size ---");
        lot.enter(VehicleType.CAR);
        lot.enter(VehicleType.CAR);
        try {
            lot.enter(VehicleType.CAR);
        } catch (RuntimeException e) {
            System.out.println("Rejected: " + e.getMessage());
        }

        System.out.println("\n--- Instant exit still charges 1h ---");
        Ticket t2 = lot.enter(VehicleType.MOTORCYCLE);
        long instantFee = lot.exit(t2.getId());
        System.out.println("Fee: " + instantFee + " cents (expected 500)");

        System.out.println("\n--- Invalid ticket ---");
        try {
            lot.exit("does-not-exist");
        } catch (RuntimeException e) {
            System.out.println("Rejected: " + e.getMessage());
        }
        try {
            lot.exit("");
        } catch (RuntimeException e) {
            System.out.println("Rejected: " + e.getMessage());
        }
    }

    static final class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant start) { this.now = start; }
        @Override public Instant instant() { return now; }
        @Override public long millis()    { return now.toEpochMilli(); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId z) { return this; }
        void advanceMinutes(long m) { now = now.plusSeconds(m * 60L); }
    }
}
