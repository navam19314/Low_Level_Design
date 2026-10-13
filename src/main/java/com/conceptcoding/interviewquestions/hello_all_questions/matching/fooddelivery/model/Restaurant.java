package com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model;

import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

public class Restaurant {

    private final String id;
    private final String name;
    private final Location location;
    private final Map<String, Long> menu = new HashMap<>();      // dish → price in rupees

    public Restaurant(String id, String name, Location location) {
        this.id = id;
        this.name = name;
        this.location = location;
    }

    public void setPrice(String dish, long price) { menu.put(dish, price); }

    public long getPrice(String dish) {
        Long price = menu.get(dish);
        if (price == null) throw new NoSuchElementException(name + " has no " + dish);
        return price;
    }

    public String   getId()       { return id; }
    public String   getName()     { return name; }
    public Location getLocation() { return location; }
}
