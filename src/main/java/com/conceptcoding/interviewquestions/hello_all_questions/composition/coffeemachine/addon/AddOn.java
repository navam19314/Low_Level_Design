package com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.addon;

import com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.beverage.Beverage;
import com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.model.Ingredient;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

// Decorator: an add-on IS a Beverage (same interface) and HAS a Beverage (the drink it wraps).
// Each layer adds its own price, ingredients and name on top of whatever is inside.
// With n add-ons that's n small classes instead of 2^n "EspressoWithMilkAndCaramel" classes.
public abstract class AddOn implements Beverage {

    protected final Beverage inner;

    protected AddOn(Beverage inner) { this.inner = Objects.requireNonNull(inner); }

    // lets rules look inside the layers, e.g. "at most 2 extra shots"
    public Beverage getInner() { return inner; }

    // the inner drink's recipe plus this layer's extra ingredients
    protected Map<Ingredient, Integer> innerRecipePlus(Map<Ingredient, Integer> extra) {
        Map<Ingredient, Integer> recipe = new EnumMap<>(Ingredient.class);
        recipe.putAll(inner.recipe());
        extra.forEach((ingredient, qty) -> recipe.merge(ingredient, qty, Integer::sum));
        return recipe;
    }
}
