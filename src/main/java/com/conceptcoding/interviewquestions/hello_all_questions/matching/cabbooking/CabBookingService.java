package com.conceptcoding.interviewquestions.hello_all_questions.matching.cabbooking;

import com.conceptcoding.interviewquestions.hello_all_questions.matching.cabbooking.matching.DriverMatchingStrategy;
import com.conceptcoding.interviewquestions.hello_all_questions.matching.cabbooking.model.Driver;
import com.conceptcoding.interviewquestions.hello_all_questions.matching.cabbooking.model.Location;
import com.conceptcoding.interviewquestions.hello_all_questions.matching.cabbooking.model.Ride;
import com.conceptcoding.interviewquestions.hello_all_questions.matching.cabbooking.model.RideStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

// The service the rider and driver apps call.
// Thread safety: every public method is synchronized, one lock for the whole service.
// Simple and correct: two riders can never get the same driver. Finer-grained locking is an extension.
public class CabBookingService {

    private static final long BASE_FARE = 50;                  // rupees
    private static final long PER_KM = 12;                     // rupees per km

    private final Map<String, Driver> drivers = new HashMap<>();
    private final Map<String, Ride> rides = new HashMap<>();
    private final DriverMatchingStrategy matchingStrategy;
    private int nextRideNumber = 1;

    public CabBookingService(DriverMatchingStrategy matchingStrategy) {
        this.matchingStrategy = matchingStrategy;
    }

    public synchronized void addDriver(Driver driver) {
        drivers.put(driver.getId(), driver);
    }

    public synchronized Ride requestRide(String riderId, Location pickup, Location drop) {
        List<Driver> available = new ArrayList<>();
        for (Driver d : drivers.values()) {
            if (d.isAvailable()) available.add(d);
        }
        Driver driver = matchingStrategy.match(pickup, available);
        if (driver == null) {
            throw new IllegalStateException("No drivers available");
        }
        driver.setAvailable(false);
        Ride ride = new Ride("R-" + nextRideNumber++, riderId, driver, pickup, drop);
        rides.put(ride.getId(), ride);
        return ride;
    }

    public synchronized void startRide(String rideId) {
        findRide(rideId).moveTo(RideStatus.IN_PROGRESS);
    }

    // returns the fare in rupees
    public synchronized long completeRide(String rideId) {
        Ride ride = findRide(rideId);
        ride.moveTo(RideStatus.COMPLETED);                     // throws if the ride never started
        ride.getDriver().finishTrip(ride.getDrop());           // free again, at the drop-off
        return BASE_FARE + Math.round(ride.getPickup().distanceTo(ride.getDrop()) * PER_KM);
    }

    public synchronized void cancelRide(String rideId) {
        Ride ride = findRide(rideId);
        ride.moveTo(RideStatus.CANCELLED);                     // throws once the trip has started
        ride.getDriver().setAvailable(true);
    }

    private Ride findRide(String rideId) {
        Ride ride = rides.get(rideId);
        if (ride == null) throw new NoSuchElementException("Unknown ride " + rideId);
        return ride;
    }
}
