# 07 — Multithreading & Concurrency 🔥🔥

> Format: **Say this** = your 30-sec interview answer · **Key points** · **Most asked** (Q → short answer) · **Trap**

---

## 1. Process vs Thread & Creating Threads

**Say this:** "A process is a running program with its own memory. A thread is a lightweight unit inside a process — threads share the heap but each has its own stack. We create a thread by passing a `Runnable` or `Callable` to a `Thread` or, better, to an `ExecutorService`."

| Process | Thread |
|---|---|
| Own memory space | Shares heap with other threads |
| Heavy to create/switch | Light |
| Talk via IPC | Talk via shared memory (needs sync) |

**Most asked**
- **Runnable vs Callable?** `Runnable.run()` returns nothing, can't throw checked exceptions. `Callable.call()` returns a value (via `Future`) and can throw.
- **Extend Thread or implement Runnable?** Runnable — Java has single inheritance, and it separates the task from the thread.
- **`start()` vs `run()`?** `start()` creates a new thread that calls `run()`. Calling `run()` directly just runs it on the current thread. Calling `start()` twice → `IllegalThreadStateException`.
- **`join()`?** Waits for that thread to finish. **Daemon thread?** Background thread (e.g., GC) — JVM exits without waiting for it.

---

## 2. Thread Lifecycle 🔥

**Say this:** "A thread is NEW after creation, RUNNABLE after `start()`, then can go to BLOCKED (waiting for a lock), WAITING (`wait()`/`join()` without timeout) or TIMED_WAITING (`sleep`, `wait(ms)`), and finally TERMINATED when `run()` ends."

```
NEW → start() → RUNNABLE ──run() ends──→ TERMINATED
                  ↕  BLOCKED        (waiting for monitor lock)
                  ↕  WAITING        (wait(), join(), park())
                  ↕  TIMED_WAITING  (sleep(ms), wait(ms), join(ms))
```

---

## 3. sleep() vs wait() 🔥

| `sleep()` | `wait()` |
|---|---|
| `Thread` class (static) | `Object` class |
| **Keeps** the lock | **Releases** the lock |
| No `synchronized` needed | Must be inside `synchronized` (else `IllegalMonitorStateException`) |
| Wakes after time | Wakes on `notify()`/`notifyAll()` (or timeout) |

**Most asked**
- **Why are `wait()/notify()` in Object, not Thread?** Because the lock (monitor) belongs to the object — threads wait on an object's lock, and any object can be a lock.
- **Why call `wait()` in a `while` loop, not `if`?** Spurious wakeups — a thread can wake up without notify, or another thread may have changed the condition first. Re-check before continuing.
- **`notify()` vs `notifyAll()`?** Wakes one random waiter vs all. Prefer `notifyAll()` — `notify()` may wake the "wrong" thread and the signal is lost (lost wakeup).

---

## 4. synchronized 🔥

**Say this:** "`synchronized` makes sure only one thread runs a critical section at a time for a given lock, and it also guarantees visibility — changes made inside are seen by the next thread that takes the same lock. It's reentrant, so a thread holding the lock can enter it again."

```java
synchronized void inc() { count++; }            // lock = this
static synchronized void s() { }                // lock = Counter.class
void m() { synchronized (lock) { count++; } }   // lock = lock object (smaller scope, better)
```

**Most asked**
- **Method vs block?** Block locks only the needed code and lets you choose the lock object → less contention.
- **Object lock vs class lock?** Instance methods lock `this` (per object). Static methods lock the `Class` object (one for all). They don't block each other.
- **Can two threads call two different synchronized methods on the same object?** No — same lock (`this`). Different objects → yes.

**Trap:** Synchronizing on a `String` literal or boxed `Integer` — they're shared/cached, so unrelated code may lock the same object.

---

## 5. volatile & Java Memory Model 🔥

**Say this:** "Each thread may cache variables in CPU cache/registers, so one thread's write may not be seen by another. `volatile` guarantees visibility — reads/writes go to main memory — and prevents reordering around it. But it does NOT make compound actions like `count++` atomic."

```java
volatile boolean running = true;
// Thread A: while (running) { ... }   Thread B: running = false;  → A sees it ✅

volatile int count;
count++;   // ❌ still a race: read → add → write (3 steps)
```

**3 problems in multithreading:** **Visibility** (other threads see my write) · **Atomicity** (operation can't be split) · **Ordering** (no harmful reordering).

| | Visibility | Atomicity | Ordering | Blocking |
|---|---|---|---|---|
| `volatile` | ✅ | ❌ | ✅ | No |
| `synchronized` / locks | ✅ | ✅ | ✅ | Yes |
| `AtomicInteger` | ✅ | ✅ (single var, CAS) | ✅ | No |

**Happens-before** (if A happens-before B, B sees A's writes):
- Unlock of a lock → later lock of the same lock.
- Write to a `volatile` → later read of it.
- `thread.start()` → actions in that thread; thread's actions → `join()` returning.

**Most asked**
- **When to use volatile?** Status flags, one writer + many readers, and the `instance` field in double-checked locking singleton.
- **Why volatile in double-checked locking?** Without it, another thread may see a partly constructed object (reordering).

---

## 6. Race Condition & Deadlock 🔥

**Say this:** "A race condition is when the result depends on the timing of threads touching shared data — like two threads doing `count++` and losing an update. A deadlock is when two threads each hold a lock and wait forever for the other's lock."

```java
// Thread 1                     // Thread 2
synchronized (A) {              synchronized (B) {
    synchronized (B) { }            synchronized (A) { }   // 💀 each waits for the other
}                               }
```

**4 Coffman conditions** (all needed for deadlock): Mutual exclusion · Hold and wait · No preemption · Circular wait.

**Prevention**
- **Lock ordering** — always take locks in the same global order (breaks circular wait).
- **`tryLock(timeout)`** — give up and release if you can't get the lock.
- Keep lock scope small; avoid calling unknown code while holding a lock.

**Detect:** `jstack <pid>` (or thread dump) prints "Found one Java-level deadlock".

- **Livelock:** threads aren't blocked but keep reacting to each other and make no progress.
- **Starvation:** a thread never gets CPU/lock because others keep taking it (fix: fair lock).

---

## 7. Locks 🔥

**Say this:** "`java.util.concurrent.locks` gives more control than `synchronized`: `tryLock` with timeout, interruptible locking, fairness, multiple conditions, and read-write locks. The cost is that we must unlock manually in `finally`."

**Reentrancy:** the same thread can acquire a lock it already holds (a hold count goes up/down). Without it, a synchronized method calling another synchronized method on the same object would deadlock itself. Both `synchronized` and `ReentrantLock` are reentrant.

| `synchronized` | `ReentrantLock` |
|---|---|
| Auto release | Must `unlock()` in `finally` |
| No timeout | `tryLock()` / `tryLock(timeout)` |
| Not interruptible while waiting | `lockInterruptibly()` |
| Always unfair | Fair or unfair |
| One wait-set (`wait/notify`) | Multiple `Condition`s |

```java
Lock lock = new ReentrantLock();
if (lock.tryLock(1, TimeUnit.SECONDS)) {
    try { transfer(); } finally { lock.unlock(); }
} else { /* couldn't get lock → retry/fail, no deadlock */ }
```

- **Fair vs unfair:** fair (`new ReentrantLock(true)`) gives the lock to the longest-waiting thread → no starvation but slower. Unfair (default) allows barging → better throughput.
- **Condition:** `lock.newCondition()` → `await()` / `signal()` / `signalAll()`. Like `wait/notify` but you can have many per lock (e.g., `notFull`, `notEmpty`).
- **ReadWriteLock** (`ReentrantReadWriteLock`): many readers OR one writer at a time. Great for read-heavy data. Can downgrade write→read, but **can't upgrade** read→write (deadlocks).
- **StampedLock** (Java 8): adds **optimistic read** — read without locking, then `validate(stamp)`; if a write happened, retry with a real read lock. **Not reentrant**, no Conditions.
- **Optimistic vs pessimistic:** Pessimistic = lock first, then work (`synchronized`, `SELECT ... FOR UPDATE`). Optimistic = no lock, check for conflict at the end and retry (CAS, `@Version` in JPA). Optimistic wins when conflicts are rare.

| Situation | Use |
|---|---|
| Simple mutual exclusion | `synchronized` |
| Need timeout / interrupt / fairness / many conditions | `ReentrantLock` |
| Many reads, few writes | `ReentrantReadWriteLock` |
| Very read-heavy, max performance | `StampedLock` (optimistic read) |
| Single counter/variable | `AtomicInteger` / `LongAdder` |

---

## 8. Atomic Classes & CAS 🔥

**Say this:** "Atomic classes like `AtomicInteger` give thread-safe operations on a single variable without locks, using CAS — Compare-And-Swap. CAS says: set the value to new only if it's still the expected old value; otherwise retry. It's a CPU instruction, so it's fast and non-blocking."

```java
AtomicInteger count = new AtomicInteger();
count.incrementAndGet();          // atomic count++
count.compareAndSet(5, 6);        // CAS: if 5 → set 6

// how incrementAndGet works inside (retry loop, no lock):
do { cur = get(); next = cur + 1; } while (!compareAndSet(cur, next));
```

**Most asked**
- **ABA problem?** Value goes A→B→A, CAS thinks nothing changed. Fix: `AtomicStampedReference` (version stamp).
- **AtomicLong vs LongAdder?** Under high contention, `LongAdder` is faster — it spreads updates across cells and sums on read.
- **CAS downside?** Busy retry under heavy contention wastes CPU; works for one variable only.

---

## 9. Thread Pools 🔥🔥

**Say this:** "Creating a thread per task is expensive and unbounded. A thread pool keeps reusable threads that pick tasks from a queue, so we control the number of threads and save creation cost. In production I create a `ThreadPoolExecutor` with a bounded queue and a rejection policy."

```java
new ThreadPoolExecutor(
    corePoolSize,          // threads kept alive
    maximumPoolSize,       // max threads
    keepAliveTime, unit,   // idle time before extra threads die
    new ArrayBlockingQueue<>(100),   // bounded work queue
    threadFactory,         // name your threads
    new ThreadPoolExecutor.CallerRunsPolicy());
```

**Task flow** (most asked 🔥)
```
New task → threads < core?  → create new thread
         → queue not full?  → add to queue
         → threads < max?   → create extra thread
         → else             → REJECT
```

**Worked example** (asked often) — core=2, max=4, queue=2:
- Tasks 1–2 → 2 core threads start.
- Tasks 3–4 → go into the queue (queue full).
- Tasks 5–6 → queue full, so 2 extra threads start (pool = 4).
- Task 7 → pool at max + queue full → **rejected**.

| Rejection policy | Behavior |
|---|---|
| `AbortPolicy` (default) | Throws `RejectedExecutionException` |
| `CallerRunsPolicy` | Caller thread runs it (natural back-pressure) |
| `DiscardPolicy` | Silently drops |
| `DiscardOldestPolicy` | Drops oldest queued task, retries |

**Most asked**
- **Why avoid `Executors.newFixedThreadPool` / `newCachedThreadPool`?** Fixed (and single) uses an **unbounded** `LinkedBlockingQueue` → tasks pile up → OOM. Cached allows **unbounded threads** → too many threads → OOM.
- **Pool size?** CPU-bound ≈ number of cores (+1). IO-bound ≈ `cores × (1 + wait time / compute time)` — more threads since they mostly wait.
- **`execute()` vs `submit()`?** `execute` → no result, exception goes to the thread's handler. `submit` → returns `Future`; exception is hidden until `future.get()` throws `ExecutionException`.
- **`shutdown()` vs `shutdownNow()`?** `shutdown` = no new tasks, finish queued ones. `shutdownNow` = interrupt running, return pending list. Follow with `awaitTermination()`.
- **ForkJoinPool?** For divide-and-conquer tasks (`RecursiveTask`). Each worker has its own queue; idle workers **steal** tasks from busy ones (work-stealing). It powers parallel streams and CompletableFuture's default pool.

**Trap:** With an unbounded queue, `maximumPoolSize` is never used — extra threads are created only when the queue is full.

---

## 10. Future vs CompletableFuture 🔥

**Say this:** "`Future` is the result of an async task, but `get()` blocks and you can't chain or combine. `CompletableFuture` (Java 8) lets us chain steps, combine multiple futures and handle errors without blocking — like a pipeline of callbacks."

```java
CompletableFuture<User> u = CompletableFuture.supplyAsync(() -> getUser(id), pool);
CompletableFuture<Order> o = CompletableFuture.supplyAsync(() -> getOrder(id), pool);

u.thenApply(User::getEmail)                       // transform
 .thenCompose(email -> sendMailAsync(email))      // chain another async call
 .exceptionally(ex -> { log(ex); return null; }); // fallback on error

u.thenCombine(o, (user, order) -> build(user, order));  // combine two
CompletableFuture.allOf(u, o).join();                   // wait for all
```

| Method | Meaning |
|---|---|
| `thenApply` | map (sync transform) |
| `thenCompose` | flatMap (returns another CF) |
| `thenCombine` | combine two independent CFs |
| `thenAccept` | consume, no result |
| `allOf` / `anyOf` | wait for all / first |
| `exceptionally` / `handle` | recover from error / handle result or error |

**Most asked**
- **thenApply vs thenCompose?** `thenApply` with a CF-returning function gives `CF<CF<T>>`; `thenCompose` flattens it to `CF<T>`.
- **Which thread runs it?** Without an executor → `ForkJoinPool.commonPool()` (shared, sized for CPU). **Pass your own executor** for IO calls.
- **`get()` vs `join()`?** `get` throws checked exceptions; `join` throws unchecked `CompletionException`.
- **Call 3 APIs in parallel and combine?** `supplyAsync` each (own executor) → `allOf(a, b, c).thenApply(v -> build(a.join(), b.join(), c.join()))`. Total time ≈ **slowest call**, not the sum.
- **`thenApply` vs `thenApplyAsync`?** Same thread that completed the previous step vs a pool thread (common pool or your executor).
- **`exceptionally` vs `handle` vs `whenComplete`?** Recover with a fallback value vs handle success **and** error vs just observe/log (doesn't change the result).

---

## 11. CountDownLatch vs CyclicBarrier vs Semaphore 🔥

| | CountDownLatch | CyclicBarrier | Semaphore |
|---|---|---|---|
| Idea | Wait until count hits 0 | N threads wait for each other | N permits for a resource |
| Reusable? | ❌ one-time | ✅ | ✅ |
| Who waits | Others wait for workers | Participants wait together | Threads wait for a permit |
| Methods | `countDown()`, `await()` | `await()` | `acquire()`, `release()` |

- **Latch:** main thread waits till 3 services start.
- **Barrier:** parallel workers sync at the end of each phase.
- **Semaphore:** limit to 10 DB connections / rate limiting. Always `release()` in `finally`.

---

## 12. ThreadLocal

**Say this:** "`ThreadLocal` gives each thread its own copy of a variable, so no sharing and no locking. Used for per-request context like user ID, transaction, or a non-thread-safe `SimpleDateFormat`."

```java
static final ThreadLocal<String> user = new ThreadLocal<>();
try { user.set("navam"); process(); } finally { user.remove(); }
```

**Trap:** In thread pools threads are reused, so values leak into the next request and cause memory leaks → **always `remove()` in `finally`**.

---

## 13. BlockingQueue & Producer–Consumer 🔥

**Say this:** "`BlockingQueue` is a thread-safe queue where `put()` blocks when full and `take()` blocks when empty — so producer-consumer needs no manual locking. In interviews they often ask for the `wait/notify` version too."

**Types:** `ArrayBlockingQueue` (bounded) · `LinkedBlockingQueue` (optionally bounded, default unbounded) · `PriorityBlockingQueue` · `SynchronousQueue` (no capacity, direct handoff).

```java
class Buffer {
    private final Queue<Integer> q = new LinkedList<>();
    private final int cap = 5;

    synchronized void produce(int x) throws InterruptedException {
        while (q.size() == cap) wait();   // WHILE, not if
        q.add(x);
        notifyAll();
    }
    synchronized int consume() throws InterruptedException {
        while (q.isEmpty()) wait();
        int x = q.poll();
        notifyAll();
        return x;
    }
}
```

---

## 14. Print Odd/Even with Two Threads 🔥

**Say this:** "Both threads share a counter and one lock. Each thread prints only when it's its turn, otherwise it waits; after printing it notifies the other."

```java
class OddEven {
    private int n = 1;
    private final int max = 10;

    synchronized void print(boolean odd) throws InterruptedException {
        while (n <= max) {
            if ((n % 2 == 1) == odd) {
                System.out.println(Thread.currentThread().getName() + ": " + n++);
                notify();
            } else wait();
        }
        notify();   // let the other thread exit
    }
}
// new Thread(() -> o.print(true), "Odd").start();  new Thread(() -> o.print(false), "Even").start();
// (wrap in try/catch for InterruptedException)
```

---

## 15. Virtual Threads (Java 21) 🔥

**Say this:** "Virtual threads are lightweight threads managed by the JVM, not the OS. Millions of them run on a few OS 'carrier' threads. When a virtual thread blocks on IO, it's unmounted and the carrier runs another one — so we get high throughput with simple blocking code."

| Platform thread | Virtual thread |
|---|---|
| 1:1 with OS thread | Many on few carriers |
| ~1 MB stack, costly | Few KB, cheap |
| Thousands | Millions |

```java
try (var ex = Executors.newVirtualThreadPerTaskExecutor()) { ex.submit(() -> callApi()); }
```

**Most asked**
- **When to use?** IO-bound work (HTTP, DB calls). No gain for CPU-bound work.
- **Should we pool them?** No — they're cheap; create one per task. Limit resources with a `Semaphore` instead.
- **Pinning?** In Java 21, blocking inside `synchronized` pins the virtual thread to its carrier (can't unmount) → use `ReentrantLock`. (Fixed in Java 24.)
- **Spring Boot?** 3.2+: `spring.threads.virtual.enabled=true`.

---

## 16. Thread-safe Collections

| Need | Use |
|---|---|
| Map | `ConcurrentHashMap` (no null key/value) |
| List, read-heavy | `CopyOnWriteArrayList` |
| Queue | `ConcurrentLinkedQueue`, `BlockingQueue` |
| Sorted map | `ConcurrentSkipListMap` |

**Trap:** `Collections.synchronizedMap` / `Hashtable` lock the whole map → slow; prefer `ConcurrentHashMap`.

---

## 📌 Quick Revision
- Thread = shares heap, own stack. `start()` makes a new thread, `run()` doesn't. Callable returns value + throws.
- States: NEW → RUNNABLE → BLOCKED / WAITING / TIMED_WAITING → TERMINATED.
- `sleep` keeps lock, `wait` releases it. `wait/notify` in Object (lock is per object). Always `wait()` in a **while**.
- `synchronized` = mutual exclusion + visibility, reentrant. Instance → `this`, static → `Class`.
- `volatile` = visibility + no reordering, **not atomic**. `count++` needs `synchronized`/Atomic.
- Deadlock = 4 Coffman conditions. Fix: **lock ordering**, `tryLock(timeout)`. Detect: `jstack`.
- `ReentrantLock` = tryLock, interruptible, fair, Conditions. RW lock = many readers / one writer. StampedLock = optimistic read.
- Atomic = **CAS**, lock-free. ABA → stamped ref. High contention → `LongAdder`.
- Pool flow: **core → queue → max → reject**. Bounded queue + `CallerRunsPolicy`. Avoid `Executors` factories in prod.
- CompletableFuture: `thenApply` map, `thenCompose` flatMap, `thenCombine`, `allOf`, `exceptionally`. Pass own executor.
- Latch = one-time, Barrier = reusable, Semaphore = permits. ThreadLocal → `remove()` in pools.
- Virtual threads = IO-bound, don't pool, avoid `synchronized` pinning (Java 21).

**Memory hook:** "**V**olatile **S**ees, **S**ynchronized **S**eals, **A**tomic **S**waps" — visibility, locking, CAS.
