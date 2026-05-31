package com.conceptcoding.interviewquestions.hello_all_questions.vendingmachine;

import com.conceptcoding.interviewquestions.hello_all_questions.vendingmachine.model.Coin;
import com.conceptcoding.interviewquestions.hello_all_questions.vendingmachine.model.Product;
import com.conceptcoding.interviewquestions.hello_all_questions.vendingmachine.state.DispensingState;
import com.conceptcoding.interviewquestions.hello_all_questions.vendingmachine.state.HasCoinState;
import com.conceptcoding.interviewquestions.hello_all_questions.vendingmachine.state.NoCoinState;
import com.conceptcoding.interviewquestions.hello_all_questions.vendingmachine.state.VendingMachineState;

import java.util.HashMap;
import java.util.Map;

/**
 * Context class for the State pattern. Holds the current state + all the data
 * the states need (balance, inventory, stock counts) — but delegates ALL
 * behavior to the current state object.
 *
 * <p>The state OBJECTS themselves are stateless (just policy); they read and
 * mutate this context. State transitions are triggered BY states calling
 * {@code setState(next)}.
 */
public class VendingMachine {

    // Three state objects — built once, reused forever.
    private final VendingMachineState noCoinState;
    private final VendingMachineState hasCoinState;
    private final VendingMachineState dispensingState;

    private VendingMachineState currentState;

    private final Map<String, Product> productsBySlot = new HashMap<>();
    private final Map<String, Integer> stockBySlot     = new HashMap<>();

    private int balanceCents = 0;
    private String selectedSlot;          // set by HasCoin → DispensingState handoff

    public VendingMachine() {
        // Construct states with a back-reference to this machine.
        this.noCoinState     = new NoCoinState(this);
        this.hasCoinState    = new HasCoinState(this);
        this.dispensingState = new DispensingState(this);
        this.currentState    = noCoinState;
    }

    public void stockProduct(Product product, int count) {
        productsBySlot.put(product.slot(), product);
        stockBySlot.merge(product.slot(), count, Integer::sum);
    }

    // ----- public ops — all delegate to current state -----

    public void insertCoin(Coin coin)         { currentState.insertCoin(coin); }
    public void selectProduct(String slot)    { currentState.selectProduct(slot); }
    public void dispense()                    { currentState.dispense(); }
    public void cancel()                      { currentState.cancel(); }

    // ----- accessors used by state objects -----

    public VendingMachineState getState()     { return currentState; }
    public void setState(VendingMachineState s) { this.currentState = s; }

    public VendingMachineState noCoinState()    { return noCoinState; }
    public VendingMachineState hasCoinState()   { return hasCoinState; }
    public VendingMachineState dispensingState(){ return dispensingState; }

    public int getBalanceCents()              { return balanceCents; }
    public void setBalanceCents(int cents)    { this.balanceCents = cents; }
    public void addBalance(int cents)         { this.balanceCents += cents; }

    public String getSelectedSlot()           { return selectedSlot; }
    public void setSelectedSlot(String slot)  { this.selectedSlot = slot; }

    public Product getProduct(String slot)    { return productsBySlot.get(slot); }
    public int getStock(String slot)          { return stockBySlot.getOrDefault(slot, 0); }
    public void decrementStock(String slot)   { stockBySlot.merge(slot, -1, Integer::sum); }
}
