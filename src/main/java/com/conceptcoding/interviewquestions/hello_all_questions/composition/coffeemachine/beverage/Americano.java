package com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.beverage;

import com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.model.Ingredient;

import java.util.Map;

public class Americano implements Beverage {
    @Override public String description()               { return "Americano"; }
    @Override public long cost()                        { return 120; }
    @Override public Map<Ingredient, Integer> recipe()  { return Map.of(Ingredient.COFFEE_BEANS, 18, Ingredient.WATER, 150); }
}
