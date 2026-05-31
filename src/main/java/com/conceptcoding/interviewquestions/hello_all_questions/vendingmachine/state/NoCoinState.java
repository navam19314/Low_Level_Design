package com.conceptcoding.interviewquestions.hello_all_questions.vendingmachine.state;

import com.conceptcoding.interviewquestions.hello_all_questions.vendingmachine.VendingMachine;
import com.conceptcoding.interviewquestions.hello_all_questions.vendingmachine.model.Coin;

/**
 * Initial / idle state. Only {@code insertCoin} is meaningful — everything
 * else is rejected with a clear error message.
 */
public class NoCoinState implements VendingMachineState {

    private final VendingMachine machine;

    public NoCoinState(VendingMachine machine) { this.machine = machine; }

    @Override
    public void insertCoin(Coin coin) {
        machine.addBalance(coin.getCents());
        System.out.println("  inserted " + coin + " — balance now " + machine.getBalanceCents() + "c");
        machine.setState(machine.hasCoinState());     // transition
    }

    @Override
    public void selectProduct(String slot) {
        throw new IllegalStateException("Insert a coin before selecting a product");
    }

    @Override
    public void dispense() {
        throw new IllegalStateException("Nothing to dispense — insert a coin and select a product");
    }

    @Override
    public void cancel() {
        // Already at zero balance; cancel is a no-op.
        System.out.println("  cancel: nothing to refund");
    }
}
