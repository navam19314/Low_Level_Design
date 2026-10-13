package com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model;

import java.util.HashMap;
import java.util.Map;

public class Order {

    private final String id;
    private final Restaurant restaurant;
    private final Map<String, Integer> items;               // dish → quantity
    private final long total;                               // worked out ONCE, at order time
    private final Location deliveryLocation;
    private OrderStatus status = OrderStatus.PLACED;
    private DeliveryPartner partner;                        // null until the restaurant accepts

    public Order(String id, Restaurant restaurant, Map<String, Integer> items, long total, Location deliveryLocation) {
        this.id = id;
        this.restaurant = restaurant;
        this.items = new HashMap<>(items);
        this.total = total;
        this.deliveryLocation = deliveryLocation;
    }

    public void moveTo(OrderStatus next) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("Order " + id + ": cannot go from " + status + " to " + next);
        }
        status = next;
    }

    public String               getId()               { return id; }
    public Restaurant           getRestaurant()       { return restaurant; }
    public Map<String, Integer> getItems()            { return new HashMap<>(items); }
    public long                 getTotal()            { return total; }
    public Location             getDeliveryLocation() { return deliveryLocation; }
    public OrderStatus          getStatus()           { return status; }
    public DeliveryPartner      getPartner()          { return partner; }
    public void                 setPartner(DeliveryPartner partner) { this.partner = partner; }
}
