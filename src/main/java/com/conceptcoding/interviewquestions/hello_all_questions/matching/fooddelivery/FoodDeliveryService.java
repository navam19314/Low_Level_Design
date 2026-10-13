package com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery;

import com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.assignment.PartnerAssignmentStrategy;
import com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model.DeliveryPartner;
import com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model.Location;
import com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model.MenuItem;
import com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model.Order;
import com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model.OrderItem;
import com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model.OrderStatus;
import com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model.Restaurant;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

// The service every app calls: customer (order), restaurant (accept), rider (pick up, deliver).
// Lite version: once placed, an order only moves forward (no cancel).
// Thread safety: every public method is synchronized, one lock for the whole service.
// Simple and correct: one rider never gets two orders. Finer-grained locking is an extension.
public class FoodDeliveryService {

    private final Map<String, Restaurant> restaurants = new HashMap<>();
    private final Map<String, DeliveryPartner> partners = new HashMap<>();
    private final Map<String, Order> orders = new HashMap<>();
    private final PartnerAssignmentStrategy assignmentStrategy;
    private int nextOrderNumber = 1;

    public FoodDeliveryService(PartnerAssignmentStrategy assignmentStrategy) {
        this.assignmentStrategy = assignmentStrategy;
    }

    public synchronized void addRestaurant(Restaurant r)   { restaurants.put(r.getId(), r); }
    public synchronized void addPartner(DeliveryPartner p) { partners.put(p.getId(), p); }

    // items: itemId → quantity, all from ONE restaurant.
    // The name and price are COPIED into the order, so a later menu change doesn't change the bill.
    public synchronized Order placeOrder(String customerId, String restaurantId,
                                         Map<String, Integer> items, Location deliveryLocation) {
        if (items.isEmpty()) throw new IllegalArgumentException("Order has no items");
        Restaurant restaurant = findRestaurant(restaurantId);

        List<OrderItem> lines = new ArrayList<>();
        long total = 0;
        for (Map.Entry<String, Integer> e : items.entrySet()) {
            MenuItem item = restaurant.getItem(e.getKey());
            if (!item.isAvailable()) throw new IllegalStateException(item.getName() + " is not available");
            if (e.getValue() <= 0) throw new IllegalArgumentException("Bad quantity for " + item.getName());
            OrderItem line = new OrderItem(item.getId(), item.getName(), item.getPrice(), e.getValue());
            lines.add(line);
            total += line.subtotal();
        }
        Order order = new Order("ORD-" + nextOrderNumber++, customerId, restaurantId, deliveryLocation, lines, total);
        orders.put(order.getId(), order);
        return order;
    }

    // The restaurant accepts → we find the nearest free rider right away.
    // The rider is chosen FIRST: if nobody is free, the order stays PLACED (nothing changed).
    public synchronized void acceptOrder(String orderId) {
        Order order = findOrder(orderId);
        if (order.getStatus() != OrderStatus.PLACED) throw new IllegalStateException("Order " + orderId + " is already " + order.getStatus());
        List<DeliveryPartner> free = new ArrayList<>();
        for (DeliveryPartner p : partners.values()) {
            if (p.isAvailable()) free.add(p);
        }
        DeliveryPartner partner = assignmentStrategy.choose(free, findRestaurant(order.getRestaurantId()).getLocation());
        if (partner == null) throw new IllegalStateException("No rider free for " + orderId);

        order.moveTo(OrderStatus.ACCEPTED);                    // throws if not PLACED
        partner.setAvailable(false);
        order.setPartner(partner);
    }

    public synchronized void pickUp(String orderId) {
        findOrder(orderId).moveTo(OrderStatus.PICKED_UP);
    }

    public synchronized void deliver(String orderId) {
        Order order = findOrder(orderId);
        order.moveTo(OrderStatus.DELIVERED);
        order.getPartner().finishDelivery(order.getDeliveryLocation());
    }

    public synchronized Order getOrder(String orderId) {
        return findOrder(orderId);
    }

    private Order findOrder(String orderId) {
        Order order = orders.get(orderId);
        if (order == null) throw new NoSuchElementException("Order not found: " + orderId);
        return order;
    }

    private Restaurant findRestaurant(String restaurantId) {
        Restaurant r = restaurants.get(restaurantId);
        if (r == null) throw new NoSuchElementException("Restaurant not found: " + restaurantId);
        return r;
    }
}
