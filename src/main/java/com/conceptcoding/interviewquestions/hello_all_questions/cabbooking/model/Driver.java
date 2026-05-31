package com.conceptcoding.interviewquestions.hello_all_questions.cabbooking.model;

import java.util.Objects;

/**
 * A driver — identity, current location, status, rating.
 *
 * <p>The {@code status} field is the contended one (race between concurrent
 * match attempts). Access is gated via {@link #tryReserve()} which performs
 * the atomic AVAILABLE → ON_TRIP transition under {@code synchronized(this)}.
 *
 * <p>Location is a mutable field — drivers move as they drive. In production
 * we'd push location updates through a stream + re-index in the spatial
 * index; here we just call {@link #updateLocation(Location)}.
 */
public class Driver {

    private final String id;
    private final String name;
    private final double ratingTenths;   // e.g. 47 = 4.7 stars
    private Location currentLocation;
    private DriverStatus status;

    public Driver(String id, String name, double ratingTenths, Location startLocation) {
        this.id              = id;
        this.name            = name;
        this.ratingTenths    = ratingTenths;
        this.currentLocation = startLocation;
        this.status          = DriverStatus.OFFLINE;
    }

    public String       getId()              { return id; }
    public String       getName()            { return name; }
    public double       getRatingTenths()    { return ratingTenths; }
    public synchronized Location getCurrentLocation() { return currentLocation; }
    public synchronized DriverStatus getStatus()      { return status; }

    /** Driver came online and is now eligible for matching. */
    public synchronized void goOnline(Location at) {
        this.currentLocation = at;
        this.status          = DriverStatus.AVAILABLE;
    }

    public synchronized void goOffline() {
        if (status == DriverStatus.ON_TRIP)
            throw new IllegalStateException("Cannot go offline mid-trip");
        this.status = DriverStatus.OFFLINE;
    }

    /**
     * Atomic AVAILABLE → ON_TRIP transition. Returns true iff THIS caller
     * won the reservation race. Loser gets false and must try the next driver.
     */
    public synchronized boolean tryReserve() {
        if (status != DriverStatus.AVAILABLE) return false;
        status = DriverStatus.ON_TRIP;
        return true;
    }

    /** Trip finished — driver becomes AVAILABLE again at the drop-off location. */
    public synchronized void releaseFromTrip(Location at) {
        if (status != DriverStatus.ON_TRIP)
            throw new IllegalStateException("Driver not on trip");
        this.currentLocation = at;
        this.status          = DriverStatus.AVAILABLE;
    }

    /** Driver location ticked (e.g. mid-trip GPS update). */
    public synchronized void updateLocation(Location loc) {
        this.currentLocation = loc;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Driver d)) return false;
        return id.equals(d.id);
    }
    @Override public int hashCode() { return Objects.hash(id); }
    @Override public String toString() { return "Driver(" + id + "," + name + "," + status + ")"; }
}
