package com.conceptcoding.interviewquestions.hello_all_questions.vendingmachine.state;

import com.conceptcoding.interviewquestions.hello_all_questions.vendingmachine.VendingMachine;
import com.conceptcoding.interviewquestions.hello_all_questions.vendingmachine.model.Coin;
import com.conceptcoding.interviewquestions.hello_all_questions.vendingmachine.model.Product;

/**
 * Transient state — exists only while a product is being released. All other
 * operations are rejected until dispense completes and transitions back to NoCoin.
 */
public class DispensingState implements VendingMachineState {

    private final VendingMachine machine;

    public DispensingState(VendingMachine machine) { this.machine = machine; }

    @Override
    public void insertCoin(Coin coin) {
        throw new IllegalStateException("Wait for current dispense to finish");
    }

    @Override
    public void selectProduct(String slot) {
        throw new IllegalStateException("Already dispensing");
    }

    @Override
    public void dispense() {
        String slot = machine.getSelectedSlot();
        Product product = machine.getProduct(slot);
        int change = machine.getBalanceCents() - product.priceCents();

        machine.decrementStock(slot);
        System.out.println("  dispensing " + product.name() + " (" + slot + ")");
        if (change > 0) {
            System.out.println("  returning change: " + change + "c");
        }

        // Reset and transition home.
        machine.setBalanceCents(0);
        machine.setSelectedSlot(null);
        machine.setState(machine.noCoinState());
    }

    @Override
    public void cancel() {
        throw new IllegalStateException("Cannot cancel during dispense");
    }
}
