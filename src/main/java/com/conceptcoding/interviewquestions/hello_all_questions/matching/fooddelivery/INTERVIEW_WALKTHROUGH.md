# Food Delivery (Swiggy / Zomato lite)

> 40–45 min round. **Reported at Ethos.** Phases: **Requirements (5) → Entities (3) → Class Design (10–15) → Implementation (10–15) → Extensibility (5)**.
> Sister problem: [Cab Booking](../cabbooking/INTERVIEW_WALKTHROUGH.md) (the same match → claim → lifecycle idea).

---

## 1. Requirements (~5 min)

**Ask:** a cart from one restaurant only? Which order statuses? Who can cancel, and until when? How is a rider chosen? What if no rider is free?

```
1. Cart (one restaurant only) → placeOrder copies item prices into the order
2. Order: PLACED → ACCEPTED → READY → PICKED_UP → DELIVERED; cancel any time before pickup
3. On accept, assign the nearest free rider; if none is free, the order waits for the next free rider
4. Reject illegal moves (deliver before pickup, cancel after pickup); safe with many users at once

Out of scope: payment, fees/coupons, search, ratings, live tracking
```

---

## 2. Entities & Relationships (~3 min)

```
FoodDeliveryService (orchestrator)
   ├── Restaurant ── MenuItem (price, available)
   ├── Cart (per user) ── itemId → qty
   ├── Order ── OrderItem (a COPY of name + price) · OrderStatus · partnerId
   ├── DeliveryPartner (location, available)
   └── PartnerAssignmentStrategy   "which rider"
```
- **Legal order moves** live in `OrderStatus.canMoveTo`; the order enforces them in `moveTo`.
- **The one-restaurant rule** lives in `Cart`, which owns its items.
- **`OrderItem` copies the price**, so a later menu change doesn't change an old order.
- **No class** for Customer (an id is enough), Payment, or Address.

---

## 3. Class Design (~10–15 min)

```
FoodDeliveryService
  state:     restaurants, partners, carts (userId → Cart), orders, assignmentStrategy
  behaviour: addToCart · placeOrder · cancel           (customer)
             acceptOrder · markReady                   (restaurant)
             pickUp · deliver                          (rider)

Restaurant       id, name, location, menu            getItem(itemId)
MenuItem         id, name, price, available
Cart             restaurantId, itemId → qty          add(restaurantId, itemId, qty) · clear
Order            id, customer, restaurant, items, total, status, partnerId   moveTo(next) · needsPartner()
OrderItem        itemId, name, unitPrice, quantity   subtotal()
OrderStatus      PLACED, ACCEPTED, READY, PICKED_UP, DELIVERED, CANCELLED   canMoveTo(next)
DeliveryPartner  id, name, location, available

PartnerAssignmentStrategy   choose(freePartners, pickup) → partner or null   ← NearestPartnerStrategy
```
- **Order status is an enum** with `canMoveTo`, not the State pattern: statuses only limit the next move.
- **Strategy** for rider choice, because the business will change it.
- **Thread safety:** every service method is `synchronized`, so one rider never gets two orders.

---

## 4. Implementation (~10–15 min)

**placeOrder:** cart → for each item, check it's available and copy name + price → total → save → clear the cart. *Edge cases:* empty cart; item sold out.
**acceptOrder:** move to ACCEPTED → assign the nearest free rider. *Edge case:* nobody free → the order waits.
**deliver / cancel:** move the status → free the rider → give waiting orders a chance. *Edge case:* an illegal move → error.

```java
public enum OrderStatus {
    PLACED, ACCEPTED, READY, PICKED_UP, DELIVERED, CANCELLED;

    public boolean canMoveTo(OrderStatus next) {
        switch (this) {
            case PLACED:    return next == ACCEPTED || next == CANCELLED;
            case ACCEPTED:  return next == READY || next == CANCELLED;
            case READY:     return next == PICKED_UP || next == CANCELLED;
            case PICKED_UP: return next == DELIVERED;                  // food on the road: no cancel
            default:        return false;                              // DELIVERED, CANCELLED are final
        }
    }
}

public class Order {
    private final String id, customerId, restaurantId;
    private final List<OrderItem> items;
    private final long total;
    private OrderStatus status = OrderStatus.PLACED;
    private String partnerId;                                          // null until assigned

    public void moveTo(OrderStatus next) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("Order " + id + ": cannot go from " + status + " to " + next);
        }
        status = next;
    }

    public boolean needsPartner() {
        return partnerId == null && (status == OrderStatus.ACCEPTED || status == OrderStatus.READY);
    }
    // constructor, getters, setPartnerId
}

public class Cart {
    private String restaurantId;                                       // null while empty
    private final Map<String, Integer> quantities = new LinkedHashMap<>();

    public void add(String restaurantId, String itemId, int qty) {
        if (qty <= 0) throw new IllegalArgumentException("Quantity must be > 0");
        if (this.restaurantId != null && !this.restaurantId.equals(restaurantId)) {
            throw new IllegalStateException("Cart has items from another restaurant. Clear it first.");
        }
        this.restaurantId = restaurantId;
        quantities.merge(itemId, qty, Integer::sum);
    }
    // clear, isEmpty, getRestaurantId, getQuantities
}

// OrderItem { itemId, name, unitPrice, quantity; subtotal() = unitPrice × quantity }
// MenuItem { id, name, price, available }   Restaurant { id, name, location, menu; getItem(id) }
// DeliveryPartner { id, name, location, available }   Location { x, y; distanceTo(other) }
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
    private final Map<String, Cart> carts = new HashMap<>();
    private final Map<String, Order> orders = new HashMap<>();
    private final PartnerAssignmentStrategy assignmentStrategy;
    private int nextOrderNumber = 1;

    // constructor sets the strategy; addRestaurant / addPartner put into the maps

    public synchronized void addToCart(String userId, String restaurantId, String itemId, int qty) {
        MenuItem item = getRestaurant(restaurantId).getItem(itemId);
        if (!item.isAvailable()) throw new IllegalStateException(item.getName() + " is unavailable");
        carts.computeIfAbsent(userId, u -> new Cart()).add(restaurantId, itemId, qty);
    }

    public synchronized Order placeOrder(String userId, Location deliveryLocation) {
        Cart cart = carts.get(userId);
        if (cart == null || cart.isEmpty()) throw new IllegalStateException("Cart is empty");
        Restaurant restaurant = getRestaurant(cart.getRestaurantId());

        List<OrderItem> lines = new ArrayList<>();
        long total = 0;
        for (Map.Entry<String, Integer> e : cart.getQuantities().entrySet()) {
            MenuItem item = restaurant.getItem(e.getKey());
            if (!item.isAvailable()) throw new IllegalStateException(item.getName() + " is no longer available");
            OrderItem line = new OrderItem(item.getId(), item.getName(), item.getPrice(), e.getValue());   // copy
            lines.add(line);
            total += line.subtotal();
        }
        Order order = new Order("ORD-" + nextOrderNumber++, userId, restaurant.getId(), deliveryLocation, lines, total);
        orders.put(order.getId(), order);
        cart.clear();
        return order;
    }

    public synchronized void acceptOrder(String orderId) {
        Order order = getOrder(orderId);
        order.moveTo(OrderStatus.ACCEPTED);
        assignPartner(order);
    }

    public synchronized void markReady(String orderId) { getOrder(orderId).moveTo(OrderStatus.READY); }

    public synchronized void pickUp(String orderId) {
        Order order = getOrder(orderId);
        if (order.getPartnerId() == null) throw new IllegalStateException("No rider assigned to " + orderId);
        order.moveTo(OrderStatus.PICKED_UP);
    }

    public synchronized void deliver(String orderId) {
        Order order = getOrder(orderId);
        order.moveTo(OrderStatus.DELIVERED);
        freePartner(order);
        assignWaitingOrders();                        // the freed rider can take a waiting order
    }

    public synchronized void cancel(String orderId) {
        Order order = getOrder(orderId);
        order.moveTo(OrderStatus.CANCELLED);          // throws once the food is picked up
        freePartner(order);
        assignWaitingOrders();
    }

    private void assignPartner(Order order) {
        List<DeliveryPartner> free = new ArrayList<>();
        for (DeliveryPartner p : partners.values()) {
            if (p.isAvailable()) free.add(p);
        }
        DeliveryPartner partner = assignmentStrategy.choose(free, getRestaurant(order.getRestaurantId()).getLocation());
        if (partner == null) return;                  // nobody free: the order waits
        partner.setAvailable(false);
        order.setPartnerId(partner.getId());
    }

    private void assignWaitingOrders() {
        for (Order order : orders.values()) {
            if (order.needsPartner()) assignPartner(order);
        }
    }

    private void freePartner(Order order) {
        if (order.getPartnerId() != null) partners.get(order.getPartnerId()).setAvailable(true);
    }
    // + getOrder / getRestaurant: throw NoSuchElementException if missing
}
```

**Verify out loud** (Ravi 1 km from the restaurant, Asha 8 km):
```
Alice: 2 × Biryani ₹320 + 1 × Raita ₹40 → placeOrder → ORD-1 total ₹680
menu price changes to ₹999 → ORD-1 still ₹680 (prices were copied)
deliver(ORD-1) now → PLACED → DELIVERED not allowed → error
accept ORD-1 → nearest free = Ravi.   accept ORD-2 → Asha.   accept ORD-3 → nobody free → waits
ORD-1: ready → picked up → delivered → Ravi free → waiting ORD-3 gets Ravi
cancel ORD-2 → Asha free.   cancel ORD-3 after pickup → error
```

---

## 5. Extensibility (~5 min)

Say **which class changes and why**.

| What if... | Change |
|---|---|
| Payment (UPI / card / cash) | A `PaymentMethod` Strategy; `placeOrder` charges the total before saving the order (order id as the idempotency key). |
| Delivery fee, surge, coupons | A `PricingStrategy` for the total; surge and coupons wrap it (Decorator): `new Coupon(new Surge(new StandardPricing(), 20), 100)`. |
| Notify the customer on every status change | Observer: an `OrderListener` called after each `moveTo`. |
| The rider rejects the order | Free them, remember they declined, and run `assignPartner` again skipping them. |
| Refund depends on how far the order got | Read the status before cancelling: PLACED → full, ACCEPTED → half, READY → none. |
| Search restaurants / restaurant closed | `searchRestaurants(query)` over the names; an `open` flag checked in `placeOrder`. |
| Many users, the lock is slow | First say *"each call holds it for microseconds; I'd measure."* Then: claim a rider atomically (`AtomicReference<String> currentOrderId`, `compareAndSet(null, orderId)`), lock per order instead of the whole service, and use concurrent maps. |
| Thousands of riders | A geo index (grid cells) so the strategy only looks at riders near the restaurant. |

<details>
<summary>Code: many users without a global lock</summary>

```java
// DeliveryPartner: claim in one atomic step
private final AtomicReference<String> currentOrderId = new AtomicReference<>();   // null = free
public boolean tryAssign(String orderId) { return currentOrderId.compareAndSet(null, orderId); }
public void release(String orderId)      { currentOrderId.compareAndSet(orderId, null); }

// FoodDeliveryService: lock only the ORDER being assigned (one order can't get two riders),
// and let the CAS decide which order gets a rider (one rider can't get two orders)
private void tryAssignPartner(Order order) {
    synchronized (order) {
        if (!order.needsPartner()) return;
        for (DeliveryPartner p : rankedFreePartners(order)) {      // best first
            if (p.tryAssign(order.getId())) { order.setPartnerId(p.getId()); return; }
        }
    }
}
```
</details>

<details>
<summary>Code: coupons and surge on top of the price (Decorator)</summary>

```java
public interface PricingStrategy { long price(List<OrderItem> items, double distanceKm); }

public class SurgePricing implements PricingStrategy {          // rain / peak hours: +X%
    private final PricingStrategy inner;
    private final int surgePercent;
    public SurgePricing(PricingStrategy inner, int surgePercent) { this.inner = inner; this.surgePercent = surgePercent; }
    public long price(List<OrderItem> items, double km) { return inner.price(items, km) * (100 + surgePercent) / 100; }
}

public class FlatCoupon implements PricingStrategy {            // "₹100 off", never below 0
    private final PricingStrategy inner;
    private final long discount;
    public FlatCoupon(PricingStrategy inner, long discount) { this.inner = inner; this.discount = discount; }
    public long price(List<OrderItem> items, double km) { return Math.max(0, inner.price(items, km) - discount); }
}
```
</details>

---

```bash
mvn -q compile exec:java -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.FoodDeliveryDriver
```
