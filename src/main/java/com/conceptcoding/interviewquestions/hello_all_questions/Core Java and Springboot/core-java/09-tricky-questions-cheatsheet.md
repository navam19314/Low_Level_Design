# 09 — Tricky Output Questions + Night-Before Cheat Sheet 🔥

> Format: **Part 1** predict the output (cover the answer, guess first) · **Part 2** must-code · **Part 3** night-before cheat sheet · **Part 4** scenario questions

---

# Part 1: Predict the Output

### Strings & Numbers

```java
String s1 = "Java";
String s2 = "Ja" + "va";
System.out.println(s1 == s2);
```
**Answer: `true`** — both are compile-time constants, joined by the compiler → same pool object.

```java
final String a = "Ja";
String b = a + "va";
System.out.println("Java" == b);
```
**Answer: `true`** — `final` + literal = compile-time constant. Remove `final` → `false` (built at runtime → new heap object).

```java
System.out.println(1 + 2 + "3" + 4 + 5);
```
**Answer: `3345`** — left to right: `1+2=3`, then `"33"`, after that it's all string concat.

```java
Integer a = 127, b = 127, c = 128, d = 128;
System.out.println(a == b);
System.out.println(c == d);
```
**Answer: `true`, `false`** — Integer cache is -128 to 127. Outside it, `==` compares different objects.

```java
Integer n = null;
int r = true ? n : 0;
```
**Answer: `NullPointerException`** — the ternary's type is `int`, so `n` is unboxed.

```java
System.out.println(0.1 + 0.2 == 0.3);
```
**Answer: `false`** — floating-point precision (`0.30000000000000004`). Use `BigDecimal` for money.

```java
int i = 5;
i = i++;
System.out.println(i);
```
**Answer: `5`** — old value (5) is saved, `i` becomes 6, then 5 is assigned back.

```java
short s = 1;
s = s + 1;   // line A
s += 1;      // line B
```
**Answer: line A compile error, line B OK** — `s + 1` is `int`; `+=` does an implicit cast.

### OOP & Overloading

```java
void m(Object o) { System.out.println("Object"); }
void m(String s) { System.out.println("String"); }
m(null);
```
**Answer: `String`** — most specific type wins. With `m(String)` + `m(Integer)` → compile error (ambiguous).

```java
void m(long x)    { System.out.println("long"); }
void m(Integer x) { System.out.println("Integer"); }
m(5);
```
**Answer: `long`** — priority: exact → widening → boxing → varargs.

```java
class Parent {
    Parent() { print(); }
    void print() { System.out.println("Parent"); }
}
class Child extends Parent {
    int x = 10;
    void print() { System.out.println("Child " + x); }
}
new Child();
```
**Answer: `Child 0`** — the overridden method runs from the parent constructor before child fields are set.

```java
class P { String name = "P"; static void hi() { System.out.println("P hi"); } }
class C extends P { String name = "C"; static void hi() { System.out.println("C hi"); } }
P obj = new C();
System.out.println(obj.name);
obj.hi();
```
**Answer: `P`, `P hi`** — fields and static methods are hidden, not overridden → reference type decides.

```java
class A { static { System.out.println("static A"); } }
public class Main {
    static { System.out.println("static Main"); }
    public static void main(String[] args) {
        System.out.println("main");
        new A(); new A();
    }
}
```
**Answer: `static Main` → `main` → `static A`** — static block runs once, when the class is first used.

### Exceptions

```java
int test() {
    try { throw new RuntimeException(); }
    finally { return 1; }
}
```
**Answer: returns `1`** — `return` in finally swallows the exception (and overrides any `return` in try).

```java
try { System.exit(0); }
finally { System.out.println("finally"); }
```
**Answer: nothing printed** — the JVM stops; finally never runs.

### Collections

```java
List<Integer> list = new ArrayList<>(List.of(1, 2, 3));
for (Integer n : list) {
    if (n == 2) list.remove(n);
}
System.out.println(list);
```
**Answer: `[1, 3]`, no exception** — removing the second-last element makes `hasNext()` false before the modCount check. Remove `1` → `ConcurrentModificationException`.

```java
List<Integer> list = new ArrayList<>(List.of(10, 20, 30));
list.remove(1);
System.out.println(list);
```
**Answer: `[10, 30]`** — `remove(int index)` beats `remove(Object)`. Use `remove(Integer.valueOf(1))` for value.

```java
class Emp { int id; Emp(int id) { this.id = id; } }   // no equals/hashCode
Set<Emp> set = new HashSet<>();
set.add(new Emp(1)); set.add(new Emp(1));
System.out.println(set.size());
```
**Answer: `2`** — default `equals/hashCode` compare references.

```java
List<Integer> list = Arrays.asList(1, 2, 3);
list.set(0, 9);   // OK
list.add(4);
```
**Answer: `UnsupportedOperationException` on `add`** — fixed-size list backed by the array; `set` works, `add/remove` don't.

### Threads & Streams

```java
Thread t = new Thread(() -> System.out.println(Thread.currentThread().getName()));
t.run();
```
**Answer: `main`** — `run()` is a normal method call; only `start()` creates a new thread.

```java
Stream<Integer> s = Stream.of(1, 2, 3);
s.forEach(System.out::println);
s.forEach(System.out::println);
```
**Answer: `1 2 3`, then `IllegalStateException`** — a stream can be consumed only once.

```java
Stream.of(1, 2, 3).peek(System.out::println);
```
**Answer: nothing printed** — no terminal operation, and streams are lazy.

---

# Part 2: Must-Code

## 1. LRU Cache (HashMap + Doubly Linked List) 🔥
HashMap → O(1) lookup. DLL → O(1) move/remove. Most recent at **head**, least recent at **tail**.

```java
class LRUCache {
    class Node { int key, val; Node prev, next; Node(int k, int v) { key = k; val = v; } }

    private final int capacity;
    private final Map<Integer, Node> map = new HashMap<>();
    private final Node head = new Node(0, 0), tail = new Node(0, 0);   // dummies

    LRUCache(int capacity) { this.capacity = capacity; head.next = tail; tail.prev = head; }

    public int get(int key) {
        Node n = map.get(key);
        if (n == null) return -1;
        remove(n); addFirst(n);                 // mark as recently used
        return n.val;
    }

    public void put(int key, int val) {
        if (map.containsKey(key)) remove(map.remove(key));
        if (map.size() == capacity) {           // evict LRU
            Node lru = tail.prev;
            remove(lru); map.remove(lru.key);
        }
        Node n = new Node(key, val);
        addFirst(n); map.put(key, n);
    }

    private void remove(Node n) { n.prev.next = n.next; n.next.prev = n.prev; }
    private void addFirst(Node n) {
        n.next = head.next; n.prev = head;
        head.next.prev = n; head.next = n;
    }
}
```
- **Shortcut answer:** `LinkedHashMap(cap, 0.75f, true)` + override `removeEldestEntry` → see [05](05-collections.md).
- **Thread-safe?** Make `get/put` `synchronized` (or use a `ReentrantLock`).

## 2. Thread-safe Singleton
DCL with `volatile`, Bill Pugh holder, Enum → see [08 — Design Patterns](08-design-patterns.md).

**Also practice:** producer–consumer & odd-even printing → [07](07-multithreading-concurrency.md) · stream coding questions → [06](06-java8-streams-generics.md).

## 3. Concurrency coding problems (know the idea, 1 line each)
| Problem | Idea |
|---|---|
| Print 1..N in order with N threads | Shared counter + lock; each thread waits (`while`) until `counter % N == myId`, prints, `notifyAll()` |
| Custom thread pool | `BlockingQueue<Runnable>` + N worker threads looping `queue.take().run()` |
| Rate limiter (token bucket) | Tokens refill at a fixed rate; `tryAcquire()` takes one or rejects when empty |
| Dining philosophers | Avoid deadlock with **lock ordering** (always pick the lower-numbered fork first) or a `Semaphore(N-1)` |
| Parallel API calls | `CompletableFuture.supplyAsync` ×3 + `allOf` → time ≈ slowest call ([07](07-multithreading-concurrency.md)) |

---

# Part 3: Night-Before Cheat Sheet

### JVM & GC → [01](01-jvm-memory-gc.md)
- Heap = objects, shared, GC'd. Stack = frames/locals, per thread. Metaspace (Java 8+, native) replaced PermGen.
- Static variables live in the heap (with the Class object) since Java 8.
- Class loaders: Bootstrap → Platform → Application, **parent delegation**.
- Java is always **pass-by-value** (object references are copied).
- Young (Eden, S0, S1) + Old. Minor GC = young, fast. Survive ~15 GCs → promoted.
- Default GC = **G1** (Java 9+). ZGC = sub-ms pauses, huge heaps. `System.gc()` is only a request.
- Eligible for GC = unreachable from GC roots (stack locals, statics, active threads).
- Leaks: static collections, caches without eviction, `ThreadLocal` not removed, unremoved listeners.
- `-Xms` initial heap, `-Xmx` max heap, `-Xss` thread stack size.

### OOP → [02](02-oop-solid.md)
- Overloading = compile time; overriding = runtime. Fields & static methods are **not** polymorphic.
- Overriding: same signature, same/wider access, covariant return, no new/broader checked exceptions.
- Abstract class = state + constructor, single inheritance. Interface = default/static/private (9+) methods, multiple.
- Two interfaces, same default method → class must override (`A.super.m()`). Class method beats interface default.
- SOLID; favor composition over inheritance.

### String & Immutability → [03](03-keywords-string-immutability.md)
- String immutable → pool, security, thread safety, cached hashCode.
- `new String("a")` → up to 2 objects. `intern()` returns the pool copy.
- StringBuilder (fast, not synced) vs StringBuffer (synced). StringBuilder doesn't override `equals`.
- Immutable class: `final` class, `private final` fields, no setters, defensive copies in & out.
- equals/hashCode contract: equal objects → equal hashCodes. Override only `equals` → HashMap/HashSet break.
- Integer cache **-128 to 127**. Unboxing `null` → NPE.
- Init order: static (parent → child, once) → parent instance block + constructor → child instance block + constructor.

### Exceptions → [04](04-exception-handling.md)
- Throwable → Error / Exception → RuntimeException (unchecked).
- Checked = must handle at compile time; unchecked = programming bugs.
- `finally`'s `return` wins and swallows exceptions. finally skipped only on `System.exit` / JVM crash.
- try-with-resources: closes `AutoCloseable` in **reverse** order; close errors become suppressed exceptions.
- Catch child before parent. Log **or** rethrow, never both; wrap with cause.

### Collections → [05](05-collections.md)
- **HashMap:** capacity **16**, load factor **0.75**, threshold 12, doubles on resize. Index = `(n-1) & hash`.
- Bucket → Red-Black tree at **8** nodes if capacity ≥ **64**; back to list at **6**. Worst case O(log n) (Java 8+).
- One `null` key allowed (bucket 0). Mutable key → entry lost after hashCode changes.
- **ConcurrentHashMap (Java 8+):** lock-free reads, **CAS** on empty bucket, **synchronized** on bucket head. No null keys/values.
- ArrayList: default 10, grows **1.5x**. Prefer it over LinkedList almost always.
- HashSet = HashMap with dummy value. TreeMap = Red-Black tree, O(log n), no null key.
- Fail-fast (CME via modCount) vs fail-safe (CHM, CopyOnWriteArrayList). Remove safely → `iterator.remove()` / `removeIf`.
- Don't use `a - b` in comparators (overflow) → `Integer.compare(a, b)`.
- `List.of` truly immutable, no nulls. `Arrays.asList` fixed size. `Set.of(1, 1)` → `IllegalArgumentException`.

### Java 8 & Streams → [06](06-java8-streams-generics.md)
- Functional interface = exactly one abstract method. Predicate / Function / Consumer / Supplier.
- Streams are **lazy** (need a terminal op) and **single-use**.
- `map` 1→1 vs `flatMap` 1→many. `groupingBy` any key vs `partitioningBy` boolean.
- `toMap` with duplicate keys → `IllegalStateException` unless you pass a merge function.
- `orElse` always evaluated vs `orElseGet` lazy. Optional only as a return type.
- Parallel streams use `ForkJoinPool.commonPool()` — avoid for I/O, small data, shared state.
- Generics: type erasure; `List<String>` is not a `List<Object>`; **PECS** = Producer Extends, Consumer Super.
- Java 17/21: records, sealed classes, switch expressions, pattern matching, virtual threads (21).

### Concurrency → [07](07-multithreading-concurrency.md)
- `start()` = new thread; `run()` = same thread. `start()` twice → `IllegalThreadStateException`.
- `sleep` keeps the lock; `wait` releases it. Always `wait()` in a `while` loop (spurious wakeups).
- **`volatile` = visibility + no reordering, NOT atomic.** `count++` is not atomic → `AtomicInteger` / `LongAdder`.
- `synchronized` is reentrant. ReentrantLock adds `tryLock`, timeout, fairness, interruptible, multiple Conditions.
- Deadlock = circular wait → fix with consistent lock ordering or `tryLock` timeout. Detect with `jstack`.
- **Pool flow:** core threads → queue → extra threads up to max → **reject** (Abort default, CallerRuns, Discard, DiscardOldest).
- `Executors.newFixedThreadPool` = unbounded queue (OOM risk); `newCachedThreadPool` = unbounded threads.
- CPU-bound pool ≈ cores; I/O-bound → more threads.
- CompletableFuture: `thenApply` (map) vs `thenCompose` (flatMap); `allOf` to combine; pass your own executor for I/O.
- CountDownLatch (one-time) vs CyclicBarrier (reusable). Semaphore limits N concurrent users.
- ThreadLocal in a pool → always `remove()`. Virtual threads: don't pool them; pinning in `synchronized` on Java 21 (fixed in 24).

### Patterns → [08](08-design-patterns.md)
- **DCL Singleton needs `volatile`** (stops other threads seeing a half-built object). Best = **Enum** (reflection + serialization safe).
- Break singleton: reflection, serialization (fix with `readResolve`), cloning.
- Builder = many optional params. Factory = hide creation. Strategy = swap algorithm (Comparator).
- Decorator adds behavior (Java I/O) vs Proxy controls access (Spring AOP, `@Transactional`).
- Spring uses: Singleton, Factory, Proxy, Template (JdbcTemplate), Observer (events).

---

# Part 4: Scenario Questions

**Q: Memory keeps growing, then OOM. How do you debug?**
Add `-XX:+HeapDumpOnOutOfMemoryError` → open the dump in Eclipse MAT → check the dominator tree for the biggest retained objects. Usually a static collection, a cache without eviction, or a `ThreadLocal` not removed.

**Q: CPU is at 100%. How do you find the cause?**
`top -H -p <pid>` → find the hot thread ID → convert to hex → `jstack <pid>` and match `nid`. Usually an infinite loop, heavy regex, or GC thrashing (check GC logs).

**Q: App hangs, no errors.**
Take 2–3 thread dumps (`jstack`) a few seconds apart. Look for "Found one Java-level deadlock", or many threads BLOCKED/WAITING on the same lock, or all pool threads stuck on a slow external call (add timeouts).

**Q: A HashMap is shared across threads. What can go wrong?**
Lost updates, wrong `size()`, corrupted buckets (Java 7 could even loop forever during resize). Fix → `ConcurrentHashMap` with atomic ops like `merge` / `computeIfAbsent`.
