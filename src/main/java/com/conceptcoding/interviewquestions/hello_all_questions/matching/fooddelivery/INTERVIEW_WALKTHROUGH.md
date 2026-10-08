# Food Delivery (Swiggy / Zomato lite)

> **Reported at:** Ethos (LLD round). Also common at Amazon, Flipkart and Swiggy themselves.
>
> **The crux (what's really being tested):**
> 1. **The order lifecycle is a state machine:** only legal moves are allowed (you can't deliver an order nobody picked up, or cancel food already on the road).
> 2. **Rider assignment under concurrency:** two orders must never get the same rider, and one order must never get two riders.
> 3. **Data you snapshot vs data you reference:** the order **copies** prices at order time.
>
> **Families:** F3 State machine + F5 Swappable policy (rider choice) + concurrency tool B (claim exactly once). See [foundations](../../00_AMAZON_LLD_FOUNDATIONS.md).
> **Patterns:** Strategy (rider assignment). Enum state machine for order status.

---

## 1. Plain-language picture

### Four actors, one order
```
Customer        →  browses menus, fills a cart, places the order, may cancel
Restaurant      →  accepts (or rejects), cooks, marks "ready"
Rider           →  is assigned, picks up, delivers
The app (us)    →  enforces the rules between them
```

### The order is a parcel moving along a conveyor belt
```
PLACED → ACCEPTED → READY → PICKED_UP → DELIVERED
   ↘        ↘         ↘
              CANCELLED            (allowed only before the rider has the food)
```
Each station only accepts the parcel from the station before it. Trying to jump from PLACED to DELIVERED is refused. That's an **enum with `canMoveTo()`**: the rules live in one place, not scattered as `if`s in every method.

### Riders are taxis at a stand
When a restaurant accepts an order, we look for the **nearest free rider**. Two orders accepted at the same instant may both want the same nearest rider. Think of it like two people grabbing the same taxi door: only one gets in. The other walks to the next taxi. In code, `compareAndSet(null, orderId)` is that door: exactly one caller succeeds.

If no rider is free, the order **waits**, and gets the next rider who finishes a delivery.

### The receipt is a photocopy
When you order biryani at ₹320, the order stores **₹320**, not a link to the menu. If the restaurant changes the price to ₹350 a minute later, your order doesn't change. That's why there's an `OrderItem` class separate from `MenuItem`.

### The cart rule
One cart = one restaurant. Adding a burger from another restaurant to a cart with biryani is refused. Every food app does this, because one rider picks up from one place.

---

## 2. From story to design

| # | Decision | Rejected | Why |
|---|---|---|---|
| D1 | `OrderStatus` enum with `canMoveTo(next)` | `if (status == X)` checks spread across every service method | All transition rules in one switch; adding a state means editing one place. |
| D2 | Enum state machine, **not** the State pattern | a class per status | The *behaviour* doesn't change per status; only *which move is allowed* changes. That's bookkeeping, so an enum is enough. (Contrast: Insurance, where each state does different work.) |
| D3 | `Order.moveTo()` is `synchronized` | unsynchronized status field | Customer cancels while rider picks up, at the same instant. Exactly one must win. |
| D4 | Rider claim = `AtomicReference.compareAndSet(null, orderId)` | `if (rider.isFree()) rider.setOrder(...)` | Check-then-set in two steps is a race: two orders both see "free". CAS does both in one atomic step. |
| D5 | Assignment is also locked **per order** | only the rider CAS | Without it, two threads assigning the *same* order could each claim a different rider: one order, two riders. |
| D6 | `PartnerAssignmentStrategy` returns a **ranked list**, and the service tries each | strategy returns just "the best rider" | The best rider might be grabbed by another order a microsecond earlier; we need the next best without recomputing. |
| D7 | `OrderItem` **copies** name + price | the order points at `MenuItem` | A later price change must not change what the customer pays. |
| D8 | `placeOrder` **re-checks** restaurant open + items available | trusting the cart | The cart may be hours old; the biryani may have sold out since. |
| D9 | Unassigned orders wait; `deliver`/`cancel` free a rider and call `assignPendingOrders()` | throwing "no rider" at accept time | The restaurant already accepted; the order should get the next free rider, not fail. |
| D10 | Money as `long` rupees | `double` | `0.1 + 0.2 != 0.3`. |
| D11 | Out of base: payment, pricing/fees, notifications, ETA, ratings | building them all | Follow-ups (§5). |

### Class shape
```
FoodDeliveryService                      ← the service every app screen calls
  restaurants, partners, carts (userId → Cart), orders     all ConcurrentHashMap
  PartnerAssignmentStrategy
  searchRestaurants · addToCart · placeOrder                (customer)
  acceptOrder · markReady                                   (restaurant)
  pickUp · deliver                                          (rider)
  cancel · assignPendingOrders

Restaurant { id, name, Location, Map<itemId, MenuItem> menu, open }
MenuItem   { id, name, price, available }
Cart       { userId, restaurantId, Map<itemId, qty> }       ← one restaurant only
Order      { id, customerId, restaurantId, List<OrderItem>, total, OrderStatus, partnerId }
OrderItem  { itemId, name, unitPrice, quantity }            ← snapshot of the menu
OrderStatus enum + canMoveTo()
DeliveryPartner { id, name, Location, AtomicReference<String> currentOrderId }

«interface» PartnerAssignmentStrategy  rank(partners, pickup) → best first
   └── NearestPartnerStrategy
```

---

## 3. Patterns that earn their place

| Pattern | In the base? | Problem it solves | Without it |
|---|---|---|---|
| **Strategy** (`PartnerAssignmentStrategy`) | **Yes** | "nearest rider" today; "best rated" or "fewest deliveries today" tomorrow | the sorting rule hard-coded in the service; every policy change edits the service |
| **Enum state machine** (`OrderStatus.canMoveTo`) | **Yes** | legal order transitions in one place | `if` checks scattered over 6 methods; one gets missed |
| **Strategy** (`PaymentMethod`) | No | UPI / card / cash on delivery | Q3 |
| **Strategy + Decorator** (`PricingStrategy`) | No | delivery fee, surge, coupons stacked | Q4 |
| **Observer** (`OrderListener`) | No | push notification on every status change | Q5 |

**Say:** *"Order status is an enum state machine, because only the allowed transitions change, not the behaviour. Rider selection is a Strategy, because the business will want to change it."*

**Tempting but wrong here:**
- **State pattern for `OrderStatus`:** each status doesn't *do* anything different; it only limits the next move. A class per status adds 6 files for no gain.
- **Singleton service:** create once and inject.
- **Observer in the base:** no listener exists until notifications are asked for.

---

## 4. The 45-minute run (trim to 35 if behavioural questions come first)

### Clarify (min 0–5)
> **You:** Core flow: a customer browses restaurants, adds items from one restaurant to a cart, places an order; the restaurant accepts and cooks; a rider is assigned, picks up and delivers. Right?
> **Interviewer:** Yes.
> **You:** Which statuses? I'm thinking placed, accepted, ready, picked up, delivered, plus cancelled.
> **Interviewer:** Good.
> **You:** Who can cancel, and until when?
> **Interviewer:** Customer or restaurant, until the rider picks it up.
> **You:** How do we choose a rider? Nearest free one?
> **Interviewer:** Nearest for now, but it may change.
> **You:** What if no rider is free?
> **Interviewer:** The order waits for the next free rider.
> **You:** Payment, fees and notifications: out of scope for now?
> **Interviewer:** Yes, we may discuss them.

```
In scope:  search restaurants · cart (one restaurant) · place order (price snapshot)
           status flow with legal transitions · cancel before pickup
           assign nearest free rider; wait if none · thread-safe
Out:       payment, delivery fee/surge/coupons, notifications, ETA, ratings
```

### Timeline
| Min | Do |
|---|---|
| 5–10 | Class shape. Say the crux: *"Status is a state machine; rider assignment must be atomic; orders snapshot prices."* |
| 10–14 | Code step 1: `OrderStatus` enum + `Order.moveTo()`. |
| 14–18 | Code step 2: `DeliveryPartner` with `tryAssign` (CAS) + the `PartnerAssignmentStrategy` interface + nearest. |
| 18–23 | Code step 3: thin models: `MenuItem`, `Restaurant`, `OrderItem`, `Cart`. |
| 23–35 | Code step 4: **`FoodDeliveryService`**: `placeOrder`, `acceptOrder` + `tryAssignPartner`, `pickUp`, `deliver`, `cancel`. |
| 35–40 | Dry-run: two orders racing for one rider. |
| 40–45 | Follow-ups. |

### The code you write, in this order

**Step 1: the state machine** ([model/OrderStatus.java](model/OrderStatus.java), [model/Order.java](model/Order.java))
```java
public enum OrderStatus {
    PLACED, ACCEPTED, READY, PICKED_UP, DELIVERED, CANCELLED;

    public boolean canMoveTo(OrderStatus next) {
        switch (this) {
            case PLACED:    return next == ACCEPTED || next == CANCELLED;   // restaurant accepts or rejects
            case ACCEPTED:  return next == READY || next == CANCELLED;
            case READY:     return next == PICKED_UP || next == CANCELLED;
            case PICKED_UP: return next == DELIVERED;                        // food is on the road: no cancel
            default:        return false;                                    // DELIVERED, CANCELLED are final
        }
    }
}

public class Order {
    private final String id;
    private final String customerId;
    private final String restaurantId;
    private final Location deliveryLocation;
    private final List<OrderItem> items;
    private final long total;
    private OrderStatus status = OrderStatus.PLACED;
    private String partnerId;                           // null until a rider is assigned

    public Order(String id, String customerId, String restaurantId, Location deliveryLocation,
                 List<OrderItem> items, long total) {
        this.id = id;
        this.customerId = customerId;
        this.restaurantId = restaurantId;
        this.deliveryLocation = deliveryLocation;
        this.items = new ArrayList<>(items);
        this.total = total;
    }

    // customer cancel vs rider pickup at the same instant: exactly one wins
    public synchronized void moveTo(OrderStatus next) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("Order " + id + ": cannot go from " + status + " to " + next);
        }
        status = next;
    }

    public synchronized void assignPartner(String partnerId) { this.partnerId = partnerId; }
    public synchronized OrderStatus getStatus()    { return status; }
    public synchronized String      getPartnerId() { return partnerId; }
    // + plain getters for the final fields
}
```

**Step 2: the rider + the strategy** ([model/DeliveryPartner.java](model/DeliveryPartner.java), [assignment/](assignment/))
```java
public class DeliveryPartner {
    private final String id;
    private final String name;
    private volatile Location location;
    private final AtomicReference<String> currentOrderId = new AtomicReference<>();   // null = free

    public DeliveryPartner(String id, String name, Location location) {
        this.id = id;
        this.name = name;
        this.location = location;
    }

    // atomic claim: true only for the ONE caller that changed it from free to busy
    public boolean tryAssign(String orderId) {
        return currentOrderId.compareAndSet(null, orderId);
    }

    // only frees the rider if they are still on THIS order
    public void release(String orderId) {
        currentOrderId.compareAndSet(orderId, null);
    }

    public boolean isAvailable() { return currentOrderId.get() == null; }
    // + getters
}

public interface PartnerAssignmentStrategy {
    List<DeliveryPartner> rank(List<DeliveryPartner> partners, Location pickup);   // best first
}

public class NearestPartnerStrategy implements PartnerAssignmentStrategy {
    @Override
    public List<DeliveryPartner> rank(List<DeliveryPartner> partners, Location pickup) {
        List<DeliveryPartner> free = new ArrayList<>();
        for (DeliveryPartner p : partners) {
            if (p.isAvailable()) free.add(p);
        }
        free.sort(Comparator.comparingDouble(p -> p.getLocation().distanceTo(pickup)));
        return free;
    }
}
```

**Step 3: thin models** ([model/](model/)). Write quickly, no surprises:
```java
public class MenuItem {
    private final String id;
    private final String name;
    private volatile long price;                 // changes while live: volatile so all threads see it
    private volatile boolean available = true;
    // constructor, getters, setPrice, setAvailable
}

public class Restaurant {
    private final String id;
    private final String name;
    private final Location location;
    private final Map<String, MenuItem> menu = new ConcurrentHashMap<>();
    private volatile boolean open = true;

    public MenuItem getItem(String itemId) {
        MenuItem item = menu.get(itemId);
        if (item == null) throw new NoSuchElementException("No item " + itemId + " at " + name);
        return item;
    }
    // constructor, addItem, getters
}

public class OrderItem {                          // a COPY of the menu line at order time
    private final String itemId;
    private final String name;
    private final long unitPrice;
    private final int quantity;
    public long subtotal() { return unitPrice * quantity; }
    // constructor, getters
}

public class Cart {
    private final String userId;
    private String restaurantId;                  // null while empty
    private final Map<String, Integer> quantities = new LinkedHashMap<>();

    public Cart(String userId) { this.userId = userId; }

    public synchronized void add(String restaurantId, String itemId, int qty) {
        if (qty <= 0) throw new IllegalArgumentException("Quantity must be > 0");
        if (this.restaurantId != null && !this.restaurantId.equals(restaurantId)) {
            throw new IllegalStateException("Cart has items from another restaurant. Clear it first.");
        }
        this.restaurantId = restaurantId;
        quantities.merge(itemId, qty, Integer::sum);
    }

    public synchronized void clear() { quantities.clear(); restaurantId = null; }
    public synchronized boolean isEmpty() { return quantities.isEmpty(); }
    public synchronized String getRestaurantId() { return restaurantId; }
    public synchronized Map<String, Integer> getQuantities() { return new LinkedHashMap<>(quantities); }
}
```

**Step 4: the service** ([FoodDeliveryService.java](FoodDeliveryService.java))
```java
public class FoodDeliveryService {

    private final Map<String, Restaurant> restaurants = new ConcurrentHashMap<>();
    private final Map<String, DeliveryPartner> partners = new ConcurrentHashMap<>();
    private final Map<String, Cart> carts = new ConcurrentHashMap<>();         // userId → cart
    private final Map<String, Order> orders = new ConcurrentHashMap<>();
    private final PartnerAssignmentStrategy assignmentStrategy;
    private final AtomicLong orderSeq = new AtomicLong();

    public FoodDeliveryService(PartnerAssignmentStrategy assignmentStrategy) {
        this.assignmentStrategy = assignmentStrategy;
    }

    public void addToCart(String userId, String restaurantId, String itemId, int qty) {
        MenuItem item = getRestaurant(restaurantId).getItem(itemId);
        if (!item.isAvailable()) throw new IllegalStateException(item.getName() + " is unavailable");
        carts.computeIfAbsent(userId, Cart::new).add(restaurantId, itemId, qty);
    }

    // re-validate everything and COPY prices into the order
    public Order placeOrder(String userId, Location deliveryLocation) {
        Cart cart = carts.computeIfAbsent(userId, Cart::new);
        synchronized (cart) {                                  // same lock as Cart's own methods
            if (cart.isEmpty()) throw new IllegalStateException("Cart is empty");
            Restaurant restaurant = getRestaurant(cart.getRestaurantId());
            if (!restaurant.isOpen()) throw new IllegalStateException(restaurant.getName() + " is closed");

            List<OrderItem> lines = new ArrayList<>();
            long total = 0;
            for (Map.Entry<String, Integer> e : cart.getQuantities().entrySet()) {
                MenuItem item = restaurant.getItem(e.getKey());
                if (!item.isAvailable()) throw new IllegalStateException(item.getName() + " is no longer available");
                OrderItem line = new OrderItem(item.getId(), item.getName(), item.getPrice(), e.getValue());
                lines.add(line);
                total += line.subtotal();
            }
            Order order = new Order("ORD-" + orderSeq.incrementAndGet(), userId, restaurant.getId(),
                                    deliveryLocation, lines, total);
            orders.put(order.getId(), order);
            cart.clear();
            return order;
        }
    }

    public void acceptOrder(String orderId) {
        Order order = getOrder(orderId);
        order.moveTo(OrderStatus.ACCEPTED);
        tryAssignPartner(order);                               // start finding a rider while it cooks
    }

    public void markReady(String orderId) { getOrder(orderId).moveTo(OrderStatus.READY); }

    public void pickUp(String orderId) {
        Order order = getOrder(orderId);
        synchronized (order) {
            if (order.getPartnerId() == null) throw new IllegalStateException("No rider assigned to " + orderId);
            order.moveTo(OrderStatus.PICKED_UP);
        }
    }

    public void deliver(String orderId) {
        Order order = getOrder(orderId);
        synchronized (order) {
            order.moveTo(OrderStatus.DELIVERED);
            releasePartner(order);
        }
        assignPendingOrders();                                 // the freed rider can take a waiting order
    }

    public void cancel(String orderId) {
        Order order = getOrder(orderId);
        synchronized (order) {
            order.moveTo(OrderStatus.CANCELLED);               // throws once the food is picked up
            releasePartner(order);
        }
        assignPendingOrders();
    }

    public void assignPendingOrders() {
        for (Order order : orders.values()) tryAssignPartner(order);
    }

    // locked per ORDER: one order can't get two riders.
    // CAS on the rider: one rider can't get two orders.
    private void tryAssignPartner(Order order) {
        synchronized (order) {
            OrderStatus status = order.getStatus();
            boolean waiting = status == OrderStatus.ACCEPTED || status == OrderStatus.READY;
            if (!waiting || order.getPartnerId() != null) return;

            Location pickup = getRestaurant(order.getRestaurantId()).getLocation();
            for (DeliveryPartner p : assignmentStrategy.rank(new ArrayList<>(partners.values()), pickup)) {
                if (p.tryAssign(order.getId())) {              // lost the race for this rider? try the next
                    order.assignPartner(p.getId());
                    return;
                }
            }
            // no free rider: stays unassigned, retried by assignPendingOrders()
        }
    }

    private void releasePartner(Order order) {
        String partnerId = order.getPartnerId();
        if (partnerId != null) partners.get(partnerId).release(order.getId());
    }

    // + addRestaurant, addPartner, searchRestaurants, getOrder, getRestaurant (throw NoSuchElementException)
}
```
**Shape to remember:** `placeOrder` = lock cart → re-validate → copy prices → clear cart. `tryAssignPartner` = lock order → still waiting? → rank → CAS each until one wins.

### Dry run: two orders, one nearby rider
```
Ravi (1 km away) and Asha (8 km) are free. Orders A and B are accepted at the same instant.

A: lock(A) → rank → [Ravi, Asha] → Ravi.tryAssign(A): CAS null→A ✓ → A.partner = Ravi
B: lock(B) → rank → [Ravi, Asha] → Ravi.tryAssign(B): CAS fails (he's on A) → Asha.tryAssign(B) ✓
Each order has a different lock, so A and B run in parallel; the CAS settles who gets Ravi.

Later, order C is accepted: no one is free → C waits.
Ravi delivers A → release(A) → assignPendingOrders() → C gets Ravi.
```
The driver proves it: 20 orders accepted at once with 5 riders gives exactly 5 assigned and 5 distinct riders.

---

## 5. Follow-ups: answer out loud first, then open

<details>
<summary><b>Q1. Two orders want the same rider at the same moment. And can one order get two riders?</b></summary>

**Two orders, one rider:** `if (rider.isAvailable()) rider.setOrder(id)` is check-then-act. Both orders see "free", and both assign. `compareAndSet(null, orderId)` checks and sets in **one atomic step**: only one caller sees `true`. The loser moves to the next rider in the ranked list.

**One order, two riders:** `assignPendingOrders()` and `acceptOrder()` might run `tryAssignPartner` on the **same** order at once. Each could claim a *different* free rider, so the order has two and one rider is stuck. That's why `tryAssignPartner` locks the order and re-checks `partnerId != null` inside.

**Why no deadlock:** we take the order lock, then do a lock-free CAS on the rider. Only one lock is ever held at a time.
</details>

<details>
<summary><b>Q2. Customer cancels at the exact moment the rider taps "picked up".</b></summary>

Both call `order.moveTo(...)`, which is `synchronized`. Whoever gets the lock first wins:
- Cancel first: status = CANCELLED, rider released. Then pickUp → `CANCELLED → PICKED_UP` is not allowed → IllegalStateException ("order was cancelled").
- Pickup first: status = PICKED_UP. Then cancel → `PICKED_UP → CANCELLED` is not allowed → "too late to cancel".

The enum's transition rules + one lock per order turn a messy race into two clean outcomes.
</details>

<details>
<summary><b>Q3. Add payment: UPI, card, cash on delivery.</b></summary>

**Strategy**, chosen by the customer at checkout:
```java
public interface PaymentMethod {
    boolean charge(String userId, long amount, String idempotencyKey);
    void refund(String userId, long amount, String idempotencyKey);
}
public class UpiPayment implements PaymentMethod { ... }
public class CashOnDelivery implements PaymentMethod {
    public boolean charge(String userId, long amount, String key) { return true; }   // collected at the door
    public void refund(String userId, long amount, String key) { }
}
```
In `placeOrder`, after computing `total` and before saving the order:
```java
String orderId = "ORD-" + orderSeq.incrementAndGet();
if (!payment.charge(userId, total, orderId)) {           // orderId doubles as the idempotency key
    throw new IllegalStateException("Payment failed");   // cart is NOT cleared: the user can retry
}
```
- **Idempotency key:** if the app retries after a timeout, the payment provider sees the same key and doesn't charge twice.
- **Lock note:** we hold only *this user's* cart lock while paying, so no other customer waits.
</details>

<details>
<summary><b>Q4. Add a delivery fee, surge pricing in the rain, and coupons.</b></summary>

**Strategy** for the base price + **Decorator** to stack surge and coupons on top of any strategy:
```java
public interface PricingStrategy {
    long price(List<OrderItem> items, double distanceKm);
}

public class StandardPricing implements PricingStrategy {
    public long price(List<OrderItem> items, double distanceKm) {
        long itemsTotal = 0;
        for (OrderItem item : items) itemsTotal += item.subtotal();
        long deliveryFee = itemsTotal >= 500 ? 0 : 30 + Math.round(distanceKm) * 5;   // free above ₹500
        return itemsTotal + deliveryFee;
    }
}

public class SurgePricing implements PricingStrategy {          // rain / peak hours: +X%
    private final PricingStrategy inner;
    private final int surgePercent;
    public SurgePricing(PricingStrategy inner, int surgePercent) { this.inner = inner; this.surgePercent = surgePercent; }
    public long price(List<OrderItem> items, double distanceKm) {
        return inner.price(items, distanceKm) * (100 + surgePercent) / 100;
    }
}

public class FlatCoupon implements PricingStrategy {            // "₹100 off": never below ₹0
    private final PricingStrategy inner;
    private final long discount;
    public FlatCoupon(PricingStrategy inner, long discount) { this.inner = inner; this.discount = discount; }
    public long price(List<OrderItem> items, double distanceKm) {
        return Math.max(0, inner.price(items, distanceKm) - discount);
    }
}
// rainy evening with a coupon: ₹240 of dosa, 4 km → (240 + 50 fee) × 1.2 − 100 = ₹248
PricingStrategy pricing = new FlatCoupon(new SurgePricing(new StandardPricing(), 20), 100);
```
**Order matters:** coupon after surge (as above) gives the customer less than surge after coupon. Say which one the business wants.
</details>

<details>
<summary><b>Q5. Notify the customer on every status change.</b></summary>

**Observer.** The service announces each change; push, SMS and the restaurant tablet subscribe.
```java
public interface OrderListener {
    void onStatusChanged(Order order, OrderStatus newStatus);
}

private final List<OrderListener> listeners = new CopyOnWriteArrayList<>();

// every place that calls order.moveTo(next) calls this instead
private void changeStatus(Order order, OrderStatus next) {
    order.moveTo(next);
    for (OrderListener l : listeners) {
        try {
            l.onStatusChanged(order, next);
        } catch (Exception e) {                               // a failed push must not undo the change
            System.err.println("Listener failed: " + e.getMessage());
        }
    }
}
// usage
app.addListener((order, status) -> push.send(order.getCustomerId(), "Your order is " + status));
```
In production, publish the event to a queue so a slow push service never slows down order handling.
</details>

<details>
<summary><b>Q6. The rider rejects the order, or doesn't respond within 30 seconds.</b></summary>

Free the rider, clear the order's rider, and re-run assignment **excluding** the riders who already declined (otherwise "nearest" picks the same rider again):
```java
public void rejectAssignment(String orderId, String partnerId) {
    Order order = getOrder(orderId);
    synchronized (order) {
        if (!partnerId.equals(order.getPartnerId())) throw new IllegalStateException("Not your order");
        partners.get(partnerId).release(orderId);
        order.assignPartner(null);
        order.addDeclinedBy(partnerId);                     // Set<String> on the order
        tryAssignPartner(order);                            // skips riders in order.getDeclinedBy()
    }
}
```
**Timeout:** when assigning, schedule a check with `ScheduledExecutorService.schedule(..., 30, SECONDS)`. If the rider hasn't accepted by then, call `rejectAssignment`.
</details>

<details>
<summary><b>Q7. Thousands of riders: scanning all of them for "nearest" is slow.</b></summary>

Use a **geo index**: split the city into grid cells (e.g. 1 km × 1 km, or geohash cells). Keep `Map<cellId, Set<DeliveryPartner>>` and move riders between cells on location updates. To find the nearest, search the pickup's cell and its 8 neighbours first, and widen the ring only if no one is free.

That's O(riders nearby) instead of O(all riders). The `PartnerAssignmentStrategy` interface doesn't change: only its implementation gets the index. Production versions are Redis `GEOSEARCH` or a quadtree.
</details>

<details>
<summary><b>Q8. Run it on many servers.</b></summary>

`AtomicReference` only works inside one JVM. Move the claim into the database as a **conditional update**:
```sql
-- 1 row updated = you got the rider; 0 rows = someone else did, try the next
UPDATE delivery_partner SET current_order_id = 'ORD-42'
WHERE id = 'P1' AND current_order_id IS NULL;

-- legal status move, same idea: only succeeds from the expected status
UPDATE orders SET status = 'PICKED_UP'
WHERE id = 'ORD-42' AND status = 'READY';
```
The `WHERE ... IS NULL` / `WHERE status = ...` is the compare-and-set. Order events (status changes) go on a queue (Kafka/SQS) for notifications, analytics and ETA services.
</details>

<details>
<summary><b>Q9. Refund rules on cancel depend on how far the order got.</b></summary>

Read the status **before** moving to CANCELLED (inside the order lock), then decide:
```java
public long refundAmount(Order order, OrderStatus statusWhenCancelled) {
    switch (statusWhenCancelled) {
        case PLACED:   return order.getTotal();          // restaurant hasn't started
        case ACCEPTED: return order.getTotal() / 2;      // cooking has started
        default:       return 0;                         // food is ready: no refund
    }
}
```
If the rules differ per city or campaign, make this a `RefundPolicy` Strategy.
</details>

<details>
<summary><b>Q10. Live tracking and ETA.</b></summary>

The rider app sends GPS every few seconds → `partner.updateLocation(loc)` (a `volatile` field, so readers always see the latest). ETA ≈ distance(rider → restaurant) + prep time left + distance(restaurant → customer), each distance ÷ average speed. Customers get updates by polling or over a WebSocket. Store the location stream separately from orders: it's high-volume and short-lived.
</details>

<details>
<summary><b>Q11. Ratings for restaurants and riders.</b></summary>

`rate(orderId, stars)`, allowed only when the status is DELIVERED and only once per order. Keep `ratingSum` and `ratingCount` (`AtomicLong`s) on the restaurant and rider; average = sum / count. A `BestRatedPartnerStrategy` can then rank riders by rating: a new Strategy, no service changes.
</details>

<details>
<summary><b>Q12. How would you know it's working in production?</b></summary>

- **Metrics:** orders/min per city, time from ACCEPTED to rider assigned, **orders waiting for a rider**, cancellation rate per status, delivery time p50/p90, payment failures.
- **Alarms:** waiting-for-rider queue growing (rider shortage), any rider on two active orders (should be 0), and a spike in restaurant rejections.
- **Logs:** orderId on every status change, with who triggered it (customer / restaurant / rider / system).
</details>

<details>
<summary><b>Q13. How do you test the concurrency?</b></summary>

From the driver: 20 orders, 5 riders, 20 threads calling `acceptOrder` at the same instant:
```java
ExecutorService pool = Executors.newFixedThreadPool(20);
CountDownLatch start = new CountDownLatch(1);
for (Order o : orders) {
    pool.submit(() -> {
        start.await();                     // all threads wait at the gate
        app.acceptOrder(o.getId());
        return null;
    });
}
start.countDown();                         // release all 20 at once
pool.shutdown();
pool.awaitTermination(5, TimeUnit.SECONDS);
// assert: exactly 5 orders have a rider, and the 5 rider ids are all different
```
Also test: every illegal transition throws, a price change after ordering doesn't change the total, the cart rejects a second restaurant, and a waiting order gets a rider after a delivery.
</details>

---

## 6. Traps that cost points
1. `if (rider.isFree()) assign(...)`: check-then-act race. Use CAS.
2. Status checks as scattered `if`s instead of one `canMoveTo()`.
3. The order referencing `MenuItem`, so a menu price change silently changes old orders.
4. Trusting the cart at checkout: not re-checking open / available.
5. Throwing when no rider is free, instead of letting the order wait.
6. A class per order status (State pattern) when only the allowed transitions differ.
7. 15 minutes on restaurant search and menus before the order flow exists.

---

## 7. Recall check (next day, no peeking)
1. Draw the status diagram from memory. Which states can be cancelled?
2. Why is `OrderStatus` an enum here, but Insurance uses the State pattern?
3. Two orders want the same rider: what line stops the double assignment, and what does the loser do?
4. Why does `tryAssignPartner` also lock the order?
5. Why does `OrderItem` exist separately from `MenuItem`?
6. What happens to an order accepted when no rider is free?
7. Pricing with surge and a coupon: which pattern stacks them, and does the order matter?

**Rebuild in 10 minutes:** `OrderStatus` + `canMoveTo` · `Order.moveTo` (synchronized) · `DeliveryPartner.tryAssign` (CAS) · `NearestPartnerStrategy.rank` · `placeOrder` (lock cart → validate → copy prices) · `tryAssignPartner` (lock order → rank → CAS loop).

---

**Files:** `FoodDeliveryService` (service) · `model/` (`Restaurant`, `MenuItem`, `Cart`, `Order`, `OrderItem`, `OrderStatus`, `DeliveryPartner`, `Location`) · `assignment/` (`PartnerAssignmentStrategy`, `NearestPartnerStrategy`) · `FoodDeliveryDriver` (cart rule, price snapshot, illegal transition, nearest rider, waiting order, cancel, 20-thread test)
```bash
mvn -q compile exec:java -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.FoodDeliveryDriver
```
