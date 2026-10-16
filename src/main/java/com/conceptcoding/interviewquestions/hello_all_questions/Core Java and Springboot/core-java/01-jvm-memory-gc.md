# 01 — JVM, Memory & Garbage Collection 🔥

> Format: **Say this** = your 30-sec interview answer · **Key points** · **Most asked** (Q → short answer) · **Trap**

---

## 1. JDK vs JRE vs JVM

**Say this:** "JVM runs bytecode and converts it to machine code. JRE is JVM plus core libraries — enough to run Java. JDK is JRE plus dev tools like `javac` — needed to write Java. Bytecode is platform independent, the JVM is not — that's how Java is 'write once, run anywhere'."

**Most asked**
- **Is Java compiled or interpreted?** Both — `javac` compiles to bytecode; JVM interprets it and the **JIT** compiles hot (frequently run) code to native code for speed.
- **Is JVM platform independent?** No. Bytecode is.

---

## 2. JVM Memory Areas 🔥

**Say this:** "Heap stores all objects and is shared by all threads — GC cleans it. Each thread has its own Stack that stores method frames, local variables and references. Metaspace (Java 8+, native memory) stores class metadata — it replaced PermGen."

```java
void drive() {
    int x = 10;              // x → Stack
    Car c = new Car();       // reference c → Stack, Car object → Heap
}
```

| Heap | Stack |
|---|---|
| Objects, arrays, String pool | Locals, references, method calls |
| Shared by all threads | One per thread (thread-safe) |
| Cleaned by GC | Freed when method returns |
| Full → `OutOfMemoryError` | Full → `StackOverflowError` |

**Most asked**
- **Heap vs Stack?** Table above.
- **PermGen vs Metaspace?** PermGen = fixed size inside heap (frequent OOM). Metaspace = native memory, grows automatically.
- **Where are static variables?** In the heap (with the Class object) since Java 8.
- **Are local variables thread-safe?** Yes — each thread has its own stack.

---

## 3. Class Loaders

**Say this:** "Class loaders load `.class` files lazily on first use. There are three: Bootstrap (core Java like `String`), Platform, and Application (our classes). They follow **parent delegation** — a loader first asks its parent, and loads the class only if the parent can't. This gives security (no fake `java.lang.String`) and avoids loading a class twice."

**Most asked**
- **ClassNotFoundException vs NoClassDefFoundError?** Exception when loading dynamically (`Class.forName`) fails vs Error when a class present at compile time is missing at runtime.

---

## 4. String Pool 🔥

**Say this:** "String literals are stored once in the String pool (in the heap) and reused. `new String("a")` always creates a new heap object."

```java
String a = "hi", b = "hi", c = new String("hi");
a == b;          // true  (same pool object)
a == c;          // false (different objects)
a.equals(c);     // true  (same content)
a == c.intern(); // true
```

**Most asked**
- **Objects created by `new String("hi")`?** Up to 2 — pool (if not there) + heap.
- **`==` vs `equals()`?** Reference vs content. Always use `equals()` for strings.

---

## 5. Pass-by-Value 🔥

**Say this:** "Java is always pass-by-value. For objects, the value passed is a copy of the reference — so a method can change the object's fields, but can't make the caller's variable point to a new object."

```java
void change(Car car) {
    car.speed = 100;     // ✅ changes the original object
    car = new Car();     // ❌ only the local copy changes
}
```

---

## 6. Garbage Collection 🔥

**Say this:** "GC automatically frees heap memory of objects that are no longer reachable from any GC root — like local variables on thread stacks, static fields and active threads. We can't force it; `System.gc()` is only a request."

**Heap generations** (most objects die young):
```
Young Gen: [ Eden | S0 | S1 ]  →  Old Gen
```
1. New objects → **Eden**.
2. Eden full → **Minor GC** → survivors move to S0/S1 (age +1).
3. Survive ~15 minor GCs → promoted to **Old Gen**.
4. Old Gen full → **Major/Full GC** (slower, longer pause).

**How:** Mark (find live objects from GC roots) → Sweep (remove dead) → Compact (remove gaps).
- **Young gen uses copying:** live objects are copied to the empty survivor space (that's why there are **two** survivors — one is always empty). Cheap, because few objects survive.
- **Old gen uses mark-sweep-compact:** most objects there are alive, so copying would be wasteful.

**Stop-the-world (STW)** = all app threads pause during some GC phases. A long pause shows up as latency (a 5 ms request suddenly takes 300 ms). Modern GCs (G1, ZGC) keep pauses short by doing most work alongside the app.

| GC | Use |
|---|---|
| Serial | Small apps, single thread |
| Parallel | Max throughput (batch jobs) |
| **G1** (default since Java 9) | Balanced — splits heap into regions, cleans most-garbage regions first |
| **ZGC** | Very low pauses (< 1 ms), huge heaps |

**Most asked**
- **When is an object eligible for GC?** When no reference chain from a GC root reaches it (set to null, reassigned, method ended, or island of isolation).
- **Minor vs Major GC?** Young gen (fast, frequent) vs Old gen (slow, rare).
- **Can we force GC?** No.
- **Default GC?** G1.
- **Why "Garbage First"?** G1 collects the regions with the most garbage first → most memory freed per pause. You set a pause goal (`-XX:MaxGCPauseMillis=200`).
- **Throughput vs latency?** Parallel GC = max work done, longer pauses (batch jobs). G1/ZGC = short, predictable pauses (APIs).

---

## 7. Memory Leaks & OOM 🔥

**Say this:** "Java can still leak memory when objects are no longer needed but are still referenced — so GC can't remove them."

**Common causes:** static collections that keep growing · caches without eviction · `ThreadLocal` not removed in thread pools · unclosed resources · listeners never unregistered · mutable keys in HashMap.

| Error | Cause |
|---|---|
| `OutOfMemoryError: Java heap space` | Heap full (leak or too small) |
| `OutOfMemoryError: GC overhead limit exceeded` | > 98% time in GC, < 2% freed — usually a leak about to become heap-space OOM |
| `OutOfMemoryError: Metaspace` | Too many classes loaded |
| `StackOverflowError` | Deep/infinite recursion |

**Most asked — "How do you debug a memory leak?"**
1. Check heap usage in GC logs/metrics — keeps rising after each GC → leak.
2. Take a heap dump (`-XX:+HeapDumpOnOutOfMemoryError` or `jmap`).
3. Open in Eclipse MAT / VisualVM → find the biggest objects and who holds them.
4. Fix the reference (remove from collection, add eviction, close resource).

**Key flags:** `-Xms` (initial heap), `-Xmx` (max heap), `-Xss` (thread stack size), `-XX:+HeapDumpOnOutOfMemoryError`, `-Xlog:gc*` (GC logs).

| Tool | Use |
|---|---|
| `jstat` | Live GC stats — is Old Gen filling and never emptying? (→ leak) |
| `jmap` | Heap dump / object count by class |
| `jstack` | Thread dump — deadlocks, stuck threads |
| VisualVM / JFR | Visual profiling: heap, CPU, threads |

---

## 8. Reference Types (short)

Strong (normal, never collected while reachable) > **Soft** (collected when memory is low → caches) > **Weak** (collected at next GC → `WeakHashMap`) > Phantom (cleanup tracking).

---

## 📌 Quick Revision
- JDK = JRE + tools; JRE = JVM + libs. Bytecode is platform independent. JIT compiles hot code.
- **Heap** = objects, shared, GC. **Stack** = locals/references, per thread. **Metaspace** = class metadata.
- Class loaders: Bootstrap → Platform → Application, **parent delegation**.
- String literals → pool; `new String` → new object; compare with `equals()`.
- Java is **always pass-by-value**.
- GC: unreachable from GC roots. **Eden → Survivor → Old**. Minor vs Major. **G1 default**, ZGC low pause.
- Leaks = unneeded objects still referenced. Debug with **heap dump + MAT**.
