package com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.addon;

import com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.beverage.Beverage;
import com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.model.Ingredient;

import java.util.Map;

public class Caramel extends AddOn {
    public Caramel(Beverage inner) { super(inner); }

    @Override public String description()              { return inner.description() + " + caramel"; }
    @Override public long cost()                       { return inner.cost() + 35; }
    @Override public Map<Ingredient, Integer> recipe() { return innerRecipePlus(Map.of(Ingredient.CARAMEL_SYRUP, 20)); }
}
