# Java + Design Patterns Refresher for LLD

Only the Java you actually type in an LLD round, and the patterns that actually come up. Nothing else.

| Part | What | When to read |
|---|---|---|
| **A. Java syntax** | classes, interfaces, enums, collections, lambdas, exceptions, generics, concurrency | when the syntax feels rusty |
| **B. Design patterns** | SOLID + the 7 core patterns used in this deck, plus 3 to know briefly. Each has a plain-language picture, when to use it, code, and where it's used | before starting the problems |
| **C. Blank-page template** | the shape of a whole LLD solution in one file | the night before |
| **D. Gotchas** | bugs that interviewers notice | the night before |

When to use which pattern and concurrency tool across problems is in [00_AMAZON_LLD_FOUNDATIONS.md](00_AMAZON_LLD_FOUNDATIONS.md). This file is the *how to write it*.

---

# Part A — Java syntax for LLD

## A1. Class anatomy

```java
public class Ticket {
    private static final long RATE_PER_HOUR = 40;   // constant: static + final, UPPER_CASE
    private static int created = 0;                 // static = ONE copy shared by all Tickets

    private final String id;                        // final = assigned once, in the constructor
    private final VehicleType type;
    private long fee;                               // not final = can change later

    public Ticket(String id, VehicleType type) {
        this.id = id;                               // this.id = the field, id = the parameter
        this.type = type;
        created++;
    }

    public String getId() { return id; }
    public long getFee()  { return fee; }
    void setFee(long fee) { this.fee = fee; }       // no modifier = package-private
}
```

| Modifier | Visible to |
|---|---|
| `private` | this class only |
| *(none)* | same package |
| `protected` | same package + subclasses |
| `public` | everyone |

**Interview rules:** fields `private`, and `final` wherever possible. Only add getters something actually uses. Skip setters unless the value really changes.

**Nested class:** `static class Bucket { ... }` inside another class is a helper that belongs to it. Without `static` it secretly holds a reference to the outer object, so use `static` unless you need that reference.

## A2. Interface vs abstract class

```java
public interface Limiter {                          // a contract: WHAT, not HOW
    RateLimitResult allow(String clientId);         // automatically public + abstract
    default boolean isEnabled() { return true; }    // optional shared code (Java 8+)
}

public class TokenBucketLimiter implements Limiter {
    @Override                                       // compiler checks you really override
    public RateLimitResult allow(String clientId) { ... }
}
```

```java
public abstract class Piece {                       // shared STATE + shared code
    protected final Color color;                    // subclasses can read it

    protected Piece(Color color) { this.color = color; }

    public abstract boolean canMove(Board b, Position from, Position to);  // each piece decides
    public Color getColor() { return color; }       // written once, inherited by all
}

public class Rook extends Piece {
    public Rook(Color color) { super(color); }      // call the parent constructor first
    @Override
    public boolean canMove(Board b, Position from, Position to) { ... }
}
```

**Which one?**
- **Interface by default.** A class can `implement` many interfaces but `extend` only one class.
- **Abstract class** only when subclasses share **fields** or real code.
- Always write `@Override`. If you misspell the method name, the compiler catches it.

## A3. Enums: more than constants

```java
public enum Coin {
    ONE(1), TWO(2), FIVE(5), TEN(10);

    private final int value;                        // each constant carries data
    Coin(int value) { this.value = value; }         // enum constructors are always private
    public int getValue() { return value; }
}

Coin c = Coin.valueOf("TEN");                       // String → enum
for (Coin coin : Coin.values()) { ... }             // loop over all constants
if (c == Coin.TEN) { ... }                          // == is correct for enums
```

**switch on an enum:**
```java
switch (direction) {                                // no "Direction." prefix inside case
    case UP:   floor++; break;                      // forget break → falls into the next case
    case DOWN: floor--; break;
    default:   break;
}

int delta = switch (direction) {                    // Java 14+: arrow form, no break needed
    case UP -> 1;
    case DOWN -> -1;
    case IDLE -> 0;
};
```

**An enum as a small state machine.** This is enough when states only need bookkeeping (see B2 for when you need classes):
```java
public enum DownloadStatus {
    QUEUED, RUNNING, PAUSED, COMPLETED, FAILED;

    public boolean canMoveTo(DownloadStatus next) {
        switch (this) {
            case QUEUED:  return next == RUNNING;
            case RUNNING: return next == PAUSED || next == COMPLETED || next == FAILED;
            case PAUSED:  return next == RUNNING;
            default:      return false;             // COMPLETED and FAILED are final
        }
    }
}
```

**`EnumMap<Channel, Sender>`:** a map with enum keys. Faster than `HashMap`, and it iterates in declaration order.

## A4. Collections: pick by what you need

| You need | Use | Main ops (cost) | Deck example |
|---|---|---|---|
| ordered list | `ArrayList` | `get(i)` O(1), `add` O(1) | spots, players |
| lookup by key | `HashMap` | `get/put` O(1) | id → booking |
| keys kept **sorted**, or "nearest key" | `TreeMap` | `floorKey/ceilingKey` O(log n) | meeting slots, elevator floors |
| remember insertion/access order | `LinkedHashMap` | O(1) | LRU cache |
| unique items | `HashSet` (`TreeSet` sorted) | `add/contains` O(1) | occupied spot ids |
| queue (FIFO), stack, both ends | `ArrayDeque` | `offer/poll/peek`, `push/pop` O(1) | sliding window log |
| always take smallest/largest | `PriorityQueue` | `offer/poll` O(log n) | job scheduler, settle debts |
| enum keys | `EnumMap` | O(1) | channel → sender |
| shared across threads | `ConcurrentHashMap` | O(1) | per-client buckets |

### Map moves you'll use constantly
```java
Map<String, Integer> stock = new HashMap<>();
stock.put("coke", 5);
int n = stock.getOrDefault("pepsi", 0);              // no null check needed
stock.merge("coke", -1, Integer::sum);               // stock["coke"] += -1 (inserts if missing)
stock.putIfAbsent("fanta", 0);                       // only if the key isn't there

// ⭐ "map of lists" idiom: create the list on first use
Map<String, List<String>> bookingsByUser = new HashMap<>();
bookingsByUser.computeIfAbsent("alice", k -> new ArrayList<>()).add("B-101");

for (Map.Entry<String, Integer> e : stock.entrySet()) {
    System.out.println(e.getKey() + " = " + e.getValue());
}
stock.entrySet().removeIf(e -> e.getValue() == 0);   // safe way to remove while looping
```

### TreeMap: "what's just before / after this key?"
```java
TreeMap<Integer, String> meetings = new TreeMap<>();   // startMinute → meetingId
meetings.put(600, "standup");                          // 10:00
Integer before = meetings.floorKey(630);               // largest key ≤ 630  → 600
Integer after  = meetings.ceilingKey(630);             // smallest key ≥ 630 → null (none)
meetings.firstKey(); meetings.lastKey(); meetings.pollFirstEntry();
```

### Queue, stack, priority queue
```java
Deque<Integer> queue = new ArrayDeque<>();
queue.offer(1); queue.peek(); queue.poll();          // FIFO: add at back, take from front

Deque<Integer> stack = new ArrayDeque<>();           // don't use the old java.util.Stack
stack.push(1); stack.peek(); stack.pop();            // LIFO

PriorityQueue<Job> pq = new PriorityQueue<>(
        Comparator.comparingInt(Job::getPriority).reversed());   // highest priority first
pq.offer(job);
Job next = pq.poll();
```

### Immutable and defensive copies
```java
List<String> fixed = List.of("A", "B");              // immutable: add() throws
List<String> mutable = new ArrayList<>(fixed);       // copy when you need to modify
public List<Booking> getBookings() {
    return Collections.unmodifiableList(bookings);   // callers can't change your internal list
}
```

## A5. Lambdas, method references, Comparator

A **lambda** is a short function you can pass around. It works anywhere Java expects an interface with exactly one method.

| Interface | Shape | Example |
|---|---|---|
| `Runnable` | `() → void` | `() -> System.out.println("hi")` |
| `Supplier<T>` | `() → T` | `() -> new ArrayList<>()` |
| `Consumer<T>` | `T → void` | `msg -> log.add(msg)` |
| `Function<T,R>` | `T → R` | `d -> d.getRating()` |
| `Predicate<T>` | `T → boolean` | `d -> d.isAvailable()` |
| your own 1-method interface | anything | `(sku, left) -> alert(sku)` |

**Method references** are shorthand for a lambda that just calls one method: `Driver::getRating`, `Integer::sum`, `ArrayList::new`, `System.out::println`.

```java
// sort by rating (high → low), then by id for ties
drivers.sort(Comparator.comparingDouble(Driver::getRating).reversed()
                       .thenComparing(Driver::getId));

// custom compare: Long.compare, never a - b (that overflows)
Comparator<Driver> byDistance = (a, b) -> Long.compare(a.distanceTo(pickup), b.distanceTo(pickup));
```
A lambda can only use local variables that are never reassigned ("effectively final").

## A6. Streams: the 4 you need (otherwise use a for loop)

```java
List<Driver> free = drivers.stream()
        .filter(d -> d.getStatus() == DriverStatus.AVAILABLE)
        .collect(Collectors.toList());

Optional<Driver> nearest = free.stream()
        .min(Comparator.comparingLong(d -> d.distanceTo(pickup)));

long total = splits.stream().mapToLong(Split::getAmount).sum();

Map<Size, List<Compartment>> bySize = compartments.stream()
        .collect(Collectors.groupingBy(Compartment::getSize));
```
**Rule:** if a stream takes you more than 10 seconds to get right, write a for loop. Nobody loses points for loops.

## A7. Optional: for "find" methods that might find nothing

```java
public Optional<Spot> findFreeSpot(VehicleType type) {
    for (Spot s : spots) if (s.fits(type)) return Optional.of(s);
    return Optional.empty();
}

Spot spot = lot.findFreeSpot(type)
               .orElseThrow(() -> new IllegalStateException("Lot full"));
lot.findFreeSpot(type).ifPresent(s -> System.out.println(s.getId()));
```
Use `Optional` for return values only, not for fields or parameters.

## A8. Exceptions

```java
if (amount <= 0)                throw new IllegalArgumentException("amount must be > 0");  // bad input
if (state != State.HAS_MONEY)   throw new IllegalStateException("insert coin first");     // wrong moment

// custom exception: extend RuntimeException so callers aren't forced to catch it
public class SeatUnavailableException extends RuntimeException {
    public SeatUnavailableException(String msg) { super(msg); }
}
```

- **Unchecked** (`RuntimeException` and its subclasses): no `throws` needed. Use these in LLD.
- **Checked** (`Exception`): forces `throws` or try/catch everywhere. You only meet them with I/O and `InterruptedException`.

```java
try {
    sender.send(msg);
} catch (Exception e) {                               // Exception, not Throwable: don't hide JVM errors
    failures.add(e.getMessage());
} finally {
    lock.unlock();                                    // always runs
}

try (BufferedWriter w = new BufferedWriter(new FileWriter("app.log", true))) {
    w.write(line);                                    // try-with-resources closes w automatically
}

catch (InterruptedException e) {
    Thread.currentThread().interrupt();               // restore the flag, then stop
    return;
}
```

## A9. Generics

```java
public interface Cache<K, V> {                       // K, V are placeholders for real types
    V get(K key);
    void put(K key, V value);
}

public class LRUCache<K, V> implements Cache<K, V> {
    private final Map<K, Node<K, V>> map = new HashMap<>();

    private static class Node<K, V> {
        K key; V value; Node<K, V> prev, next;
        Node(K key, V value) { this.key = key; this.value = value; }
    }
    ...
}

// generic method with a bound: T must be comparable to itself
public static <T extends Comparable<T>> T max(List<T> items) { ... }
```
**Limits:** no primitives (`Map<Integer, Long>`, not `Map<int, long>`), and no `new T()`.

## A10. equals + hashCode: whenever an object is a map key or in a set

```java
public final class Position {
    private final int row, col;

    public Position(int row, int col) { this.row = row; this.col = col; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Position)) return false;
        Position p = (Position) o;
        return row == p.row && col == p.col;
    }

    @Override
    public int hashCode() { return Objects.hash(row, col); }

    @Override
    public String toString() { return "(" + row + "," + col + ")"; }
}
```
**Without these,** `map.get(new Position(1, 2))` returns `null` even though you stored a `Position(1, 2)`: it's a different object. Override **both or neither**, and keep key fields `final`.

## A11. Ids and time

```java
String id = UUID.randomUUID().toString();             // globally unique, ugly
private final AtomicLong seq = new AtomicLong();
String ticketId = "T-" + seq.incrementAndGet();       // readable, thread-safe

long nowMs = System.currentTimeMillis();
Instant now = Instant.now();
Duration parked = Duration.between(entryTime, exitTime);
long hours = (parked.toMinutes() + 59) / 60;          // round UP to whole hours
LocalDateTime show = LocalDateTime.of(2026, 10, 3, 18, 30);
boolean earlier = a.isBefore(b);

private final Clock clock;                            // inject for testable time
long t = clock.millis();                              // tests pass a fixed or movable clock
```

## A12. Concurrency syntax

*Which* tool to use is covered in foundations §4. This is *how to write* each one.

```java
// synchronized: one thread at a time per lock object
public synchronized void book() { ... }               // lock = this object
synchronized (seat) { ... }                           // lock = the seat object

// volatile: every thread sees the latest value. NOT atomic: count++ is still unsafe.
private volatile boolean running = true;

// Atomics: safe single-variable updates without a lock
AtomicInteger count = new AtomicInteger();
count.incrementAndGet();
boolean won = status.compareAndSet(FREE, BOOKED);     // true only if it WAS FREE and now is BOOKED

// ReentrantLock: like synchronized, plus "try for a while, then give up"
private final ReentrantLock lock = new ReentrantLock();
if (lock.tryLock(100, TimeUnit.MILLISECONDS)) {       // throws InterruptedException
    try { ... } finally { lock.unlock(); }            // ALWAYS unlock in finally
}

// ReadWriteLock: many readers at once, OR one writer
private final ReadWriteLock rw = new ReentrantReadWriteLock();
rw.readLock().lock();
try { ... } finally { rw.readLock().unlock(); }

// Thread pool
ExecutorService pool = Executors.newFixedThreadPool(4);
Future<Long> f = pool.submit(() -> download(url));    // runs in the background
long bytes = f.get();                                 // waits for the result
pool.shutdown();

// Producer–consumer
BlockingQueue<Task> q = new LinkedBlockingQueue<>(100);  // bounded = backpressure
q.put(task);                                          // waits if full
Task t = q.take();                                    // waits if empty

// Run later / repeatedly
ScheduledExecutorService sched = Executors.newScheduledThreadPool(1);
sched.schedule(() -> retry(job), 2, TimeUnit.SECONDS);
sched.scheduleAtFixedRate(this::sweep, 1, 1, TimeUnit.MINUTES);

// Async chain
CompletableFuture.supplyAsync(() -> charge(order), pool)
                 .thenAccept(result -> notifyUser(result))
                 .exceptionally(ex -> { log(ex); return null; });

// Test gate: release N threads at the same instant
CountDownLatch start = new CountDownLatch(1);
// each worker: start.await(); then act.      main thread: start.countDown();
```

---

# Part B — Design patterns

## B0. Principles first: they're *why* patterns exist

| Principle | Plain meaning | Smell when broken |
|---|---|---|
| **S**ingle Responsibility | a class has one reason to change | `ParkingLot` stores spots **and** calculates fees **and** prints receipts |
| **O**pen/Closed | add behaviour by adding classes, not editing old ones | a `switch(type)` that grows with every feature |
| **L**iskov Substitution | a subclass works anywhere its parent does, without surprises | `Penguin extends Bird` throws in `fly()` |
| **I**nterface Segregation | small focused interfaces | a `Machine` interface forces `fax()` on a simple printer |
| **D**ependency Inversion | depend on interfaces; get the concrete class passed in | `new EmailSender()` hard-coded inside the service |

```java
// ✗ hard-wired: can't swap or fake EmailSender in a test
class NotificationService {
    private final EmailSender sender = new EmailSender();
}
// ✓ injected: depends on the interface, the caller chooses
class NotificationService {
    private final NotificationSender sender;
    NotificationService(NotificationSender sender) { this.sender = sender; }
}
```

**Composition over inheritance:** "has-a" beats "is-a". A logger with 2 formats × 3 outputs needs **6 subclasses** with inheritance (`JsonFileLogger`, `TextConsoleLogger`...). With composition, `Logger` simply **has a** `Formatter` and **has a** `Sink`: 2 + 3 = **5 small classes**, combined freely. Most patterns below are composition in disguise.

---

Only the patterns your Amazon problems actually use.
- **B1–B7, learn fully:** Strategy, State, Decorator, Factory, Observer, Builder, Singleton.
- **B8–B10, know briefly:** Facade, Adapter, Chain of Responsibility.

---

## The 7 core patterns

### B1. Strategy ⭐ (the most used pattern in LLD)
**Picture:** Google Maps. Same trip, choose car / bike / walk. The route algorithm swaps; the app stays the same.
**Use when:** there are 2+ algorithms for the same job, chosen by config, by the user, or at runtime.

```java
interface SplitStrategy {
    Map<String, Long> split(long amount, List<String> users);
}

class EqualSplit implements SplitStrategy {
    public Map<String, Long> split(long amount, List<String> users) {
        Map<String, Long> shares = new LinkedHashMap<>();
        long base = amount / users.size();
        long remainder = amount % users.size();             // first users absorb leftover rupees
        for (int i = 0; i < users.size(); i++) {
            shares.put(users.get(i), base + (i < remainder ? 1 : 0));
        }
        return shares;
    }
}

class ExpenseService {
    private final SplitStrategy strategy;                   // holds the INTERFACE, not EqualSplit
    ExpenseService(SplitStrategy strategy) { this.strategy = strategy; }

    Map<String, Long> addExpense(long amount, List<String> users) {
        return strategy.split(amount, users);
    }
}
// usage
ExpenseService service = new ExpenseService(new EqualSplit());
System.out.println(service.addExpense(100, List.of("A", "B", "C")));   // {A=34, B=33, C=33}
```
- **Variation:** pass the strategy **per call** (`addExpense(amount, users, strategy)`) when each request picks its own, as in Splitwise.
- **In the deck:** rate limiter algorithms, ride matching, split types, elevator dispatch, room allocation, job scheduling policies.
- **One-sentence test:** *"Do I need 2+ implementations on day 1?"* If not, write a plain method and mention Strategy as a follow-up.

### B2. State
**Picture:** a vending machine. Pressing "select" does something different depending on whether you've paid.
**Use when:** **several** actions all behave differently depending on the current state, and actions change the state.

```java
interface MachineState {
    void insertCoin(VendingMachine m, int amount);
    void selectItem(VendingMachine m, int price);
}

class IdleState implements MachineState {
    public void insertCoin(VendingMachine m, int amount) {
        m.addBalance(amount);
        m.setState(m.hasMoneyState());
    }
    public void selectItem(VendingMachine m, int price) {
        System.out.println("Insert a coin first");
    }
}

class HasMoneyState implements MachineState {
    public void insertCoin(VendingMachine m, int amount) { m.addBalance(amount); }
    public void selectItem(VendingMachine m, int price) {
        if (m.getBalance() < price) {
            System.out.println("Need ₹" + (price - m.getBalance()) + " more");
            return;
        }
        System.out.println("Dispensed. Change ₹" + (m.getBalance() - price));
        m.resetBalance();
        m.setState(m.idleState());
    }
}

class VendingMachine {
    private final MachineState idle = new IdleState();        // created once, reused
    private final MachineState hasMoney = new HasMoneyState();
    private MachineState state = idle;
    private int balance;

    void insertCoin(int amount) { state.insertCoin(this, amount); }   // just delegate
    void selectItem(int price)  { state.selectItem(this, price); }

    MachineState idleState()     { return idle; }
    MachineState hasMoneyState() { return hasMoney; }
    void setState(MachineState s) { this.state = s; }
    void addBalance(int amount)   { balance += amount; }
    int getBalance()              { return balance; }
    void resetBalance()           { balance = 0; }
}
// usage
VendingMachine vm = new VendingMachine();
vm.selectItem(20);   // Insert a coin first
vm.insertCoin(10);
vm.selectItem(20);   // Need ₹10 more
vm.insertCoin(20);
vm.selectItem(20);   // Dispensed. Change ₹10
```
- **What goes wrong without it:** every method starts with `if (state == IDLE) ... else if (state == HAS_MONEY) ...`. One missed branch and the machine dispenses for free.
- **State classes vs an enum (A3):** use classes when the **behaviour** differs per state (vending machine). Use an enum with `canMoveTo()` when states are just labels with allowed transitions (download status, ride status).

### B3. Decorator
**Picture:** gift wrapping. Each layer adds something, and the gift inside doesn't know or care.
**Use when:** optional add-ons are combined freely and each adds its own behaviour, **or** you want to add behaviour (metrics, logging, retry) around an existing interface without editing it.

```java
interface Beverage {
    long cost();
    String description();
}
class Espresso implements Beverage {
    public long cost() { return 120; }
    public String description() { return "Espresso"; }
}

abstract class AddOn implements Beverage {           // IS a Beverage and HAS a Beverage
    protected final Beverage inner;
    AddOn(Beverage inner) { this.inner = inner; }
}
class Milk extends AddOn {
    Milk(Beverage inner) { super(inner); }
    public long cost() { return inner.cost() + 20; }
    public String description() { return inner.description() + " + Milk"; }
}
class ExtraShot extends AddOn {
    ExtraShot(Beverage inner) { super(inner); }
    public long cost() { return inner.cost() + 40; }
    public String description() { return inner.description() + " + Extra shot"; }
}
// usage
Beverage order = new ExtraShot(new Milk(new Espresso()));
System.out.println(order.description() + " = ₹" + order.cost());   // Espresso + Milk + Extra shot = ₹180
```
- **What goes wrong without it:** `EspressoWithMilk`, `EspressoWithMilkAndShot`, ... With *n* add-ons that's 2ⁿ classes.
- **In the deck:** Coffee Machine, Pizza; `MeteredLimiter` (rate limiter follow-up).

### B4. Factory
**Picture:** a restaurant counter. You say "veg burger"; the kitchen decides which recipe to follow. You never walk into the kitchen.
**Use when:** the concrete class is chosen at runtime (from config/input), and that choice would otherwise be repeated wherever objects get created.

```java
interface NotificationSender { void send(String to, String msg); }

class EmailSender implements NotificationSender {
    public void send(String to, String msg) { System.out.println("EMAIL " + to + ": " + msg); }
}
class SmsSender implements NotificationSender {
    public void send(String to, String msg) { System.out.println("SMS " + to + ": " + msg); }
}
enum Channel { EMAIL, SMS }

class SenderFactory {
    static NotificationSender create(Channel channel) {
        switch (channel) {
            case EMAIL: return new EmailSender();
            case SMS:   return new SmsSender();
            default:    throw new IllegalArgumentException("Unsupported channel: " + channel);
        }
    }
}
// usage
NotificationSender sender = SenderFactory.create(Channel.SMS);
sender.send("+91-98xxxxxx", "Your OTP is 4821");
```
- **The point:** the `switch` still exists, but in **one** place. Callers only know the interface.
- **In the deck:** Rate Limiter follow-up Q5 ("limits come from a config file"), notification senders.
- **Don't:** add a factory for a class with only one implementation.

### B5. Observer
**Picture:** a YouTube subscription. The channel posts once and every subscriber is notified. The channel doesn't know who they are.
**Use when:** one event needs several independent reactions, and you want to add new reactions without touching the source.

```java
interface StockListener {
    void onLowStock(String sku, int remaining);
}

class Inventory {
    private final Map<String, Integer> stock = new HashMap<>();
    private final List<StockListener> listeners = new CopyOnWriteArrayList<>();
    private final int threshold;

    Inventory(int threshold) { this.threshold = threshold; }

    void subscribe(StockListener listener) { listeners.add(listener); }

    void add(String sku, int qty) { stock.merge(sku, qty, Integer::sum); }

    void remove(String sku, int qty) {
        int left = stock.merge(sku, -qty, Integer::sum);
        if (left < threshold) {
            for (StockListener l : listeners) l.onLowStock(sku, left);
        }
    }
}
// usage
Inventory inv = new Inventory(5);
inv.subscribe((sku, left) -> System.out.println("Reorder " + sku + ", only " + left + " left"));
inv.subscribe((sku, left) -> System.out.println("Email manager about " + sku));
inv.add("coke", 10);
inv.remove("coke", 7);   // both listeners fire
```
- A listener is a 1-method interface, so you can pass a lambda.
- **Production detail:** wrap each listener call in try/catch so one broken listener doesn't stop the rest. This is the fan-out family's crux.
- **In the deck:** `inventory/AlertListener`, `jobscheduler/JobListener`. A rider being notified when a driver is assigned is another.
- **Don't:** add Observer "in case someone needs it later".

### B6. Builder
**Picture:** ordering at Subway. You add parts step by step, then it gets wrapped.
**Use when:** 4+ constructor parameters, many optional, or you need to validate before the object exists.

```java
class Pizza {
    private final String size;
    private final String crust;
    private final List<String> toppings;

    private Pizza(Builder b) {                       // only the Builder can create a Pizza
        this.size = b.size;
        this.crust = b.crust;
        this.toppings = List.copyOf(b.toppings);
    }
    public String toString() { return size + " " + crust + " " + toppings; }

    static class Builder {
        private String size = "MEDIUM";               // defaults for optional parts
        private String crust = "REGULAR";
        private final List<String> toppings = new ArrayList<>();

        Builder size(String size)   { this.size = size; return this; }    // return this = chaining
        Builder crust(String crust) { this.crust = crust; return this; }
        Builder topping(String t)   { toppings.add(t); return this; }

        Pizza build() {
            if (toppings.size() > 5) throw new IllegalStateException("Max 5 toppings");
            return new Pizza(this);
        }
    }
}
// usage
Pizza p = new Pizza.Builder().size("LARGE").topping("Olives").topping("Paneer").build();
System.out.println(p);   // LARGE REGULAR [Olives, Paneer]
```
- **Builder or Decorator for pizza?** Builder when you assemble a fixed object once. Decorator (B3) when each add-on brings its **own behaviour** (price, description) and new add-ons keep appearing.

### B7. Singleton
**Picture:** a country has one president. Everyone refers to the same one.
**Use when:** exactly one instance must exist, such as a hardware controller or shared config. **Preferred:** create it once in `main` and pass it in. A global `getInstance()` hides the dependency and makes tests share state.

```java
class AppConfig {
    private AppConfig() {}                                     // nobody else can call new

    private static class Holder {                              // loaded on the first getInstance() call
        static final AppConfig INSTANCE = new AppConfig();     // the JVM makes this thread-safe
    }
    static AppConfig getInstance() { return Holder.INSTANCE; }
}
```
Interviewers like asking for the **double-checked locking** version:
```java
class Registry {
    private static volatile Registry instance;                 // volatile: never see a half-built object
    private Registry() {}

    static Registry getInstance() {
        if (instance == null) {                                // 1st check: skip the lock if already built
            synchronized (Registry.class) {
                if (instance == null) {                        // 2nd check: another thread may have built it
                    instance = new Registry();
                }
            }
        }
        return instance;
    }
}
```
The simplest correct version is `enum Registry { INSTANCE; }`.
**Say:** *"One instance, created at startup and injected, so tests can pass a fake."*

---

## Know briefly

### B8. Facade
**Picture:** a hotel reception desk. One desk; behind it, housekeeping, billing and the kitchen.
**What it is:** your main service class (`RateLimiter`, `BookingSystem`, `ParkingLot`). Callers use 2–3 methods; the service coordinates the internal objects. Every problem in this deck already has one.
**Say:** just call it "the service class". Naming it Facade is fine, but don't spend interview time on it.

### B9. Adapter
**Picture:** a travel plug adapter. Your Indian plug, a UK socket; the adapter converts the shape.
**Use when:** a third-party or legacy class does the right job but has the wrong method shape for your interface.

```java
interface PaymentProcessor { boolean pay(String orderId, long amountRupees); }   // OUR interface

class RazorpayClient {                                   // third-party SDK: we can't change it
    String createCharge(long amountPaise, String reference) { return "SUCCESS"; }
}

class RazorpayAdapter implements PaymentProcessor {
    private final RazorpayClient client;
    RazorpayAdapter(RazorpayClient client) { this.client = client; }

    public boolean pay(String orderId, long amountRupees) {
        String status = client.createCharge(amountRupees * 100, orderId);   // convert units + names
        return "SUCCESS".equals(status);
    }
}
// usage
PaymentProcessor processor = new RazorpayAdapter(new RazorpayClient());
System.out.println(processor.pay("ORD-1", 499));   // true
```
- **In the deck:** `notification/sender/*`, `paymentgateway/processor/*`. Use it whenever a follow-up says "integrate with an external X".

### B10. Chain of Responsibility
**Picture:** support escalation. L1 tries; if they can't solve it, it goes to L2, then to a manager.
**Use when:** a request goes through **ordered** handlers, and each handles it, partly handles it, or passes it on.

```java
class NoteDispenser {
    private final int noteValue;
    private final NoteDispenser next;                   // null = end of the chain

    NoteDispenser(int noteValue, NoteDispenser next) {
        this.noteValue = noteValue;
        this.next = next;
    }

    void dispense(int amount) {
        int count = amount / noteValue;
        int rest = amount % noteValue;
        if (count > 0) System.out.println(count + " x ₹" + noteValue);
        if (rest > 0) {
            if (next == null) throw new IllegalArgumentException("Cannot dispense ₹" + rest);
            next.dispense(rest);
        }
    }
}
// usage
NoteDispenser atm = new NoteDispenser(500, new NoteDispenser(200, new NoteDispenser(100, null)));
atm.dispense(1800);   // 3 x ₹500, 1 x ₹200, 1 x ₹100
```
- **Logger levels:** the Amazon guide mentions Chain of Responsibility for log levels. A simple `if (level >= threshold)` check is usually enough. Use the chain only when **each level routes to a different handler**, and say that trade-off out loud.

---

## B11. Patterns people mix up

| Pair | The difference |
|---|---|
| **Strategy vs State** | Strategy is **chosen by the caller** and usually stays fixed. State **changes itself** as actions happen. |
| **Decorator vs Strategy** | Decorator **wraps and adds** to behaviour, and stacks. Strategy **replaces** one algorithm with another. |
| **Decorator vs Adapter** | Both wrap an object. Adapter **changes the interface** to fit yours. Decorator keeps the same interface and **adds behaviour**. |
| **Builder vs Decorator** (pizza) | Builder assembles a **fixed** object once. Decorator lets each add-on carry its **own behaviour** and new add-ons keep coming. |
| **Factory vs Builder** | Factory decides **which class** to create. Builder assembles **one complex object** step by step. |
| **Observer vs Chain** | Observer: **everyone** gets the event. Chain: handlers **in order**, each may stop it. |
| **Facade vs Adapter** | Facade **simplifies many** objects. Adapter **converts one**. |

## B12. Which patterns for which Amazon problem

| Problem | Patterns that earn their place | Note |
|---|---|---|
| Rate Limiter | Strategy (+ Factory if config, Decorator for metrics) | the crux is the math + per-client lock |
| Movie Ticket / Meeting Room | Facade + Strategy (room allocation, payment) | the crux is **concurrency**, not patterns |
| Ride / Rider Matching | Strategy (matching, pricing) + enum trip state | Observer to notify the rider (follow-up) |
| Vending Machine | **State** | the textbook State question |
| Notification Router | Strategy per channel + Factory | Adapter around provider SDKs; Decorator for retry (follow-ups) |
| Coffee Machine / Pizza | **Decorator** (+ Builder for pizza) | requirements get added mid-interview |
| Amazon Locker / Parking Lot | Strategy (slot choice, pricing) | the crux is atomic slot claim |
| Download Manager | enum state + thread pool | producer–consumer |
| Logger | Strategy (formatter, sink) via composition | Singleton discussion likely; Chain of Responsibility only if levels route to different handlers |
| Elevator | Strategy (dispatch) + enum direction/state | |
| Tic-Tac-Toe / Snake & Ladder | plain classes | Strategy for dice/player only as a follow-up |
| Splitwise | Strategy (split types) | the crux is balance math + simplify debts |

---

# Part C — Blank-page template

How a whole LLD answer looks in **one file**. Only one class may be `public` (the one with `main`); the others have no modifier.

```java
import java.util.*;

// 1. Enums: fixed sets of values
enum VehicleType { BIKE, CAR }

// 2. Models: data + the behaviour that belongs to that data
class Spot {
    private final String id;
    private final VehicleType type;
    Spot(String id, VehicleType type) { this.id = id; this.type = type; }
    String getId() { return id; }
    boolean fits(VehicleType v) { return type == v; }
}

// 3. Interface for what varies
interface SpotSelector {
    Optional<Spot> select(List<Spot> freeSpots, VehicleType v);
}

// 4. Implementation(s)
class FirstFitSelector implements SpotSelector {
    public Optional<Spot> select(List<Spot> freeSpots, VehicleType v) {
        for (Spot s : freeSpots) if (s.fits(v)) return Optional.of(s);
        return Optional.empty();
    }
}

// 5. Service: the public API, owns the state, holds the lock
class ParkingLot {
    private final List<Spot> spots;
    private final SpotSelector selector;
    private final Set<String> occupiedSpotIds = new HashSet<>();
    private final Map<String, String> ticketToSpot = new HashMap<>();
    private int nextTicket = 1;

    ParkingLot(List<Spot> spots, SpotSelector selector) {
        this.spots = spots;
        this.selector = selector;
    }

    // one lock for the whole lot: correct and simple. Per-spot locking is the follow-up upgrade.
    synchronized String park(VehicleType v) {
        List<Spot> free = new ArrayList<>();
        for (Spot s : spots) if (!occupiedSpotIds.contains(s.getId())) free.add(s);

        Spot spot = selector.select(free, v)
                .orElseThrow(() -> new IllegalStateException("No spot for " + v));
        occupiedSpotIds.add(spot.getId());
        String ticketId = "T" + nextTicket++;
        ticketToSpot.put(ticketId, spot.getId());
        return ticketId;
    }

    synchronized void leave(String ticketId) {
        String spotId = ticketToSpot.remove(ticketId);
        if (spotId == null) throw new IllegalArgumentException("Unknown ticket " + ticketId);
        occupiedSpotIds.remove(spotId);
    }
}

// 6. Demo: proves it works end to end
public class Main {
    public static void main(String[] args) {
        ParkingLot lot = new ParkingLot(
                List.of(new Spot("S1", VehicleType.CAR), new Spot("S2", VehicleType.BIKE)),
                new FirstFitSelector());
        String ticket = lot.park(VehicleType.CAR);
        System.out.println("Parked with " + ticket);   // Parked with T1
        lot.leave(ticket);
    }
}
```

**The order to write it in during the interview** (not top to bottom):
1. The service's **public method signatures** (`park`, `leave`). These are your API, agreed with the interviewer.
2. The **interface** for what varies.
3. The **crux method body** (`park`) in full.
4. Models and enums, filled in as the crux method needs them.
5. `main` demo, if time allows.

---

# Part D — Gotchas interviewers notice

| Gotcha | Fix |
|---|---|
| `==` on `String`/`Integer` compares **references** (it seems to work for small Integers, -128..127) | use `.equals()` |
| `7 / 2` is `3` (integer division) | `7 / 2.0`; round up with `(a + b - 1) / b` |
| `int` overflows past ~2.1 billion | use `long`, and the `L` suffix: `1000L * 60 * 60 * 24 * 365` |
| removing from a list inside a for-each → `ConcurrentModificationException` | `list.removeIf(...)` or `Iterator.remove()` |
| old `switch` without `break` falls into the next case | add `break`, or use arrow `->` cases |
| `compare` written as `a - b` overflows | `Integer.compare(a, b)` / `Long.compare` |
| changing a field of an object that's a `HashMap` key → entry "lost" | make key fields `final` |
| `List.of(...)` / `Arrays.asList(...)` can't grow | `new ArrayList<>(List.of(...))` |
| a getter returning your internal list lets callers change it | return `Collections.unmodifiableList(list)` |
| `double` for money: `0.1 + 0.2 != 0.3` | `long` rupees/paise |
| `synchronized` on a `String` or boxed `Integer` | lock a dedicated object you own |
| plain `HashMap` shared by threads → lost updates or corruption | `ConcurrentHashMap` |
| `volatile int count; count++` is not atomic | `AtomicInteger.incrementAndGet()` |
| `lock.lock()` without `finally { unlock() }` | an exception leaves it locked forever |
