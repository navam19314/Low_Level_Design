package com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.beverage;

import com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.model.Ingredient;

import java.util.Map;

public class HotChocolate implements Beverage {
    @Override public String description()               { return "Hot chocolate"; }
    @Override public long cost()                        { return 130; }
    @Override public Map<Ingredient, Integer> recipe()  { return Map.of(Ingredient.CHOCOLATE, 30, Ingredient.MILK, 200); }
}
