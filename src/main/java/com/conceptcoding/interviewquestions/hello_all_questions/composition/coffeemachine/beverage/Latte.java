package com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.beverage;

import com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.model.Ingredient;

import java.util.Map;

public class Latte implements Beverage {
    @Override public String description()               { return "Latte"; }
    @Override public long cost()                        { return 150; }
    @Override public Map<Ingredient, Integer> recipe()  { return Map.of(Ingredient.COFFEE_BEANS, 18, Ingredient.WATER, 30, Ingredient.MILK, 150); }
}
