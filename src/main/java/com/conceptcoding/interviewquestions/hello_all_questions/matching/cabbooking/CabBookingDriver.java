package com.conceptcoding.interviewquestions.hello_all_questions.matching.cabbooking;

import com.conceptcoding.interviewquestions.hello_all_questions.matching.cabbooking.matching.NearestDriverStrategy;
import com.conceptcoding.interviewquestions.hello_all_questions.matching.cabbooking.model.Driver;
import com.conceptcoding.interviewquestions.hello_all_questions.matching.cabbooking.model.Location;
import com.conceptcoding.interviewquestions.hello_all_questions.matching.cabbooking.model.Ride;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class CabBookingDriver {

    static final Location HOME = new Location(0, 0);
    static final Location AIRPORT = new Location(0, 25);         // 25 km away

    public static void main(String[] args) throws Exception {
        CabBookingService app = new CabBookingService(new NearestDriverStrategy());
        Driver bharat = new Driver("D1", "Bharat", new Location(1, 0));     // 1 km from HOME
        Driver chitra = new Driver("D2", "Chitra", new Location(5, 0));     // 5 km from HOME
        app.addDriver(bharat);
        app.addDriver(chitra);

        System.out.println("=== Nearest available driver is matched ===");
        Ride r1 = app.requestRide("Asha", HOME, AIRPORT);
        System.out.println("  " + r1.getId() + " → " + r1.getDriver().getName() + " (expect Bharat), status " + r1.getStatus());

        System.out.println("\n=== Next request gets the next driver ===");
        Ride r2 = app.requestRide("Ravi", HOME, AIRPORT);
        System.out.println("  " + r2.getId() + " → " + r2.getDriver().getName() + " (expect Chitra)");

        System.out.println("\n=== No driver left ===");
        tryIt("third request", () -> app.requestRide("Meera", HOME, AIRPORT));

        System.out.println("\n=== Trip: start → complete → fare; driver free at the drop-off ===");
        tryIt("complete before start", () -> app.completeRide(r1.getId()));
        app.startRide(r1.getId());
        long fare = app.completeRide(r1.getId());
        System.out.println("  fare ₹" + fare + " (expect 50 + 25 × 12 = ₹350), " + r1.getStatus()
                + ", Bharat available again? " + bharat.isAvailable());

        System.out.println("\n=== Cancel before start frees the driver; cancel after start is refused ===");
        app.cancelRide(r2.getId());
        System.out.println("  " + r2.getStatus() + ", Chitra available again? " + chitra.isAvailable());
        Ride r3 = app.requestRide("Kiran", HOME, AIRPORT);
        System.out.println("  Kiran → " + r3.getDriver().getName() + " (expect Chitra: Bharat is now 25 km away at the airport)");
        app.startRide(r3.getId());
        tryIt("cancel an in-progress ride", () -> app.cancelRide(r3.getId()));

        concurrentRequests();
    }

    // 50 riders request at the same instant, 10 drivers: exactly 10 rides, 10 different drivers.
    private static void concurrentRequests() throws Exception {
        System.out.println("\n=== Concurrency: 50 riders, 10 drivers ===");
        CabBookingService app = new CabBookingService(new NearestDriverStrategy());
        for (int i = 0; i < 10; i++) app.addDriver(new Driver("D" + i, "Driver " + i, new Location(i, 0)));
        List<Ride> rides = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(50);
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < 50; i++) {
            final int n = i;
            pool.submit(() -> {
                start.await();
                try { rides.add(app.requestRide("U" + n, HOME, AIRPORT)); }
                catch (IllegalStateException e) { /* no driver left */ }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
        Set<String> driverIds = new HashSet<>();
        for (Ride r : rides) driverIds.add(r.getDriver().getId());
        System.out.println("  rides = " + rides.size() + " (expect 10), distinct drivers = " + driverIds.size() + " (expect 10)");
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
