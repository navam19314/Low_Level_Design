package com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine;

import com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.beverage.Beverage;
import com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.model.Ingredient;
import com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.model.Inventory;
import com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.model.Receipt;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

// The machine: take an order, use up the ingredients (all or nothing), brew, hand back a receipt.
// It knows nothing about which add-ons exist: it only talks to the Beverage interface.
public class CoffeeMachine {

    private final Inventory inventory;
    private final AtomicLong orderSeq = new AtomicLong();

    public CoffeeMachine(Inventory inventory) { this.inventory = inventory; }

    public Receipt brew(Beverage drink) {
        inventory.consume(drink.recipe());           // throws if anything is short; then nothing is used
        return new Receipt("ORD-" + orderSeq.incrementAndGet(), drink.description(), drink.cost());
    }

    // grey out drinks the machine can't make right now
    public boolean canMake(Beverage drink) { return inventory.has(drink.recipe()); }

    public List<Ingredient> needsRefill(int threshold) { return inventory.lowStock(threshold); }
}
