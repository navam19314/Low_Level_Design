# Food Delivery (Zomato lite)

> 30 min round. **Reported at Ethos.** Phases: **Requirements (5) → Entities (3) → Class Design (5–10) → Implementation (10) → Extensibility (5)**.
> Sister problem: [Cab Booking](../cabbooking/INTERVIEW_WALKTHROUGH.md), the same "pick the nearest free one, then move forward step by step" idea.

---

## 0. The problem in plain words (read first, don't say it in the interview)

**Picture ordering on Zomato.** You order dishes from **one** restaurant. The restaurant accepts, and the app sends the **nearest free rider**. The rider picks up the food and delivers it, and is then free again. **Lite version:** there's no cancel; an order only moves forward.

**The whole system is 4 actions:**
```
Customer    →  placeOrder(restaurant, {biryani: 2})   → PLACED, total fixed now
Restaurant  →  acceptOrder(order)                      → ACCEPTED + the nearest free rider
Rider       →  pickUp(order)                           → PICKED_UP
Rider       →  deliver(order)                          → DELIVERED, the rider is free again
```

**Real world → code**
| In the real world | In the code |
|---|---|
| A restaurant and its menu | `Restaurant`: location + a menu map (dish → price) |
| Your order | `Order`: restaurant, dishes, **total**, status, rider |
| Placed → Accepted → Picked up → Delivered | `OrderStatus` with `canMoveTo` |
| A delivery rider | `DeliveryPartner`: location, free or busy |
| How the app picks a rider | `PartnerAssignmentStrategy` (nearest) |

**Only 3 things are tricky:**
1. **The total is fixed at order time.** If biryani goes from ₹320 to ₹999 after you order, you still pay ₹680. So `Order` stores the total; it doesn't look prices up again.
2. **Two restaurants accept at the same moment, and one rider is nearest to both.** Only one may get that rider, so the methods are `synchronized`.
3. **An order only moves forward.** You can't deliver food that was never picked up. `canMoveTo` lists the legal next steps.

**Picture this run before you start coding:**
```
Ravi is 1 km from the restaurant, Asha 8 km
Order 1: 2 biryani + 1 raita     → ₹680; then biryani becomes ₹999 → order 1 is still ₹680
Accept order 1                   → Ravi (nearest)
Accept order 2                   → Asha (Ravi is busy)
Accept order 3                   → "No rider free", stays PLACED
Order 1: deliver before pickup   → refused;  pick up → deliver → Ravi free, at the customer's door
Accept order 3 again             → Ravi
```

---

## 1. Requirements (~5 min)

**Ask:** one restaurant per order? Does the price change after ordering? When is the rider assigned? Can an order be cancelled (lite: no)? Many orders at once?

```
1. placeOrder(restaurant, dishes, address) → total from today's prices, fixed in the order
2. acceptOrder → assign the nearest free rider (nobody free: error, the order stays PLACED)
3. PLACED → ACCEPTED → PICKED_UP → DELIVERED, forward only
4. Safe with many orders at once (a rider never gets two orders)

Out of scope: cancel, cart, out-of-stock dishes, READY status, waiting for a rider, fees, notifications
```

---

## 2. Entities & Relationships (~3 min)

```
FoodDeliveryService
   ├── Restaurant (many)        menu: dish → price
   ├── Order (many)             restaurant, dishes, total, status, rider
   ├── DeliveryPartner (many)
   └── PartnerAssignmentStrategy   "which rider"
```
- **One order = one restaurant**, because `placeOrder` takes a single restaurant.
- **No MenuItem, OrderItem, Cart or Customer classes.** The menu is a map, and the order keeps the total. Those classes arrive in the extensions, once they have a job.

---

## 3. Class Design (~5 min)

```
FoodDeliveryService   placeOrder(restaurantId, dishes, address) → Order · acceptOrder(id) · pickUp(id) · deliver(id)

Restaurant        id, name, location, menu (dish → price)       setPrice, getPrice(dish)
Order             id, restaurant, items, total, deliveryLocation, status, partner      moveTo(next)
OrderStatus       PLACED, ACCEPTED, PICKED_UP, DELIVERED        canMoveTo(next)
DeliveryPartner   id, name, location, available                 finishDelivery(dropPoint)
Location          x, y                                          distanceTo(other)

PartnerAssignmentStrategy   choose(freePartners, pickup) → rider or null   ← NearestPartnerStrategy
```
- **Thread safety:** every service method is `synchronized`.

### Design patterns (how to say it)
**Strategy for choosing a rider.** *Problem:* the rule changes: nearest today, best-rated tomorrow. *Without it:* `if (mode == ...)` inside `acceptOrder`. *With it:* a new rule is a new class.
**Say:** *"The service decides **when** to assign a rider; the strategy decides **which** one."*

**Enum + `canMoveTo` for the status.** The statuses only limit the next step, so an enum is enough. **Say:** *"Every change goes through `moveTo`, so I can't forget a check. The State pattern would be overkill here."*

### ACID (if asked)
**Atomic:** `acceptOrder` picks the rider **first**. If nobody is free, nothing changes. **Consistent:** a busy rider has exactly one order. **Isolated:** `synchronized`. **Durable:** not in memory; *"in production, a DB transaction."*

---

## 4. Implementation (~10 min)

**Write it in this order:** `OrderStatus` → `Order` → `Restaurant`, `DeliveryPartner`, `Location` → the strategy → the service.

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
    private final String id;
    private final Restaurant restaurant;
    private final Map<String, Integer> items;                // dish → quantity
    private final long total;                                // fixed at order time
    private final Location deliveryLocation;
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

public class Restaurant {
    private final String id, name;
    private final Location location;
    private final Map<String, Long> menu = new HashMap<>();  // dish → price

    public void setPrice(String dish, long price) { menu.put(dish, price); }
    public long getPrice(String dish) {
        Long price = menu.get(dish);
        if (price == null) throw new NoSuchElementException(name + " has no " + dish);
        return price;
    }
    // constructor, getters
}

public class DeliveryPartner {
    private final String id, name;
    private Location location;
    private boolean available = true;
    public void finishDelivery(Location dropPoint) { location = dropPoint; available = true; }
    // constructor, getters, setAvailable
}
// Location { x, y, distanceTo(other) = Math.hypot(dx, dy) }
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
    private final List<DeliveryPartner> partners = new ArrayList<>();
    private final Map<String, Order> orders = new HashMap<>();
    private final PartnerAssignmentStrategy assignmentStrategy;
    private int nextOrderNumber = 1;

    // constructor sets the strategy; addRestaurant / addPartner just store them

    public synchronized Order placeOrder(String restaurantId, Map<String, Integer> items, Location deliveryLocation) {
        Restaurant restaurant = restaurants.get(restaurantId);
        if (restaurant == null) throw new NoSuchElementException("Unknown restaurant " + restaurantId);
        if (items.isEmpty()) throw new IllegalArgumentException("Order has no items");

        long total = 0;
        for (Map.Entry<String, Integer> e : items.entrySet()) {
            total += restaurant.getPrice(e.getKey()) * e.getValue();     // throws if not on the menu
        }
        Order order = new Order("ORD-" + nextOrderNumber++, restaurant, items, total, deliveryLocation);
        orders.put(order.getId(), order);
        return order;
    }

    public synchronized void acceptOrder(String orderId) {
        Order order = findOrder(orderId);
        if (order.getStatus() != OrderStatus.PLACED) throw new IllegalStateException("Order " + orderId + " is already " + order.getStatus());
        List<DeliveryPartner> free = new ArrayList<>();
        for (DeliveryPartner p : partners) {
            if (p.isAvailable()) free.add(p);
        }
        DeliveryPartner partner = assignmentStrategy.choose(free, order.getRestaurant().getLocation());
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
    // findOrder(id): look up, or throw NoSuchElementException
}
```

**Verify out loud:** walk through the run from section 0. For the race, say: *"Two restaurants accept at once, and Ravi is nearest to both. The first takes the lock, picks Ravi and marks him busy. The second then sees only Asha."*

---

## 5. Extensibility (~5 min)

Name **which class changes**, write the few new lines, and don't redesign the rest.
(`freePartners()` below = the "collect available riders" loop from `acceptOrder`, pulled into a helper.)

**Quick revision:**

| # | Interviewer asks | One-line answer |
|---|---|---|
| 1 | "Let the customer cancel before pickup" | Add CANCELLED to the enum; free the rider |
| 2 | "No rider free: don't fail, make it wait" | A FIFO queue of waiting orders; a freed rider takes the oldest |
| 3 | "Add a cart, one restaurant only" | `Cart` remembers its restaurant and refuses items from another |
| 4 | "Show each line on the bill / dishes go out of stock" | `MenuItem` (price, available) + `OrderItem` (a copy of name and price) |
| 5 | "Restaurant marks the food READY" | One more status in the enum + `markReady()` |
| 6 | "Delivery fee, coupons" | `PricingStrategy`; a coupon wraps it (Decorator) |
| 7 | "Notify the customer at every step" | Observer: listeners hear each status change |
| 8 | "The rider can reject" | Free them and offer the order to the next rider (skip the ones who said no) |
| 9 | "Many orders, the lock is slow" | `tryReserve()` claims a rider atomically, retry if taken |

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
**Idea:** accept anyway and put the order in a **queue** (oldest first). When a rider is freed after a delivery, they take the oldest waiting order.
```java
private final Queue<Order> waiting = new LinkedList<>();

public synchronized void acceptOrder(String orderId) {
    Order order = findOrder(orderId);
    order.moveTo(OrderStatus.ACCEPTED);
    if (!tryAssign(order)) waiting.add(order);                  // nobody free: wait in line
}

private boolean tryAssign(Order order) {
    DeliveryPartner partner = assignmentStrategy.choose(freePartners(), order.getRestaurant().getLocation());
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

### 3. "Add a cart: dishes from one restaurant only"
```java
public class Cart {
    private String restaurantId;                                 // null while empty
    private final Map<String, Integer> items = new LinkedHashMap<>();

    public void add(String restaurantId, String dish, int qty) {
        if (this.restaurantId != null && !this.restaurantId.equals(restaurantId)) {
            throw new IllegalStateException("Cart has items from another restaurant. Clear it first.");
        }
        this.restaurantId = restaurantId;
        items.put(dish, items.getOrDefault(dish, 0) + qty);
    }
    public void clear() { items.clear(); restaurantId = null; }
}
// service: Map<customerId, Cart> carts;  checkout(customerId) → placeOrder(cart's restaurant, cart's items, ...) → cart.clear()
```
**Say:** *"`placeOrder` doesn't change. The cart just collects the dishes first."*

---

### 4. "Show each line on the bill, and dishes can go out of stock"
**Idea:** the menu map becomes `MenuItem`s, because a dish now has more than a price. The order keeps a **copy** of each line, so the bill never changes.
```java
public class MenuItem {
    private final String name;
    private long price;
    private boolean available = true;
    // constructor, getters, setPrice, setAvailable
}

public class OrderItem {                                         // a COPY taken at order time
    private final String name;
    private final long unitPrice;
    private final int quantity;
    public long subtotal() { return unitPrice * quantity; }
}

// placeOrder(): for each dish
//   MenuItem item = restaurant.getItem(dish);
//   if (!item.isAvailable()) throw new IllegalStateException(dish + " is out of stock");
//   lines.add(new OrderItem(item.getName(), item.getPrice(), qty));   total += line.subtotal();
```
**Say:** *"In the base, storing the total was enough. Once the bill shows lines, I copy each line, for the same reason: the menu can change later."*

---

### 5. "The restaurant marks the food READY"
```java
// OrderStatus: PLACED, ACCEPTED, READY, PICKED_UP, DELIVERED
case ACCEPTED: return next == READY;
case READY:    return next == PICKED_UP;

public synchronized void markReady(String orderId) { findOrder(orderId).moveTo(OrderStatus.READY); }
```
**Say:** *"Only the enum and one method change. Every other check already goes through `moveTo`."*

---

### 6. "Delivery fee and coupons"
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

### 7. "Notify the customer at every step"
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

### 8. "The rider can reject the order"
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
    DeliveryPartner next = assignmentStrategy.choose(candidates, order.getRestaurant().getLocation());
    order.setPartner(next);
    if (next == null) waiting.add(order);                       // everyone said no: wait (Extension 2)
    else next.setAvailable(false);
}
```
**Check:** P1 rejects → P2 gets it, P1 is free · P2 rejects too → the order waits.

---

### 9. "Many orders, the single lock is slow"
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
    DeliveryPartner partner = assignmentStrategy.choose(freePartners(), order.getRestaurant().getLocation());
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
