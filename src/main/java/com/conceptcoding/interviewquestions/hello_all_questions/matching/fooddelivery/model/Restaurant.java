package com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

public class Restaurant {

    private final String id;
    private final String name;
    private final Location location;
    private final Map<String, MenuItem> menu = new HashMap<>();     // itemId → item

    public Restaurant(String id, String name, Location location) {
        this.id = id;
        this.name = name;
        this.location = location;
    }

    public void addItem(MenuItem item) { menu.put(item.getId(), item); }

    public MenuItem getItem(String itemId) {
        MenuItem item = menu.get(itemId);
        if (item == null) throw new NoSuchElementException("No item " + itemId + " at " + name);
        return item;
    }

    public String   getId()       { return id; }
    public String   getName()     { return name; }
    public Location getLocation() { return location; }
}
