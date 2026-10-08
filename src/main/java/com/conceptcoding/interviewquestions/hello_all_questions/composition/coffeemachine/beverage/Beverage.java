package com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.beverage;

import com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.model.Ingredient;

import java.util.Map;

// What every drink can tell you, whether it's a plain Espresso or
// Large(Caramel(Milk(ExtraShot(Espresso)))). Decorators implement this too.
public interface Beverage {
    String description();
    long cost();                                  // rupees
    Map<Ingredient, Integer> recipe();            // what it uses up
}
