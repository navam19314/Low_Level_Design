package com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model;

public class DeliveryPartner {

    private final String id;
    private final String name;
    private Location location;
    private boolean available = true;

    public DeliveryPartner(String id, String name, Location location) {
        this.id = id;
        this.name = name;
        this.location = location;
    }

    // delivery done: free again, at the customer's door (so "nearest" stays true)
    public void finishDelivery(Location dropPoint) {
        this.location = dropPoint;
        this.available = true;
    }

    public String   getId()       { return id; }
    public String   getName()     { return name; }
    public Location getLocation() { return location; }
    public boolean  isAvailable() { return available; }
    public void     setAvailable(boolean available) { this.available = available; }
}
