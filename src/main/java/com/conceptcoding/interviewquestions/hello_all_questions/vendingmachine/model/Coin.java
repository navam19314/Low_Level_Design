package com.conceptcoding.interviewquestions.hello_all_questions.vendingmachine.model;

/**
 * Accepted coin denominations. Each carries its value in cents — same money
 * discipline as Parking Lot / Splitwise / PaymentGateway (`long` cents, never doubles).
 */
public enum Coin {
    PENNY(1),
    NICKEL(5),
    DIME(10),
    QUARTER(25);

    private final int cents;

    Coin(int cents) { this.cents = cents; }

    public int getCents() { return cents; }
}
