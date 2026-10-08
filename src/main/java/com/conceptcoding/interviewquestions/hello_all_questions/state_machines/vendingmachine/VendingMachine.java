package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.vendingmachine;

import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.vendingmachine.model.Coin;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.vendingmachine.model.Product;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.vendingmachine.state.DispensingState;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.vendingmachine.state.HasCoinState;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.vendingmachine.state.NoCoinState;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.vendingmachine.state.SoldOutState;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.vendingmachine.state.VendingMachineState;

import java.util.HashMap;
import java.util.Map;

// Context for the State pattern. Holds the data (balance, inventory, stock)
// and the current state — but delegates ALL behavior to the state object.
public class VendingMachine {

    // Three state objects — constructed once, reused forever (effectively flyweights).
    private final VendingMachineState noCoinState;
    private final VendingMachineState hasCoinState;
    private final VendingMachineState dispensingState;
    private final VendingMachineState soldOutState;

    private VendingMachineState currentState;

    private final Map<String, Product> productsBySlot = new HashMap<>();
    private final Map<String, Integer> stockBySlot    = new HashMap<>();

    private int balance = 0;
    private String selectedSlot;   // handoff from HasCoin → Dispensing

    // Build all three state objects UP FRONT and keep them forever.
    // Why not create new ones on every transition? States are stateless policy — they only
    // hold a back-reference to the machine, nothing per-transition. Reusing them = zero garbage,
    // and it makes `setState(noCoinState)` a pointer swap (no allocation).
    //
    // Start position: SoldOut (nothing stocked yet); the first stockProduct() makes it NoCoin.
    public VendingMachine() {
        this.noCoinState     = new NoCoinState(this);
        this.hasCoinState    = new HasCoinState(this);
        this.dispensingState = new DispensingState(this);
        this.soldOutState    = new SoldOutState(this);
        this.currentState    = soldOutState;      // empty until the first stockProduct()
    }

    // Admin/refill operation — put a product in a slot and add units to its stock.
    //
    // Example:
    //   stockProduct(new Product("A1", "Soda", 15), 5)   → slot A1 holds 5 Sodas @ ₹15
    //   stockProduct(new Product("A1", "Soda", 15), 3)   → same slot, now 8 (merge SUMS)
    //
    // Why `merge(..., Integer::sum)`? If the slot already has stock, we ADD to it (restocking).
    // Plain `put` would overwrite and silently lose stock.
    public synchronized void stockProduct(Product product, int count) {
        if (count <= 0) throw new IllegalArgumentException("Restock count must be > 0");
        String slot = product.getSlot();
        productsBySlot.put(slot, product);
        stockBySlot.put(slot, stockBySlot.getOrDefault(slot, 0) + count);
        if (currentState == soldOutState) currentState = noCoinState;   // something to sell again
    }

    // ─── Public API ─────────────────────────────────────────────────────────────
    // Every user action is a ONE-LINER that forwards to whichever state is active.
    // This is the ENTIRE point of the State pattern: the context is dumb, the state decides.
    //
    // Example: user calls vm.selectProduct("A1")
    //   - if currentState is NoCoin      → NoCoinState throws "insert a coin first"
    //   - if currentState is HasCoin     → HasCoinState validates & chains to Dispensing
    //   - if currentState is Dispensing  → DispensingState throws "already dispensing"
    //
    // Notice: no `if/else` on state anywhere here. That's the win vs. an enum-machine.
    //
    // synchronized: the coin slot, keypad and cancel button can fire on different hardware threads.
    public synchronized void insertCoin(Coin coin)      { currentState.insertCoin(coin); }
    public synchronized void selectProduct(String slot) { currentState.selectProduct(slot); }
    public synchronized void dispense()                 { currentState.dispense(); }
    public synchronized void cancel()                   { currentState.cancel(); }

    // Accessors used by state objects.
    public VendingMachineState getState()          { return currentState; }
    public void setState(VendingMachineState s)    { this.currentState = s; }

    public VendingMachineState noCoinState()       { return noCoinState; }
    public VendingMachineState hasCoinState()      { return hasCoinState; }
    public VendingMachineState dispensingState()   { return dispensingState; }
    public VendingMachineState soldOutState()      { return soldOutState; }

    public int  getBalance()               { return balance; }
    public void setBalance(int value)      { this.balance = value; }
    public void addBalance(int value)      { this.balance += value; }

    public String getSelectedSlot()             { return selectedSlot; }
    public void   setSelectedSlot(String slot)  { this.selectedSlot = slot; }

    public Product getProduct(String slot)      { return productsBySlot.get(slot); }
    public int     getStock(String slot)        { return stockBySlot.getOrDefault(slot, 0); }
    public void    decrementStock(String slot)  { stockBySlot.put(slot, stockBySlot.get(slot) - 1); }

    public boolean isEmpty() {
        for (int count : stockBySlot.values()) if (count > 0) return false;
        return true;
    }
}
