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
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

// The service every app screen talks to: customer (cart, order, cancel),
// restaurant (accept, ready) and rider (pick up, deliver).
public class FoodDeliveryService {

    private final Map<String, Restaurant> restaurants = new ConcurrentHashMap<>();
    private final Map<String, DeliveryPartner> partners = new ConcurrentHashMap<>();
    private final Map<String, Cart> carts = new ConcurrentHashMap<>();         // userId → cart
    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final PartnerAssignmentStrategy assignmentStrategy;
    private final AtomicLong orderSeq = new AtomicLong();

    public FoodDeliveryService(PartnerAssignmentStrategy assignmentStrategy) {
        this.assignmentStrategy = assignmentStrategy;
    }

    public void addRestaurant(Restaurant restaurant)    { restaurants.put(restaurant.getId(), restaurant); }
    public void addPartner(DeliveryPartner partner)     { partners.put(partner.getId(), partner); }

    // ---------- customer: browse + cart ----------

    public List<Restaurant> searchRestaurants(String query) {
        String q = query.toLowerCase();
        List<Restaurant> result = new ArrayList<>();
        for (Restaurant r : restaurants.values()) {
            if (r.isOpen() && r.getName().toLowerCase().contains(q)) result.add(r);
        }
        return result;
    }

    public void addToCart(String userId, String restaurantId, String itemId, int qty) {
        MenuItem item = getRestaurant(restaurantId).getItem(itemId);     // throws if no such item
        if (!item.isAvailable()) throw new IllegalStateException(item.getName() + " is unavailable");
        carts.computeIfAbsent(userId, Cart::new).add(restaurantId, itemId, qty);
    }

    public Cart getCart(String userId) {
        return carts.computeIfAbsent(userId, Cart::new);
    }

    // ---------- customer: place order ----------

    // Re-validates everything (restaurant open, items still available) and COPIES prices
    // into the order, so later menu changes never change this order's total.
    public Order placeOrder(String userId, Location deliveryLocation) {
        Cart cart = getCart(userId);
        synchronized (cart) {                                         // same lock as Cart's own methods
            if (cart.isEmpty()) throw new IllegalStateException("Cart is empty");
            Restaurant restaurant = getRestaurant(cart.getRestaurantId());
            if (!restaurant.isOpen()) throw new IllegalStateException(restaurant.getName() + " is closed");

            List<OrderItem> lines = new ArrayList<>();
            long total = 0;
            for (Map.Entry<String, Integer> e : cart.getQuantities().entrySet()) {
                MenuItem item = restaurant.getItem(e.getKey());
                if (!item.isAvailable()) {
                    throw new IllegalStateException(item.getName() + " is no longer available");
                }
                OrderItem line = new OrderItem(item.getId(), item.getName(), item.getPrice(), e.getValue());
                lines.add(line);
                total += line.subtotal();
            }
            Order order = new Order("ORD-" + orderSeq.incrementAndGet(), userId, restaurant.getId(),
                                    deliveryLocation, lines, total);
            orders.put(order.getId(), order);
            cart.clear();
            return order;
        }
    }

    // ---------- restaurant ----------

    // Accepting is the moment we start looking for a rider, so the rider
    // reaches the restaurant around the time the food is ready.
    public void acceptOrder(String orderId) {
        Order order = getOrder(orderId);
        order.moveTo(OrderStatus.ACCEPTED);
        tryAssignPartner(order);
    }

    public void markReady(String orderId) {
        getOrder(orderId).moveTo(OrderStatus.READY);
    }

    // ---------- rider ----------

    public void pickUp(String orderId) {
        Order order = getOrder(orderId);
        synchronized (order) {
            if (order.getPartnerId() == null) throw new IllegalStateException("No rider assigned to " + orderId);
            order.moveTo(OrderStatus.PICKED_UP);
        }
    }

    public void deliver(String orderId) {
        Order order = getOrder(orderId);
        synchronized (order) {
            order.moveTo(OrderStatus.DELIVERED);
            releasePartner(order);
        }
        assignPendingOrders();                        // the freed rider can take a waiting order
    }

    // ---------- cancel (customer or restaurant) ----------

    public void cancel(String orderId) {
        Order order = getOrder(orderId);
        synchronized (order) {
            order.moveTo(OrderStatus.CANCELLED);      // throws once the food is picked up
            releasePartner(order);
        }
        assignPendingOrders();
    }

    // ---------- rider assignment ----------

    // Orders accepted while every rider was busy wait here until a rider frees up.
    public void assignPendingOrders() {
        for (Order order : orders.values()) {
            tryAssignPartner(order);
        }
    }

    // Locked per ORDER, so two threads can't give one order two riders.
    // The rider claim itself is a lock-free compareAndSet, so one rider can't get two orders.
    private void tryAssignPartner(Order order) {
        synchronized (order) {
            OrderStatus status = order.getStatus();
            boolean waiting = status == OrderStatus.ACCEPTED || status == OrderStatus.READY;
            if (!waiting || order.getPartnerId() != null) return;

            Location pickup = getRestaurant(order.getRestaurantId()).getLocation();
            for (DeliveryPartner p : assignmentStrategy.rank(new ArrayList<>(partners.values()), pickup)) {
                if (p.tryAssign(order.getId())) {     // lost the race for this rider? try the next one
                    order.assignPartner(p.getId());
                    return;
                }
            }
            // no free rider right now: the order stays unassigned and is retried by assignPendingOrders()
        }
    }

    private void releasePartner(Order order) {
        String partnerId = order.getPartnerId();
        if (partnerId != null) partners.get(partnerId).release(order.getId());
    }

    // ---------- lookups ----------

    public Order getOrder(String orderId) {
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
