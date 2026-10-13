package com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model;

import java.util.ArrayList;
import java.util.List;

public class Order {

    private final String id;
    private final String customerId;
    private final String restaurantId;
    private final Location deliveryLocation;
    private final List<OrderItem> items;
    private final long total;                              // rupees
    private OrderStatus status = OrderStatus.PLACED;
    private DeliveryPartner partner;                       // null until the restaurant accepts

    public Order(String id, String customerId, String restaurantId, Location deliveryLocation,
                 List<OrderItem> items, long total) {
        this.id = id;
        this.customerId = customerId;
        this.restaurantId = restaurantId;
        this.deliveryLocation = deliveryLocation;
        this.items = new ArrayList<>(items);
        this.total = total;
    }

    public void moveTo(OrderStatus next) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("Order " + id + ": cannot go from " + status + " to " + next);
        }
        status = next;
    }

    public String          getId()               { return id; }
    public String          getCustomerId()       { return customerId; }
    public String          getRestaurantId()     { return restaurantId; }
    public Location        getDeliveryLocation() { return deliveryLocation; }
    public List<OrderItem> getItems()            { return new ArrayList<>(items); }
    public long            getTotal()            { return total; }
    public OrderStatus     getStatus()           { return status; }
    public DeliveryPartner getPartner()          { return partner; }
    public void            setPartner(DeliveryPartner partner) { this.partner = partner; }
}
