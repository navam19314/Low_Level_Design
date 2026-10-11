package com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model;

import java.util.LinkedHashMap;
import java.util.Map;

// One cart per user, items from ONE restaurant only (like Swiggy/Zomato).
public class Cart {

    private String restaurantId;                                       // null while empty
    private final Map<String, Integer> quantities = new LinkedHashMap<>();   // itemId → qty

    public void add(String restaurantId, String itemId, int qty) {
        if (qty <= 0) throw new IllegalArgumentException("Quantity must be > 0");
        if (this.restaurantId != null && !this.restaurantId.equals(restaurantId)) {
            throw new IllegalStateException("Cart has items from another restaurant. Clear it first.");
        }
        this.restaurantId = restaurantId;
        quantities.merge(itemId, qty, Integer::sum);
    }

    public void clear() {
        quantities.clear();
        restaurantId = null;
    }

    public boolean isEmpty()                    { return quantities.isEmpty(); }
    public String getRestaurantId()             { return restaurantId; }
    public Map<String, Integer> getQuantities() { return new LinkedHashMap<>(quantities); }
}
