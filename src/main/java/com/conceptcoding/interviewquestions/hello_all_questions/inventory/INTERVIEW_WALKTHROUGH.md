# Inventory Management — 45-min LLD Interview Walkthrough

**Target role:** SDE‑2 (Amazon, Adobe, Microsoft, Atlassian, etc.)
**Source method:** Hello Interview *Delivery Framework* applied to the *Inventory Management* problem breakdown.

> Inventory Management is the **canonical "ordered lock acquisition" LLD problem** and the canonical home for the **Observer pattern in the base**. Three things separate senior signal from mid: (a) the threshold-CROSSING check for alerts, (b) ordered lock acquisition for `transfer` to prevent deadlock, and (c) firing listeners OUTSIDE the warehouse lock. Get those three right and you're senior.

> Hello Interview labels this *hard* — and they're right. The concurrency story here is the trickiest of all 9 problems.

---

## Time budget (45 min)

| Step | Activity                                                                                      | Budget   | Cumulative |
| ---- | --------------------------------------------------------------------------------------------- | -------- | ---------- |
| 1    | Requirements                                                                                  | ~5 min   | 5          |
| 2    | Entities & Relationships                                                                      | ~4 min   | 9          |
| 3    | Class Design (Observer + the alert-config shape)                                              | ~9 min   | 18         |
| 4    | Implementation (Warehouse + transfer w/ ordered locks + dry-run + 2 concurrency scenarios)    | ~18 min  | 36         |
| 5    | Extensibility (reservations during checkout, in-transit inventory)                            | ~8 min   | 44         |
| —    | Wrap & questions                                                                              | ~1 min   | 45         |

Step 4 is the longest — `transfer` plus the alert-fire-outside-lock pattern plus dry-runs of both is genuinely the most concurrency-dense single method across all 9 problems.

Watch the clock at minute **5** (Step 1 done), minute **18** (start coding), minute **36** (extensibility).

---

## Mental models — internalize these BEFORE you walk in

### M1. The dispatch flow — manager → warehouse → alerts

```
   mgr.addStock("EAST", "WIDGET", 50)
          |
          v
   +----------------------------+
   | warehouses.get("EAST")     |    <-- O(1) lookup
   +----------------------------+
          |
          v
   +-----------------------------------------+
   | Warehouse.addStock(productId, quantity) |
   |   List<PendingAlert> toFire             |
   |   synchronized(this) {                  |    <-- per-warehouse lock
   |     prev = inventory[productId]         |
   |     next = prev + quantity              |
   |     inventory[productId] = next         |
   |     toFire = collectAlertsToFire(...)   |    <-- COLLECT under lock
   |   }                                      |
   |   fireAll(toFire)                        |    <-- FIRE outside lock
   +-----------------------------------------+
          |
          v
   AlertListener.onLowStock(warehouseId, productId, currentQty)
          (Observer pattern — pluggable)
```

### M2. Threshold-CROSSING — the alert correctness rule

```
   The naive idea:                The right rule:
   ---------------                ----------------
   "fire when stock < threshold"   "fire when stock CROSSES threshold downward"

                                   prev >= threshold  AND  next < threshold


   Why CROSSING beats "currently below":
   --------------------------------------

   threshold = 10

   case A:  prev=15 → next=9    crossing? 15>=10 && 9<10  → YES  → fire        ✓
   case B:  prev=9  → next=7    crossing? 9>=10  is false → NO   → no dup      ✓
   case C:  prev=7  → next=22   crossing? 7>=10  is false → NO   → no spurious upward fire ✓
   case D:  prev=22 → next=9    crossing? 22>=10 && 9<10 → YES  → fire AGAIN  ✓
                                                                    (natural reset on recovery)

   Win: no mutable state on AlertConfig. One arithmetic check captures
        "fire once, don't duplicate, reset on recovery" in one line.
```

**Senior soundbite (memorize):** *"Alerts fire on the downward CROSSING, not on the absolute-below state. With `prev >= threshold && next < threshold`, the first dip below fires, subsequent dips don't duplicate (prev is already below), and a recovery-then-dip naturally fires again. Zero state on AlertConfig."*

### M3. Ordered lock acquisition — the deadlock-prevention rule

```
   Without lock ordering — classic deadlock:
   -----------------------------------------

        Thread A: transfer("WIDGET", "EAST", "WEST", 5)
        Thread B: transfer("WIDGET", "WEST", "EAST", 3)
                                                          
        t=1  A acquires lock(EAST)                        
        t=2  B acquires lock(WEST)                        
        t=3  A wants lock(WEST) — BLOCKED, B holds it     
        t=4  B wants lock(EAST) — BLOCKED, A holds it     
        ============================================       
              DEADLOCK — neither can progress              


   WITH lock ordering by warehouse-id string (compareTo):
   ------------------------------------------------------

        Both A and B compute:
          first  = "EAST" < "WEST"  →  EAST goes first
          second = WEST

        t=1  A acquires lock(EAST)                        
        t=2  B tries to acquire lock(EAST) — BLOCKED      
        t=3  A acquires lock(WEST)                        
        t=4  A completes transfer; releases both          
        t=5  B acquires lock(EAST)                        
        t=6  B acquires lock(WEST)                        
        t=7  B completes transfer; releases both          
        ============================================       
              both succeed sequentially, no cycle
```

> **The interview rule (same across Movie Ticket / File System / Inventory):** *"When acquiring more than one resource lock at once, always acquire them in a globally consistent order. The order doesn't matter — only that everyone agrees. By warehouse-id string compareTo() is the simplest choice."*

---

## STEP 1 — Requirements (~5 min)

### What to say out loud (opener)
> "Inventory across multiple warehouses with concurrent operations — the headline correctness concerns are the no-negative invariant and the transfer atomicity. Let me clarify scope before designing."

### Probe the 4 themes

| Theme               | Question to ask                                                                                              |
| ------------------- | ------------------------------------------------------------------------------------------------------------ |
| Primary capabilities| "Add stock, remove stock, transfer between warehouses, query availability across warehouses, low-stock alerts?" |
| Rules / completion  | "Reject operations that would go negative? Alerts per (product, warehouse)? Transfer atomic — both legs succeed or neither?" |
| Error handling      | "Unknown warehouse → throw or return false? Insufficient stock for transfer → no partial state change?" |
| Concurrency         | "Multi-threaded callers — handle concurrent transfers in opposite directions without deadlock?" |
| Scope boundaries    | "Out: product catalog, orders, payment, persistence, dynamic warehouses. Confirm?" |

### What to write on the board

```
Functional Requirements
1. Track inventory per (warehouse, product) — quantities only, no negative.
2. Fixed set of warehouses configured at startup.
3. addStock(warehouseId, productId, quantity)
4. removeStock(warehouseId, productId, quantity) → boolean (false if insufficient)
5. transfer(productId, fromId, toId, quantity) → boolean — ATOMIC across both warehouses.
6. getWarehousesWithAvailability(productId, quantity) → list of warehouse ids.
7. setLowStockAlert(warehouseId, productId, threshold, listener) — Observer.
8. Multiple alert configs per (warehouse, product) allowed.
9. Thread-safe — concurrent operations must be correct.

Out of Scope
- Product catalog management (products exist externally; productId is a string)
- Order processing / payment / fulfillment
- Persistence
- Dynamic add/remove of warehouses
- Distributed across multiple processes
```

### Close the step
> "Does this match what you had in mind? Two requirements are load-bearing — 'concurrent operations' and 'pluggable alerts'. Concurrency means per-warehouse locking + ordered acquisition for transfer; pluggable alerts means the Observer pattern lives in the base, not Step 5."

---

## STEP 2 — Entities & Relationships (~4 min)

### What to say out loud
> "Four types: **InventoryManager** (orchestrator + facade), **Warehouse** (per-location state + per-warehouse lock + alert configs), **AlertConfig** (immutable threshold + listener pair), **AlertListener** (Observer interface — pluggable notification). No `Product` class — products exist externally; the productId string is all we need. No `Inventory` class — it's just `Map<String, Integer>` inside Warehouse."

### Why no `Product` class
> "Product catalog is explicitly out of scope. Inside our system a product is identified by an opaque string — we don't manage product properties, lifecycles, or attributes. Adding a `Product` class would be ceremony without behavior."

### Why no `Inventory` class
> "Inventory is just a `productId → quantity` map. A separate class would only forward `get`/`put` to a map; no rules or invariants live on it. The invariants — no negative stock, alert firing on threshold crossing — live on Warehouse, which owns both the map AND the alert configs (because they're conceptually one thing: 'what the warehouse holds and how it reports it')."

### What to write on the board

```
Entities
- InventoryManager   (orchestrator + facade: add, remove, transfer, query, alerts)
- Warehouse          (per-location: inventory map + alert-config map + the synchronized lock)
- AlertConfig        (immutable: threshold + listener)
- AlertListener      (interface — Observer; one method onLowStock)

NOT entities
- Product            (external; productId string is enough)
- Inventory          (just a Map<String,Integer> inside Warehouse)
- Transfer           (an operation, not an entity — until §5.2 makes it one)

Relationships
- InventoryManager   owns       Map<String, Warehouse>     (fixed post-ctor)
- Warehouse          owns       Map<String, Integer>       (inventory)
- Warehouse          owns       Map<String, List<AlertConfig>>  (multiple alerts per product OK)
- AlertConfig        refs       AlertListener
- Listeners are CALLBACKS — warehouse calls onLowStock; doesn't know what they do
```

### Diagram — boxes and arrows

```
                  +--------------------------------+
                  |        InventoryManager        |   <- orchestrator + facade
                  |  add / remove / transfer / ... |
                  +--------------------------------+
                                  |
                            owns  | (Map<String, Warehouse>)
                                  v
                  +----------------------------------+
                  |  Warehouse "EAST"                |
                  |  - inventory: Map<prod, qty>     |
                  |  - alertConfigs: Map<prod, list> |
                  |  + synchronized addStock/...     |     (one of N warehouses, fixed at ctor)
                  +----------------------------------+
                            |
                            | per-product (Map<String, List<AlertConfig>>)
                            v
                  +----------------------------------+
                  |  AlertConfig (immutable)         |
                  |  threshold + AlertListener       |
                  +----------------------------------+
                            |
                            | refs (Observer)
                            v
                +----------------------------------+
                | <<interface>> AlertListener      |
                | + onLowStock(warehouseId,        |
                |              productId, qty)     |
                +----------------------------------+
                       ^                ^
                       |                |
                 EmailAlertL...   LoggingAlertL...     (pluggable impls — §5)
```

---

## STEP 3 — Class Design (~9 min)

### Work top-down: InventoryManager → Warehouse → AlertConfig → AlertListener.

### InventoryManager — state ↔ requirement table

| Requirement                              | State InventoryManager must own                          |
| ---------------------------------------- | -------------------------------------------------------- |
| Multiple warehouses, fixed at startup    | `Map<String, Warehouse> warehouses` (immutable post-ctor) |

That's it. **One field.** All per-product state lives on each Warehouse.

### InventoryManager — behavior table

| Need from requirements              | Method                                                  |
| ----------------------------------- | ------------------------------------------------------- |
| Add stock                           | `void addStock(warehouseId, productId, quantity)`        |
| Remove stock (can fail)             | `boolean removeStock(warehouseId, productId, quantity)`  |
| Atomic cross-warehouse move         | `boolean transfer(productId, fromId, toId, quantity)`    |
| Query availability                  | `List<String> getWarehousesWithAvailability(productId, quantity)` |
| Configure alert                     | `void setLowStockAlert(warehouseId, productId, threshold, listener)` |
| Inspect current stock               | `int getStock(warehouseId, productId)` (test/debug API)  |

### InventoryManager — class outline (write this on the board)

```java
public class InventoryManager {
    private final Map<String, Warehouse> warehouses;

    public InventoryManager(List<String> warehouseIds) {
        this.warehouses = new HashMap<>();
        for (String id : warehouseIds) warehouses.put(id, new Warehouse(id));
    }

    public void    addStock(String warehouseId, String productId, int quantity);
    public boolean removeStock(String warehouseId, String productId, int quantity);
    public boolean transfer(String productId, String fromId, String toId, int quantity);
    public List<String> getWarehousesWithAvailability(String productId, int quantity);
    public void    setLowStockAlert(String warehouseId, String productId, int threshold, AlertListener listener);
    public int     getStock(String warehouseId, String productId);
}
```

### Warehouse — state ↔ requirement table

| Requirement                              | State Warehouse must own                                       |
| ---------------------------------------- | -------------------------------------------------------------- |
| Inventory per product                    | `Map<String, Integer> inventory`                               |
| Alerts per product (multiple allowed)    | `Map<String, List<AlertConfig>> alertConfigs`                  |
| Identity (used in alert callback)        | `String id`                                                    |
| Concurrency                              | Implicit — `synchronized(this)` on every public method         |

### Warehouse — class outline

```java
public class Warehouse {
    private final String id;
    private final Map<String, Integer> inventory       = new HashMap<>();
    private final Map<String, List<AlertConfig>> alertConfigs = new HashMap<>();

    public          Warehouse(String id);
    public          void    addStock(String productId, int quantity);            // synchronized inside
    public          boolean removeStock(String productId, int quantity);         // synchronized inside
    public synchronized int  getStock(String productId);
    public synchronized boolean checkAvailability(String productId, int quantity);
    public synchronized void setLowStockAlert(String productId, int threshold, AlertListener listener);

    private List<PendingAlert> collectAlertsToFire(String productId, int prev, int next);  // called under lock
}
```

### AlertConfig — immutable record

```java
public record AlertConfig(int threshold, AlertListener listener) {
    public AlertConfig {
        if (threshold <= 0) throw new IllegalArgumentException("threshold must be > 0");
        Objects.requireNonNull(listener);
    }
}
```

### AlertListener — Observer interface

```java
public interface AlertListener {
    void onLowStock(String warehouseId, String productId, int currentQuantity);
}
```

### Diagram — class cards

```
+----------------------------------+    +-------------------------------------+
|        InventoryManager          |    |             Warehouse               |
+----------------------------------+    +-------------------------------------+
| - warehouses:                    |    | - id: String                        |
|     Map<String, Warehouse>       |    | - inventory:                        |
+----------------------------------+    |     Map<String, Integer>            |
| + addStock(...)                  |    | - alertConfigs:                     |
| + removeStock(...): bool         |    |     Map<String, List<AlertConfig>>  |
| + transfer(...): bool            |    +-------------------------------------+
| + getWarehousesWithAvailability(.|    | + synchronized addStock(...)        |
|                       ): List<S> |    | + synchronized removeStock(...): b  |
| + setLowStockAlert(...)          |    | + synchronized getStock(...): int   |
| + getStock(...)                  |    | + synchronized checkAvailability... |
+----------------------------------+    | + synchronized setLowStockAlert(...)|
                                        +-------------------------------------+

+-----------------------------+   +---------------------------------------+
|  AlertConfig (record)       |   |  <<interface>> AlertListener          |
+-----------------------------+   +---------------------------------------+
| int           threshold     |   | + onLowStock(warehouseId, productId,  |
| AlertListener listener      |   |              currentQty)              |
+-----------------------------+   +---------------------------------------+
                                          ^             ^
                                          |             |
                                 EmailAlertListener  LoggingAlertListener   ... (Step 5)

InventoryManager --owns--> Map<String, Warehouse>
Warehouse        --owns--> inventory + alertConfigs
AlertConfig      --refs--> AlertListener  (Observer)
```

### The principle to verbalize — Observer + Information Expert
> "Per-warehouse state lives on Warehouse — that's Information Expert; only the warehouse knows its own stock. Notification mechanism is decoupled via Observer — the warehouse just calls a callback; it doesn't know whether the listener sends email or logs to disk. That decoupling is the whole point of Observer here."

---

## STEP 4 — Implementation (~18 min)

### Open by asking
> "Real Java or pseudo-code? I'll do `Warehouse.addStock` first because it shows the fire-outside-lock pattern, then `transfer` because it shows ordered locking, then dry-run the alert-crossing scenario and a concurrent transfer scenario."

### 4.1 `Warehouse.addStock` — collect under lock, fire outside

```java
public void addStock(String productId, int quantity) {
    if (quantity <= 0) {
        throw new IllegalArgumentException("quantity must be > 0");
    }
    List<PendingAlert> toFire;
    synchronized (this) {
        int prev = inventory.getOrDefault(productId, 0);
        int next = prev + quantity;
        inventory.put(productId, next);
        toFire = collectAlertsToFire(productId, prev, next);   // ← COLLECT under lock
    }
    fireAll(toFire);                                            // ← FIRE outside lock
}
```

**Three callouts to deliver out loud while writing this:**

1. *"`getOrDefault(0)` — products not yet in the map implicitly have zero stock. No need to initialize entries we've never received."*

2. *"`collectAlertsToFire` runs inside the lock so it sees the exact prev/next pair. It only BUILDS the list — it does not invoke listeners. The fire happens OUTSIDE the synchronized block."*

3. *"Why fire outside the lock? Two reasons: (a) a slow listener doing network I/O would hold the warehouse lock for seconds — every other operation blocks. (b) a listener that calls back into the warehouse would re-enter the lock indirectly; with non-reentrant locks that's a deadlock, with reentrant locks it's at least surprising. Decoupling capture from delivery eliminates both."*

### 4.2 `Warehouse.removeStock` — same pattern + the negative-stock guard

```java
public boolean removeStock(String productId, int quantity) {
    if (quantity <= 0) return false;
    List<PendingAlert> toFire;
    synchronized (this) {
        int prev = inventory.getOrDefault(productId, 0);
        if (prev < quantity) {
            return false;                                  // ← all-or-nothing
        }
        int next = prev - quantity;
        inventory.put(productId, next);
        toFire = collectAlertsToFire(productId, prev, next);
    }
    fireAll(toFire);
    return true;
}
```

> **Senior callout:** *"All-or-nothing. If `prev < quantity` we return false BEFORE mutating anything — the caller never sees a partially-applied state. Same pattern as Movie Ticket's atomic seat booking."*

### 4.3 `collectAlertsToFire` — the threshold-CROSSING check

```java
private List<PendingAlert> collectAlertsToFire(String productId, int prev, int next) {
    List<AlertConfig> configs = alertConfigs.get(productId);
    if (configs == null) return List.of();
    List<PendingAlert> result = new ArrayList<>();
    for (AlertConfig cfg : configs) {
        if (prev >= cfg.threshold() && next < cfg.threshold()) {     // ← THE rule
            result.add(new PendingAlert(cfg.listener(), productId, next));
        }
    }
    return result;
}
```

> **Senior callout:** *"One arithmetic check captures three behaviors: fires on the first downward crossing (prev was at-or-above, next is below); doesn't duplicate while we stay below (prev is already below the threshold); and naturally resets if stock recovers above and drops again (new crossing). No mutable state on AlertConfig."*

### 4.4 `InventoryManager.transfer` — ordered locks, atomic, deadlock-free

```java
public boolean transfer(String productId, String fromId, String toId, int quantity) {
    if (quantity <= 0)        return false;
    if (fromId.equals(toId))  return false;                     // would deadlock on self-transfer

    Warehouse from = warehouses.get(fromId);
    Warehouse to   = warehouses.get(toId);
    if (from == null || to == null) return false;

    // Order locks by warehouse-id string — breaks the deadlock cycle.
    Warehouse first  = fromId.compareTo(toId) < 0 ? from : to;
    Warehouse second = (first == from) ? to : from;

    synchronized (first) {
        synchronized (second) {
            if (!from.removeStock(productId, quantity)) {
                return false;                                    // ← insufficient stock
            }
            to.addStock(productId, quantity);
            return true;
        }
    }
}
```

**Three callouts to deliver:**

1. *"Self-transfer guarded explicitly. `synchronized(x)` then `synchronized(x)` on the same thread is reentrant in Java — would NOT deadlock — but the operation makes no sense, so I reject it."*

2. *"Lock ordering by `compareTo` on the warehouse id. Both threads in any A↔B pair acquire EAST before WEST. There's no cycle to deadlock around. The choice of ordering doesn't matter — only that everyone agrees."*

3. *"`from.removeStock` and `to.addStock` are themselves synchronized, but Java locks are reentrant per-thread — we already hold both locks via the outer blocks, so the inner synchronized blocks are no-ops on this thread. No double-acquire, no contention."*

> **Subtle limitation worth mentioning:** *"Because alerts in `addStock`/`removeStock` fire AFTER releasing each method's `synchronized(this)`, but transfer's outer `synchronized(first/second)` is still held, alerts during a transfer fire while transfer holds the outer locks. For the interview this is fine; for production-grade you'd extract internal `applyDelta` methods that return alerts to the caller, and the transfer method fires them after releasing both outer locks."*

### 4.5 `getWarehousesWithAvailability` — cross-warehouse query

```java
public List<String> getWarehousesWithAvailability(String productId, int quantity) {
    List<String> result = new ArrayList<>();
    for (Map.Entry<String, Warehouse> e : warehouses.entrySet()) {
        if (e.getValue().checkAvailability(productId, quantity)) {
            result.add(e.getKey());
        }
    }
    return result;
}
```

> *"Each warehouse's `checkAvailability` is itself synchronized so each per-warehouse read is consistent. The aggregated view is eventually-consistent — different warehouses may be at slightly different moments — but that's acceptable for an availability query (worst case you call again and try one of the listed warehouses)."*

### 4.6 Verification — dry-run the threshold-crossing scenario

```
Setup: EAST has 0 WIDGETs. Alert: threshold=10, listener=counter.

addStock("EAST","WIDGET",15):
   prev=0, next=15, inventory={WIDGET:15}
   collectAlertsToFire(15, 15? no... prev=0, next=15):
     prev=0 >= 10 FALSE → no fire
   counter.fires = 0                                                       ✓ (no upward fire)

removeStock("EAST","WIDGET",6):
   prev=15, next=9, inventory={WIDGET:9}
   collectAlertsToFire(15, 9):
     prev=15 >= 10 AND next=9 < 10 → TRUE → CROSSING; collect alert
   counter.fires = 1                                                       ✓ (first downward crossing)

removeStock("EAST","WIDGET",2):
   prev=9, next=7
   collectAlertsToFire(9, 7):
     prev=9 >= 10 FALSE → no fire
   counter.fires = 1                                                       ✓ (no duplicate while below)

addStock("EAST","WIDGET",15):
   prev=7, next=22
   collectAlertsToFire(7, 22):
     prev=7 >= 10 FALSE → no fire
   counter.fires = 1                                                       ✓ (no spurious upward fire)

removeStock("EAST","WIDGET",13):
   prev=22, next=9
   collectAlertsToFire(22, 9):
     prev=22 >= 10 AND next=9 < 10 → TRUE → CROSSING again; collect alert
   counter.fires = 2                                                       ✓ (natural reset on recovery)
```

### 4.7 Verification — dry-run a concurrent bidirectional transfer

```
Setup: A=1000, B=1000 WIDGETs.  Many threads run concurrently:
       even-id threads do transfer("WIDGET","A","B",1)
       odd-id  threads do transfer("WIDGET","B","A",1)

Each thread computes:  first = "A" < "B" ? warehouse("A") : warehouse("B")
                       second = the other one
Therefore: EVERY thread acquires warehouse("A") FIRST, then warehouse("B").
No two threads ever hold them in opposite orders. The deadlock cycle is gone.

Driver actually runs 100 threads of this. Result:
   finished in time:    true     ✓ (no deadlock)
   successful transfers: 100     ✓ (every one completes)
   A + B total stock:    2000    ✓ (invariant preserved — no inventory leaks in/out)
```

> **This is the single most-important test in this problem.** Mention you'd verify it with a `CountDownLatch` barrier (all threads start the transfer simultaneously) and assert (a) the executor terminates within a timeout, (b) `successes == thread count`, (c) `A + B == 2000`.

---

## STEP 5 — Extensibility (~8 min)

The two highest-likelihood follow-ups: **reservations during checkout** (everyone who runs an e-commerce system needs this) and **in-transit inventory** (cross-warehouse transfers don't teleport in the real world).

### 5.1 "How would you prevent overselling during checkout?"

> **Problem in current design:** *"There's no inventory commitment until removeStock fires. Two customers see '1 available', both fill out a 60-second checkout form, only the faster one's request succeeds. The slower one wastes a minute on a form that can't complete."*
>
> **Pattern as the fix:** *"Add a reservation state — between 'available' and 'consumed'. Each warehouse tracks a `Map<String, Integer> reserved` of held-but-not-removed quantities, plus a `Map<String, Reservation>` keyed by reservation id. checkAvailability returns `inventory - reserved`."*
>
> **Tradeoff:** *"Reservation timeout is a business decision. Too short — legitimate slow customers lose their cart. Too long — abandoned carts hold inventory hostage. Typical: 5–15 minutes. Background sweeper releases expired reservations."*

```java
public class Warehouse {
    private final Map<String, Integer>      inventory;            // physical stock
    private final Map<String, Integer>      reserved;             // held-during-checkout
    private final Map<String, Reservation>  reservations;         // by reservation id

    public synchronized boolean reserveStock(String pid, int qty, String resId, long ttlMs) {
        int avail = inventory.getOrDefault(pid, 0) - reserved.getOrDefault(pid, 0);
        if (avail < qty) return false;
        reservations.put(resId, new Reservation(pid, qty, clock.millis() + ttlMs));
        reserved.merge(pid, qty, Integer::sum);
        return true;
    }

    public synchronized boolean confirmReservation(String resId) {
        Reservation r = reservations.remove(resId);
        if (r == null || clock.millis() > r.expiresAt()) return false;
        inventory.merge(r.productId(), -r.quantity(), Integer::sum);
        reserved.merge(r.productId(), -r.quantity(), Integer::sum);
        return true;
    }

    public synchronized void releaseReservation(String resId) {
        Reservation r = reservations.remove(resId);
        if (r != null) reserved.merge(r.productId(), -r.quantity(), Integer::sum);
    }
}
```

> *"The same per-warehouse lock protects both `inventory` and `reserved` — no new lock granularity. Background sweeper periodically scans `reservations` and calls `releaseReservation` on expired ones."*

### 5.2 "How would you handle in-transit inventory between warehouses?"

> **Problem in current design:** *"Transfers magically teleport stock. In reality, when you transfer A→B, the stock spends 3–5 days on a truck — not at A anymore, not at B yet, but still in the system."*
>
> **Pattern as the fix:** *"Promote `Transfer` to a first-class entity that HOLDS inventory during shipment. Extract an `InventoryHolder` interface that both `Warehouse` and `Transfer` implement. Initiating a transfer removes from source and 'adds' to a Transfer holder; completing the transfer moves it to the destination warehouse."*
>
> **Tradeoff:** *"Total system inventory = sum across warehouses AND transfers. Any availability check that queries 'is this stock somewhere in the system' must include in-transit. Most queries only care about 'at a warehouse' availability so this is a small reporting layer, not a refactor of the hot path."*

```java
interface InventoryHolder {
    int     getStock(String productId);
    boolean addStock(String productId, int qty);
    boolean removeStock(String productId, int qty);
}

class Transfer implements InventoryHolder {
    final String id, productId, fromId, toId;
    int quantity;
    long createdAt;
    // ...
}

// InventoryManager gains:
public String initiateTransfer(String pid, String fromId, String toId, int qty) {
    if (!warehouses.get(fromId).removeStock(pid, qty)) return null;
    Transfer t = new Transfer(uuid(), pid, qty, fromId, toId);
    transfers.put(t.id, t);
    return t.id;
}

public boolean completeTransfer(String transferId) {
    Transfer t = transfers.remove(transferId);
    if (t == null) return false;
    warehouses.get(t.toId).addStock(t.productId, t.quantity);
    return true;
}
```

### 5.3 Other "what-if" answers

| Follow-up                                  | Answer                                                                                              |
| ------------------------------------------ | --------------------------------------------------------------------------------------------------- |
| "Persist across restart"                   | Inject a `WarehouseRepository`; write on every state-mutating call; load on boot. Repository interface = clean DIP boundary. |
| "Multiple notification channels (email + Slack + page)" | Compose listeners — `CompositeAlertListener(List<AlertListener>)` fans out. Observer composes naturally. |
| "Rate-limit alerts (don't page if last fired < 1 min ago)" | Decorate the listener — `RateLimitedAlertListener(AlertListener, Duration)`. Stackable. |
| "Audit log of every stock change"          | Add a second Observer interface — `InventoryListener.onStockChanged(...)`. Same fire-outside-lock pattern. |
| "Dynamic add/remove of warehouses"         | Replace `final Map<>` with `volatile AtomicReference<Map<>>`; mutate atomically by swap. Or move to ConcurrentHashMap and accept eventual consistency in `getWarehousesWithAvailability`. |
| "Per-product locks instead of per-warehouse" | Finer concurrency but trickier — transfer of the same product between warehouses needs to lock BOTH product-level locks; ordering still required. Default to per-warehouse unless contention is measured. |

---

## Design Patterns — Hello Interview's canonical 8

> **HI's stance:** *"Patterns arise from good design decisions, not the other way around. Most interview designs use zero to two patterns maximum."*
>
> **Inventory Management's base uses ONE pattern (Observer) — explicitly justified by the requirement** *"keep notification pluggable; what happens after is someone else's problem."* That sentence is the Observer contract in plain English.

### The 5-step timing rule

| Step                       | Use a pattern here?                                                                 |
| -------------------------- | ----------------------------------------------------------------------------------- |
| **1. Requirements**        | **Never.**                                                                          |
| **2. Entities**            | **Sometimes** — if a clear seam exists. *AlertListener belongs here.*               |
| **3. Class Design**        | **YES, when you can state the design pressure in one sentence.** *Observer passes.* |
| **4. Implementation**      | **No new patterns.**                                                                |
| **5. Extensibility**       | **YES — Composite listener, Decorator for rate-limiting, InventoryHolder interface for in-transit.** |

### Hello Interview's canonical 8 × interviewer trigger

| # | Pattern              | Category   | Trigger phrase                                                                | One-line response                                                                                       |
| - | -------------------- | ---------- | ------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------- |
| 1 | **Strategy**         | Behavioral | "different rules" · "swap algorithms"                                           | *"Promote behavior to an interface; inject."*                                                            |
| 2 | **Observer** ⭐       | Behavioral | "notify multiple" · "pluggable notification" · "callback when X happens"        | *"X publishes events; subscribers register independently."*                                              |
| 3 | **State Machine**    | Behavioral | "behavior depends on state"                                                     | *"Each state owns its transitions."*                                                                    |
| 4 | **Factory**          | Creational | "heterogeneous config"                                                          | *"Centralize creation behind a method."*                                                                  |
| 5 | **Builder**          | Creational | "many optional fields"                                                          | *"Builder collects fields incrementally."*                                                              |
| 6 | **Singleton**        | Creational | "exactly one" · "global"                                                        | *"Resist textbook Singleton; DI a single instance."*                                                    |
| 7 | **Decorator**        | Structural | "wrap with X" · "rate-limit / log / retry the listener"                         | *"Wrap X in decorators, each adding one concern."*                                                       |
| 8 | **Facade**           | Structural | "single entry point"                                                            | *"Orchestrators usually ARE facades."*                                                                    |

### How this maps to Inventory Management specifically

**Already in the BASE design — call out by name:**

- **Observer (#2)** ⭐ — `AlertListener` interface; warehouses are subjects, listeners are observers. **Justified directly by R7 + the clarifying-Q answer.** Name it in Step 2.
- **Facade (#8)** — `InventoryManager` is the only class application code touches.
- **Information Expert** (GRASP principle) — per-warehouse state lives on each Warehouse.
- **Tell, Don't Ask** (principle) — InventoryManager calls `warehouse.addStock(...)`; never reaches into the inventory map.
- **Immutability** (principle) — `AlertConfig` is a record. The warehouses map is immutable post-ctor.

> **Why no Strategy in base?** The one-sentence test fails: there's no algorithmic variation in the problem. Pricing isn't a thing, allocation rules aren't a thing, the alert math is fixed (threshold crossing). Save Strategy for §5 if "different alert delivery semantics" or "different allocation policies" come up.

**Reach for these on the matching Step-5 follow-up — 3-beat phrasing (problem → pattern → tradeoff):**

| Follow-up                                  | Pattern (HI's 8)             | Your line                                                                                            |
| ------------------------------------------ | ---------------------------- | ---------------------------------------------------------------------------------------------------- |
| "Multiple notification channels"           | **Composite listener (Observer + Composite)** | *"Wrap multiple listeners in `CompositeAlertListener(List<AlertListener>)`. Implements AlertListener — slot in as one. Observer composes naturally."* |
| "Rate-limit / debounce alerts"             | **Decorator (#7)**           | *"`RateLimitedAlertListener(AlertListener, Duration)`. Stackable with email/Slack/page wrappers. Decorator on the listener axis."* |
| "Persist across restart"                   | (Repository — not HI's 8)    | Describe technique: *"Inject `WarehouseRepository`; write on every mutation."* Name Repository only if asked. |
| "Multiple kinds of warehouses (cold-storage, hazmat)" | **Factory (#4)**    | *"`WarehouseFactory.create(WarehouseKind)` produces the right pre-configured warehouse."* |
| "Different alert-firing semantics (level-triggered vs edge-triggered)" | **Strategy (#1)** | *"`AlertTriggerStrategy` — extracts the threshold-crossing logic. Today's threshold-crossing impl is one strategy; future 'time-since-last-fire' is another."* |
| "Reservations during checkout"             | (per-warehouse state extension) | *"Two new maps on Warehouse — reserved + reservations. Same per-warehouse lock. See §5.1."* |
| "In-transit inventory between warehouses"  | (Polymorphism over inventory) | *"Extract `InventoryHolder` interface. Both Warehouse and Transfer implement it. See §5.2."* |

**Patterns to actively refuse:**

- **Singleton on InventoryManager** — kills tests; DI a single instance.
- **State pattern on Warehouse** — there are no per-state behaviors; warehouses are uniform.
- **Builder for `Warehouse(id)`** — one constructor arg. Pure ceremony.
- **Strategy on the threshold-crossing math** — there's only one correct rule; promoting it to an interface is speculative abstraction.

### One sentence to say at the end of Step 3

> *"The base design names one pattern out loud — Observer for the AlertListener — plus Facade and Information Expert as principles. Observer earns rent here because the requirements explicitly say notification delivery is pluggable. Other patterns (Composite listener, Decorator for rate-limit, Strategy for allocation) land in Step 5 if their triggers surface."*

---

## Interview deep-dives — the questions you'll definitely get asked

### 1. Complexity (Big-O)

Let `W` = number of warehouses, `P` = products per warehouse, `A` = alert configs per (warehouse, product) — typically 1–3.

| Operation                                | Time                                              | Space               | Notes                                                                              |
| ---------------------------------------- | ------------------------------------------------- | ------------------- | ---------------------------------------------------------------------------------- |
| `addStock` / `removeStock`               | **`O(A)`** for alert collection                   | O(A) per call       | HashMap get/put is O(1); only the alert scan is bounded by A                       |
| `getStock` / `checkAvailability`         | **`O(1)`**                                        | O(1)                | Single map lookup                                                                  |
| `transfer`                               | **`O(A)`** (dominated by removeStock + addStock)   | O(A)                | Two locks acquired in order; pure delegation otherwise                            |
| `getWarehousesWithAvailability`          | **`O(W)`** — checks every warehouse               | O(matching)         | Could be `O(1)` with an inverted index `Map<product, List<warehouseId>>` — call out the tradeoff |
| `setLowStockAlert`                       | **`O(1)`** amortized                              | O(1) per call       | Add to per-product list                                                            |
| Storage — Warehouse                      | —                                                 | **`O(P + A·P)`**   | Inventory map + alert-config map                                                  |
| Storage — InventoryManager               | —                                                 | **`O(W)`**          | Just the warehouses map                                                            |

> **Senior callout:** *"All hot-path operations are O(1) or O(A) where A is small. The only `O(W)` operation is `getWarehousesWithAvailability`. If W grew to thousands and that became a bottleneck, I'd maintain an inverted index — per-product list of warehouse ids — keyed by product. Tradeoff: one more map to keep consistent on every add/remove."*

### 2. Concurrency / thread-safety — the full menu

| Approach                                | When to use                                  | Cost                                                              |
| --------------------------------------- | -------------------------------------------- | ----------------------------------------------------------------- |
| **Per-warehouse `synchronized(this)`** ⭐ | **Default.** Correct + simple                | Same-warehouse traffic serializes; different warehouses are independent |
| Per-product locks within a warehouse    | High contention on a single warehouse        | Tricky — transfer needs BOTH product locks in BOTH warehouses with ordering |
| `ReadWriteLock` per warehouse           | Heavily read-biased workloads                | Slight complexity; reads + writes still serialize against each other; only concurrent reads benefit |
| Coordination (single command thread)    | Distributed / strict ordering required       | All work serialized through a queue — a bottleneck unless carefully sharded |

> **The two deadlock traps in this problem:**
> 1. **Bidirectional transfer:** A→B and B→A concurrent. Fix: **acquire warehouse locks in `compareTo` order** (the only pattern shown in the code).
> 2. **Self-transfer:** `synchronized(x); synchronized(x);` is reentrant in Java, but the operation is nonsensical. Reject `fromId.equals(toId)` at the boundary.

### 3. Testing — what to write tests for

| Test category                | Cases to cover                                                                                              |
| ---------------------------- | ----------------------------------------------------------------------------------------------------------- |
| Basic invariants             | `removeStock` returns false (no state change) when insufficient; `transfer` rolls back the same way        |
| **Alert crossing**           | Stock crosses down → fire; stays below → no duplicate; recovery; crosses again → fire again                |
| Multi-alert per product      | Two configs at thresholds 20 and 5 → first fires at 20→19, second fires at 6→5                              |
| Multi-warehouse availability | Stock in EAST=30, WEST=10 → query for >=20 returns [EAST] only                                              |
| Concurrent same-warehouse    | 50 threads remove 1-unit each from a warehouse with 20 stock → exactly 20 succeed, 30 fail                  |
| **Concurrent transfer**      | 100 threads A↔B both directions; 1 unit each → all 100 succeed, no deadlock, A+B total unchanged           |
| Listener exception           | Throwing listener does not corrupt warehouse state or kill the caller                                       |
| Self-transfer                | `transfer(p, "X", "X", 5)` returns false without any state change                                           |

```java
@Test
void hundred_threads_bidirectional_transfer_no_deadlock() throws Exception {
    InventoryManager mgr = new InventoryManager(List.of("A", "B"));
    mgr.addStock("A", "WIDGET", 1000);
    mgr.addStock("B", "WIDGET", 1000);

    int N = 100;
    ExecutorService pool = Executors.newFixedThreadPool(N);
    CountDownLatch fire = new CountDownLatch(1);
    AtomicInteger ok = new AtomicInteger();

    for (int i = 0; i < N; i++) {
        final int n = i;
        pool.submit(() -> {
            try { fire.await(); } catch (InterruptedException e) { return; }
            String from = (n % 2 == 0) ? "A" : "B";
            String to   = (n % 2 == 0) ? "B" : "A";
            if (mgr.transfer("WIDGET", from, to, 1)) ok.incrementAndGet();
        });
    }
    fire.countDown();
    pool.shutdown();
    boolean finished = pool.awaitTermination(5, TimeUnit.SECONDS);

    assertTrue(finished);
    assertEquals(100, ok.get());
    assertEquals(2000, mgr.getStock("A", "WIDGET") + mgr.getStock("B", "WIDGET"));
}
```

> **Senior callout:** *"This is THE test for this problem. Without `compareTo`-ordered lock acquisition, `awaitTermination` would time out — that's deadlock. With ordered locks, all 100 complete and the 2000-total invariant holds."*

### 4. SOLID mapping

| Letter                       | Where it shows up                                                                                          |
| ---------------------------- | ---------------------------------------------------------------------------------------------------------- |
| **S** Single Responsibility  | InventoryManager = routing + cross-warehouse coordination. Warehouse = per-location state. AlertConfig = threshold+listener pair. AlertListener = notification mechanism. Four reasons to change, four types. |
| **O** Open/Closed            | New notification channel = new `AlertListener` impl. New reservation system = new fields on Warehouse + new methods. Zero changes to existing methods on InventoryManager. |
| **L** Liskov Substitution    | Any `AlertListener` substitutable behind the interface — same `onLowStock(...)` contract, same expected absence of side effects on the warehouse. |
| **I** Interface Segregation  | `AlertListener` has ONE method. No `onRecovery`, `onAdd`, `onWhatever` mixed in. Recovery alerts (if added) get their own interface or are a separate Observer. |
| **D** Dependency Inversion   | Warehouse depends on the `AlertListener` interface, not on `EmailAlertListener` or `WebhookAlertListener` concretes. Listener wiring happens at composition time, outside the hot path. |

### 5. "Summarize your design in 30 seconds"

> *"Four types: InventoryManager (orchestrator + facade), Warehouse (per-location inventory map + per-product alert-config map + per-warehouse synchronized lock), AlertConfig (immutable threshold + listener pair), AlertListener (Observer interface — pluggable notification). The base design names Observer by name — explicitly justified by the requirement to keep notification pluggable. Two concurrency rules: (a) every Warehouse mutation is `synchronized(this)`; (b) `transfer` acquires both warehouse locks in `compareTo`-ordered sequence to prevent deadlock when threads transfer in opposite directions concurrently. Alerts use the THRESHOLD-CROSSING rule — `prev >= threshold && next < threshold` — which captures fire-once + no-duplicate + natural-reset-on-recovery in one arithmetic check, with zero state on AlertConfig. Alerts are COLLECTED while holding the lock but FIRED after releasing it — prevents slow listeners from holding the warehouse lock and prevents reentrant-callback deadlocks. The 100-thread bidirectional-transfer driver test proves no deadlock and 2000-unit total invariant. Extensions: reservation pool for checkout (additional reserved map on Warehouse), in-transit inventory via `InventoryHolder` interface that both Warehouse and Transfer implement."*

That's ~60 seconds. Hits: structure, the Observer + lock-ordering choices, the threshold-crossing rule, the fire-outside-lock pattern, and the empirical concurrency proof.

---

## Closing soundbites (memorize these)

- **Opening:** *"The headline correctness concerns are no-negative inventory and transfer atomicity under concurrent calls."*
- **Why Observer in the base:** *"R7 + the clarifying-Q literally describe Observer — pluggable callback that doesn't care what the listener does. This isn't pattern-stuffing; it's responding to the stated requirement."*
- **Why threshold-CROSSING:** *"`prev >= threshold && next < threshold` fires once on the downward crossing, naturally doesn't duplicate while below, and naturally resets on recovery. Zero state on AlertConfig."*
- **Why collect-under-lock fire-outside-lock:** *"A slow listener doing network I/O would hold the warehouse lock for seconds — every other operation blocks. A listener calling back into the warehouse would re-enter the lock. Both go away if we decouple capture from delivery."*
- **Why ordered lock acquisition in transfer:** *"Without it, A→B and B→A concurrent threads deadlock. With it, both threads acquire EAST before WEST — no cycle, no deadlock."*
- **Why per-warehouse (not global, not per-product):** *"Coarse enough to be simple — one lock per warehouse. Fine enough that different warehouses run in parallel. Per-product would force product-level lock ordering across warehouses for transfer — more correct in theory, way more complex in practice."*
- **On reservations:** *"Reserved inventory is a third map on Warehouse — same per-warehouse lock, no new granularity needed. Background sweeper releases expired reservations."*

---

## Top mistakes that lose points

- **No `synchronized` on the warehouse mutators** — concurrent removes can both pass `prev >= quantity` and both decrement; inventory goes negative.
- **Firing listeners INSIDE the synchronized block** — a slow listener holds the warehouse lock; a listener calling back into the warehouse deadlocks (non-reentrant) or surprises (reentrant).
- **No lock ordering in `transfer`** — guaranteed deadlock under bidirectional concurrent transfers.
- **Mutating BEFORE the negative-stock check in `removeStock`** — partial state change on failure. Always validate first, mutate after.
- **Using "currently below" instead of CROSSING for alerts** — duplicate fires on every removal while below the threshold; spam.
- **State on AlertConfig** (e.g., `boolean hasFired`) — works for fire-once, but the recovery-then-drop reset becomes a new bug surface. CROSSING captures everything with zero state.
- **`Product` class with no behavior** — speculative ceremony.
- **Single global lock on InventoryManager** — different warehouses serialize for no correctness reason.
- **Forgetting to guard `fromId.equals(toId)` in transfer** — operation is nonsensical; should reject at the boundary.
- **Returning the internal `inventory` map** from any method — caller can mutate it and bypass all synchronization. Defensive copies or no exposure.
- **Skipping the bidirectional-transfer test** — the empirical deadlock proof is exactly what the interviewer wants to see.

---

## Files in this folder (your reference implementation)

| File                                              | What it shows                                                                            |
| ------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| `model/AlertListener.java`                        | Observer interface — `onLowStock(warehouseId, productId, currentQty)`                    |
| `model/AlertConfig.java`                          | Immutable record pairing threshold + listener                                            |
| `Warehouse.java`                                  | **The hot class** — per-warehouse `synchronized(this)`, threshold-CROSSING check, **fire-outside-lock** pattern, isolated listener exceptions |
| `InventoryManager.java`                           | Orchestrator + facade — ordered-lock transfer, cross-warehouse queries                   |
| `InventoryManagerDriver.java`                     | 5 scenarios — basics / alert crossing / negative rejection / **50-thread concurrent remove** / **100-thread bidirectional A↔B transfer (deadlock test)** |

Run from the project root:

```bash
mvn -q compile exec:java \
  -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.inventory.InventoryManagerDriver
```
