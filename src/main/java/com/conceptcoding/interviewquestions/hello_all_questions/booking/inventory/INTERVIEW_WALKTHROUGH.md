# Inventory Management (multi-warehouse)

> **Why it's in the deck:** stock that can't go negative, transfers between warehouses without deadlock, and low-stock alerts. It's the LLD core of Amazon's ★ "Inventory / Flash Sale (last-unit contention)" HLD question: Q1 covers the flash-sale part.
>
> **The crux (what's really being tested):**
> 1. **No negative stock:** check and subtract under one lock, all-or-nothing.
> 2. **Transfers lock two warehouses in a fixed order:** A→B and B→A at the same time must not deadlock.
> 3. **Alerts fire once per downward crossing, and only after locks are released.**
>
> **Family:** F2 Booking under contention + concurrency tool C (lock ordering) + Observer. See [foundations](../../00_AMAZON_LLD_FOUNDATIONS.md).

---

## 1. Plain-language picture

### Shelves in several warehouses
```
Bengaluru:  iphone 10, charger 40          Delhi:  iphone 2, charger 15
removeStock(BLR, iphone, 12) → only 10 → refuse, nothing changes
transfer(iphone, BLR → DEL, 7) → BLR 3, DEL 9, in one step (never "taken from BLR but not yet in DEL")
```

### The deadlock trap
Two clerks. Clerk 1 moves iPhones BLR → DEL: grabs the BLR key, then wants the DEL key. Clerk 2 moves chargers DEL → BLR: grabs the DEL key, then wants the BLR key. **Each holds what the other needs, forever.** The fix: **everyone takes keys in alphabetical order** (BLR before DEL), whichever direction they're moving. Then both clerks go for BLR first; one waits, and the other finishes.

### Alerts: once, when it drops below
"Tell me when iPhones drop below 5." Stock 10 → 3: alert. 3 → 2: no new alert (still below). Restocked to 20, then down to 4: alert again. The rule is fire on the **crossing** from ≥ threshold to < threshold. No flags to remember.

### Alerts must not run while holding the warehouse
An alert might send an email (slow) or check another warehouse. If it ran while holding the warehouse lock, every other order would wait for the email, or deadlock. So stock changes under the lock, the alerts to send are **noted**, and they're **sent after** the lock is released. (This deck had a bug where transfers sent alerts while still holding both locks; it's now fixed and covered by a driver test.)

---

## 2. From story to design

| # | Decision | Rejected | Why |
|---|---|---|---|
| D1 | `Warehouse` owns `Map<product, qty>`; every read and write `synchronized` on the warehouse | one global lock | Different warehouses never block each other. |
| D2 | `removeStock` checks and subtracts in one locked step; returns `false` if short | check, then subtract | Check-then-act across two calls oversells. |
| D3 | `transfer` locks both warehouses in **id order** | locking `from` then `to` | A→B and B→A at once would deadlock. |
| D4 | Java locks are **reentrant**: inside `transfer`, the warehouse's own synchronized methods don't block | separate unlocked internals for everything | The same thread can re-enter a lock it holds. |
| D5 | Alert = **crossing** check (`prev ≥ t && next < t`) | a "fired" flag per alert | Stateless; auto-resets when stock recovers; no duplicates. |
| D6 | Collect alerts under the lock, **fire after** releasing it, including in `transfer` | firing inside the lock | A slow or re-entrant listener can't stall or deadlock warehouses. |
| D7 | Several thresholds per product (`List<AlertConfig>`) | one threshold | "Warn at 20, critical at 5." |
| D8 | `AlertListener` (Observer); each call in try/catch | warehouse sending emails itself | The warehouse doesn't care what the listener does. |

### Class shape
```
InventoryManager                             ← the service: routes to warehouses; transfer() with ordered locks
  Map<warehouseId, Warehouse>
  addStock · removeStock · getStock · getWarehousesWithAvailability · setLowStockAlert · transfer

Warehouse                                    ← one lock per warehouse
  Map<product, qty> · Map<product, List<AlertConfig>>
  addStock · removeStock · getStock · checkAvailability · setLowStockAlert
  addStockCollectingAlerts / removeStockCollectingAlerts (for transfer) · fireAll

AlertConfig { threshold, listener }         «interface» AlertListener  onLowStock(warehouse, product, qty)
```

---

## 3. Patterns that earn their place

| Pattern | Problem it solves | Without it |
|---|---|---|
| **Observer** (`AlertListener`) | email, reorder bots and dashboards react to low stock | the warehouse knows every reaction |
| **Lock ordering** (concurrency, not GoF) | two-warehouse transfers without deadlock | random deadlocks under load |

**Say:** *"One lock per warehouse; remove is check-and-subtract under it. Transfer takes both locks in id order to avoid deadlock. Alerts fire on the downward crossing, collected under the lock and fired after it."*

**Tempting but wrong:** a Singleton manager, Strategy (no interchangeable algorithms in the base; fulfilment choice is a follow-up), a global `synchronized` on the manager.

---

## 4. The 45-minute run

### Clarify (min 0–5)
> **You:** Several warehouses, each with stock per product. Add, remove, check, and find warehouses that have enough?
> **Interviewer:** Yes.
> **You:** Stock can never go negative; a remove that's too big is refused entirely?
> **Interviewer:** Yes.
> **You:** Move stock between warehouses atomically?
> **Interviewer:** Yes.
> **You:** Low-stock alerts per product and warehouse, possibly several thresholds?
> **Interviewer:** Yes, but don't spam.
> **You:** Heavy concurrency?
> **Interviewer:** Yes.

### Timeline
| Min | Do |
|---|---|
| 5–9 | Draw warehouses; the deadlock picture; the crossing rule. |
| 9–12 | Code step 1: `AlertListener`, `AlertConfig`. |
| 12–24 | Code step 2: **`Warehouse`**: `addStock`, `removeStock`, crossing detection, fire-outside-lock. |
| 24–33 | Code step 3: **`InventoryManager.transfer`** with ordered locks; the delegating methods. |
| 33–38 | Dry run: two opposite transfers; the alert sequence 10 → 3 → 2 → 20 → 4. |
| 38–45 | Follow-ups: reservations / flash sale. |

### The code you write, in this order

**Step 1: alert types** ([model/](model/))
```java
public interface AlertListener {
    void onLowStock(String warehouseId, String productId, int currentQuantity);
}

public class AlertConfig {
    private final int threshold;
    private final AlertListener listener;
    public AlertConfig(int threshold, AlertListener listener) {
        if (threshold <= 0) throw new IllegalArgumentException("threshold must be > 0");
        this.threshold = threshold;
        this.listener = Objects.requireNonNull(listener);
    }
    public int threshold() { return threshold; }
    public AlertListener listener() { return listener; }
}
```

**Step 2: the warehouse** ([Warehouse.java](Warehouse.java))
```java
public class Warehouse {
    private final String id;
    private final Map<String, Integer> inventory = new HashMap<>();
    private final Map<String, List<AlertConfig>> alertConfigs = new HashMap<>();

    public void addStock(String productId, int quantity) {
        fireAll(addStockCollectingAlerts(productId, quantity));          // fire AFTER the lock
    }

    public boolean removeStock(String productId, int quantity) {
        List<PendingAlert> toFire = removeStockCollectingAlerts(productId, quantity);
        if (toFire == null) return false;
        fireAll(toFire);
        return true;
    }

    synchronized List<PendingAlert> addStockCollectingAlerts(String productId, int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("quantity must be > 0");
        int prev = inventory.getOrDefault(productId, 0);
        int next = prev + quantity;
        inventory.put(productId, next);
        return collectAlertsToFire(productId, prev, next);
    }

    synchronized List<PendingAlert> removeStockCollectingAlerts(String productId, int quantity) {   // null = not enough
        if (quantity <= 0) return null;
        int prev = inventory.getOrDefault(productId, 0);
        if (prev < quantity) return null;                                // all-or-nothing
        int next = prev - quantity;
        inventory.put(productId, next);
        return collectAlertsToFire(productId, prev, next);
    }

    // crossing: was at/above, now below → fire once; stays quiet while below; re-arms on recovery
    private List<PendingAlert> collectAlertsToFire(String productId, int prev, int next) {
        List<PendingAlert> result = new ArrayList<>();
        for (AlertConfig cfg : alertConfigs.getOrDefault(productId, List.of())) {
            if (prev >= cfg.threshold() && next < cfg.threshold()) result.add(new PendingAlert(cfg.listener(), productId, next));
        }
        return result;
    }

    void fireAll(List<PendingAlert> alerts) {                            // called with NO lock held
        for (PendingAlert a : alerts) {
            try {
                a.listener.onLowStock(id, a.productId, a.currentQuantity);
            } catch (Exception e) {
                System.err.println("Warehouse " + id + ": alert listener threw — " + e.getMessage());
            }
        }
    }
    // + synchronized getStock, checkAvailability, setLowStockAlert; PendingAlert {listener, productId, qty}
}
```

**Step 3: transfer with ordered locks** ([InventoryManager.java](InventoryManager.java))
```java
public boolean transfer(String productId, String fromId, String toId, int quantity) {
    if (quantity <= 0 || fromId.equals(toId)) return false;
    Warehouse from = warehouses.get(fromId), to = warehouses.get(toId);
    if (from == null || to == null) return false;

    Warehouse first  = fromId.compareTo(toId) < 0 ? from : to;          // everyone locks in id order
    Warehouse second = (first == from) ? to : from;

    List<Warehouse.PendingAlert> fromAlerts, toAlerts;
    synchronized (first) {
        synchronized (second) {
            fromAlerts = from.removeStockCollectingAlerts(productId, quantity);
            if (fromAlerts == null) return false;                       // not enough: nothing changed
            toAlerts = to.addStockCollectingAlerts(productId, quantity);
        }
    }
    from.fireAll(fromAlerts);                                           // both locks released now
    to.fireAll(toAlerts);
    return true;
}
// addStock / removeStock / getStock / getWarehousesWithAvailability / setLowStockAlert: look up and delegate
```
**Shape to remember:** sort the two locks by id → lock both → remove (or fail) → add → unlock both → fire alerts.

### Dry run
```
Thread 1: transfer BLR→DEL     Thread 2: transfer DEL→BLR   (same instant)
both: first = BLR (alphabetically), second = DEL
T1 locks BLR → T2 waits for BLR (it never got DEL, so no cycle) → T1 locks DEL → moves → unlocks → T2 proceeds
Alerts (threshold 5): 10 → 3 fires · 3 → 2 silent · 2 → 20 silent · 20 → 4 fires again
```
The driver runs 100 concurrent opposite transfers: no deadlock, and total stock is conserved.

---

## 5. Follow-ups: answer out loud first, then open

<details>
<summary><b>Q1. Flash sale: 100 units, 1,000 buyers, payment takes a minute. No overselling, and abandoned carts must free up.</b></summary>

**Reserve, then confirm**, with expiring holds (the same idea as Movie Ticket's seat holds):
```java
public class ReservableStock {
    private int onHand;                                         // physically in the warehouse
    private final Map<String, Instant> holds = new HashMap<>(); // reservationId → expiry (1 unit each)
    private final Clock clock;

    private int available() {                                   // on hand − unexpired holds
        Instant now = clock.instant();
        holds.values().removeIf(exp -> !now.isBefore(exp));     // lazy expiry: abandoned carts free up
        return onHand - holds.size();
    }

    public synchronized String reserve(Duration ttl) {
        if (available() <= 0) throw new IllegalStateException("Sold out");
        String id = UUID.randomUUID().toString();
        holds.put(id, clock.instant().plus(ttl));
        return id;
    }

    public synchronized void confirm(String reservationId) {    // paid: the unit leaves stock
        Instant exp = holds.remove(reservationId);
        if (exp == null || !clock.instant().isBefore(exp)) throw new IllegalStateException("Reservation expired");
        onHand--;
    }

    public synchronized void release(String reservationId) { holds.remove(reservationId); }   // payment failed
}
// 1,000 concurrent reserve() calls on 100 units → exactly 100 succeed; after the TTL, unpaid holds return.
```
At Amazon scale: Redis `DECR` on a stock counter (atomic; if the result is < 0, `INCR` back and refuse), or a DB conditional update `UPDATE stock SET qty = qty - 1 WHERE sku = ? AND qty > 0`. Put a queue in front to smooth the spike.
</details>

<details>
<summary><b>Q2. An order has 3 products: all-or-nothing in one warehouse.</b></summary>

Lock the warehouse once, check **all** lines, then subtract **all** (or none). Across several warehouses: lock them in id order (like transfer), check everything, subtract everything, unlock, and fire alerts after. Never subtract line by line and "roll back on failure": another thread can see the half-done state.
</details>

<details>
<summary><b>Q3. Choose which warehouse ships an order.</b></summary>

`FulfilmentStrategy` (Strategy): nearest warehouse with all items, the one with the most stock (balancing), or split across warehouses when none has everything (fewest shipments first). `getWarehousesWithAvailability` is the building block. Try the candidates in ranked order and claim with `removeStock`; if it fails (sold meanwhile), try the next. Same pattern as rider assignment.
</details>

<details>
<summary><b>Q4. Automatic reordering from suppliers.</b></summary>

An `AlertListener` that creates a purchase order when stock crosses its reorder point. Because alerts fire **once per crossing**, you don't create 50 duplicate purchase orders while stock sits below the threshold. Track "PO pending" so the next crossing (after a partial restock) doesn't double-order.
</details>

<details>
<summary><b>Q5. Many servers, one inventory.</b></summary>

The DB becomes the lock: `UPDATE stock SET qty = qty - :n WHERE warehouse = ? AND sku = ? AND qty >= :n`. 1 row = success, 0 = not enough. A transfer = one transaction updating both rows, **in a consistent order** (by warehouse id) to avoid DB deadlocks too. Alerts: publish an event after commit (transactional outbox) so a crash never loses or invents an alert.
</details>

<details>
<summary><b>Q6. How do you test it?</b></summary>

Basics, alert crossing (0→15 silent, 15→9 fires, 9→7 silent, 7→22 silent, 22→9 fires), rejected over-removals and transfers leave state unchanged, 50 threads removing from 20 units → exactly 20 succeed, 100 opposite transfers → no deadlock and the total conserved, and transfer alerts fire with no lock held. All are in the driver.
</details>

---

## 6. Traps
1. `if (getStock() >= n) remove(n)`: two calls, so a race and overselling.
2. Locking `from` then `to` (deadlock on opposite transfers).
3. Firing alerts while holding a lock (including inside transfer).
4. A "fired" boolean that never resets, or alerting on every decrement below the threshold.
5. A global lock on the manager.
6. Line-by-line subtract with "undo" for multi-item orders.

## 7. Recall check
1. Explain the deadlock and the ordered-locking fix in two sentences.
2. Write the crossing condition. Why no flag?
3. Why must alerts fire outside the lock? What was the transfer bug?
4. Flash sale: reserve/confirm/release. What frees abandoned carts?
5. Java locks are reentrant: where does `transfer` rely on that (or avoid needing it)?

**Rebuild in 10 minutes:** `Warehouse` (map, `removeStockCollectingAlerts`, crossing, `fireAll`) · `InventoryManager.transfer` (ordered locks, fire after).

---

**Files:** `InventoryManager` · `Warehouse` · `model/` (`AlertConfig`, `AlertListener`) · `InventoryManagerDriver` (basics, alert crossing, no negatives, 50-thread removal, 100 opposite transfers, alerts fire outside locks)
```bash
mvn -q compile exec:java -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.booking.inventory.InventoryManagerDriver
```
