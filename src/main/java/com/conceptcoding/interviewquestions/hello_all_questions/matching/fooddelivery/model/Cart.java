package com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model;

import java.util.LinkedHashMap;
import java.util.Map;

// One cart per user, items from ONE restaurant only (same rule as Swiggy/Zomato).
// synchronized: the same user can tap "add" from two devices at once.
public class Cart {

    private final String userId;
    private String restaurantId;                                  // null while the cart is empty
    private final Map<String, Integer> quantities = new LinkedHashMap<>();   // itemId → qty

    public Cart(String userId) { this.userId = userId; }

    public synchronized void add(String restaurantId, String itemId, int qty) {
        if (qty <= 0) throw new IllegalArgumentException("Quantity must be > 0");
        if (this.restaurantId != null && !this.restaurantId.equals(restaurantId)) {
            throw new IllegalStateException("Cart has items from another restaurant. Clear it first.");
        }
        this.restaurantId = restaurantId;
        quantities.merge(itemId, qty, Integer::sum);
    }

    public synchronized void remove(String itemId) {
        quantities.remove(itemId);
        if (quantities.isEmpty()) restaurantId = null;
    }

    public synchronized void clear() {
        quantities.clear();
        restaurantId = null;
    }

    public synchronized boolean isEmpty()          { return quantities.isEmpty(); }
    public synchronized String getRestaurantId()   { return restaurantId; }
    public synchronized Map<String, Integer> getQuantities() { return new LinkedHashMap<>(quantities); }
    public String getUserId() { return userId; }
}
