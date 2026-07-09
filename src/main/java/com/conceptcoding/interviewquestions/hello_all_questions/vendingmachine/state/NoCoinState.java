package com.conceptcoding.interviewquestions.hello_all_questions.vendingmachine.state;

import com.conceptcoding.interviewquestions.hello_all_questions.vendingmachine.VendingMachine;
import com.conceptcoding.interviewquestions.hello_all_questions.vendingmachine.model.Coin;

// Idle state — the only useful action is inserting a coin.
public class NoCoinState implements VendingMachineState {

    private final VendingMachine machine;

    public NoCoinState(VendingMachine machine) { this.machine = machine; }

    // The ONLY event that moves us forward from idle.
    //
    // Example: user inserts a TEN (₹10)
    //   1. balance goes from 0 → 10
    //   2. setState(hasCoinState) — next event (another coin, selectProduct, cancel)
    //      will be handled by HasCoinState, NOT this class.
    //
    // Note: even a single coin puts us in HasCoin. We don't wait for "enough" coins —
    // "enough" is only checked when user picks a product (in HasCoinState.selectProduct).
    @Override
    public void insertCoin(Coin coin) {
        machine.addBalance(coin.getValue());
        System.out.println("  inserted " + coin + " — balance now ₹" + machine.getBalance());
        machine.setState(machine.hasCoinState());
    }

    // Illegal from idle — you can't pick a product without paying first.
    // Throwing is the correct response: it signals the caller (or the UI wrapper)
    // that this event doesn't belong here.
    @Override
    public void selectProduct(String slot) {
        throw new IllegalStateException("Insert a coin before selecting a product");
    }

    // Also illegal — nothing has been selected, nothing to release.
    @Override
    public void dispense() {
        throw new IllegalStateException("Nothing to dispense — insert a coin and select a product");
    }

    // Cancel with nothing to refund. Not illegal (users press cancel by mistake all the time),
    // just a no-op with a diagnostic print.
    @Override
    public void cancel() {
        System.out.println("  cancel: nothing to refund");
    }
}
