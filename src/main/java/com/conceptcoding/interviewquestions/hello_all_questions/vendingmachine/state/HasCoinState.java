package com.conceptcoding.interviewquestions.hello_all_questions.vendingmachine.state;

import com.conceptcoding.interviewquestions.hello_all_questions.vendingmachine.VendingMachine;
import com.conceptcoding.interviewquestions.hello_all_questions.vendingmachine.model.Coin;
import com.conceptcoding.interviewquestions.hello_all_questions.vendingmachine.model.Product;

// Balance > 0. Accept more coins, allow selection (if valid), or cancel to refund.
public class HasCoinState implements VendingMachineState {

    private final VendingMachine machine;

    public HasCoinState(VendingMachine machine) { this.machine = machine; }

    // User keeps feeding coins — accumulate, stay in HasCoin.
    // No transition here: we're already in HasCoin, more coins just increase the balance.
    //
    // Example: balance=25 (QUARTER), user adds a DIME → balance=35, still HasCoin.
    @Override
    public void insertCoin(Coin coin) {
        machine.addBalance(coin.getCents());
        System.out.println("  inserted " + coin + " — balance now " + machine.getBalanceCents() + "c");
    }

    // THE core method of the whole problem. Three guards, then a two-step chain.
    //
    // Guards (order matters — most-specific error first):
    //   1. Unknown slot          → IllegalArgumentException (bad input, not bad state)
    //   2. Out of stock          → IllegalStateException     (state can't fulfill)
    //   3. Insufficient balance  → IllegalStateException     (state can't fulfill)
    //
    // Then the two-step chain — this is the trick most candidates miss:
    //   a. Save the chosen slot on the machine (Dispensing needs to know what to release).
    //   b. Transition to DispensingState.
    //   c. IMMEDIATELY call dispense() on the (now-current) DispensingState.
    //
    // Why chain? A vending machine dispenses AUTOMATICALLY after a valid selection —
    // the user doesn't press two buttons. But the two events (select, dispense) are
    // conceptually distinct, so we model them as two state operations and chain them.
    //
    // Worked example: balance=75c, slot A1 = "Soda 75c", stock=5
    //   guards pass → setSelectedSlot("A1") → setState(Dispensing) → Dispensing.dispense()
    //   result: Soda released, balance reset to 0, back to NoCoinState.
    @Override
    public void selectProduct(String slot) {
        Product product = machine.getProduct(slot);
        if (product == null) {
            throw new IllegalArgumentException("Unknown slot: " + slot);
        }
        if (machine.getStock(slot) <= 0) {
            throw new IllegalStateException("Out of stock: " + product.getName() + " (" + slot + ")");
        }
        if (machine.getBalanceCents() < product.getPriceCents()) {
            throw new IllegalStateException("Insufficient balance — need "
                    + product.getPriceCents() + "c, have " + machine.getBalanceCents() + "c");
        }
        machine.setSelectedSlot(slot);
        System.out.println("  selected " + product.getName() + " (" + slot + ") — proceeding to dispense");

        // Auto-chain: transition to Dispensing, then invoke dispense() on the NEW current state.
        machine.setState(machine.dispensingState());
        machine.getState().dispense();
    }

    // Illegal — dispense is auto-triggered inside selectProduct. A direct call means
    // the caller skipped selectProduct, which is a programming error.
    @Override
    public void dispense() {
        throw new IllegalStateException("Select a product before dispensing");
    }

    // User changed their mind. Refund the whole balance and return to idle.
    //
    // Example: balance=20 (2 DIMES), user hits cancel
    //   refund = 20c → print → balance=0 → setState(NoCoin)
    //
    // Note: we snapshot `refund` BEFORE resetting balance to 0. If we printed
    // machine.getBalanceCents() after the reset it would always say "refunding 0c".
    @Override
    public void cancel() {
        int refund = machine.getBalanceCents();
        machine.setBalanceCents(0);
        System.out.println("  cancelled — refunding " + refund + "c");
        machine.setState(machine.noCoinState());
    }
}
