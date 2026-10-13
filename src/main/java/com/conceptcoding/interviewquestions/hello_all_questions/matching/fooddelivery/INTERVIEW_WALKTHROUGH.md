# Food Delivery (Zomato lite)

> 30–40 min round. **Reported at Ethos.** Phases: **Requirements (5) → Entities (3) → Class Design (10–15) → Implementation (10) → Extensibility (5)**.
> Sister problem: [Cab Booking](../cabbooking/INTERVIEW_WALKTHROUGH.md), the same "pick the nearest free one, then run a lifecycle" idea.

---

## 0. The problem in plain words (read first, don't say it in the interview)

**Picture ordering on Zomato.** You pick dishes from **one** restaurant and place the order. The restaurant accepts, and the app sends the **nearest free rider**. The rider picks up the food and delivers it, and is then free for the next order. **Lite version:** once placed, an order can't be cancelled. It only moves forward.

**The whole system is 4 actions:**
```
Customer    →  placeOrder(restaurant, {biryani: 2})  → PLACED, prices copied into the order
Restaurant  →  acceptOrder(order)                     → ACCEPTED + the nearest free rider
Rider       →  pickUp(order)                          → PICKED_UP
Rider       →  deliver(order)                         → DELIVERED, the rider is free again
```

**Real world → code**
| In the real world | In the code |
|---|---|
| A restaurant and its menu | `Restaurant` (location, items) + `MenuItem` (price, available) |
| Your order | `Order`: items, total, status, rider |
| A line on the bill: "2 × Biryani @ ₹320" | `OrderItem`: a **copy** of the name and price |
| Placed → Accepted → Picked up → Delivered | `OrderStatus` with `canMoveTo` |
| A delivery rider | `DeliveryPartner`: location, free or busy |
| How the app picks a rider | `PartnerAssignmentStrategy` (nearest) |

**Only 3 things are tricky:**
1. **Copy the price into the order.** If the restaurant changes biryani from ₹320 to ₹999 after you order, your bill must stay ₹680. So `OrderItem` keeps its own copy of the name and price.
2. **Two restaurants accept at the same moment, and one rider is nearest to both.** Only one may get that rider, so the methods are `synchronized`.
3. **An order only moves forward.** You can't deliver food that was never picked up, or pick up an order twice. `canMoveTo` lists the legal moves.

**Picture this run before you start coding:**
```
Ravi is 1 km from the restaurant, Asha 8 km
Alice orders 2 biryani + 1 raita     → ₹680; then the menu changes biryani to ₹999 → Alice still pays ₹680
Restaurant accepts Alice's order     → Ravi (nearest)
Bob's order accepted                 → Asha (Ravi is busy)
Carol's order accepted               → "No rider free", stays PLACED
Alice: deliver before pickup         → refused;  picked up → delivered → Ravi free, now at Alice's door
Carol's order accepted again         → Ravi (free again)
```

---

## 1. Requirements (~5 min)

**Ask:** one restaurant per order? What happens if the price changes after ordering? When is the rider assigned? Can an order be cancelled (lite: no)? Many orders at once?

```
1. placeOrder(customer, restaurant, items) → total from the CURRENT prices, copied into the order
2. acceptOrder → the nearest free rider is assigned (if nobody is free: an error, the order stays PLACED)
3. Order: PLACED → ACCEPTED → PICKED_UP → DELIVERED, forward only (no cancel)
4. Safe with many orders at once (a rider never gets two orders)

Out of scope: cancel, cart, READY status, waiting for a rider, delivery fee / coupons, notifications, payment
```

---

## 2. Entities & Relationships (~3 min)

```
FoodDeliveryService
   ├── Restaurant ── MenuItem (many)
   ├── Order ── OrderItem (many: copied name + price)
   │     └── DeliveryPartner (once accepted)
   ├── DeliveryPartner (many)
   └── PartnerAssignmentStrategy   "which rider"
```
- **One order = one restaurant**, because `placeOrder` takes a single restaurant id. That gives the "one restaurant" rule for free.
- **No Cart or Customer class in the base.** The app sends the chosen items, and a customer is just an id.

---

## 3. Class Design (~10 min)

```
FoodDeliveryService
  behaviour: placeOrder(customer, restaurant, items, deliveryLocation) → Order · acceptOrder · pickUp · deliver

Restaurant        id, name, location, menu (id → MenuItem)       getItem(id)
MenuItem          id, name, price, available
Order             id, customerId, restaurantId, deliveryLocation, items, total, status, partner     moveTo(next)
OrderItem         itemId, name, unitPrice, quantity               subtotal()
OrderStatus       PLACED, ACCEPTED, PICKED_UP, DELIVERED      canMoveTo(next)
DeliveryPartner   id, name, location, available                   finishDelivery(dropPoint)
Location          x, y                                            distanceTo(other)

PartnerAssignmentStrategy   choose(freePartners, pickup) → rider or null   ← NearestPartnerStrategy
```
- **Thread safety:** every service method is `synchronized`.

### Design patterns: the problem → the fix (how to say it)
**Strategy for choosing a rider.**
*Problem:* the rule changes: nearest today, best-rated or fewest deliveries tomorrow. *Without it:* `if (mode == ...)` branches inside `acceptOrder`. *With it:* `acceptOrder` asks `assignmentStrategy.choose(freeRiders, restaurantLocation)`, and a new rule is a new class.
**Say:** *"The service decides **when** to assign a rider; the strategy decides **which** one."*

**Enum + `canMoveTo` for the order status.**
*Problem:* what's allowed depends on the status. *Without it:* `if` checks in every method. *With it:* every change goes through `moveTo`, and there's one list of legal moves.
**Say:** *"The statuses only limit the next move, so an enum is enough. The State pattern would be overkill here."*

**Not used:** the State pattern (no state has its own behaviour), Singleton (it's hard to test), and Builder for `Order` (the constructor is small).

### ACID (if asked)
**Atomic:** `acceptOrder` picks the rider **first**. If nobody is free it throws before anything changes, so the order is never "ACCEPTED with no rider". **Consistent:** a busy rider has exactly one active order. **Isolated:** `synchronized`. **Durable:** not in memory; *"in production, a DB transaction."*

---

## 4. Implementation (~10 min)

**Write it in this order:** `OrderStatus` → `Order` + `OrderItem` → `DeliveryPartner` + the strategy → the service (`placeOrder` and `acceptOrder` are the crux).

```java
public enum OrderStatus {
    PLACED, ACCEPTED, PICKED_UP, DELIVERED;                  // forward only

    public boolean canMoveTo(OrderStatus next) {
        switch (this) {
            case PLACED:    return next == ACCEPTED;
            case ACCEPTED:  return next == PICKED_UP;
            case PICKED_UP: return next == DELIVERED;
            default:        return false;                      // DELIVERED is final
        }
    }
}

public class Order {
    private final String id, customerId, restaurantId;
    private final Location deliveryLocation;
    private final List<OrderItem> items;
    private final long total;
    private OrderStatus status = OrderStatus.PLACED;
    private DeliveryPartner partner;                         // null until accepted

    public void moveTo(OrderStatus next) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("Order " + id + ": cannot go from " + status + " to " + next);
        }
        status = next;
    }
    // constructor, getters, setPartner
}

public class OrderItem {                                     // a COPY: the menu can change later
    private final String itemId, name;
    private final long unitPrice;
    private final int quantity;
    public long subtotal() { return unitPrice * quantity; }
    // constructor, getters
}

public class DeliveryPartner {
    private final String id, name;
    private Location location;
    private boolean available = true;
    public void finishDelivery(Location dropPoint) { location = dropPoint; available = true; }
    // constructor, getters, setAvailable
}
// Restaurant { id, name, location, Map<id, MenuItem> menu, getItem(id) throws if unknown }
// MenuItem   { id, name, price, available }       Location { x, y, distanceTo }
```

```java
public interface PartnerAssignmentStrategy {
    DeliveryPartner choose(List<DeliveryPartner> freePartners, Location pickup);   // null = nobody free
}

public class NearestPartnerStrategy implements PartnerAssignmentStrategy {
    public DeliveryPartner choose(List<DeliveryPartner> freePartners, Location pickup) {
        DeliveryPartner nearest = null;
        for (DeliveryPartner p : freePartners) {
            if (nearest == null || p.getLocation().distanceTo(pickup) < nearest.getLocation().distanceTo(pickup)) {
                nearest = p;
            }
        }
        return nearest;
    }
}
```

```java
public class FoodDeliveryService {
    private final Map<String, Restaurant> restaurants = new HashMap<>();
    private final Map<String, DeliveryPartner> partners = new HashMap<>();
    private final Map<String, Order> orders = new HashMap<>();
    private final PartnerAssignmentStrategy assignmentStrategy;
    private int nextOrderNumber = 1;

    // constructor sets the strategy; addRestaurant / addPartner put them in the maps

    public synchronized Order placeOrder(String customerId, String restaurantId,
                                         Map<String, Integer> items, Location deliveryLocation) {
        if (items.isEmpty()) throw new IllegalArgumentException("Order has no items");
        Restaurant restaurant = findRestaurant(restaurantId);
        List<OrderItem> lines = new ArrayList<>();
        long total = 0;
        for (Map.Entry<String, Integer> e : items.entrySet()) {
            MenuItem item = restaurant.getItem(e.getKey());
            if (!item.isAvailable()) throw new IllegalStateException(item.getName() + " is not available");
            OrderItem line = new OrderItem(item.getId(), item.getName(), item.getPrice(), e.getValue());  // copy
            lines.add(line);
            total += line.subtotal();
        }
        Order order = new Order("ORD-" + nextOrderNumber++, customerId, restaurantId, deliveryLocation, lines, total);
        orders.put(order.getId(), order);
        return order;
    }

    public synchronized void acceptOrder(String orderId) {
        Order order = findOrder(orderId);
        if (order.getStatus() != OrderStatus.PLACED) throw new IllegalStateException("Order " + orderId + " is already " + order.getStatus());
        List<DeliveryPartner> free = new ArrayList<>();
        for (DeliveryPartner p : partners.values()) {
            if (p.isAvailable()) free.add(p);
        }
        DeliveryPartner partner = assignmentStrategy.choose(free, findRestaurant(order.getRestaurantId()).getLocation());
        if (partner == null) throw new IllegalStateException("No rider free for " + orderId);   // nothing changed

        order.moveTo(OrderStatus.ACCEPTED);
        partner.setAvailable(false);
        order.setPartner(partner);
    }

    public synchronized void pickUp(String orderId) {
        findOrder(orderId).moveTo(OrderStatus.PICKED_UP);
    }

    public synchronized void deliver(String orderId) {
        Order order = findOrder(orderId);
        order.moveTo(OrderStatus.DELIVERED);
        order.getPartner().finishDelivery(order.getDeliveryLocation());   // free, at the customer's door
    }
    // findOrder / findRestaurant: look up, or throw NoSuchElementException
}
```

**Verify out loud:** walk through the run from section 0. For the race, say: *"Two restaurants accept at once, and Ravi is nearest to both. The first takes the lock, picks Ravi and marks him busy. The second then sees only Asha."*

---

## 5. Extensibility (~5 min)

Name **which class changes**, write the few new lines, and don't redesign the rest.

**Quick revision:**

| # | Interviewer asks | One-line answer |
|---|---|---|
| 1 | "Let the customer cancel before pickup" | Add CANCELLED to the enum; free the rider |
| 2 | "No rider free: don't fail, make it wait" | A FIFO queue of waiting orders; a rider who becomes free takes the oldest |
| 3 | "Add a cart, one restaurant only" | `Cart` remembers its restaurant and refuses items from another |
| 4 | "Restaurant marks the food READY" | One more status in the enum + `markReady()` |
| 5 | "Delivery fee, coupons" | `PricingStrategy`; a coupon wraps it (Decorator) |
| 6 | "Notify the customer at every step" | Observer: listeners hear each status change |
| 7 | "The rider can reject" | Free them and offer the order to the next rider (skip the ones who said no) |
| 8 | "Many orders, the lock is slow" | `tryReserve()` claims a rider atomically, retry if taken |

---

### 1. "Let the customer cancel before pickup"
**Idea:** one more status, reachable only **before** pickup. Free the rider if one was assigned.
```java
// OrderStatus: + CANCELLED
case PLACED:    return next == ACCEPTED || next == CANCELLED;
case ACCEPTED:  return next == PICKED_UP || next == CANCELLED;
case PICKED_UP: return next == DELIVERED;                     // food on the road: no cancel

public synchronized void cancel(String orderId) {
    Order order = findOrder(orderId);
    order.moveTo(OrderStatus.CANCELLED);                       // throws once picked up
    if (order.getPartner() != null) order.getPartner().setAvailable(true);
}
```
**Check:** cancel while ACCEPTED → the rider is free · cancel after pickup → refused.
**Say:** *"Only the enum and one method change. A refund would go through the payment service after the cancel succeeds."*

---

### 2. "No rider free: let the order wait"
**Idea:** accept anyway and put the order in a **queue** (oldest first). When a rider is freed (after a delivery), they take the oldest waiting order.
```java
private final Queue<Order> waiting = new LinkedList<>();

public synchronized void acceptOrder(String orderId) {
    Order order = findOrder(orderId);
    order.moveTo(OrderStatus.ACCEPTED);
    if (!tryAssign(order)) waiting.add(order);                  // nobody free: wait in line
}

private boolean tryAssign(Order order) {                        // the old accept logic, returning true/false
    DeliveryPartner partner = assignmentStrategy.choose(freePartners(), restaurantLocation(order));
    if (partner == null) return false;
    partner.setAvailable(false);
    order.setPartner(partner);
    return true;
}

private void assignWaiting() {                                  // call at the end of deliver()
    while (!waiting.isEmpty() && tryAssign(waiting.peek())) waiting.poll();
}
// pickUp(): if (order.getPartner() == null) throw "No rider yet"
```
**Check:** 1 rider, 3 orders → 2 wait · first delivery → the **oldest** waiting order gets the rider.

---

### 3. "Add a cart: items from one restaurant only"
```java
public class Cart {
    private String restaurantId;                                 // null while empty
    private final Map<String, Integer> items = new LinkedHashMap<>();

    public void add(String restaurantId, String itemId, int qty) {
        if (this.restaurantId != null && !this.restaurantId.equals(restaurantId)) {
            throw new IllegalStateException("Cart has items from another restaurant. Clear it first.");
        }
        this.restaurantId = restaurantId;
        items.put(itemId, items.getOrDefault(itemId, 0) + qty);
    }
    public void clear() { items.clear(); restaurantId = null; }
}
// service: Map<customerId, Cart> carts;  placeOrder(customerId) reads the cart, then calls cart.clear()
```
**Say:** *"`placeOrder` doesn't change. The cart just collects the items first."*

---

### 4. "The restaurant marks the food READY"
```java
// OrderStatus: PLACED, ACCEPTED, READY, PICKED_UP, DELIVERED
case ACCEPTED: return next == READY;
case READY:    return next == PICKED_UP;

public synchronized void markReady(String orderId) { findOrder(orderId).moveTo(OrderStatus.READY); }
```
**Say:** *"Only the enum and one method change. Every other check already goes through `moveTo`."*

---

### 5. "Delivery fee and coupons"
```java
public interface PricingStrategy {
    long price(long foodTotal, double distanceKm);
}

public class StandardPricing implements PricingStrategy {       // ₹20 + ₹5 per km
    public long price(long foodTotal, double km) { return foodTotal + 20 + Math.round(km * 5); }
}

public class CouponDiscount implements PricingStrategy {        // wraps any pricing (Decorator)
    private final PricingStrategy inner;
    private final long off;
    public CouponDiscount(PricingStrategy inner, long off) { this.inner = inner; this.off = off; }
    public long price(long foodTotal, double km) { return Math.max(0, inner.price(foodTotal, km) - off); }
}
// new CouponDiscount(new StandardPricing(), 100)
```
**Check:** food ₹680, 3 km → ₹715 · with ₹100 off → ₹615 · a huge coupon → ₹0, never negative.

---

### 6. "Notify the customer at every step"
```java
public interface OrderListener {
    void statusChanged(Order order, OrderStatus newStatus);
}

// FoodDeliveryService
private final List<OrderListener> listeners = new ArrayList<>();
public synchronized void addListener(OrderListener l) { listeners.add(l); }

// after every moveTo(...):
for (OrderListener l : listeners) l.statusChanged(order, order.getStatus());

// app.addListener(new PushNotifier());  app.addListener(new RestaurantDashboard());
```
**Say:** *"The service just announces the change. Push, SMS and the restaurant screen are listeners. Slow ones go on a queue."*

---

### 7. "The rider can reject the order"
```java
// Order: + Set<String> rejectedBy

public synchronized void rejectOrder(String orderId) {
    Order order = findOrder(orderId);
    DeliveryPartner old = order.getPartner();
    old.setAvailable(true);
    order.addRejectedBy(old.getId());

    List<DeliveryPartner> candidates = new ArrayList<>();
    for (DeliveryPartner p : freePartners()) {
        if (!order.wasRejectedBy(p.getId())) candidates.add(p);
    }
    DeliveryPartner next = assignmentStrategy.choose(candidates, restaurantLocation(order));
    order.setPartner(next);
    if (next == null) waiting.add(order);                       // everyone said no: wait (Extension 2)
    else next.setAvailable(false);
}
```
**Check:** P1 rejects → P2 gets it, P1 is free · P2 rejects too → the order waits.

---

### 8. "Many orders, the single lock is slow"
**First say:** *"Each call holds the lock for microseconds. I'd measure first."*

**Idea:** lock **one rider**, not the whole service. `tryReserve()` checks and claims in one step. If another order just took that rider, choose again.
```java
// DeliveryPartner
public synchronized boolean tryReserve() {
    if (!available) return false;
    available = false;
    return true;
}

// FoodDeliveryService.acceptOrder (no synchronized; maps become ConcurrentHashMap; a lock per order for moveTo)
while (true) {
    DeliveryPartner partner = assignmentStrategy.choose(freePartners(), restaurantLocation(order));
    if (partner == null) throw new IllegalStateException("No rider free");
    if (partner.tryReserve()) { order.setPartner(partner); break; }   // true for exactly one order
    // someone else just took this rider: choose again
}
```
**Check:** 20 orders, 5 riders at the same instant → exactly 5 claims, all different riders.

---

```bash
mvn -q compile exec:java -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.FoodDeliveryDriver
```
