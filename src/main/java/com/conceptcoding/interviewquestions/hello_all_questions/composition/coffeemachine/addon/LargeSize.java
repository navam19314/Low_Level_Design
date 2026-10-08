package com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.addon;

import com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.beverage.Beverage;
import com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.model.Ingredient;

import java.util.EnumMap;
import java.util.Map;

// A decorator whose BEHAVIOUR differs: it doesn't add a fixed amount, it scales
// everything inside it by 1.5×. That's why decorators are classes, not just data rows.
// Order matters: Large(Milk(Espresso)) scales the milk too; Milk(Large(Espresso)) doesn't.
public class LargeSize extends AddOn {
    public LargeSize(Beverage inner) { super(inner); }

    @Override public String description() { return "Large (" + inner.description() + ")"; }
    @Override public long cost()          { return inner.cost() * 3 / 2; }

    @Override
    public Map<Ingredient, Integer> recipe() {
        Map<Ingredient, Integer> scaled = new EnumMap<>(Ingredient.class);
        inner.recipe().forEach((ingredient, qty) -> scaled.put(ingredient, qty * 3 / 2));
        return scaled;
    }
}
