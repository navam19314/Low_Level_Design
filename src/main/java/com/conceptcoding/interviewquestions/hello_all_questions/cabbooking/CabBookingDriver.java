package com.conceptcoding.interviewquestions.hello_all_questions.cabbooking;

import com.conceptcoding.interviewquestions.hello_all_questions.cabbooking.matching.NearestDriverStrategy;
import com.conceptcoding.interviewquestions.hello_all_questions.cabbooking.model.Driver;
import com.conceptcoding.interviewquestions.hello_all_questions.cabbooking.model.DriverStatus;
import com.conceptcoding.interviewquestions.hello_all_questions.cabbooking.model.Location;
import com.conceptcoding.interviewquestions.hello_all_questions.cabbooking.model.Ride;
import com.conceptcoding.interviewquestions.hello_all_questions.cabbooking.model.RideStatus;
import com.conceptcoding.interviewquestions.hello_all_questions.cabbooking.model.Rider;
import com.conceptcoding.interviewquestions.hello_all_questions.cabbooking.pricing.DistanceBasedPricing;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class CabBookingDriver {

    public static void main(String[] args) throws Exception {
        scenarioHappyPath();
        scenarioNearestWinsAmongMany();
        scenarioCancelBeforeStartReleasesDriver();
        scenarioNoDriversAvailable();
        scenarioSurgePricing();
        scenarioConcurrentRequestsNoDoubleBooking();
        scenarioInvalidTransitionRejected();
    }

    private static CabBookingService freshService() {
        return new CabBookingService(
                new NearestDriverStrategy(10.0 /* km */),
                new DistanceBasedPricing(5_000L /* ₹50.00 base */, 1_500L /* ₹15.00/km */),
                Clock.systemUTC());
    }

    // ---- 1. Happy path: request → start → complete → fare ----
    private static void scenarioHappyPath() {
        System.out.println("=== Scenario 1: happy path ride ===");
        CabBookingService svc = freshService();
        Rider  r = new Rider("R1", "Asha");
        Driver d = new Driver("D1", "Bharat", 47, new Location(12.97, 77.59));
        d.goOnline(new Location(12.97, 77.59));
        svc.registerRider(r); svc.registerDriver(d);

        Ride ride = svc.requestRide(r, new Location(12.97, 77.59), new Location(13.00, 77.59));
        System.out.println("  matched: " + ride);
        System.out.println("  driver status: " + d.getStatus() + " (expect ON_TRIP)");

        svc.startRide(ride.getId());
        long fare = svc.completeRide(ride.getId());
        System.out.println("  fare cents: " + fare + " (~₹" + (fare / 100.0) + ")");
        System.out.println("  ride final status: " + ride.getStatus() + " (expect COMPLETED)");
        System.out.println("  driver status:     " + d.getStatus() + " (expect AVAILABLE)");
        System.out.println();
    }

    // ---- 2. Three drivers — nearest one is picked ----
    private static void scenarioNearestWinsAmongMany() {
        System.out.println("=== Scenario 2: nearest driver wins ===");
        CabBookingService svc = freshService();
        Rider r = new Rider("R1", "Asha");

        Driver close  = new Driver("D-close",  "Close",  48, new Location(12.975, 77.595));
        Driver medium = new Driver("D-medium", "Medium", 49, new Location(13.000, 77.595));
        Driver far    = new Driver("D-far",    "Far",    50, new Location(13.100, 77.595));
        close.goOnline(close.getCurrentLocation());
        medium.goOnline(medium.getCurrentLocation());
        far.goOnline(far.getCurrentLocation());

        svc.registerRider(r);
        svc.registerDriver(close); svc.registerDriver(medium); svc.registerDriver(far);

        Ride ride = svc.requestRide(r, new Location(12.97, 77.59), new Location(13.00, 77.59));
        System.out.println("  matched driver: " + ride.getDriver().getId() + " (expect D-close)");
        System.out.println();
    }

    // ---- 3. Cancel after match releases driver ----
    private static void scenarioCancelBeforeStartReleasesDriver() {
        System.out.println("=== Scenario 3: cancel-after-match releases driver ===");
        CabBookingService svc = freshService();
        Rider r = new Rider("R1", "Asha");
        Driver d = new Driver("D1", "Solo", 47, new Location(12.97, 77.59));
        d.goOnline(d.getCurrentLocation());
        svc.registerRider(r); svc.registerDriver(d);

        Ride ride = svc.requestRide(r, new Location(12.97, 77.59), new Location(13.00, 77.59));
        System.out.println("  before cancel — driver: " + d.getStatus() + ", ride: " + ride.getStatus());
        svc.cancelRide(ride.getId());
        System.out.println("  after cancel  — driver: " + d.getStatus() + " (expect AVAILABLE), ride: " + ride.getStatus());
        System.out.println();
    }

    // ---- 4. No drivers available ----
    private static void scenarioNoDriversAvailable() {
        System.out.println("=== Scenario 4: no drivers available ===");
        CabBookingService svc = freshService();
        Rider r = new Rider("R1", "Asha");
        Driver d = new Driver("D1", "Far", 47, new Location(20.0, 77.59));  // 700+ km away — outside 10 km cap
        d.goOnline(d.getCurrentLocation());
        svc.registerRider(r); svc.registerDriver(d);
        try {
            svc.requestRide(r, new Location(12.97, 77.59), new Location(13.00, 77.59));
        } catch (IllegalStateException e) {
            System.out.println("  rejected: " + e.getMessage());
        }
        System.out.println();
    }

    // ---- 5. Surge pricing — same ride costs more under surge ----
    private static void scenarioSurgePricing() {
        System.out.println("=== Scenario 5: surge pricing ===");
        CabBookingService svc = freshService();
        Rider r = new Rider("R1", "Asha");
        Driver d = new Driver("D1", "B", 47, new Location(12.97, 77.59));
        d.goOnline(d.getCurrentLocation());
        svc.registerRider(r); svc.registerDriver(d);

        // ride 1 — no surge
        Ride r1 = svc.requestRide(r, new Location(12.97, 77.59), new Location(13.00, 77.59));
        svc.startRide(r1.getId());
        long normalFare = svc.completeRide(r1.getId());

        // ride 2 — same route, 2.0× surge
        svc.setSurgeBasisPoints(20_000);
        Ride r2 = svc.requestRide(r, new Location(12.97, 77.59), new Location(13.00, 77.59));
        svc.startRide(r2.getId());
        long surgeFare = svc.completeRide(r2.getId());

        System.out.println("  normal fare: " + normalFare + " cents");
        System.out.println("  surge fare:  " + surgeFare  + " cents (expect ~2× normal)");
        System.out.println("  ratio: " + String.format("%.2f", surgeFare / (double) normalFare) + " (expect ~2.0)");
        System.out.println();
    }

    // ---- 6. CONCURRENCY: 50 riders compete for 10 drivers — no driver double-booked ----
    private static void scenarioConcurrentRequestsNoDoubleBooking() throws Exception {
        System.out.println("=== Scenario 6: 50 concurrent requests, 10 drivers — no double-booking ===");
        CabBookingService svc = freshService();

        // 10 drivers clustered around the pickup
        List<Driver> drivers = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Driver d = new Driver("D" + i, "D" + i, 47, new Location(12.97 + i * 0.0001, 77.59));
            d.goOnline(d.getCurrentLocation());
            svc.registerDriver(d);
            drivers.add(d);
        }
        // 50 riders all wanting a cab at the same time
        List<Rider> riders = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            Rider r = new Rider("R" + i, "R" + i);
            svc.registerRider(r);
            riders.add(r);
        }

        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch  go   = new CountDownLatch(1);
        AtomicInteger   matched   = new AtomicInteger();
        AtomicInteger   rejected  = new AtomicInteger();
        List<Ride>      matchedRides = java.util.Collections.synchronizedList(new ArrayList<>());

        for (Rider r : riders) {
            pool.submit(() -> {
                try {
                    go.await();
                    try {
                        Ride ride = svc.requestRide(r,
                                new Location(12.97, 77.59),
                                new Location(13.00, 77.59));
                        matchedRides.add(ride);
                        matched.incrementAndGet();
                    } catch (IllegalStateException e) {
                        rejected.incrementAndGet();
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        go.countDown();
        pool.shutdown();
        if (!pool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS))
            throw new IllegalStateException("test timed out");

        System.out.println("  matched: " + matched.get()  + " (expect 10)");
        System.out.println("  rejected: " + rejected.get() + " (expect 40)");

        // Verify NO driver appears twice across matched rides — that would be a double-booking
        Set<String> driversUsed = new HashSet<>();
        boolean     duplicate   = false;
        for (Ride r : matchedRides) {
            if (!driversUsed.add(r.getDriver().getId())) { duplicate = true; break; }
        }
        System.out.println("  any driver double-booked? " + duplicate + " (expect false)");

        // Every driver in matchedRides should be ON_TRIP; every other driver should be AVAILABLE.
        long onTrip     = drivers.stream().filter(d -> d.getStatus() == DriverStatus.ON_TRIP).count();
        long available  = drivers.stream().filter(d -> d.getStatus() == DriverStatus.AVAILABLE).count();
        System.out.println("  drivers ON_TRIP:    " + onTrip    + " (expect 10)");
        System.out.println("  drivers AVAILABLE:  " + available + " (expect 0)");
        System.out.println();
    }

    // ---- 7. Invalid state transition rejected (try to complete before start) ----
    private static void scenarioInvalidTransitionRejected() {
        System.out.println("=== Scenario 7: invalid state transition rejected ===");
        CabBookingService svc = freshService();
        Rider r = new Rider("R1", "Asha");
        Driver d = new Driver("D1", "B", 47, new Location(12.97, 77.59));
        d.goOnline(d.getCurrentLocation());
        svc.registerRider(r); svc.registerDriver(d);

        Ride ride = svc.requestRide(r, new Location(12.97, 77.59), new Location(13.00, 77.59));
        // MATCHED → COMPLETED is illegal (must go via IN_PROGRESS)
        try {
            svc.completeRide(ride.getId());
        } catch (IllegalStateException e) {
            System.out.println("  MATCHED → COMPLETED rejected: " + e.getMessage());
        }
        // RideStatus enum self-check
        System.out.println("  RideStatus.REQUESTED can transition to MATCHED?     " + RideStatus.REQUESTED.canTransitionTo(RideStatus.MATCHED));
        System.out.println("  RideStatus.COMPLETED can transition to IN_PROGRESS? " + RideStatus.COMPLETED.canTransitionTo(RideStatus.IN_PROGRESS));
        System.out.println();
    }
}
