package com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.addon;

import com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.beverage.Beverage;
import com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.model.Ingredient;

import java.util.Map;

public class Milk extends AddOn {
    public Milk(Beverage inner) { super(inner); }

    @Override public String description()              { return inner.description() + " + milk"; }
    @Override public long cost()                       { return inner.cost() + 30; }
    @Override public Map<Ingredient, Integer> recipe() { return innerRecipePlus(Map.of(Ingredient.MILK, 100)); }
}
