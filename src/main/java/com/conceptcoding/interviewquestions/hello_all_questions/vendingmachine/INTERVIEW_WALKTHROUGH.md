# Vending Machine — 45-min LLD Interview Walkthrough

**Target role:** SDE-2 (Amazon, Microsoft, Adobe, Atlassian)

> The textbook GoF State pattern problem. The senior signal is one class per state, not enum + switch. Know the contrast: **class-per-state when behavior differs per state, enum-machine when only transitions differ** (PaymentGateway / JobScheduler).

---

## Time budget

| Step | Activity | Budget | Cumulative |
|------|----------|--------|------------|
| 1 | Requirements | ~4 min | 4 |
| 2 | Entities & relationships | ~4 min | 8 |
| 3 | Class design | ~12 min | 20 |
| 4 | Implementation + dry-run | ~16 min | 36 |
| 5 | Extensibility | ~8 min | 44 |
| — | Wrap | ~1 min | 45 |

---

## Mental models — memorize before you walk in

### M1. State pattern shape

```
             +----------------------------+
             |     VendingMachine         |   ← CONTEXT
             |  currentState: VMState     |
             |  insertCoin / selectProduct / dispense / cancel  (all DELEGATE)
             +----------------------------+
                         │  holds reference to
                         ▼
             +----------------------------+
             | <<interface>>              |
             | VendingMachineState        |
             +----------------------------+
                  ▲       ▲       ▲
                  │       │       │
          NoCoinState  HasCoinState  DispensingState

    Behavior LIVES on the state class:
       NoCoinState.selectProduct       → throw "insert coin first"
       HasCoinState.selectProduct      → validate → transition → chain dispense()
       DispensingState.selectProduct   → throw "already dispensing"

    Context is a thin shell — owns data, delegates all behavior.
```

### M2. Class-per-state vs enum-machine (the core signal)

```
   ENUM STATE MACHINE (Payment, Job)          GoF STATE PATTERN (Vending Machine)
   ─────────────────────────────────          ──────────────────────────────────
   1 class + Map<Status, Set<Status>>          N classes, same interface
   1 method: transitionTo(next)                Each class handles the SAME 4 events differently
   Only ALLOWED targets vary per state         The ACTION itself varies per state

   Use when:                                   Use when:
   - Every state does status = next            - insertCoin ADDS in NoCoin,
   - Only "who can go where" varies              STAYS in HasCoin, THROWS in Dispensing
                                               - Same event, materially different logic
```

**One-sentence test:** *"For the same event, is the ACTION different across states, or just whether it's ALLOWED?"*
Different action → class-per-state. Just allowed/not-allowed → enum-machine.

### M3. State transitions are SELF-DRIVEN

The CURRENT state decides the next state — never the context. Adding a new state (e.g. `MaintenanceState`) doesn't require touching `VendingMachine`.

```java
// From inside HasCoinState.selectProduct(...):
machine.setSelectedSlot(slot);
machine.setState(machine.dispensingState());   // transition self-driven
machine.getState().dispense();                  // chain — one event, two transitions
```

**Anti-pattern:** `if (status == HAS_COIN) { ... } else if ...` inside VendingMachine. That's back to an enum-machine.

---

## Step 1 — Requirements (~4 min)

### Clarifying dialogue

**You:** *"User actions — insert coins, select a product, and cancel? Anything else?"*
**Interviewer:** *"Those three. Dispense happens automatically after a valid selection."*
> Signals the auto-chain HasCoin → Dispensing.

**You:** *"Coin denominations — a fixed set? I'll assume ₹1, ₹2, ₹5, ₹10, ₹20."*
**Interviewer:** *"Fixed set is fine."*
> Enum is the right model.

**You:** *"Return change as an integer, or do we need physical coin-making?"*
**Interviewer:** *"Integer amount. Physical change-making is out of scope for v1."*

**You:** *"Illegal actions per state — throw a clear exception?"*
**Interviewer:** *"Yes. The state should decide what's illegal."*
> Direct signal for class-per-state.

**You:** *"Single-user or concurrent?"*
**Interviewer:** *"Single-user for v1."*
> No `synchronized` needed.

### Requirements to write down

```
IN SCOPE
1. Insert coins (₹1 / ₹2 / ₹5 / ₹10 / ₹20) — balance accumulates.
2. Select product by slot code — validates stock + balance.
3. Dispense — auto-triggered after valid selection; returns integer change.
4. Cancel — refunds balance, back to idle.
5. Out-of-stock / insufficient balance rejected with clear error.
6. Invalid actions per state throw IllegalStateException.

OUT OF SCOPE
- Concurrency (single-user, v1)
- Card/wallet payment (Step 5 — Strategy)
- Multi-item cart (Step 5)
- Physical coin change-making (Step 5)
- Maintenance mode / refill (Step 5 — extra state)
- Persistence
```

---

## Step 2 — Entities & relationships (~4 min)

```
- VendingMachine          context — currentState + balance + inventory maps
- VendingMachineState     interface — 4 ops (insertCoin/selectProduct/dispense/cancel)
- NoCoinState             idle — only insertCoin advances
- HasCoinState            balance > 0 — accepts coins, allows selection, cancels refund
- DispensingState         transient — release + return to NoCoin
- Product                 immutable: slot, name, price
- Coin                    enum: ONE/TWO/FIVE/TEN/TWENTY with value in ₹
```

**Not entities:** Inventory (just two maps), Change (just an int), User/Session (single-user).

**Why exactly 3 states?** NoCoin / HasCoin / Dispensing covers the happy path. Out-of-stock is a guard inside `HasCoinState.selectProduct`, not a separate state. Maintenance is a Step-5 4th state.

**Transitions:**
```
NoCoin  ─ insertCoin ─▶  HasCoin
HasCoin ─ insertCoin ─▶  HasCoin  (stay)
HasCoin ─ selectProduct (valid) ─▶  Dispensing ─ dispense (auto) ─▶  NoCoin
HasCoin ─ cancel ─▶  NoCoin
```

---

## Step 3 — Class design (~12 min)

### VendingMachineState — the contract

```java
public interface VendingMachineState {
    void insertCoin(Coin coin);
    void selectProduct(String slot);
    void dispense();
    void cancel();
}
```

### VendingMachine — the context

```java
public class VendingMachine {
    // 3 state objects — flyweights, constructed once at boot
    private final VendingMachineState noCoinState;
    private final VendingMachineState hasCoinState;
    private final VendingMachineState dispensingState;

    private VendingMachineState currentState;

    private final Map<String, Product> productsBySlot = new HashMap<>();
    private final Map<String, Integer> stockBySlot    = new HashMap<>();
    private int balance = 0;
    private String selectedSlot;   // HasCoin → Dispensing handoff

    public VendingMachine() {
        this.noCoinState     = new NoCoinState(this);
        this.hasCoinState    = new HasCoinState(this);
        this.dispensingState = new DispensingState(this);
        this.currentState    = noCoinState;
    }

    // Public API — pure delegation
    public void insertCoin(Coin coin)      { currentState.insertCoin(coin); }
    public void selectProduct(String slot) { currentState.selectProduct(slot); }
    public void dispense()                 { currentState.dispense(); }
    public void cancel()                   { currentState.cancel(); }

    // Accessors used by state objects (getters/setters for balance, slot, stock, state)
}
```

> *"State objects are flyweights — constructed once, reused forever. They're stateless policy. Rebuilding them per transition would be wasted allocations."*

### NoCoinState — idle

```java
public class NoCoinState implements VendingMachineState {
    private final VendingMachine machine;

    public void insertCoin(Coin coin) {
        machine.addBalance(coin.getValue());
        machine.setState(machine.hasCoinState());       // transition
    }

    public void selectProduct(String slot) { throw new IllegalStateException("Insert a coin first"); }
    public void dispense()                 { throw new IllegalStateException("Nothing to dispense"); }
    public void cancel()                   { /* no-op — zero balance */ }
}
```

### HasCoinState — the busy middle

```java
public void selectProduct(String slot) {
    Product product = machine.getProduct(slot);
    if (product == null)                            throw new IllegalArgumentException("Unknown slot");
    if (machine.getStock(slot) <= 0)                throw new IllegalStateException("Out of stock");
    if (machine.getBalance() < product.getPrice())  throw new IllegalStateException("Insufficient balance");

    machine.setSelectedSlot(slot);
    machine.setState(machine.dispensingState());     // transition
    machine.getState().dispense();                    // chain — auto-dispense
}
```

> **Chain callout:** *"HasCoin transitions to Dispensing AND immediately calls dispense(). One user event, two state transitions — bounded because Dispensing terminates back to NoCoin. No infinite-loop risk."*

### DispensingState — transient

```java
public void dispense() {
    String slot = machine.getSelectedSlot();
    Product product = machine.getProduct(slot);
    int change = machine.getBalance() - product.getPrice();

    machine.decrementStock(slot);
    // print dispense + change

    machine.setBalance(0);
    machine.setSelectedSlot(null);       // reset — avoid stale-state bug on next session
    machine.setState(machine.noCoinState());
}
```

All other operations in DispensingState throw — coin slot locked, motor running.

---

## Step 4 — Implementation + dry-run (~16 min)

### Dry-run — happy path (say this out loud at the board)

```
Setup: 5 Sodas at A1 (₹15), balance=0, state=NoCoinState.

insertCoin(TEN):    noCoin.insertCoin      → balance=₹10, state=HasCoin
insertCoin(FIVE):   hasCoin.insertCoin     → balance=₹15, stay in HasCoin
selectProduct("A1"):
    hasCoin.selectProduct:
      guards pass (product OK, stock=5, balance≥15)
      setSelectedSlot("A1")
      setState(dispensing)
      getState().dispense()                ← CHAIN
        dispensing.dispense:
          change = 15 - 15 = 0
          decrementStock(A1) → 4
          balance=0, selectedSlot=null
          setState(noCoin)

Final: NoCoinState, balance=0, stock[A1]=4.
```

### Per-state rejection matrix — verify all 12 cells

| Event ↓ / State →   | NoCoin                 | HasCoin                             | Dispensing                     |
|---------------------|------------------------|--------------------------------------|--------------------------------|
| `insertCoin`         | balance+=v, → HasCoin  | balance+=v, stay                    | THROW "wait for dispense"      |
| `selectProduct`      | THROW "insert first"   | guards → Dispensing, chain dispense | THROW "already dispensing"     |
| `dispense`           | THROW "nothing to"      | THROW "select first"                | RELEASE, reset, → NoCoin        |
| `cancel`             | no-op                  | refund balance, → NoCoin            | THROW "cannot cancel"          |

The "throws" are policy — each state OWNS its rejection message. No `if (status == ...)` ladder anywhere.

---

## Step 5 — Extensibility (~8 min)

### E1. "Add a MaintenanceState — admin can disable the machine"

Add a 4th class. All 4 ops throw "machine offline" except an admin `exitMaintenance`. Other states gain `enterMaintenance` (only callable when balance == 0). Existing states unchanged — that's the pattern's payoff.

### E2. "Card / wallet payment alongside coins"

Strategy on the payment mode:

```java
public interface PaymentMethod {
    void addToBalance(VendingMachine machine, int amount);
}
class CoinPayment implements PaymentMethod   { /* add via Coin enum */ }
class CardPayment implements PaymentMethod   { /* call issuer */ }
class WalletPayment implements PaymentMethod { /* deduct stored balance */ }
```

HasCoin just calls `paymentMethod.addToBalance(...)`. New payment type = new class. State machine untouched.

### E3. "Multi-item cart — accumulate multiple selections"

Add a `CartState` between HasCoin and Dispensing. Replace `selectedSlot: String` with `cart: List<String>`. Add `checkout` event that transitions Cart → Dispensing (which now dispenses everything and totals the change).

### E4. "Physical change-making — return actual coins"

Strategy for change:

```java
public interface ChangeProvider {
    Map<Coin, Integer> makeChange(int amount, Map<Coin, Integer> inventory);
}
class GreedyChangeProvider implements ChangeProvider { /* largest denom first */ }
class DPChangeProvider     implements ChangeProvider { /* DP for min coins */ }
```

Pluggable at machine construction. Failed change is a design choice — reject the sale, or dispense with apology.

### E5. Other one-liners

| Follow-up | Answer |
|-----------|--------|
| Notify a backend on every sale | Observer — publish `ProductDispensed` events; listeners subscribe. |
| Concurrency — multi-user | `synchronized` on the 4 public methods; operations are short so contention is fine. |
| Different machine layouts | `Layout` Strategy at construction. |
| Audit log of transitions | `onEnter`/`onExit` hooks on VendingMachineState. |

---

## What is expected at each level

### Junior (SDE-1)
- Recognizes the machine has multiple modes but may write a single class with a status enum + switch.
- With a nudge, extracts a state interface. May forget the HasCoin → Dispensing auto-chain.
- Handles happy path + insufficient balance. Out-of-stock and mid-dispense rejections often need prompting.

### Mid-level (SDE-2) — the target
- Reaches class-per-state without prompting; names "GoF State" out loud.
- State objects are flyweights (constructed once, reused).
- All 12 event × state cells correct, each with a state-specific rejection message.
- Implements the HasCoin → Dispensing chain as deliberate.
- Money is `int` (rupees) or `long` cents — never `double`. Snapshots `refund` before resetting balance.
- Runs the dry-run unprompted.

### Senior (SDE-3 / SDE-II)
- Names the enum-machine contrast up front, referencing another problem in their portfolio.
- Discusses extensibility axes as orthogonal Strategies (payment × change-making × maintenance × cart) before being asked.
- Catches subtle bugs: stale `selectedSlot` after dispense, "refunding ₹0" if you print balance after reset.
- Finishes early; uses buffer to discuss the 12-cell test coverage matrix.

---

## 30-second summary (memorize for closing)

> *"Six classes: VendingMachine (context), VendingMachineState (interface — 4 ops), three state classes (NoCoin, HasCoin, Dispensing), Coin enum, Product. **Class-per-state**, not enum-machine — because the same event does materially different things per state (insertCoin adds balance in NoCoin/HasCoin but throws in Dispensing). Contrast with PaymentGateway which uses an enum-machine — every status just does `status = next`, only the allowed targets differ. State objects are flyweights, constructed once. HasCoin's selectProduct chains a dispense() call after transitioning — one user event, two transitions, bounded by Dispensing → NoCoin. Extensions: MaintenanceState as a 4th class, Strategy for payment methods, Strategy for physical change-making."*

---

## Top mistakes that lose points

- **Enum + switch instead of class-per-state** — misses the whole point of the problem.
- **States constructed on every transition** — they're stateless flyweights; build once.
- **Context holding `if (currentState == ...)` logic** — behavior belongs on the state class.
- **Transitions decided by the context** — should be by the current state.
- **Forgetting to reset `selectedSlot` after dispense** — stale-state bug on the next session.
- **Printing `getBalance()` after resetting it to 0** — "refunding ₹0" bug. Snapshot first.

---

## Files in this folder

| File | Purpose |
|------|---------|
| `model/Coin.java` | Enum with ₹ values |
| `model/Product.java` | Immutable: slot, name, price |
| `state/VendingMachineState.java` | Interface — 4 ops |
| `state/NoCoinState.java` | Idle |
| `state/HasCoinState.java` | Busy — chains to DispensingState |
| `state/DispensingState.java` | Transient — releases, resets, back to NoCoin |
| `VendingMachine.java` | Context — owns 3 state objects + data + delegates |
| `VendingMachineDriver.java` | 6 scenarios covering the 12-cell matrix |

Run:
```bash
mvn -q compile exec:java \
  -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.vendingmachine.VendingMachineDriver
```
