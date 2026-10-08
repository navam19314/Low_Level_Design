package com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model;

import java.util.ArrayList;
import java.util.List;

// Restaurant, rider and customer can all act on the same order at once
// (customer cancels while the rider picks up), so status changes are synchronized:
// exactly one of two conflicting moves wins, the other gets IllegalStateException.
public class Order {

    private final String id;
    private final String customerId;
    private final String restaurantId;
    private final Location deliveryLocation;
    private final List<OrderItem> items;
    private final long total;
    private OrderStatus status = OrderStatus.PLACED;
    private String partnerId;                           // null until a rider is assigned

    public Order(String id, String customerId, String restaurantId, Location deliveryLocation,
                 List<OrderItem> items, long total) {
        this.id = id;
        this.customerId = customerId;
        this.restaurantId = restaurantId;
        this.deliveryLocation = deliveryLocation;
        this.items = new ArrayList<>(items);
        this.total = total;
    }

    public synchronized void moveTo(OrderStatus next) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("Order " + id + ": cannot go from " + status + " to " + next);
        }
        status = next;
    }

    public synchronized void assignPartner(String partnerId) { this.partnerId = partnerId; }

    public synchronized OrderStatus getStatus()    { return status; }
    public synchronized String      getPartnerId() { return partnerId; }

    public String          getId()               { return id; }
    public String          getCustomerId()       { return customerId; }
    public String          getRestaurantId()     { return restaurantId; }
    public Location        getDeliveryLocation() { return deliveryLocation; }
    public List<OrderItem> getItems()            { return new ArrayList<>(items); }
    public long            getTotal()            { return total; }
}
