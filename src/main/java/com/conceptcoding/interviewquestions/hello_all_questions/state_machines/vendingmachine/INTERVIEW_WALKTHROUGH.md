# Vending Machine

> **Amazon:** ★ reported in 2026. Also a Microsoft favourite. **The** textbook State-pattern question: if you can do this one cold, you own the State pattern.
>
> **The crux (what's really being tested):**
> 1. **State pattern:** the same button does different things depending on the machine's state, with no `if (state == ...)` anywhere.
> 2. **Guards in the right order:** unknown slot → out of stock → not enough money.
> 3. **Money handling:** integer rupees, refunds on cancel, change, and (follow-up) *can we even give change?*
>
> **Family:** F3 State machine. See [foundations](../../00_AMAZON_LLD_FOUNDATIONS.md). Compare with [Insurance](../insurance/INTERVIEW_WALKTHROUGH.md) (also State) and [Food Delivery](../../matching/fooddelivery/INTERVIEW_WALKTHROUGH.md) (an enum is enough there).

---

## 1. Plain-language picture

### The machine has moods
```
SOLD OUT   →  nothing to sell. Coins are pushed straight back out.
NO COIN    →  waiting. Pressing "A1" does nothing ("insert coin first").
HAS COIN   →  money inside. More coins add up; pressing "A1" buys (if enough money + in stock); cancel refunds.
DISPENSING →  the motor is running. Every button is ignored until the product drops.
```
**Same button, different reaction per mood.** Pressing "A1" in NO COIN says "insert coin"; in HAS COIN it buys; in DISPENSING it says "wait".

### Two ways to code that
```java
// ✗ the if-ladder: every method checks every state; a new state edits every method
void selectProduct(String slot) {
    if (state == NO_COIN) ...
    else if (state == HAS_COIN) ...
    else if (state == DISPENSING) ...
    else if (state == SOLD_OUT) ...     // forgot this in one method → bug
}

// ✓ State pattern: the machine just forwards; the current state object decides
void selectProduct(String slot) { currentState.selectProduct(slot); }
```
Each state is a class that knows its own reaction to every button. **Adding SOLD OUT = one new class**; the other states don't change. (This deck added `SoldOutState` exactly that way.)

### A purchase
```
NO COIN  --insert ₹10-->  HAS COIN (₹10)  --insert ₹10-->  HAS COIN (₹20)
HAS COIN --press A1 (Soda ₹15)--> checks pass --> DISPENSING --> drop Soda, return ₹5 --> NO COIN
                                                                     (or SOLD OUT if that was the last item)
```

---

## 2. From story to design

| # | Decision | Rejected | Why |
|---|---|---|---|
| D1 | **State pattern**: `VendingMachineState` interface + one class per state | an enum + `switch` in every method | Every method's behaviour depends on the state. With switches, 4 events × 4 states = 16 branches spread over 4 methods. |
| D2 | `VendingMachine` (the *context*) holds the data (balance, stock, selected slot) and **forwards** every action | the states holding data | States are pure behaviour, so they can be shared. |
| D3 | State objects built **once** in the constructor, reused (`setState(noCoinState)`) | `new HasCoinState()` on every transition | No garbage; a transition is a pointer swap. |
| D4 | `selectProduct` in HAS COIN chains into DISPENSING and calls `dispense()` | making the user press a second "dispense" button | A real machine dispenses automatically, but "select" and "release" are still separate steps in the model. |
| D5 | Guards: unknown slot → out of stock → insufficient balance | any order | The most specific/real problem first; `IllegalArgumentException` for bad input vs `IllegalStateException` for "can't right now". |
| D6 | **SOLD OUT** state when every slot is empty; restock → NO COIN | finding out only when the product is selected | Don't take a coin you can't sell against. |
| D7 | Money as `int` rupees, `Coin` enum with values (1, 2, 5, 10, 20) | `double` | No rounding; only valid denominations exist. |
| D8 | Public actions `synchronized` | no locking | The coin slot, keypad and cancel button can fire on different hardware threads. |
| D9 | Out of base: coin inventory for change, card/UPI payment, admin mode | building them | Follow-ups (§5). |

### Class shape
```
VendingMachine                         ← CONTEXT: data + forwards every action to currentState
  noCoinState · hasCoinState · dispensingState · soldOutState   (built once)
  currentState · balance · selectedSlot · Map<slot, Product> · Map<slot, stock>
  insertCoin · selectProduct · dispense · cancel · stockProduct

«interface» VendingMachineState   insertCoin · selectProduct · dispense · cancel
  ├── SoldOutState     refuses everything (cancel = no-op)
  ├── NoCoinState      insertCoin → HAS COIN
  ├── HasCoinState     insertCoin (add) · selectProduct (guards → DISPENSING) · cancel (refund → NO COIN)
  └── DispensingState  dispense (stock−1, change, reset → NO COIN / SOLD OUT)

Coin enum (1, 2, 5, 10, 20)   Product { slot, name, price }
```

---

## 3. Patterns that earn their place

| Pattern | In the base? | Problem it solves | Without it |
|---|---|---|---|
| **State** | **Yes** | every action behaves differently per state | if-ladders in 4 methods; a 5th state edits all of them |
| **Strategy** (`PaymentMethod`: coins / card / UPI) | No | Q3 | payment logic tangled with states |

**Say:** *"The State pattern, because the reaction to the same button differs per state. Adding SoldOut was one new class with no other state changed."*

**State vs enum (know this cold):** use **classes** when the states **behave** differently (here: the same `selectProduct` buys, refuses, or waits). Use an **enum + `canMoveTo`** when states only limit the next transition (Food Delivery's order status).

**Tempting but wrong:** Singleton machine (a building has many), Observer (nothing listens yet; an "out of stock" alert to the supplier is a follow-up), Factory for products.

---

## 4. The 45-minute run

### Clarify (min 0–5)
> **You:** Products in slots with a price and stock. The user inserts coins, picks a slot, gets the product and change. Cancel refunds?
> **Interviewer:** Yes.
> **You:** Which coins? I'll use ₹1, 2, 5, 10, 20.
> **Interviewer:** Fine.
> **You:** What if a slot is empty, or the whole machine is empty?
> **Interviewer:** Reject the selection; when it's fully empty, don't take coins.
> **You:** Does the machine have unlimited change?
> **Interviewer:** Assume yes for now.

```
In scope:  insertCoin · selectProduct (auto-dispense) · cancel (refund) · stockProduct
           states: SoldOut, NoCoin, HasCoin, Dispensing · guards with reasons · change returned
Out:       limited coin inventory for change, card/UPI, admin mode, multiple items per purchase
```

### Timeline
| Min | Do |
|---|---|
| 5–9 | Draw the 4 states and their arrows. Say *"State pattern"* and why (§3). |
| 9–12 | Code step 1: `VendingMachineState` interface, `Coin`, `Product`. |
| 12–20 | Code step 2: `VendingMachine` (context: built-once states, data, forwarding). |
| 20–33 | Code step 3: the states. **`HasCoinState`** first (guards + chain), then `DispensingState`, `NoCoinState`, `SoldOutState`. |
| 33–38 | Dry run: ₹20 for a ₹15 Soda; then the last item → SOLD OUT. |
| 38–45 | Follow-ups: change inventory (Q1). |

### The code you write, in this order

**Step 1: the contract + values** ([state/VendingMachineState.java](state/VendingMachineState.java), [model/](model/))
```java
public interface VendingMachineState {
    void insertCoin(Coin coin);
    void selectProduct(String slot);
    void dispense();
    void cancel();
}

public enum Coin {
    ONE(1), TWO(2), FIVE(5), TEN(10), TWENTY(20);
    private final int value;
    Coin(int value) { this.value = value; }
    public int getValue() { return value; }
}

public class Product {
    private final String slot;              // "A1"
    private final String name;
    private final int price;                // rupees
    // constructor + getters
}
```

**Step 2: the context** ([VendingMachine.java](VendingMachine.java))
```java
public class VendingMachine {
    // built once, reused forever: a transition is just a pointer swap
    private final VendingMachineState noCoinState     = new NoCoinState(this);
    private final VendingMachineState hasCoinState    = new HasCoinState(this);
    private final VendingMachineState dispensingState = new DispensingState(this);
    private final VendingMachineState soldOutState    = new SoldOutState(this);
    private VendingMachineState currentState = soldOutState;     // empty until stocked

    private final Map<String, Product> productsBySlot = new HashMap<>();
    private final Map<String, Integer> stockBySlot    = new HashMap<>();
    private int balance = 0;
    private String selectedSlot;                                  // handed from HasCoin to Dispensing

    public synchronized void stockProduct(Product product, int count) {
        if (count <= 0) throw new IllegalArgumentException("Restock count must be > 0");
        productsBySlot.put(product.getSlot(), product);
        stockBySlot.merge(product.getSlot(), count, Integer::sum);   // restock ADDS
        if (currentState == soldOutState) currentState = noCoinState;
    }

    // every user action just forwards to the current state: no if/else on state anywhere
    public synchronized void insertCoin(Coin coin)      { currentState.insertCoin(coin); }
    public synchronized void selectProduct(String slot) { currentState.selectProduct(slot); }
    public synchronized void dispense()                 { currentState.dispense(); }
    public synchronized void cancel()                   { currentState.cancel(); }

    // used by the states
    public void setState(VendingMachineState s) { this.currentState = s; }
    public VendingMachineState getState()       { return currentState; }
    public VendingMachineState noCoinState()     { return noCoinState; }
    public VendingMachineState hasCoinState()    { return hasCoinState; }
    public VendingMachineState dispensingState() { return dispensingState; }
    public VendingMachineState soldOutState()    { return soldOutState; }

    public int  getBalance()          { return balance; }
    public void setBalance(int v)     { balance = v; }
    public void addBalance(int v)     { balance += v; }
    public String getSelectedSlot()   { return selectedSlot; }
    public void setSelectedSlot(String s) { selectedSlot = s; }
    public Product getProduct(String slot)     { return productsBySlot.get(slot); }
    public int getStock(String slot)           { return stockBySlot.getOrDefault(slot, 0); }
    public void decrementStock(String slot)    { stockBySlot.put(slot, stockBySlot.get(slot) - 1); }
    public boolean isEmpty() {
        for (int count : stockBySlot.values()) if (count > 0) return false;
        return true;
    }
}
```
In an interview, it's fine to initialise the states in field declarations like this. The file does it in the constructor, which works the same.

**Step 3: the states** ([state/](state/))
```java
public class HasCoinState implements VendingMachineState {      // ← the core of the problem
    private final VendingMachine machine;
    public HasCoinState(VendingMachine machine) { this.machine = machine; }

    public void insertCoin(Coin coin) { machine.addBalance(coin.getValue()); }      // stay in HasCoin

    public void selectProduct(String slot) {
        Product product = machine.getProduct(slot);
        if (product == null) throw new IllegalArgumentException("Unknown slot: " + slot);
        if (machine.getStock(slot) <= 0) {
            throw new IllegalStateException("Out of stock: " + product.getName() + " (" + slot + ")");
        }
        if (machine.getBalance() < product.getPrice()) {
            throw new IllegalStateException("Insufficient balance — need ₹" + product.getPrice()
                    + ", have ₹" + machine.getBalance());
        }
        machine.setSelectedSlot(slot);
        machine.setState(machine.dispensingState());              // transition...
        machine.getState().dispense();                             // ...and auto-dispense
    }

    public void dispense() { throw new IllegalStateException("Select a product before dispensing"); }

    public void cancel() {
        int refund = machine.getBalance();                         // read BEFORE resetting
        machine.setBalance(0);
        System.out.println("  cancelled — refunding ₹" + refund);
        machine.setState(machine.noCoinState());
    }
}

public class DispensingState implements VendingMachineState {
    private final VendingMachine machine;
    public DispensingState(VendingMachine machine) { this.machine = machine; }

    public void insertCoin(Coin coin)       { throw new IllegalStateException("Wait for current dispense to finish"); }
    public void selectProduct(String slot)  { throw new IllegalStateException("Already dispensing"); }
    public void cancel()                    { throw new IllegalStateException("Cannot cancel during dispense"); }

    public void dispense() {
        String slot = machine.getSelectedSlot();
        Product product = machine.getProduct(slot);
        int change = machine.getBalance() - product.getPrice();
        machine.decrementStock(slot);
        System.out.println("  dispensing " + product.getName() + (change > 0 ? ", change ₹" + change : ""));
        machine.setBalance(0);                                     // reset EVERYTHING for the next customer
        machine.setSelectedSlot(null);
        machine.setState(machine.isEmpty() ? machine.soldOutState() : machine.noCoinState());
    }
}

public class NoCoinState implements VendingMachineState {
    private final VendingMachine machine;
    public NoCoinState(VendingMachine machine) { this.machine = machine; }

    public void insertCoin(Coin coin) {
        machine.addBalance(coin.getValue());
        machine.setState(machine.hasCoinState());
    }
    public void selectProduct(String slot) { throw new IllegalStateException("Insert a coin before selecting a product"); }
    public void dispense()                 { throw new IllegalStateException("Nothing to dispense"); }
    public void cancel()                   { System.out.println("  cancel: nothing to refund"); }   // harmless, not an error
}

public class SoldOutState implements VendingMachineState {
    private final VendingMachine machine;
    public SoldOutState(VendingMachine machine) { this.machine = machine; }

    public void insertCoin(Coin coin)      { throw new IllegalStateException("Sold out — coin returned"); }
    public void selectProduct(String slot) { throw new IllegalStateException("Sold out"); }
    public void dispense()                 { throw new IllegalStateException("Sold out"); }
    public void cancel()                   { System.out.println("  cancel: nothing to refund"); }
}
```
**Shape to remember:** each state = 4 methods; the legal ones change data and call `setState(...)`, and the illegal ones throw with a reason.

### Dry run
```
stockProduct(A1 Soda ₹15, 1)          SoldOut → NoCoin (something to sell)
insertCoin(TEN)                       NoCoin.insertCoin  → balance 10 → HasCoin
insertCoin(TEN)                       HasCoin.insertCoin → balance 20
selectProduct(A1)                     HasCoin: slot ok, stock 1, 20 ≥ 15 → Dispensing.dispense()
                                      change 5, stock 0, reset → machine empty → SoldOut
insertCoin(TEN)                       SoldOut → "Sold out — coin returned"
stockProduct(A1, 5)                   SoldOut → NoCoin
```

---

## 5. Follow-ups: answer out loud first, then open

<details>
<summary><b>Q1. The machine has a limited supply of coins. Don't sell if you can't give change.</b></summary>

Keep a coin inventory. Plan the change **before** dispensing; if it can't be made, refuse the sale (the user can cancel for a refund or insert exact money):
```java
public class CoinInventory {
    private final EnumMap<Coin, Integer> counts = new EnumMap<>(Coin.class);

    public void add(Coin coin, int n) { counts.merge(coin, n, Integer::sum); }

    // greedy, largest coin first; returns null if change can't be made with the coins we have
    public Map<Coin, Integer> planChange(int amount) {
        Map<Coin, Integer> plan = new EnumMap<>(Coin.class);
        Coin[] largestFirst = Coin.values();
        for (int i = largestFirst.length - 1; i >= 0 && amount > 0; i--) {
            Coin c = largestFirst[i];
            int use = Math.min(amount / c.getValue(), counts.getOrDefault(c, 0));
            if (use > 0) {
                plan.put(c, use);
                amount -= use * c.getValue();
            }
        }
        return amount == 0 ? plan : null;
    }

    public void remove(Map<Coin, Integer> plan) { plan.forEach((c, n) -> counts.merge(c, -n, Integer::sum)); }
}
```
In `HasCoinState.selectProduct`, add a 4th guard: `if (inventory.planChange(balance - price) == null) throw new IllegalStateException("Exact change only")`. In `insertCoin`, add the coin to the inventory.
- **Why greedy works:** ₹1/2/5/10/20 is a "canonical" coin system, where largest-first is always optimal. For odd systems (coins 1, 3, 4: change for 6 → greedy gives 4+1+1, optimal is 3+3), you'd need DP. Say this; it's a nice signal.
</details>

<details>
<summary><b>Q2. Add a new state, e.g. MAINTENANCE (the technician opened the machine).</b></summary>

One new class: `MaintenanceState` refuses all customer actions. Two new machine methods, `enterMaintenance()` (only from NoCoin/SoldOut, so you never trap a customer's money) and `exitMaintenance()` → NoCoin or SoldOut depending on stock. **No existing state class changes.** That's the reason for the pattern; say "Open/Closed".
</details>

<details>
<summary><b>Q3. Accept card and UPI too.</b></summary>

Payment becomes a **Strategy**: `PaymentMethod { boolean pay(int amount); void refund(int amount); }` with `CoinPayment`, `CardPayment`, `UpiPayment`. For card/UPI the flow is *select first, then pay the exact price*, so there's a new state `AwaitingPaymentState` (select → AwaitingPayment → pay ok → Dispensing). A payment timeout returns to NoCoin. The coin flow stays as it is.
</details>

<details>
<summary><b>Q4. Notify the supplier when a slot runs low.</b></summary>

**Observer**: `StockListener.onLowStock(slot, remaining)`, fired from `decrementStock` when stock drops below a threshold. The supplier app and a dashboard subscribe. Wrap each listener in try/catch, because a failed alert must never stop the dispense.
</details>

<details>
<summary><b>Q5. Buy several items in one go.</b></summary>

HAS COIN accumulates a **cart** (`Map<slot, qty>`) instead of buying immediately; a "checkout" button checks the total vs the balance and the stock of every slot, then DISPENSING releases each item. The guards are the same, just over a list. That's a good moment to say the state machine absorbs new flows without rewrites.
</details>

<details>
<summary><b>Q6. Power cut in the middle of dispensing.</b></summary>

Persist `{state, balance, selectedSlot}` before each transition (a tiny local store). On boot: if the saved state is DISPENSING, check the drop sensor. Product dropped → finish (return change); not dropped → refund the balance. Never lose the customer's money silently. Log every transaction for reconciliation.
</details>

<details>
<summary><b>Q7. How do you test it?</b></summary>

For each state, call all 4 actions and assert which are legal (the illegal ones throw with the right message). Then: the happy path with change, exact money (no change), insufficient balance, one slot out of stock while others still sell, the whole machine sold out (coins refused) → restock → buy again, and cancel refunds. All are in the driver.
</details>

---

## 6. Traps
1. `if (state == ...)` ladders instead of state classes.
2. `new XState()` on every transition (it works, but say why reusing is better).
3. Not resetting `balance` **and** `selectedSlot` after a dispense, so the next customer inherits them.
4. Printing the refund **after** zeroing the balance ("refunding ₹0").
5. Guards in the wrong order, or using the same exception type for bad input and wrong state.
6. Taking coins when the machine is empty.
7. `double` for money.

## 7. Recall check
1. Draw the 4 states and every arrow, with the action on each arrow.
2. What does `selectProduct` do in each of the 4 states?
3. Why are the state objects created once? What do they hold?
4. Why does HasCoin call `dispense()` itself after `setState(dispensing)`?
5. Change-making: when does greedy fail? Give a coin set.
6. State pattern here vs an enum in Food Delivery: the one-sentence difference.

**Rebuild in 12 minutes:** interface (4 methods) · `VendingMachine` (4 built-once states + data + forwarding) · `HasCoinState` (3 guards + chain + refund) · `DispensingState` (change, stock−1, reset, NoCoin/SoldOut) · `NoCoinState` · `SoldOutState`.

---

**Files:** `VendingMachine` (context) · `state/` (`VendingMachineState`, `NoCoinState`, `HasCoinState`, `DispensingState`, `SoldOutState`) · `model/` (`Coin`, `Product`) · `VendingMachineDriver` (happy path, insufficient money, cancel, slot out of stock, illegal actions, exact change, sold out → restock)
```bash
mvn -q compile exec:java -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.state_machines.vendingmachine.VendingMachineDriver
```
