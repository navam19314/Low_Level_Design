package com.conceptcoding.interviewquestions.hello_all_questions.matching.cabbooking.model;

public class Ride {

    private final String id;
    private final String riderId;
    private final Driver driver;
    private final Location pickup;
    private final Location drop;
    private RideStatus status = RideStatus.MATCHED;          // a ride is created only once a driver is found

    public Ride(String id, String riderId, Driver driver, Location pickup, Location drop) {
        this.id = id;
        this.riderId = riderId;
        this.driver = driver;
        this.pickup = pickup;
        this.drop = drop;
    }

    public void moveTo(RideStatus next) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("Ride " + id + ": cannot go from " + status + " to " + next);
        }
        status = next;
    }

    public String     getId()      { return id; }
    public String     getRiderId() { return riderId; }
    public Driver     getDriver()  { return driver; }
    public Location   getPickup()  { return pickup; }
    public Location   getDrop()    { return drop; }
    public RideStatus getStatus()  { return status; }
}
