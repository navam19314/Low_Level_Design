package com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine;

import com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.addon.Caramel;
import com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.addon.ExtraShot;
import com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.addon.LargeSize;
import com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.addon.Milk;
import com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.beverage.Americano;
import com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.beverage.Beverage;
import com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.beverage.Espresso;
import com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.beverage.HotChocolate;
import com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.beverage.Latte;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

// The touchscreen sends names ("latte", ["extra_shot", "caramel"]); the menu turns them
// into a decorated Beverage. A Factory keyed by name: adding a drink is one map entry.
public class Menu {

    private static final int MAX_ADD_ONS = 5;

    private final Map<String, Supplier<Beverage>> bases = new LinkedHashMap<>();
    private final Map<String, UnaryOperator<Beverage>> addOns = new LinkedHashMap<>();

    public Menu() {
        bases.put("espresso", Espresso::new);
        bases.put("americano", Americano::new);
        bases.put("latte", Latte::new);
        bases.put("hot_chocolate", HotChocolate::new);
        addOns.put("milk", Milk::new);                  // Milk::new is a Beverage → Beverage function
        addOns.put("extra_shot", ExtraShot::new);
        addOns.put("caramel", Caramel::new);
        addOns.put("large", LargeSize::new);
    }

    public Beverage build(String base, List<String> addOnNames) {
        Supplier<Beverage> baseMaker = bases.get(base);
        if (baseMaker == null) throw new IllegalArgumentException("Unknown drink: " + base);
        if (addOnNames.size() > MAX_ADD_ONS) throw new IllegalArgumentException("At most " + MAX_ADD_ONS + " add-ons");
        Beverage drink = baseMaker.get();
        for (String name : addOnNames) {
            UnaryOperator<Beverage> wrap = addOns.get(name);
            if (wrap == null) throw new IllegalArgumentException("Unknown add-on: " + name);
            drink = wrap.apply(drink);                  // wrap in the order the customer chose
        }
        return drink;
    }

    public List<String> drinkNames() { return List.copyOf(bases.keySet()); }
    public List<String> addOnNames() { return List.copyOf(addOns.keySet()); }
}
