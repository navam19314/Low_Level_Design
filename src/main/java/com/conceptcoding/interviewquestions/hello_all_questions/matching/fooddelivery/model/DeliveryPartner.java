package com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model;

import java.util.concurrent.atomic.AtomicReference;

// A rider. The contended field is currentOrderId: two orders may try to grab the same
// free rider at the same instant. compareAndSet(null, orderId) lets exactly one win.
public class DeliveryPartner {

    private final String id;
    private final String name;
    private volatile Location location;
    private final AtomicReference<String> currentOrderId = new AtomicReference<>();   // null = free

    public DeliveryPartner(String id, String name, Location location) {
        this.id = id;
        this.name = name;
        this.location = location;
    }

    // atomic claim: true only for the ONE caller that changed it from free to busy
    public boolean tryAssign(String orderId) {
        return currentOrderId.compareAndSet(null, orderId);
    }

    // only frees the rider if they are still on THIS order
    public void release(String orderId) {
        currentOrderId.compareAndSet(orderId, null);
    }

    public boolean  isAvailable()       { return currentOrderId.get() == null; }
    public String   getCurrentOrderId() { return currentOrderId.get(); }
    public String   getId()             { return id; }
    public String   getName()           { return name; }
    public Location getLocation()       { return location; }
    public void     updateLocation(Location location) { this.location = location; }
}
