package com.conceptcoding.interviewquestions.hello_all_questions.vendingmachine.state;

import com.conceptcoding.interviewquestions.hello_all_questions.vendingmachine.VendingMachine;
import com.conceptcoding.interviewquestions.hello_all_questions.vendingmachine.model.Coin;
import com.conceptcoding.interviewquestions.hello_all_questions.vendingmachine.model.Product;

// Transient — release the product, return change, transition back to NoCoin.
public class DispensingState implements VendingMachineState {

    private final VendingMachine machine;

    public DispensingState(VendingMachine machine) { this.machine = machine; }

    // No new coins mid-dispense — physically the coin slot is locked while the motor runs.
    @Override
    public void insertCoin(Coin coin) {
        throw new IllegalStateException("Wait for current dispense to finish");
    }

    // Can't pick another product mid-dispense either.
    @Override
    public void selectProduct(String slot) {
        throw new IllegalStateException("Already dispensing");
    }

    // The "release the product and reset" method. Called once, chained from HasCoin.
    //
    // Four things happen here, in order:
    //   1. Compute change   = balanceCents - price   (may be 0 for exact payment)
    //   2. Decrement stock  (one less unit in this slot)
    //   3. Print dispense + change lines
    //   4. RESET everything: balance=0, selectedSlot=null, state=NoCoin
    //
    // Why reset ALL three? Because we're going back to idle. If we left `selectedSlot`
    // set, the next dispense() (in some future session) would try to release the OLD
    // product — a stale-state bug.
    //
    // Worked example: user paid 100c for a 75c Soda
    //   change = 100 - 75 = 25 → "returning change: 25c"
    //   stock[A1]-- → 4 left
    //   balance = 0, selectedSlot = null, state = NoCoin
    //   Machine is ready for the next customer.
    @Override
    public void dispense() {
        String slot = machine.getSelectedSlot();
        Product product = machine.getProduct(slot);
        int change = machine.getBalanceCents() - product.getPriceCents();

        machine.decrementStock(slot);
        System.out.println("  dispensing " + product.getName() + " (" + slot + ")");
        if (change > 0) {
            System.out.println("  returning change: " + change + "c");
        }

        // Reset all transient state and return to idle.
        machine.setBalanceCents(0);
        machine.setSelectedSlot(null);
        machine.setState(machine.noCoinState());
    }

    // Can't cancel mid-dispense — the motor is running, the product is dropping.
    // Once the physical action starts, only the machine controls when it ends.
    @Override
    public void cancel() {
        throw new IllegalStateException("Cannot cancel during dispense");
    }
}
