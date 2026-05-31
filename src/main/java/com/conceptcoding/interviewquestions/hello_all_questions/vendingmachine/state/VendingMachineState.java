package com.conceptcoding.interviewquestions.hello_all_questions.vendingmachine.state;

import com.conceptcoding.interviewquestions.hello_all_questions.vendingmachine.model.Coin;

/**
 * State pattern contract — every state implements the same four operations.
 * The state object decides what to do (or to reject as illegal) for each call.
 *
 * <p>This is the textbook GoF State pattern: one CLASS per state (not just an
 * enum value), and per-state behavior lives on the class. The VendingMachine
 * holds a reference to the current state and delegates everything to it.
 *
 * <p>States transition each other by calling {@code machine.setState(next)} —
 * the state itself decides the next state for each event it accepts.
 */
public interface VendingMachineState {

    /** User inserted a coin. */
    void insertCoin(Coin coin);

    /** User selected a product by slot. */
    void selectProduct(String slot);

    /** User requested dispense (or it's auto-triggered after selectProduct). */
    void dispense();

    /** User pressed cancel — return whatever balance is present. */
    void cancel();
}
