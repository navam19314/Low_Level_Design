package com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.model;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// What's in the machine's containers. consume() is ALL-OR-NOTHING: check every ingredient,
// then take every ingredient, under one lock. Two orders racing for the last 100 ml of milk:
// exactly one gets it, and a failed order consumes nothing.
public class Inventory {

    private final Map<Ingredient, Integer> stock = new EnumMap<>(Ingredient.class);

    public synchronized void refill(Ingredient ingredient, int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("Refill must be > 0");
        stock.merge(ingredient, quantity, Integer::sum);
    }

    public synchronized boolean has(Map<Ingredient, Integer> recipe) {
        for (Map.Entry<Ingredient, Integer> e : recipe.entrySet()) {
            if (stock.getOrDefault(e.getKey(), 0) < e.getValue()) return false;
        }
        return true;
    }

    public synchronized void consume(Map<Ingredient, Integer> recipe) {
        List<String> missing = new ArrayList<>();
        for (Map.Entry<Ingredient, Integer> e : recipe.entrySet()) {
            int have = stock.getOrDefault(e.getKey(), 0);
            if (have < e.getValue()) missing.add(e.getKey() + " (need " + e.getValue() + ", have " + have + ")");
        }
        if (!missing.isEmpty()) throw new IllegalStateException("Not enough " + missing);
        for (Map.Entry<Ingredient, Integer> e : recipe.entrySet()) {
            stock.merge(e.getKey(), -e.getValue(), Integer::sum);
        }
    }

    public synchronized int get(Ingredient ingredient) { return stock.getOrDefault(ingredient, 0); }

    // for the "please refill" light
    public synchronized List<Ingredient> lowStock(int threshold) {
        List<Ingredient> low = new ArrayList<>();
        for (Ingredient i : Ingredient.values()) if (stock.getOrDefault(i, 0) < threshold) low.add(i);
        return low;
    }
}
