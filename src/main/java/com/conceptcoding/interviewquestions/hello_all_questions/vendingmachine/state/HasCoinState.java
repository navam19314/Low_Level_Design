package com.conceptcoding.interviewquestions.hello_all_questions.vendingmachine.state;

import com.conceptcoding.interviewquestions.hello_all_questions.vendingmachine.VendingMachine;
import com.conceptcoding.interviewquestions.hello_all_questions.vendingmachine.model.Coin;
import com.conceptcoding.interviewquestions.hello_all_questions.vendingmachine.model.Product;

/**
 * Coins inserted, balance > 0. User can insert more coins, select a product
 * (if balance covers price + stock available), or cancel to get a refund.
 */
public class HasCoinState implements VendingMachineState {

    private final VendingMachine machine;

    public HasCoinState(VendingMachine machine) { this.machine = machine; }

    @Override
    public void insertCoin(Coin coin) {
        machine.addBalance(coin.getCents());
        System.out.println("  inserted " + coin + " — balance now " + machine.getBalanceCents() + "c");
        // stay in HasCoin
    }

    @Override
    public void selectProduct(String slot) {
        Product product = machine.getProduct(slot);
        if (product == null) {
            throw new IllegalArgumentException("Unknown slot: " + slot);
        }
        if (machine.getStock(slot) <= 0) {
            throw new IllegalStateException("Out of stock: " + product.name() + " (" + slot + ")");
        }
        if (machine.getBalanceCents() < product.priceCents()) {
            throw new IllegalStateException("Insufficient balance — need "
                    + product.priceCents() + "c, have " + machine.getBalanceCents() + "c");
        }
        machine.setSelectedSlot(slot);
        System.out.println("  selected " + product.name() + " (" + slot + ") — proceeding to dispense");
        machine.setState(machine.dispensingState());      // transition
        machine.getState().dispense();                     // immediately dispense
    }

    @Override
    public void dispense() {
        throw new IllegalStateException("Select a product before dispensing");
    }

    @Override
    public void cancel() {
        int refund = machine.getBalanceCents();
        machine.setBalanceCents(0);
        System.out.println("  cancelled — refunding " + refund + "c");
        machine.setState(machine.noCoinState());          // transition
    }
}
