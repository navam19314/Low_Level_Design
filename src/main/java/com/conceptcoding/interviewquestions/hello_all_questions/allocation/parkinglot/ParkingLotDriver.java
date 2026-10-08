package com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot;

import com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot.allocation.LowestFloorStrategy;
import com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot.allocation.SmallestFitStrategy;
import com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot.model.ParkingSpot;
import com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot.model.SpotType;
import com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot.model.Ticket;
import com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot.model.Vehicle;
import com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot.model.VehicleType;
import com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot.pricing.HourlyPricing;
import com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot.pricing.PricingStrategy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ParkingLotDriver {

    static final PricingStrategy PRICING = new HourlyPricing(Map.of(
            VehicleType.MOTORCYCLE, 20L, VehicleType.CAR, 50L, VehicleType.TRUCK, 100L));   // ₹ per hour

    public static void main(String[] args) throws Exception {
        MutableClock clock = new MutableClock();
        List<ParkingSpot> spots = List.of(
                new ParkingSpot("G-S1", 0, SpotType.SMALL),
                new ParkingSpot("G-M1", 0, SpotType.MEDIUM),
                new ParkingSpot("F1-M2", 1, SpotType.MEDIUM),
                new ParkingSpot("F1-L1", 1, SpotType.LARGE));
        ParkingLot lot = new ParkingLot(spots, new SmallestFitStrategy(), PRICING, clock);

        System.out.println("=== Smallest fit: bikes use bike spots first ===");
        Ticket bike1 = lot.enter(new Vehicle("KA01-B1", VehicleType.MOTORCYCLE));
        Ticket bike2 = lot.enter(new Vehicle("KA01-B2", VehicleType.MOTORCYCLE));
        System.out.println("  bike 1 → " + bike1.getSpotId() + " (expect G-S1), bike 2 → " + bike2.getSpotId()
                + " (expect G-M1: bike spots full, takes a car spot)");

        System.out.println("\n=== Truck only fits a LARGE spot ===");
        Ticket truck = lot.enter(new Vehicle("KA01-T1", VehicleType.TRUCK));
        System.out.println("  truck → " + truck.getSpotId() + " (expect F1-L1)");

        System.out.println("\n=== Same car can't enter twice ===");
        Ticket car = lot.enter(new Vehicle("KA01-C1", VehicleType.CAR));
        try { lot.enter(new Vehicle("KA01-C1", VehicleType.CAR)); }
        catch (IllegalStateException e) { System.out.println("  Rejected: " + e.getMessage()); }

        System.out.println("\n=== Lot full for cars ===");
        System.out.println("  free spots for a car: " + lot.freeSpotsFor(new Vehicle("x", VehicleType.CAR)));
        try { lot.enter(new Vehicle("KA01-C2", VehicleType.CAR)); }
        catch (IllegalStateException e) { System.out.println("  Rejected: " + e.getMessage()); }

        System.out.println("\n=== Pricing: per started hour, minimum 1 hour ===");
        clock.advanceMinutes(130);                                       // 2h10m
        System.out.println("  car after 2h10m: ₹" + lot.exit(car.getId()) + " (expect 3h × ₹50 = ₹150)");
        System.out.println("  bike after 2h10m: ₹" + lot.exit(bike1.getId()) + " (expect 3h × ₹20 = ₹60)");
        Ticket quick = lot.enter(new Vehicle("KA01-C3", VehicleType.CAR));
        clock.advanceMinutes(5);
        System.out.println("  car after 5 min: ₹" + lot.exit(quick.getId()) + " (expect minimum 1h = ₹50)");

        System.out.println("\n=== Exit twice / unknown ticket ===");
        try { lot.exit(car.getId()); }
        catch (RuntimeException e) { System.out.println("  Rejected: " + e.getMessage()); }

        System.out.println("\n=== Swap the strategy: lowest floor first ===");
        ParkingLot lot2 = new ParkingLot(spots, new LowestFloorStrategy(), PRICING, clock);
        System.out.println("  truck → " + lot2.enter(new Vehicle("T9", VehicleType.TRUCK)).getSpotId()
                + ", bike → " + lot2.enter(new Vehicle("B9", VehicleType.MOTORCYCLE)).getSpotId()
                + " (expect a ground-floor spot)");

        concurrentEntry();
    }

    // 50 cars hit the gates at the same instant, 10 car spots: exactly 10 get in, all different spots.
    private static void concurrentEntry() throws Exception {
        System.out.println("\n=== Concurrency: 50 cars, 10 spots ===");
        List<ParkingSpot> spots = new ArrayList<>();
        for (int i = 1; i <= 10; i++) spots.add(new ParkingSpot("M" + i, i % 3, SpotType.MEDIUM));
        ParkingLot lot = new ParkingLot(spots, new SmallestFitStrategy(), PRICING, Clock.systemUTC());

        List<Ticket> tickets = java.util.Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(50);
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < 50; i++) {
            final int n = i;
            pool.submit(() -> {
                start.await();
                try {
                    tickets.add(lot.enter(new Vehicle("CAR-" + n, VehicleType.CAR)));
                } catch (IllegalStateException e) {
                    // lot full: expected for 40 cars
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        Set<String> spotIds = new HashSet<>();
        for (Ticket t : tickets) spotIds.add(t.getSpotId());
        System.out.println("  tickets = " + tickets.size() + " (expect 10), distinct spots = " + spotIds.size() + " (expect 10)");
        System.out.println(tickets.size() == 10 && spotIds.size() == 10 ? "  no spot double-assigned ✓" : "  RACE ✗");
    }

    static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-07T09:00:00Z");
        void advanceMinutes(long m)                { now = now.plusSeconds(m * 60); }
        @Override public Instant instant()          { return now; }
        @Override public ZoneId getZone()           { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId z)   { return this; }
    }
}
