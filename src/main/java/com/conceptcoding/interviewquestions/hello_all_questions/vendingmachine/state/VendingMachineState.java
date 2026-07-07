package com.conceptcoding.interviewquestions.hello_all_questions.vendingmachine.state;

import com.conceptcoding.interviewquestions.hello_all_questions.vendingmachine.model.Coin;

// GoF State pattern — one class per state, all implementing this contract.
// Each state decides how to react (or reject) for each of the four events.
//
// Why not an enum with a switch? Because the reaction to the SAME event differs
// drastically per state — insertCoin adds balance in NoCoin/HasCoin but THROWS in
// Dispensing. Encoding that as switch statements scattered around means every new
// state or event forces edits in multiple places. With this interface, a new state
// = ONE new class; existing states never change.
public interface VendingMachineState {

    // User dropped a coin in the slot. State decides whether to accept, add balance, and/or transition.
    void insertCoin(Coin coin);

    // User pressed a slot button (e.g. "A1"). State decides whether the machine is ready to sell.
    void selectProduct(String slot);

    // Actually release the product. Only meaningful in DispensingState; other states throw.
    void dispense();

    // User pressed cancel. State decides whether there's a refund to issue or if it's a no-op.
    void cancel();
}
