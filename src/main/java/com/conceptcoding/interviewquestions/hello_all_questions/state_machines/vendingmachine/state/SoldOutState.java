package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.vendingmachine.state;

import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.vendingmachine.VendingMachine;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.vendingmachine.model.Coin;

// Nothing left to sell in ANY slot. Refuse coins up front (so nobody pays for nothing).
// Restocking moves the machine back to NoCoin (see VendingMachine.stockProduct).
// Adding this state touched no other state class: that's the State pattern's payoff.
public class SoldOutState implements VendingMachineState {

    private final VendingMachine machine;

    public SoldOutState(VendingMachine machine) { this.machine = machine; }

    @Override
    public void insertCoin(Coin coin) {
        throw new IllegalStateException("Sold out — coin returned");
    }

    @Override
    public void selectProduct(String slot) {
        throw new IllegalStateException("Sold out");
    }

    @Override
    public void dispense() {
        throw new IllegalStateException("Sold out");
    }

    @Override
    public void cancel() {
        System.out.println("  cancel: nothing to refund");
    }
}
