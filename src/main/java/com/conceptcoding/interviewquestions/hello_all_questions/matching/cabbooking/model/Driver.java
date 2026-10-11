package com.conceptcoding.interviewquestions.hello_all_questions.matching.cabbooking.model;

public class Driver {

    private final String id;
    private final String name;
    private Location location;
    private boolean available = true;

    public Driver(String id, String name, Location location) {
        this.id = id;
        this.name = name;
        this.location = location;
    }

    // trip over: the driver is free again, at the drop-off point
    public void finishTrip(Location dropOff) {
        this.location = dropOff;
        this.available = true;
    }

    public String   getId()        { return id; }
    public String   getName()      { return name; }
    public Location getLocation()  { return location; }
    public boolean  isAvailable()  { return available; }
    public void     setAvailable(boolean available) { this.available = available; }
}
