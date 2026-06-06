package com.conceptcoding.interviewquestions.hello_all_questions.parkinglot;

import com.conceptcoding.interviewquestions.hello_all_questions.parkinglot.model.ParkingSpot;
import com.conceptcoding.interviewquestions.hello_all_questions.parkinglot.model.SpotType;
import com.conceptcoding.interviewquestions.hello_all_questions.parkinglot.model.Ticket;
import com.conceptcoding.interviewquestions.hello_all_questions.parkinglot.model.VehicleType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ParkingLotDriver {

    public static void main(String[] args) {
        List<ParkingSpot> spots = new ArrayList<>();
        spots.add(new ParkingSpot("M1", SpotType.MOTORCYCLE));
        spots.add(new ParkingSpot("C1", SpotType.CAR));
        spots.add(new ParkingSpot("C2", SpotType.CAR));
        spots.add(new ParkingSpot("L1", SpotType.LARGE));

        MutableClock clock = new MutableClock(LocalDateTime.of(2026, 6, 1, 8, 0));
        ParkingLot lot = new ParkingLot(spots, /* hourlyRateCents */ 500, clock::now);

        System.out.println("--- Happy path: car parks for 2.5h, charged 3h ---");
        Ticket t1 = lot.enter(VehicleType.CAR);
        System.out.println("Issued ticket " + t1.getId() + " for spot " + t1.getSpotId() + " at " + t1.getEntryTime());
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

    static final class MutableClock {
        private LocalDateTime now;
        MutableClock(LocalDateTime start) { this.now = start; }
        LocalDateTime now() { return now; }
        void advanceMinutes(long m) { now = now.plusMinutes(m); }
    }
}
