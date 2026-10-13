package com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery;

import com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.assignment.PartnerAssignmentStrategy;
import com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model.DeliveryPartner;
import com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model.Location;
import com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model.Order;
import com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model.OrderStatus;
import com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model.Restaurant;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

// Zomato lite: place → accept (nearest free rider) → pick up → deliver. Forward only, no cancel.
// Thread safety: every public method is synchronized, one lock for the whole service,
// so one rider never gets two orders.
public class FoodDeliveryService {

    private final Map<String, Restaurant> restaurants = new HashMap<>();
    private final List<DeliveryPartner> partners = new ArrayList<>();
    private final Map<String, Order> orders = new HashMap<>();
    private final PartnerAssignmentStrategy assignmentStrategy;
    private int nextOrderNumber = 1;

    public FoodDeliveryService(PartnerAssignmentStrategy assignmentStrategy) {
        this.assignmentStrategy = assignmentStrategy;
    }

    public synchronized void addRestaurant(Restaurant r)   { restaurants.put(r.getId(), r); }
    public synchronized void addPartner(DeliveryPartner p) { partners.add(p); }

    // The total is worked out NOW from today's prices, so a later menu change doesn't change the bill.
    public synchronized Order placeOrder(String restaurantId, Map<String, Integer> items, Location deliveryLocation) {
        Restaurant restaurant = restaurants.get(restaurantId);
        if (restaurant == null) throw new NoSuchElementException("Unknown restaurant " + restaurantId);
        if (items.isEmpty()) throw new IllegalArgumentException("Order has no items");

        long total = 0;
        for (Map.Entry<String, Integer> e : items.entrySet()) {
            total += restaurant.getPrice(e.getKey()) * e.getValue();     // throws if the dish isn't on the menu
        }
        Order order = new Order("ORD-" + nextOrderNumber++, restaurant, items, total, deliveryLocation);
        orders.put(order.getId(), order);
        return order;
    }

    // The rider is chosen FIRST: if nobody is free, nothing changes and the order stays PLACED.
    public synchronized void acceptOrder(String orderId) {
        Order order = findOrder(orderId);
        if (order.getStatus() != OrderStatus.PLACED) throw new IllegalStateException("Order " + orderId + " is already " + order.getStatus());
        List<DeliveryPartner> free = new ArrayList<>();
        for (DeliveryPartner p : partners) {
            if (p.isAvailable()) free.add(p);
        }
        DeliveryPartner partner = assignmentStrategy.choose(free, order.getRestaurant().getLocation());
        if (partner == null) throw new IllegalStateException("No rider free for " + orderId);

        order.moveTo(OrderStatus.ACCEPTED);
        partner.setAvailable(false);
        order.setPartner(partner);
    }

    public synchronized void pickUp(String orderId) {
        findOrder(orderId).moveTo(OrderStatus.PICKED_UP);
    }

    public synchronized void deliver(String orderId) {
        Order order = findOrder(orderId);
        order.moveTo(OrderStatus.DELIVERED);
        order.getPartner().finishDelivery(order.getDeliveryLocation());   // free, at the customer's door
    }

    private Order findOrder(String orderId) {
        Order order = orders.get(orderId);
        if (order == null) throw new NoSuchElementException("Unknown order " + orderId);
        return order;
    }
}
