package com.conceptcoding.interviewquestions.hello_all_questions.booking.inventory;

import java.util.HashMap;
import java.util.Map;

// One warehouse: product → quantity. Every method is synchronized on THIS warehouse,
// so two orders on the same warehouse wait for each other, and different warehouses never do.
public class Warehouse {

    private final String id;
    private final Map<String, Integer> stock = new HashMap<>();

    public Warehouse(String id) {
        this.id = id;
    }

    public String getId() { return id; }

    public synchronized void addStock(String productId, int quantity) {
        stock.put(productId, getStock(productId) + quantity);
    }

    // Check and subtract in ONE locked step, so stock can never go negative.
    // false = not enough (nothing changed).
    public synchronized boolean removeStock(String productId, int quantity) {
        int current = getStock(productId);
        if (current < quantity) return false;
        stock.put(productId, current - quantity);
        return true;
    }

    public synchronized int getStock(String productId) {
        return stock.getOrDefault(productId, 0);
    }
}
