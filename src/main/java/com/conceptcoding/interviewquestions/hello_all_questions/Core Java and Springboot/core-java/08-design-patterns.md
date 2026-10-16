# 08 — Design Patterns (Most Asked) ⭐

> Format: **Say this** = your 30-sec interview answer · **Key points** · **Most asked** (Q → short answer) · **Trap**

Design pattern = proven, reusable solution to a common design problem (GoF). Types: Creational · Structural · Behavioral.

---

## 1. Singleton 🔥🔥

**Say this:** "Singleton makes sure a class has only one instance and gives a global access point to it. I use it for shared things like config, cache or a connection pool. The recipe is a private constructor, a private static instance and a public static `getInstance()`. In practice I prefer an enum or the Bill Pugh holder."

```java
// a) Eager — created at class load
private static final S INSTANCE = new S();

// b) Lazy — NOT thread-safe (two threads can create two objects)
if (instance == null) instance = new S();

// c) Synchronized method — safe but locks on EVERY call (slow)
public static synchronized S getInstance() { ... }

// d) Double-Checked Locking 🔥
private static volatile S instance;               // volatile is a MUST
public static S getInstance() {
    if (instance == null) {                       // 1st check, no lock (fast path)
        synchronized (S.class) {
            if (instance == null) instance = new S();   // 2nd check, with lock
        }
    }
    return instance;
}

// e) Bill Pugh holder — lazy + thread-safe, no locks
private static class Holder { static final S INSTANCE = new S(); }
public static S getInstance() { return Holder.INSTANCE; }

// f) Enum — best
enum S { INSTANCE; void doWork() {} }
```

| Variant | Lazy | Thread-safe | Note |
|---|---|---|---|
| Eager | No | Yes | Created even if unused |
| Lazy | Yes | **No** | Don't use with threads |
| Synchronized method | Yes | Yes | Slow — lock on every call |
| DCL | Yes | Yes | Needs `volatile` |
| Bill Pugh | Yes | Yes | JVM class init is thread-safe; holder loads on first call |
| **Enum** | No | Yes | Reflection + serialization safe 🏆 |

**Most asked**
- **Why `volatile` in DCL?** `new S()` = allocate → initialize → assign reference. Without `volatile`, JIT/CPU can **reorder** to allocate → assign → initialize, so another thread sees a **non-null but half-constructed** object. `volatile` blocks this reordering and makes the write visible to all threads.
- **Why two null checks?** 1st skips the lock once created (speed). 2nd stops a thread that was waiting for the lock from creating a second instance.
- **Why is enum best?** JVM guarantees one instance; reflection can't create enum objects; serialization returns the same constant. Only downside: can't extend a class.

**Breaking Singleton & fixes 🔥**

| Attack | How it breaks | Fix |
|---|---|---|
| Reflection | `setAccessible(true)` calls private constructor | Throw in constructor if instance exists, or use **enum** |
| Serialization | Deserializing creates a new object | Add `readResolve()` returning `INSTANCE` (enum is automatic) |
| Cloning | `clone()` copies it (if `Cloneable`) | Override `clone()` to throw / return `INSTANCE` |

```java
private S() { if (INSTANCE != null) throw new IllegalStateException(); }   // reflection
private Object readResolve() { return INSTANCE; }                           // serialization
@Override protected Object clone() throws CloneNotSupportedException {
    throw new CloneNotSupportedException();                                  // cloning
}
```

**Used in:** `Runtime.getRuntime()`, Spring beans (default scope).

**Trap:** Spring singleton = one bean **per ApplicationContext**; GoF singleton = one instance **per ClassLoader**.

---

## 2. Factory

**Say this:** "Factory hides object creation. The caller asks for a type, and the factory decides which concrete class to create. I use it when the class depends on input or config, so callers don't have `new` and `if-else` everywhere."

```java
interface Notification { void send(); }

class NotificationFactory {
    static Notification create(String type) {
        return switch (type) {
            case "EMAIL" -> new Email();
            case "SMS"   -> new Sms();
            default -> throw new IllegalArgumentException(type);
        };
    }
}
NotificationFactory.create("SMS").send();   // caller never writes new Sms()
```

**Used in:** `Calendar.getInstance()`, `NumberFormat.getInstance()`, `List.of()`; Spring `BeanFactory`, `FactoryBean`.

**Most asked**
- **Factory vs Abstract Factory?** Factory creates **one product**; Abstract Factory is a "factory of factories" that creates a **family of related products** (e.g. WindowsButton + WindowsCheckbox).
- **Simple Factory vs Factory Method?** Above is Simple Factory (static method). GoF Factory Method lets **subclasses** override the creation method. Both are fine to call "Factory" in interviews.

---

## 3. Builder 🔥

**Say this:** "Builder creates a complex object step by step. I use it when a class has many optional fields — it fixes the telescoping constructor problem, reads clearly, and gives an immutable object."

```java
class User {
    private final String name; private final int age;
    private User(Builder b) { name = b.name; age = b.age; }

    static class Builder {
        private final String name; private int age;
        Builder(String name) { this.name = name; }            // required
        Builder age(int age) { this.age = age; return this; } // optional
        User build() { return new User(this); }
    }
}
User u = new User.Builder("Navam").age(25).build();
```

**Used in:** `StringBuilder`, `HttpRequest.newBuilder()`, `Stream.builder()`; Lombok `@Builder`.

**Most asked**
- **Builder vs constructor?** Builder = named steps, optional fields, immutable result. A 6+ param constructor is error-prone (easy to swap two `String` args).
- **Where do you validate?** In `build()` — before creating the object.

---

## 4. Strategy 🔥

**Say this:** "Strategy puts each algorithm in its own class behind a common interface, so we can swap them at runtime. I use it to replace long `if-else` or `switch` blocks — like payment types or discount rules. It follows Open/Closed: add a new strategy without touching existing code."

```java
interface PaymentStrategy { void pay(int amt); }
class UpiPayment  implements PaymentStrategy { public void pay(int amt) { /* UPI */ } }
class CardPayment implements PaymentStrategy { public void pay(int amt) { /* Card */ } }

class Checkout {
    private final PaymentStrategy strategy;
    Checkout(PaymentStrategy s) { this.strategy = s; }
    void pay(int amt) { strategy.pay(amt); }
}
new Checkout(new UpiPayment()).pay(500);
```

**Used in:** `Comparator` passed to `sort()`; in Spring, inject `Map<String, PaymentStrategy>` and pick by key.

**Most asked**
- **Strategy vs Template Method?** Strategy = **composition**, swaps the **whole algorithm** at runtime. Template Method = **inheritance**, fixed skeleton in parent, subclasses change only **some steps** (e.g. `JdbcTemplate`, `HttpServlet`).

---

## 5. Observer

**Say this:** "Observer is a one-to-many relationship — when the subject changes, all its observers are notified automatically. The subject doesn't need to know who they are, so it gives loose coupling. I use it for events and notifications."

```java
interface Subscriber { void update(String msg); }
class Channel {
    private final List<Subscriber> subs = new ArrayList<>();
    void subscribe(Subscriber s) { subs.add(s); }
    void upload(String video) { subs.forEach(s -> s.update(video)); }
}
channel.subscribe(msg -> System.out.println("Got: " + msg));
```

**Used in:** Event listeners, Spring `ApplicationEventPublisher` + `@EventListener`, pub-sub (Kafka at system level).

**Trap:** `java.util.Observer` / `Observable` are **deprecated since Java 9**. Unremoved observers = memory leak.

---

## 6. Proxy 🔥

**Say this:** "Proxy is a substitute object that controls access to the real one — it adds logic before or after the real call. Common uses are logging, security, caching, lazy loading and transactions. Spring AOP, `@Transactional` and `@Cacheable` all work through proxies."

```java
class LoggingProxy implements Service {
    private final Service real;
    LoggingProxy(Service real) { this.real = real; }
    public void run() { log("before"); real.run(); log("after"); }
}
```

**Used in:** Spring AOP, `@Transactional`, `@Cacheable`, `@Async`; Hibernate lazy loading.

**Most asked**
- **JDK dynamic proxy vs CGLIB?** JDK proxy works only with **interfaces**; CGLIB creates a **subclass** (so works on classes, but not on `final` classes/methods). Spring Boot uses CGLIB by default.
- **Self-invocation trap 🔥:** Calling a `@Transactional` method from another method **in the same class** skips the proxy → no transaction. Fix: move it to another bean (or inject self).

---

## 7. Decorator

**Say this:** "Decorator adds behavior to an object at runtime by wrapping it in another object with the same interface. Wrappers can be stacked in any combination, so we avoid a subclass for every combination. Java I/O is built on this."

```java
interface Coffee { int cost(); }
class Basic implements Coffee { public int cost() { return 50; } }
class Milk implements Coffee {
    private final Coffee c;
    Milk(Coffee c) { this.c = c; }
    public int cost() { return c.cost() + 20; }
}
new Milk(new Milk(new Basic())).cost();   // 90
```

**Used in:** `new BufferedReader(new FileReader("a.txt"))`, `Collections.unmodifiableList()`.

**Most asked**
- **Decorator vs Proxy?** Same structure (wrap with same interface), different intent: Decorator **adds features** and is stacked by the client; Proxy **controls access** and the client usually doesn't know it's there.

---

## 8. Adapter

**Say this:** "Adapter converts one interface into another that the client expects, so incompatible classes can work together without changing either. I use it to plug in old or third-party code — like wrapping a vendor payment SDK behind our own interface."

```java
interface PaymentGateway { void pay(int amountInr); }
class OldPayPalApi { void sendMoney(double usd) {} }     // incompatible

class PayPalAdapter implements PaymentGateway {
    private final OldPayPalApi api = new OldPayPalApi();
    public void pay(int amountInr) { api.sendMoney(amountInr / 83.0); }  // translate
}
```

**Used in:** `Arrays.asList()` (array → List), `InputStreamReader` (bytes → chars); Spring MVC `HandlerAdapter`.

---

## Pattern → Real Example

| Pattern | JDK / Spring example |
|---|---|
| Singleton | `Runtime.getRuntime()`, Spring beans |
| Factory | `Calendar.getInstance()`, `BeanFactory` |
| Builder | `StringBuilder`, Lombok `@Builder` |
| Strategy | `Comparator` |
| Observer | `@EventListener` |
| Proxy | Spring AOP, `@Transactional`, Hibernate lazy loading |
| Decorator | `BufferedReader`, `Collections.unmodifiableList()` |
| Adapter | `Arrays.asList()`, `InputStreamReader` |
| Template Method | `JdbcTemplate`, `HttpServlet` |
| Chain of Responsibility | Servlet `FilterChain`, Spring Security filters |

---

## 📌 Quick Revision
- **Singleton** = one instance. DCL needs **`volatile`** (stops reordering → no half-built object). Best = **enum** or **Bill Pugh**.
- Break singleton: reflection (constructor check / enum), serialization (`readResolve`), cloning (override `clone`). Spring singleton = per context.
- **Factory** = hide `new`, decide the class. Abstract Factory = family of products.
- **Builder** = many optional fields, step by step, immutable.
- **Strategy** = swap algorithm at runtime (composition). Template Method = fixed steps (inheritance).
- **Observer** = notify subscribers (`@EventListener`).
- **Proxy** = control access → Spring AOP / `@Transactional`. JDK proxy (interface) vs CGLIB (subclass). Watch self-invocation.
- **Decorator** = add behavior by wrapping (Java I/O). **Adapter** = convert interface.
