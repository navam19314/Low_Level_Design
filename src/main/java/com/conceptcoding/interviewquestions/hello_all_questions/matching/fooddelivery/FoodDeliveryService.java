package com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery;

import com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.assignment.PartnerAssignmentStrategy;
import com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model.Cart;
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

// The service every app calls: customer (cart, order, cancel), restaurant (accept, ready),
// rider (pick up, deliver).
// Thread safety: every public method is synchronized, one lock for the whole service.
// Simple and correct: one rider never gets two orders. Finer-grained locking is an extension.
public class FoodDeliveryService {

    private final Map<String, Restaurant> restaurants = new HashMap<>();
    private final Map<String, DeliveryPartner> partners = new HashMap<>();
    private final Map<String, Cart> carts = new HashMap<>();          // userId → cart
    private final Map<String, Order> orders = new HashMap<>();
    private final PartnerAssignmentStrategy assignmentStrategy;
    private int nextOrderNumber = 1;

    public FoodDeliveryService(PartnerAssignmentStrategy assignmentStrategy) {
        this.assignmentStrategy = assignmentStrategy;
    }

    public synchronized void addRestaurant(Restaurant r)    { restaurants.put(r.getId(), r); }
    public synchronized void addPartner(DeliveryPartner p)  { partners.put(p.getId(), p); }

    // ---------- customer ----------

    public synchronized void addToCart(String userId, String restaurantId, String itemId, int qty) {
        MenuItem item = getRestaurant(restaurantId).getItem(itemId);
        if (!item.isAvailable()) throw new IllegalStateException(item.getName() + " is unavailable");
        carts.computeIfAbsent(userId, u -> new Cart()).add(restaurantId, itemId, qty);
    }

    // copies each item's name and price into the order, so later menu changes don't affect it
    public synchronized Order placeOrder(String userId, Location deliveryLocation) {
        Cart cart = carts.get(userId);
        if (cart == null || cart.isEmpty()) throw new IllegalStateException("Cart is empty");
        Restaurant restaurant = getRestaurant(cart.getRestaurantId());

        List<OrderItem> lines = new ArrayList<>();
        long total = 0;
        for (Map.Entry<String, Integer> e : cart.getQuantities().entrySet()) {
            MenuItem item = restaurant.getItem(e.getKey());
            if (!item.isAvailable()) throw new IllegalStateException(item.getName() + " is no longer available");
            OrderItem line = new OrderItem(item.getId(), item.getName(), item.getPrice(), e.getValue());
            lines.add(line);
            total += line.subtotal();
        }
        Order order = new Order("ORD-" + nextOrderNumber++, userId, restaurant.getId(), deliveryLocation, lines, total);
        orders.put(order.getId(), order);
        cart.clear();
        return order;
    }

    public synchronized void cancel(String orderId) {
        Order order = getOrder(orderId);
        order.moveTo(OrderStatus.CANCELLED);          // throws once the food is picked up
        freePartner(order);
        assignWaitingOrders();
    }

    // ---------- restaurant ----------

    // accepting is when we look for a rider, so they arrive around when the food is ready
    public synchronized void acceptOrder(String orderId) {
        Order order = getOrder(orderId);
        order.moveTo(OrderStatus.ACCEPTED);
        assignPartner(order);
    }

    public synchronized void markReady(String orderId) {
        getOrder(orderId).moveTo(OrderStatus.READY);
    }

    // ---------- rider ----------

    public synchronized void pickUp(String orderId) {
        Order order = getOrder(orderId);
        if (order.getPartnerId() == null) throw new IllegalStateException("No rider assigned to " + orderId);
        order.moveTo(OrderStatus.PICKED_UP);
    }

    public synchronized void deliver(String orderId) {
        Order order = getOrder(orderId);
        order.moveTo(OrderStatus.DELIVERED);
        freePartner(order);
        assignWaitingOrders();                        // the freed rider can take a waiting order
    }

    // ---------- rider assignment ----------

    private void assignPartner(Order order) {
        List<DeliveryPartner> free = new ArrayList<>();
        for (DeliveryPartner p : partners.values()) {
            if (p.isAvailable()) free.add(p);
        }
        DeliveryPartner partner = assignmentStrategy.choose(free, getRestaurant(order.getRestaurantId()).getLocation());
        if (partner == null) return;                  // nobody free: the order waits (see assignWaitingOrders)
        partner.setAvailable(false);
        order.setPartnerId(partner.getId());
    }

    private void assignWaitingOrders() {
        for (Order order : orders.values()) {
            if (order.needsPartner()) assignPartner(order);
        }
    }

    private void freePartner(Order order) {
        if (order.getPartnerId() != null) partners.get(order.getPartnerId()).setAvailable(true);
    }

    // ---------- lookups ----------

    public synchronized Order getOrder(String orderId) {
        Order order = orders.get(orderId);
        if (order == null) throw new NoSuchElementException("Order not found: " + orderId);
        return order;
    }

    private Restaurant getRestaurant(String restaurantId) {
        Restaurant r = restaurants.get(restaurantId);
        if (r == null) throw new NoSuchElementException("Restaurant not found: " + restaurantId);
        return r;
    }
}
