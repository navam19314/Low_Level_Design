# Vending Machine — 45-min LLD Interview Walkthrough

**Target role:** SDE‑2 (Amazon, Adobe, Microsoft, Atlassian, etc.)

> Vending Machine is the **textbook GoF State pattern problem**. It's the one problem in your deck where the senior signal is *"one class per state, each implementing the same interface"* — not the enum-state-machine pattern you used in PaymentGateway/JobScheduler/Inventory. If the interviewer asks "design a state machine for this," they want to see THIS — and you should know the contrast between enum-machine and class-per-state.

---

## Time budget (45 min)

| Step | Activity                                                                                | Budget   | Cumulative |
| ---- | --------------------------------------------------------------------------------------- | -------- | ---------- |
| 1    | Requirements                                                                            | ~4 min   | 4          |
| 2    | Entities & Relationships                                                                | ~4 min   | 8          |
| 3    | Class Design (State pattern — interface + one class per state + context)                | ~12 min  | 20         |
| 4    | Implementation (each state's reactions + transitions + dry-run)                          | ~16 min  | 36         |
| 5    | Extensibility (multi-item carts, change-making, refund flows, async dispense)            | ~8 min   | 44         |
| —    | Wrap & questions                                                                        | ~1 min   | 45         |

Step 3 is unusually long because there are 4 named things on the board (interface + 3 state classes + context).

Watch the clock at minute **4**, minute **20** (start coding), minute **36** (extensibility).

---

## Mental models — internalize these BEFORE you walk in

### M1. State pattern shape — one class per state

```
                  +----------------------------+
                  |     VendingMachine         |   <- CONTEXT
                  | - currentState: VMState    |
                  | + insertCoin / selectProduct / dispense / cancel
                  |   ALL DELEGATE to currentState
                  +----------------------------+
                              │
                              │ holds reference to ONE
                              ▼
                  +----------------------------+
                  | <<interface>>              |
                  | VendingMachineState        |
                  +----------------------------+
                  | + insertCoin               |
                  | + selectProduct            |
                  | + dispense                 |
                  | + cancel                   |
                  +----------------------------+
                       ▲       ▲       ▲
                       │       │       │
            NoCoinState  HasCoinState  DispensingState
            (idle)       (balance > 0) (releasing product)

   Per-state behavior LIVES on the state class:

     NoCoinState.selectProduct(...)   → throw "insert coin first"
     HasCoinState.selectProduct(...)  → if balance >= price && stock > 0,
                                        transition to DispensingState
     DispensingState.selectProduct(...)→ throw "already dispensing"

   The CONTEXT (VendingMachine) is just a thin shell — owns data, delegates to state.
```

**Senior soundbite (memorize):** *"This is the GoF State pattern — one class per state, all implementing the same interface. Per-state behavior lives on the state class, not on a giant switch in VendingMachine. Compare to the enum-state-machine in my PaymentGateway: that has ONE class, an `EnumMap<Status, EnumSet<Status>>` of allowed transitions, and a single `transitionTo` method. The two approaches solve different problems — class-per-state when each state has materially different BEHAVIOR for the same operations; enum machine when only TRANSITIONS differ."*

### M2. Enum-machine vs class-per-state — when to use which

```
   ENUM STATE MACHINE                   GoF STATE PATTERN (this problem)
   ------------------                   --------------------------------
   ONE class (Payment, Job)             N classes (NoCoinState, HasCoinState, ...)
   ONE method `transitionTo(status)`    EACH class implements same interface
   ONE table of allowed transitions     EACH class decides reactions
                                                                       
   Use when:                            Use when:
   - per-state BEHAVIOR is uniform      - per-state BEHAVIOR materially varies for
   - only the allowed TRANSITIONS vary    the SAME operation
   - state == data                      - state == behavior + transitions
                                                                       
   Examples in your deck:               Example: Vending Machine
   - PaymentGateway (state per payment) - Each state reacts DIFFERENTLY to
   - JobScheduler   (state per job)        insertCoin/selectProduct/dispense/cancel
   - Inventory      (compartment status)  - Even though some reject ("not now"),
                                            REJECTION ITSELF is per-state policy.
```

> **The choice is honest, not arbitrary.** If you use class-per-state when an enum-machine would do, you've over-engineered (e.g., one class per Payment status would be ridiculous — they all just `setStatus(next)`). If you use enum-machine when class-per-state would do, you end up with switch-on-status branches in every method.

### M3. State transitions are SELF-DRIVEN

```
   In the State pattern, who decides the next state?
   The CURRENT STATE — NOT the context.

   HasCoinState.selectProduct(slot):
       validate balance, stock
       if OK:
           machine.setSelectedSlot(slot)
           machine.setState(machine.dispensingState())    ◀── state transitions itself
           machine.getState().dispense()                  ◀── may even invoke next state

   Why this matters:
     - The transition rules live with the state that REACTS to the event,
       not duplicated in the context.
     - Adding a new state (e.g., MaintenanceState) doesn't require touching
       the context — just create the new class and have other states transition
       to it when appropriate.

   Anti-pattern (DON'T do):
     VendingMachine.selectProduct(slot):
        if (status == HAS_COIN && balance >= price && stock > 0) {
            status = DISPENSING; dispense();
        } else if (status == NO_COIN) throw ...
        else if (status == DISPENSING) throw ...
     ← This is back to a switch — NOT the State pattern.
```

---

## STEP 1 — Requirements (~4 min)

### What to say out loud (opener)
> "Vending machine is the textbook State pattern problem. The senior signal here is one class per state, with per-state behavior, not a switch on a status enum."

### Clarifying-questions dialogue (do this before writing anything)

**You:** *"User actions — insert coins, select a product, and cancel? Anything else?"*
**Interviewer:** *"Those three. And dispense happens automatically after a valid selection."*
> Signals the auto-chain from HasCoin → Dispensing inside `selectProduct`.

**You:** *"Coin denominations — fixed set, or arbitrary integers? I'll assume rupee coins ₹1, ₹2, ₹5, ₹10, ₹20."*
**Interviewer:** *"Fixed denominations is fine."*
> An enum is the right model. No need for a Money value object.

**You:** *"When user pays more than the price, do we return change automatically? And should we track physical coin inventory to make exact change?"*
**Interviewer:** *"Return change as an integer amount — no physical coin change algorithm for v1."*
> Simplifies to `int change = balance - price`. Physical change-making is a Step-5 extension.

**You:** *"Multiple products per slot code, or one product per slot? And can slots go out of stock?"*
**Interviewer:** *"One product per slot. Slots can go out of stock — reject with a clear error."*
> Two maps: `slot → Product`, `slot → stockCount`. No `Inventory` class needed.

**You:** *"What happens if a user calls an action in the wrong state — like `selectProduct` before inserting a coin?"*
**Interviewer:** *"Throw a clear exception. The state should decide what's illegal."*
> This is the direct signal for class-per-state. Each state defines its own rejection message.

**You:** *"Concurrency — is this a single-user machine, or can multiple users interact with it at once?"*
**Interviewer:** *"Single-user for v1."*
> No `synchronized` needed. Concurrency becomes a Step-5 extension.

### Then state out loud:

### What to write on the board

```
Functional Requirements
1. Insert coins (₹1 / ₹2 / ₹5 / ₹10 / ₹20) — balance accumulates.
2. Select product by slot code (e.g., "A1") — validates stock + balance.
3. Dispense product — auto-triggered after a valid selectProduct; returns change.
4. Cancel — refunds the current balance, returns to idle.
5. Out-of-stock items rejected with clear error.
6. Insufficient balance rejected.
7. Invalid actions per state (e.g., dispense before paying) throw IllegalStateException.

Out of Scope
- Concurrency (single-user vending machine, v1)
- Bill/card payments (Step 5 — Strategy for payment methods)
- Multi-item carts (Step 5)
- Change-making with specific coins (we just track total cents; physical coin
   change is a separate algorithm — Step 5)
- Maintenance mode / refilling (Step 5 extension states)
- Persistence
```

### Close the step
> "One load-bearing requirement: the per-state behavior is materially different for the SAME operation. That's what justifies class-per-state over enum-machine."

---

## STEP 2 — Entities & Relationships (~4 min)

### What to say out loud
> "Six types: **VendingMachine** (context — owns data and current state), **VendingMachineState** (interface — 4 ops), three concrete state classes (**NoCoinState**, **HasCoinState**, **DispensingState**), and the data models **Coin** (enum with cent values) and **Product** (immutable record). Inventory tracking is a couple of maps inside VendingMachine — not a separate class."

### Why exactly 3 states (not 4 or 5)
> "NoCoin / HasCoin / Dispensing covers the full happy path. OutOfStock is handled inline by HasCoin's selectProduct guard — no transition needed; we just reject and stay in HasCoin. MaintenanceMode would be a 4th state if requirements add it (Step 5)."

### What to write on the board

```
Entities
- VendingMachine          (context — holds currentState + balance + inventory + maps)
- VendingMachineState     (interface — insertCoin / selectProduct / dispense / cancel)
- NoCoinState             (idle — only insertCoin advances; everything else rejects)
- HasCoinState            (balance > 0 — insertCoin adds, selectProduct may advance, cancel refunds)
- DispensingState         (transient — dispense releases + transitions to NoCoin)
- Product                 (immutable: slot, name, price)
- Coin                    (enum: ONE / TWO / FIVE / TEN / TWENTY with rupee value)

NOT entities
- Inventory               (two maps inside VendingMachine — no separate behavior)
- Change                  (just an int — physical change-making is a Step-5 algorithm)
- User / Session          (single-user, single-session machine)

Relationships
- VendingMachine owns:
    3 state-object instances (constructed once at boot, reused forever)
    currentState  →  one of the three
    Map<slot, Product> productsBySlot
    Map<slot, Integer> stockBySlot
    int balance, String selectedSlot
- Each state holds a back-reference to VendingMachine (its CONTEXT)
- States transition the context: machine.setState(machine.dispensingState())
```

### Diagram — boxes and arrows

```
                  +-------------------------------------+
                  |          VendingMachine             |   <- context + facade
                  |   insertCoin / selectProduct /      |
                  |   dispense / cancel                 |    (all delegate)
                  +-------------------------------------+
                       |               |              |
                       | owns          | owns         | owns
                       v               v              v
                  state objects    Map<slot,Product>   Map<slot,Integer>
                  (3 singletons)   productsBySlot      stockBySlot
                       |
                       v
                  +-------------------------------------+
                  | <<interface>>                       |
                  | VendingMachineState                 |
                  +-------------------------------------+
                  | insertCoin / selectProduct /        |
                  | dispense / cancel                   |
                  +-------------------------------------+
                       ▲          ▲              ▲
                       │          │              │
                 NoCoinState  HasCoinState  DispensingState
                 (idle)       (balance > 0) (releasing — transient)

   Transitions (drawn separately for clarity):

      NoCoin  ── insertCoin ──▶  HasCoin
      HasCoin ── insertCoin ──▶  HasCoin   (stay)
      HasCoin ── selectProduct ──▶  Dispensing   (if balance + stock OK)
      HasCoin ── cancel ──▶  NoCoin
      Dispensing ── dispense (auto-called) ──▶  NoCoin
```

---

## STEP 3 — Class Design (~12 min)

### VendingMachineState — the contract

```java
public interface VendingMachineState {
    void insertCoin(Coin coin);
    void selectProduct(String slot);
    void dispense();
    void cancel();
}
```

> *"Same four methods, three different implementations. The interface is what makes them substitutable; the implementations are where the per-state behavior lives."*

### VendingMachine — the context (state outline)

```java
public class VendingMachine {
    // 3 state objects, constructed ONCE at boot.
    private final VendingMachineState noCoinState;
    private final VendingMachineState hasCoinState;
    private final VendingMachineState dispensingState;

    private VendingMachineState currentState;       // ← changes over time

    // Data states need to read / mutate
    private final Map<String, Product> productsBySlot = new HashMap<>();
    private final Map<String, Integer> stockBySlot     = new HashMap<>();
    private int balance = 0;
    private String selectedSlot;                     // set by HasCoin → Dispensing handoff

    public VendingMachine() {
        this.noCoinState     = new NoCoinState(this);
        this.hasCoinState    = new HasCoinState(this);
        this.dispensingState = new DispensingState(this);
        this.currentState    = noCoinState;
    }

    // public API — pure delegation to currentState
    public void insertCoin(Coin coin)       { currentState.insertCoin(coin); }
    public void selectProduct(String slot)  { currentState.selectProduct(slot); }
    public void dispense()                  { currentState.dispense(); }
    public void cancel()                    { currentState.cancel(); }

    // accessors for state objects
    public VendingMachineState getState();
    public void setState(VendingMachineState s);
    public VendingMachineState noCoinState() / hasCoinState() / dispensingState();
    public int getBalance();  public void addBalance(int);  public void setBalance(int);
    public Product getProduct(String slot);  public int getStock(String slot);  public void decrementStock(String slot);
    public String getSelectedSlot();  public void setSelectedSlot(String slot);
}
```

**Two design notes worth saying:**

1. *"The 3 state objects are constructed ONCE in the ctor and reused forever. They're effectively flyweights — stateless policy objects. Constructing new ones on every transition would be wasted allocations."*

2. *"The public API has FOUR methods, each one-liner. That's deliberate. The State pattern's whole point is the context is dumb; behavior is on the state."*

### NoCoinState — idle

```java
public class NoCoinState implements VendingMachineState {
    private final VendingMachine machine;
    public NoCoinState(VendingMachine machine) { this.machine = machine; }

    public void insertCoin(Coin coin) {
        machine.addBalance(coin.getValue());
        machine.setState(machine.hasCoinState());       // ← transition
    }

    public void selectProduct(String slot) {
        throw new IllegalStateException("Insert a coin before selecting a product");
    }

    public void dispense() {
        throw new IllegalStateException("Nothing to dispense");
    }

    public void cancel() {
        // No-op at zero balance
    }
}
```

### HasCoinState — the busy intermediate

```java
public void selectProduct(String slot) {
    Product product = machine.getProduct(slot);
    if (product == null)                            throw new IllegalArgumentException("Unknown slot");
    if (machine.getStock(slot) <= 0)                throw new IllegalStateException("Out of stock");
    if (machine.getBalance() < product.getPrice())
                                                    throw new IllegalStateException("Insufficient balance");

    machine.setSelectedSlot(slot);
    machine.setState(machine.dispensingState());     // ← transition
    machine.getState().dispense();                    // ← immediately dispense
}
```

> **Senior callout — chaining transitions:** *"HasCoin transitions to Dispensing AND immediately calls `dispense()`. That's clean — the user only fired ONE event (selectProduct), but two state transitions happen automatically. The chain is bounded (Dispensing → NoCoin is the terminator), so there's no risk of infinite loops."*

### DispensingState — transient

```java
public void dispense() {
    String slot = machine.getSelectedSlot();
    Product product = machine.getProduct(slot);
    int change = machine.getBalance() - product.getPrice();

    machine.decrementStock(slot);
    // log/return change
    machine.setBalance(0);
    machine.setSelectedSlot(null);
    machine.setState(machine.noCoinState());          // ← back to idle
}
```

> *"Dispensing is a TRANSIENT state — we're only in it long enough to release the product, return change, and reset to NoCoin. All other operations in Dispensing throw, including insertCoin (no, you can't add more coins mid-dispense)."*

### The principle to verbalize — State + Tell-Don't-Ask
> "Per-state behavior lives on the state class — TELL the state 'insertCoin happened' and let it decide what to do. The context just holds data and delegates. This is the OPPOSITE of an enum-machine where ALL behavior lives in one place and switches on the status. Different tool for different jobs — for THIS problem, with materially different per-state reactions, the GoF pattern wins."

---

## STEP 4 — Implementation (~16 min)

### Open by asking
> "Real Java or pseudo-code? I'll write the interface + the three state classes + the context. The state classes are the senior signal — each one's reactions show the per-state policy."

### 4.1 The implementation already shown above in Step 3

(All four classes complete; rewriting in full would duplicate Step 3.)

### 4.2 Verification — dry-run the happy path

```
Setup: 5 units of Soda at A1 (₹15), balance = 0, state = NoCoinState.

insertCoin(TEN):
   currentState = NoCoinState
   → noCoin.insertCoin(TEN):
     machine.addBalance(10)        balance = ₹10
     machine.setState(hasCoin)
   ✓ now in HasCoinState

insertCoin(FIVE):
   currentState = HasCoinState
   → hasCoin.insertCoin(FIVE):
     machine.addBalance(5)         balance = ₹15
     (stays in HasCoin)
   ✓

selectProduct("A1"):
   currentState = HasCoinState
   → hasCoin.selectProduct("A1"):
     product = Soda @ ₹15, stock = 5, balance = ₹15 → OK
     machine.setSelectedSlot("A1")
     machine.setState(dispensing)
     machine.getState().dispense()                ← chains!
       → dispensing.dispense():
         change = 15 − 15 = 0
         decrementStock(A1)        stock = 4
         balance = 0
         selectedSlot = null
         machine.setState(noCoin)
   ✓ back in NoCoinState; product released; balance reset

State after: NoCoinState, balance=0, stock[A1]=4, no selectedSlot.
```

### 4.3 Verification — invalid actions per state

```
state = NoCoinState
selectProduct("A1") → throws IllegalStateException("Insert a coin first")     ✓
dispense()          → throws IllegalStateException("Nothing to dispense")     ✓
cancel()            → no-op (zero balance, nothing to refund)                  ✓

state = HasCoinState (balance = ₹5)
selectProduct("A1") → throws IllegalStateException("Insufficient balance")    ✓
                       (Soda needs ₹15, have ₹5)

state = HasCoinState, stock[A1] = 0
selectProduct("A1") → throws IllegalStateException("Out of stock")            ✓

state = DispensingState (mid-dispense, hypothetically)
insertCoin(...)     → throws "wait for dispense to finish"                    ✓
cancel()            → throws "cannot cancel during dispense"                  ✓
```

> **The clean win:** each state's "rejection" is self-documenting — the throw message is specific to that state. No `if (status == ...)` ladder anywhere.

---

## STEP 5 — Extensibility (~8 min)

### 5.1 "Add MaintenanceState — admin can disable the machine"

> **Problem in current design:** *"The machine is always available. In production, maintenance staff need to refill / repair without users mid-transaction."*
>
> **Pattern as the fix:** *"Add a 4th state — `MaintenanceState`. All four ops reject with 'machine offline' except a special `exitMaintenance` admin op. Other states gain a `enterMaintenance` op that's only callable when balance == 0 (refund first if mid-session). The State pattern's KEY win is here: adding one class + one constructor wiring + minor edits to the other classes — no central switch to update."*

### 5.2 "Add card / wallet payment alongside coins"

> **Problem in current design:** *"Only coin denominations are accepted."*
>
> **Pattern as the fix:** *"Strategy on the payment mode. `PaymentMethod` interface with `addToBalance(amount)`. `CoinPayment` adds via Coin enum; `CardPayment` calls a (mocked) issuer; `WalletPayment` deducts from a stored balance. HasCoin just calls `paymentMethod.addToBalance(...)` regardless of source. Adds an axis of variation without changing the state pattern."*

### 5.3 "Multi-item shopping cart — accumulate multiple selections"

> **Problem in current design:** *"One selectProduct → immediate dispense. Real machines might let you add multiple items to a cart, then checkout."*
>
> **Pattern as the fix:** *"Replace `selectedSlot: String` with `cart: List<String>`. New state: `CartState` (after first selectProduct but before checkout). Add `checkout` op that transitions Cart → Dispensing (which dispenses everything). The state pattern stretches easily — just one new state class."*

### 5.4 "Physical change-making — return specific coins"

> **Problem in current design:** *"`change = balance - price` returns an integer. Real machines need to return actual COINS — and may not have the right denominations."*
>
> **Pattern as the fix:** *"`ChangeProvider` Strategy — `GreedyChangeProvider` (default: largest-denomination-first), `DPChangeProvider` (DP for guaranteed-minimum-coins). On dispense, ChangeProvider takes (totalAmount, coinInventory) and returns `Map<Coin, Integer>` to dispense — or fails if no exact change. Failed change is a Step-5 design choice — reject the sale, or dispense with apology."*

### 5.5 Other "what-if" answers

| Follow-up                                  | Answer                                                                                              |
| ------------------------------------------ | --------------------------------------------------------------------------------------------------- |
| "Notify a backend on every sale"           | Observer — VendingMachine publishes `ProductDispensed` events; analytics listeners subscribe.       |
| "Audit log — every state transition"       | Add `onEnter`/`onExit` hooks to VendingMachineState; emit transition events.                       |
| "Concurrency — multi-user machine"         | Wrap `currentState` access in `synchronized`; each operation is short enough that contention is low. |
| "Different machine models with different layouts" | Inject a `Layout` strategy that defines slots / capacities; VendingMachine becomes parameterizable. |
| "Multi-machine network / inventory sharing" | Out of scope — that's distributed systems / HLD territory.                                          |

---

## Design Patterns — Hello Interview's canonical 8

> **One pattern earns rent in the base — and it's the headline:**
> - **State Machine (#3)** — but specifically the **class-per-state** variant, not the enum/EnumMap variant.

### How this maps to VendingMachine specifically

**Already in the BASE design — call out by name:**

- **State Machine (#3)** ⭐ — `VendingMachineState` interface with one class per state. Justified because per-state behavior for the SAME operation materially differs (insertCoin reacts differently in NoCoin vs HasCoin vs Dispensing).
- **Facade (#8)** — VendingMachine is the only class user code touches.
- **Flyweight** (not in HI's 8 but worth mentioning) — the 3 state objects are constructed once and reused forever; they're stateless policy objects.

**Reach for these on Step-5 follow-ups:**

| Follow-up                                  | Pattern (HI's 8)             | Your line                                                                                            |
| ------------------------------------------ | ---------------------------- | ---------------------------------------------------------------------------------------------------- |
| "Card / wallet payments"                   | **Strategy (#1)** ⭐         | *"`PaymentMethod` Strategy — Coin / Card / Wallet impls. HasCoin just calls `paymentMethod.addToBalance`."* |
| "Change-making"                            | **Strategy (#1)** ⭐         | *"`ChangeProvider` Strategy — Greedy / DP impls. Pluggable at machine construction."*                |
| "Notify on sale"                           | **Observer (#2)**            | *"VendingMachine publishes `ProductDispensed` events; subscribers register independently."*         |
| "Maintenance mode"                         | (4th state)                   | *"Add a `MaintenanceState` class. The State pattern's win — minimal touch on existing states."*    |
| "Different machine models"                 | **Factory (#4)** + Layout    | *"`VendingMachineFactory.create(model)` produces machines with the appropriate slot layout."*       |

**Patterns to actively refuse:**

- **Singleton on VendingMachine** — works if there's truly one machine, but DI of a single instance is cleaner.
- **Builder for the 0-arg ctor** — academic noise.
- **Enum state machine** — works, but defeats the senior signal here. The interviewer specifically wants to see class-per-state vs enum-machine.

### One sentence to say at the end of Step 3

> *"The base design names State Machine by name — class-per-state, not enum-machine. That's the distinct senior signal for THIS problem, contrasting with my PaymentGateway / JobScheduler / Inventory which use enum-machines because their per-state behavior is uniform. Strategy lands in Step 5 if payment methods or change-making come up."*

---

## Interview deep-dives — the questions you'll definitely get asked

### 1. Complexity (Big-O)

| Operation                                | Time           | Space          | Notes                                                                              |
| ---------------------------------------- | -------------- | -------------- | ---------------------------------------------------------------------------------- |
| `insertCoin`                              | **`O(1)`**     | O(1)           | balance += value; maybe transition                                                  |
| `selectProduct`                           | **`O(1)`**     | O(1)           | HashMap lookup + validation                                                         |
| `dispense`                                | **`O(1)`**     | O(1)           | Decrement stock, reset balance, transition                                          |
| `cancel`                                  | **`O(1)`**     | O(1)           |                                                                                    |
| Storage — productsBySlot                  | -              | **`O(N)`**     | One Product per slot                                                                |
| Storage — stockBySlot                     | -              | **`O(N)`**     |                                                                                    |
| Storage — state objects                   | -              | **`O(1)`**     | 3 singletons, regardless of usage                                                  |

### 2. Concurrency / thread-safety

| Approach                                | When to use                                  | Cost                                                              |
| --------------------------------------- | -------------------------------------------- | ----------------------------------------------------------------- |
| Single-user assumption                   | **Default for v1.** No locks needed.        | Trivial                                                          |
| `synchronized` on every public method    | Multi-user (concurrent insertCoin's etc.)   | Operations are very short; contention is fine                    |
| Per-user session state                   | True multi-user system                       | Out of scope — that's a different problem (you'd need user IDs and per-session machines) |

### 3. Testing — what to write tests for

| Test category                | Cases to cover                                                                                              |
| ---------------------------- | ----------------------------------------------------------------------------------------------------------- |
| Happy path                   | insert coins → selectProduct → dispense; balance reset; stock decremented                                  |
| **Insufficient balance**     | selectProduct with balance < price → throws; balance unchanged                                              |
| **Out of stock**             | selectProduct on 0-stock → throws; balance unchanged                                                        |
| **Cancel refunds**           | Insert coins, cancel → balance returned, state back to NoCoin                                              |
| **Per-state rejections**     | All 4 ops in all 3 states → expected throws / valid transitions                                            |
| Exact change                 | Insert exact amount → no change returned                                                                    |
| Update value mid-cart        | Add coins after partial deposit → balance accumulates                                                       |

```java
@Test
void insufficient_balance_rejected_and_state_unchanged() {
    VendingMachine vm = new VendingMachine();
    vm.stockProduct(new Product("A1", "Soda", 15), 1);
    vm.insertCoin(Coin.FIVE);
    assertThrows(IllegalStateException.class, () -> vm.selectProduct("A1"));
    assertEquals(5, vm.getBalance());              // balance preserved
}
```

### 4. SOLID mapping

| Letter                       | Where it shows up                                                                                          |
| ---------------------------- | ---------------------------------------------------------------------------------------------------------- |
| **S** Single Responsibility  | Each state class = one state's behavior. VendingMachine = context + data. Coin/Product = data records. Four reasons to change. |
| **O** Open/Closed            | Adding a new state = new class implementing the interface + minor edits to states that transition to it. Existing state classes unchanged. |
| **L** Liskov Substitution    | Any VendingMachineState substitutable behind the interface. New states must honor the same throw-on-invalid contract. |
| **I** Interface Segregation  | VendingMachineState has 4 narrow methods. No "stats" / "lifecycle" methods mixed in. |
| **D** Dependency Inversion   | VendingMachine depends on `VendingMachineState` interface, not on concrete state classes. (It does construct the concretes at ctor — that's the composition root.) |

### 5. "Summarize your design in 30 seconds"

> *"Six classes: VendingMachine (context), VendingMachineState (interface — 4 ops), three state classes (NoCoin, HasCoin, Dispensing), Coin enum, Product record. **State pattern** — class-per-state, NOT enum-machine. Each state CLASS decides what to do for each of the 4 operations (insertCoin / selectProduct / dispense / cancel) — most operations throw IllegalStateException in 'wrong' states; the right one transitions by calling `machine.setState(machine.<next>State())`. State objects are constructed ONCE and reused (effectively flyweights). HasCoin's `selectProduct` immediately chains a `dispense()` call after transitioning — clean way to express auto-dispense after valid selection. Compares to my PaymentGateway / JobScheduler / Inventory which use enum-state-machines because their per-state behavior is uniform; here per-state behavior materially differs, which is what justifies class-per-state. Extensions: MaintenanceState as a 4th class, Strategy for payment methods, Strategy for change-making algorithm."*

That's ~50 seconds. Hits: class-per-state, transitions self-driven, the contrast with enum-machine, extension via more state classes.

---

## What is expected at each level

### Junior (SDE-1)
- Recognizes the machine has multiple modes (idle / has money / dispensing) but may still write a single class with a status enum + switch statements.
- With a nudge, extracts a state interface. May forget that DispensingState is transient or that HasCoin → Dispensing should auto-chain.
- Handles happy path + insufficient balance. Out-of-stock and mid-dispense rejections often need to be prompted.
- No thread-safety talk unless asked.

### Mid-level (SDE-2) — this is the level most interviews target
- Reaches class-per-state pattern without prompting; names it "GoF State" out loud.
- Correctly makes state objects flyweights (constructed once in the context ctor, reused forever).
- Handles all four events × three states = 12 cells correctly, including the "reject" cells with specific error messages per state.
- Implements the HasCoin → Dispensing auto-chain (transition + immediate `dispense()` call) as a deliberate design, not a mistake.
- Money is `int` (rupees) or `long` cents — never `double`. Snapshots `refund` before resetting balance.
- Runs the happy-path dry-run out loud without prompting.

### Senior (SDE-3 / SDE-II at Amazon)
- Everything mid-level does, faster, and with proactive tradeoff commentary.
- Names the contrast up front: *"Class-per-state here because behavior differs materially per state. Enum-machine in PaymentGateway/JobScheduler because only transitions differ."*
- Discusses extensibility axes as ORTHOGONAL Strategies before being asked (payment methods × change-making × maintenance mode × cart).
- Catches subtle bugs: stale `selectedSlot` after dispense, "refunding ₹0" if you print balance after reset, unbounded state chain if new states are added without a terminator.
- Would move to `synchronized` on the four public methods if the machine went multi-user, and can explain why the coin slot / motor being a physical resource matches naturally to the state-locking model.
- Finishes early. Uses buffer to discuss testing strategy (12-cell coverage matrix: 3 states × 4 events).

---

## Closing soundbites (memorize these)

- **Opening:** *"This is the textbook State pattern — class-per-state, not enum-machine."*
- **Why class-per-state HERE:** *"Per-state behavior for the same operation materially differs — `insertCoin` adds balance in NoCoin/HasCoin but THROWS in Dispensing. That's what justifies class-per-state."*
- **Contrast with enum-machine:** *"My PaymentGateway uses an enum-machine because every payment status reacts the SAME WAY to `transitionTo` — only the allowed transitions differ. Different tool for different jobs."*
- **State transitions self-driven:** *"The CURRENT state decides the next state — `machine.setState(machine.dispensingState())` from inside HasCoin. Context is dumb, behavior is on the state."*
- **State objects as flyweights:** *"3 state objects, constructed once at ctor, reused forever. They're stateless policy — no point allocating new ones per transition."*
- **HasCoin chains to Dispensing:** *"`selectProduct` transitions to Dispensing AND immediately calls `dispense()`. The user fired one event; two transitions happened. Bounded chain — Dispensing terminates back to NoCoin."*
- **On extensibility:** *"MaintenanceState as a 4th class. Payment methods as Strategy. Change-making as Strategy. Multi-item cart as a CartState."*

---

## Top mistakes that lose points

- **Using an enum/switch instead of class-per-state** — that's an enum-machine, which is fine for problems where per-state behavior is uniform but NOT what the interviewer is testing here. Recognize the difference.
- **States constructed on every transition** — wastes allocations. They're stateless; build once and reuse.
- **The context (VendingMachine) holding logic** — `if (currentState == NO_COIN)` branches in VendingMachine defeats the whole pattern. All behavior on the state class.
- **Transitions decided by the context** — should be by the state. The current state knows when to advance.
- **Forgetting selectedSlot handoff between HasCoin → Dispensing** — Dispensing needs to know which product. Set it BEFORE the state change.
- **Not handling out-of-stock as a guard in selectProduct** — defining a 4th OutOfStock state is overkill; an inline guard in HasCoin is simpler.
- **Mutating Product after creation** — record + final fields prevents this.
- **Skipping the dry-run** — the chained-transition (HasCoin → Dispensing → NoCoin via one selectProduct call) is the trickiest piece; dry-running it surfaces bugs.

---

## Files in this folder (your reference implementation)

| File                                       | What it shows                                                                            |
| ------------------------------------------ | ---------------------------------------------------------------------------------------- |
| `model/Coin.java`                          | Enum with cent values                                                                    |
| `model/Product.java`                       | Immutable record                                                                          |
| `state/VendingMachineState.java`           | Interface — 4 ops                                                                         |
| `state/NoCoinState.java`                   | Idle — only insertCoin advances                                                          |
| `state/HasCoinState.java`                  | Busy — chains to DispensingState                                                          |
| `state/DispensingState.java`               | Transient — releases + returns to NoCoin                                                  |
| `VendingMachine.java`                      | **The context** — owns 3 state objects + data + delegates the 4 public ops                |
| `VendingMachineDriver.java`                | 6 scenarios — happy path, insufficient balance, cancel refund, out-of-stock, invalid transitions, exact change |

Run from the project root:

```bash
mvn -q compile exec:java \
  -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.vendingmachine.VendingMachineDriver
```
