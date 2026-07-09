package com.conceptcoding.interviewquestions.hello_all_questions.vendingmachine.model;

// Accepted coin denominations (Indian rupee-style: ₹1, ₹2, ₹5, ₹10, ₹20).
// Value is an int — never doubles for money.
public enum Coin {
    ONE(1),
    TWO(2),
    FIVE(5),
    TEN(10),
    TWENTY(20);

    private final int value;

    Coin(int value) { this.value = value; }

    public int getValue() { return value; }
}
