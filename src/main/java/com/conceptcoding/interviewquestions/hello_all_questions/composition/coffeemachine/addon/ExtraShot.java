package com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.addon;

import com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.beverage.Beverage;
import com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.model.Ingredient;

import java.util.Map;

public class ExtraShot extends AddOn {
    public ExtraShot(Beverage inner) { super(inner); }

    @Override public String description()              { return inner.description() + " + extra shot"; }
    @Override public long cost()                       { return inner.cost() + 40; }
    @Override public Map<Ingredient, Integer> recipe() {
        return innerRecipePlus(Map.of(Ingredient.COFFEE_BEANS, 18, Ingredient.WATER, 30));
    }
}
