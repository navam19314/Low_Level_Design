# Inventory Management (multi-warehouse)

> 30–40 min round. Amazon ★ (the LLD core of the "Inventory / Flash Sale" question). Phases: **Requirements (5) → Entities (3) → Class Design (10–15) → Implementation (10) → Extensibility (5)**.
> Sister problem: [Movie Ticket](../movieticket/INTERVIEW_WALKTHROUGH.md), the same "check and take in one step" idea, with stock counts instead of seats.

---

## 0. The problem in plain words (read first, don't say it in the interview)

**Picture Amazon's warehouses.** Bengaluru has 10 iPhones and Delhi has 2. Orders take stock out, deliveries put stock in, and sometimes trucks move stock from one warehouse to another.

**The whole system is 4 actions:**
```
Delivery arrives  →  addStock(BLR, iphone, 10)
Order ships       →  removeStock(BLR, iphone, 4)      → refused if there isn't enough
Where can I ship? →  warehousesWith(iphone, 5)         → [BLR]
Truck moves stock →  transfer(iphone, BLR → DEL, 5)   → taken from one AND added to the other, in one step
```

**Real world → code**
| In the real world | In the code |
|---|---|
| One warehouse and its shelves | `Warehouse`: product → quantity |
| The warehouse's door key | `synchronized` on that `Warehouse` |
| Head office running all warehouses | `InventoryManager` |
| A product | just a String id (no class) |

**Only 2 things are tricky:**
1. **Stock can never go negative.** Two buyers want the last iPhone. "Is there one?" and "take it" must be **one step** under the warehouse's lock, so only one buyer gets it.
2. **A transfer needs two keys, and that can deadlock.** Clerk 1 moves BLR → DEL: takes the BLR key and waits for DEL. Clerk 2 moves DEL → BLR: takes the DEL key and waits for BLR. They wait for each other forever. **Fix:** everyone takes the keys **in alphabetical order** (BLR before DEL), whichever way the stock moves. Then both clerks go for BLR first: one waits, and the other finishes.

**Picture this run before you start coding:**
```
BLR iphone 10, DEL iphone 2
remove 12 from BLR      → refused, BLR still 10 (nothing changed)
remove 4 from BLR       → BLR 6
who can ship 5?         → [BLR]
transfer 5 BLR → DEL    → BLR 1, DEL 7
transfer 3 BLR → DEL    → refused (BLR has only 1), nothing moved
50 buyers, 20 units     → exactly 20 succeed
100 transfers BLR⇄DEL at once → no deadlock, the total stays the same
```
If you can explain this run, you understand the problem.

---

## 1. Requirements (~5 min)

**Ask:** several warehouses? Can stock go negative, and is a too-big remove refused entirely? Transfers between warehouses? Low-stock alerts? Many orders at once?

```
1. addStock / removeStock per warehouse and product; never below zero (refuse, change nothing)
2. getStock, and warehousesWith(product, qty): who can ship this order
3. transfer(product, from, to, qty): all at once, or nothing
4. Safe with many orders at once: no overselling, and no deadlock on opposite transfers

Out of scope: low-stock alerts, reservations, multi-product orders, choosing a warehouse
```
If the interviewer insists on alerts in the base, write Extension 1 right after `transfer`.

---

## 2. Entities & Relationships (~3 min)

```
InventoryManager
   └── Warehouse (many)   id, product → quantity   ← the lock lives here
```
- **The lock is per warehouse.** Orders in Delhi never wait for orders in Bengaluru.
- **A product is just an id.** It has no behaviour in this problem.
- **The warehouses are fixed at startup,** so the manager's map is only ever read and needs no lock.

---

## 3. Class Design (~10 min)

```
InventoryManager
  state:     warehouses (id → Warehouse)
  behaviour: addStock · removeStock → boolean · getStock · warehousesWith(product, qty) → ids · transfer(...) → boolean

Warehouse   id, stock (product → qty)   synchronized addStock, removeStock → boolean, getStock
```
- **`removeStock` returns `false` for "not enough".** That's a normal business outcome, not an error. Bad input (qty ≤ 0, unknown warehouse) throws.
- **`transfer` locks two warehouses in id order.** This is the one place that needs two locks.
- **No patterns in the base.** The crux is locking. Observer arrives with alerts, Strategy with choosing a warehouse (Extensions 1, 4).

---

## 4. Implementation (~10 min)

**Write it in this order:** `Warehouse` → `InventoryManager.transfer` (the crux) → the simple delegating methods.

```java
public class Warehouse {
    private final String id;
    private final Map<String, Integer> stock = new HashMap<>();

    // constructor, getId()

    public synchronized void addStock(String productId, int quantity) {
        stock.put(productId, getStock(productId) + quantity);
    }

    // check and subtract in ONE locked step; false = not enough (nothing changed)
    public synchronized boolean removeStock(String productId, int quantity) {
        int current = getStock(productId);
        if (current < quantity) return false;
        stock.put(productId, current - quantity);
        return true;
    }

    public synchronized int getStock(String productId) {
        return stock.getOrDefault(productId, 0);
    }
}
```

```java
public class InventoryManager {
    private final Map<String, Warehouse> warehouses = new HashMap<>();   // filled once in the constructor

    public InventoryManager(List<String> warehouseIds) {
        for (String id : warehouseIds) warehouses.put(id, new Warehouse(id));
    }

    public boolean transfer(String productId, String fromId, String toId, int quantity) {
        checkQuantity(quantity);
        if (fromId.equals(toId)) throw new IllegalArgumentException("Same warehouse: " + fromId);
        Warehouse from = findWarehouse(fromId);
        Warehouse to = findWarehouse(toId);

        Warehouse first = fromId.compareTo(toId) < 0 ? from : to;      // smaller id first, always
        Warehouse second = (first == from) ? to : from;
        synchronized (first) {
            synchronized (second) {
                if (!from.removeStock(productId, quantity)) return false;   // not enough: nothing moved
                to.addStock(productId, quantity);
                return true;
            }
        }
    }

    public List<String> warehousesWith(String productId, int quantity) {
        List<String> result = new ArrayList<>();
        for (Warehouse w : warehouses.values()) {
            if (w.getStock(productId) >= quantity) result.add(w.getId());
        }
        return result;
    }

    public boolean removeStock(String warehouseId, String productId, int quantity) {
        checkQuantity(quantity);
        return findWarehouse(warehouseId).removeStock(productId, quantity);
    }
    // addStock and getStock: the same shape (look up the warehouse, call it)

    private void checkQuantity(int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive: " + quantity);
    }

    private Warehouse findWarehouse(String warehouseId) {
        Warehouse w = warehouses.get(warehouseId);
        if (w == null) throw new NoSuchElementException("Unknown warehouse " + warehouseId);
        return w;
    }
}
```
**Why the nested `synchronized` works:** `removeStock` and `addStock` are themselves `synchronized` on their warehouse. Java locks are **reentrant**: a thread that already holds a warehouse's lock can enter its methods again without blocking.

**Verify out loud:** walk through the run from section 0. For the deadlock, say: *"T1 moves BLR → DEL and T2 moves DEL → BLR. Both lock BLR first. T2 waits for BLR without holding DEL, so there's no cycle. T1 locks DEL, moves the stock, and unlocks both. Then T2 goes."*

---

## 5. Extensibility (~5 min)

Name **which class changes**, write the few new lines, and don't redesign the rest.

**Quick revision** (read this table on the day of the interview):

| # | Interviewer asks | One-line answer | What changes |
|---|---|---|---|
| 1 | "Alert me when stock is low" | Observer; fire only when stock **crosses** below the threshold, after the lock is released | `StockListener` + a few lines in the manager |
| 2 | "Flash sale: hold stock while the user pays" | Reserve (stock leaves now) → confirm, or release / expire (stock comes back) | `Reservation` + 3 methods on `Warehouse` |
| 3 | "One order, 3 products, all or nothing" | One lock: check every line, then take every line | 1 method on `Warehouse` |
| 4 | "Choose which warehouse ships it" | `WarehouseChooser` (Strategy) + retry if someone else bought it first | 1 interface, 1 class, `fulfil()` |
| 5 | "Reorder from the supplier automatically" | A listener from Extension 1 that remembers what's already ordered | 1 class |
| 6 | "Many servers" | The database refuses the update if there isn't enough | a SQL `UPDATE ... WHERE qty >= n` |

---

### 1. "Alert me when stock drops below 5"
**Idea:** fire only on the **crossing**: from ≥ 5 to below 5. Going 3 → 2 doesn't fire again, and after a restock the next drop fires again. Also, **never** call listeners while holding a warehouse lock: an email is slow, and a listener might call back into the warehouse.
```java
public interface StockListener {
    void onLowStock(String warehouseId, String productId, int left);
}

// Warehouse.removeStock now returns what's left (-1 = not enough), so the manager knows before and after
public synchronized int removeStock(String productId, int quantity) {
    int current = getStock(productId);
    if (current < quantity) return -1;
    stock.put(productId, current - quantity);
    return current - quantity;
}

// InventoryManager
private final Map<String, Integer> lowStockThreshold = new ConcurrentHashMap<>();
private final List<StockListener> listeners = new CopyOnWriteArrayList<>();

public void setLowStockAlert(String productId, int threshold) { lowStockThreshold.put(productId, threshold); }
public void addListener(StockListener listener)               { listeners.add(listener); }

public boolean removeStock(String warehouseId, String productId, int quantity) {
    int left = findWarehouse(warehouseId).removeStock(productId, quantity);
    if (left < 0) return false;
    alertIfCrossed(warehouseId, productId, left + quantity, left);   // the warehouse lock is already released
    return true;
}

// transfer(): declare "int left;" before the locks, set it inside them, then after both blocks close:
//     alertIfCrossed(fromId, productId, left + quantity, left);

private void alertIfCrossed(String warehouseId, String productId, int before, int after) {
    Integer threshold = lowStockThreshold.get(productId);
    if (threshold == null) return;
    if (before >= threshold && after < threshold) {                  // crossed downward: fire once
        for (StockListener l : listeners) {
            try {
                l.onLowStock(warehouseId, productId, after);
            } catch (Exception e) {
                System.err.println("listener failed: " + e.getMessage());   // one bad listener doesn't stop the others
            }
        }
    }
}
```
**Check** (threshold 5): 10 → 3 fires · 3 → 2 silent · restock to 20 silent · 20 → 4 fires again.
**Say:** *"The crossing rule needs no 'already alerted' flag. Listeners run after the lock is released, so a slow email never blocks orders."*

---

### 2. "Flash sale: 100 units, 1,000 buyers, payment takes a minute"
**Idea:** **reserve** takes the units out of stock straight away, so nobody can oversell. **Confirm** (paid) keeps them out. **Release** (payment failed) or **expiry** (user left) puts them back.
```java
public class Reservation {
    final String productId;
    final int quantity;
    final Instant expiresAt;
    // constructor
    boolean isExpired(Instant now) { return !now.isBefore(expiresAt); }
}

// Warehouse: new fields (it gets a Clock, so tests can move time)
private final Map<String, Reservation> reservations = new HashMap<>();
private final Clock clock;
private int nextReservationNumber = 1;

public synchronized String reserve(String productId, int quantity, Duration holdFor) {
    returnExpired();
    if (!removeStock(productId, quantity)) throw new IllegalStateException("Not enough " + productId);
    String reservationId = id + "-R" + nextReservationNumber++;
    reservations.put(reservationId, new Reservation(productId, quantity, clock.instant().plus(holdFor)));
    return reservationId;
}

public synchronized void confirm(String reservationId) {                  // paid: the units stay out
    returnExpired();
    if (reservations.remove(reservationId) == null) {
        throw new IllegalStateException("Reservation expired or unknown: " + reservationId);
    }
}

public synchronized void release(String reservationId) {                  // payment failed: put them back now
    Reservation r = reservations.remove(reservationId);
    if (r != null) addStock(r.productId, r.quantity);
}

private void returnExpired() {                                           // also call at the start of getStock()
    Iterator<Reservation> it = reservations.values().iterator();
    while (it.hasNext()) {
        Reservation r = it.next();
        if (r.isExpired(clock.instant())) {
            stock.put(r.productId, stock.getOrDefault(r.productId, 0) + r.quantity);
            it.remove();
        }
    }
}
```
**Check:** 1,000 threads reserve 1 unit each from 100 → exactly 100 succeed · after the hold time, the unpaid units are back in stock.
**Say:** *"It's the same lock, so there's no new race. Expiry is lazy: expired reservations go back when someone next looks, with no background thread. At Amazon scale this is a Redis `DECR`, or a DB conditional update with a queue in front."*

---

### 3. "One order has 3 products: all or nothing"
```java
// Warehouse
public synchronized boolean removeAll(Map<String, Integer> order) {      // product → qty
    for (Map.Entry<String, Integer> line : order.entrySet()) {           // 1. check EVERY line
        if (getStock(line.getKey()) < line.getValue()) return false;
    }
    for (Map.Entry<String, Integer> line : order.entrySet()) {           // 2. then take every line
        stock.put(line.getKey(), getStock(line.getKey()) - line.getValue());
    }
    return true;
}
```
**Check:** phone 1, case 0, charger 5 → refused, and the phone is untouched.
**Say:** *"Never take line by line and 'undo on failure': another thread could see the half-done state. Across several warehouses, I lock them all in id order, like transfer does."*

---

### 4. "Choose which warehouse ships the order"
**Idea:** the rule (nearest, most stock, cheapest) is a Strategy. Between choosing and taking, someone else might buy the stock, so if `removeStock` fails, choose again (the same retry idea as Parking Lot's lock-free claim).
```java
public interface WarehouseChooser {
    Warehouse choose(List<Warehouse> withEnough, String productId);      // null = none
}

public class MostStockChooser implements WarehouseChooser {             // keeps warehouses balanced
    public Warehouse choose(List<Warehouse> withEnough, String productId) {
        Warehouse best = null;
        for (Warehouse w : withEnough) {
            if (best == null || w.getStock(productId) > best.getStock(productId)) best = w;
        }
        return best;
    }
}

// InventoryManager (gets a WarehouseChooser in its constructor)
public String fulfil(String productId, int quantity) {
    while (true) {
        List<Warehouse> withEnough = new ArrayList<>();
        for (Warehouse w : warehouses.values()) {
            if (w.getStock(productId) >= quantity) withEnough.add(w);
        }
        Warehouse chosen = chooser.choose(withEnough, productId);
        if (chosen == null) throw new IllegalStateException("Out of stock: " + productId);
        if (chosen.removeStock(productId, quantity)) return chosen.getId();   // sold meanwhile? choose again
    }
}
```
**Check:** BLR 3, DEL 8, order 2 → DEL. Order 9 → "Out of stock".

---

### 5. "Reorder from the supplier automatically"
**Idea:** just another listener from Extension 1. It remembers what's already on order, so a second crossing doesn't order twice.
```java
public class ReorderListener implements StockListener {
    private final Set<String> pending = ConcurrentHashMap.newKeySet();   // "BLR:iphone"

    public void onLowStock(String warehouseId, String productId, int left) {
        if (pending.add(warehouseId + ":" + productId)) {                // true only if not already ordered
            // supplier.order(warehouseId, productId, 50);
        }
    }
    public void delivered(String warehouseId, String productId) {
        pending.remove(warehouseId + ":" + productId);
    }
}
```
**Say:** *"The warehouse doesn't know reordering exists. That's the point of Observer: email, dashboard and reordering are just listeners."*

---

### 6. "Many servers, one inventory"
**Say:** *"`synchronized` only protects one JVM. Across servers, the database does the check-and-take in one statement:"*
```sql
UPDATE stock SET qty = qty - :n
WHERE warehouse_id = :w AND product_id = :p AND qty >= :n;
-- 1 row updated = success, 0 rows = not enough
```
*"A transfer is one transaction that updates both rows, in warehouse-id order, so the database can't deadlock either. Alerts are published as an event after the commit."*

---

```bash
mvn -q compile exec:java -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.booking.inventory.InventoryManagerDriver
```
