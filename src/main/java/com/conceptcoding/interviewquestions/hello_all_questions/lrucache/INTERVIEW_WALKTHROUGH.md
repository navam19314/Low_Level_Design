# LRU Cache — 45-min LLD Interview Walkthrough

**Target role:** SDE-2 (Amazon, Adobe, Microsoft, Atlassian)

> The **single most-asked LLD problem** at SDE-2 level. The senior signal is the **HashMap + doubly-linked-list composition** giving O(1) for get/put/evict. Get the data structures right, write it cleanly with sentinel head/tail nodes, mention `LinkedHashMap` as the production-clean alternative — that's the full signal. Often asked in a 30–45 min slot that blends DSA + LLD — be ready for both framings.

---

## Time budget

| Step | Activity | Budget | Cumulative |
|------|----------|--------|------------|
| 1 | Requirements | ~4 min | 4 |
| 2 | Entities & relationships | ~3 min | 7 |
| 3 | Class design | ~8 min | 15 |
| 4 | Implementation + dry-run | ~18 min | 33 |
| 5 | Extensibility | ~10 min | 43 |
| — | Wrap | ~2 min | 45 |

Step 4 is the longest — DLL pointer manipulation needs precision.

---

## Mental models — memorize before you walk in

### M1. The two-data-structures trick

```
   We need O(1) for ALL of: get, put, evict-LRU.

   HashMap alone:          O(1) get/put, but eviction = O(N) (scan for oldest)
   DoublyLinkedList alone: O(N) get (linear scan for key)
   Combine the two:        O(1) get (map lookup) + O(1) move-to-head (DLL) ⭐

                                       key → Node lookup
                                       ┌─────────────────┐
                                       │  HashMap<K,Node>│
                                       └────────┬────────┘
                                                │
                                                v (each Node lives in BOTH)
           MRU                                                              LRU
           v                                                                v
    [head ⇄ Node(a) ⇄ Node(b) ⇄ Node(c) ⇄ Node(d) ⇄ Node(e) ⇄ tail]
    sentinel                                                       sentinel

   On get(b):    HashMap → b's Node → moveToHead(b) → b now sits next to head
   On put(f) when at capacity:
                 lru = tail.prev (= e) → removeNode + map.remove(e.key)
                 then addToHead(new f)
   Every operation is O(1). No scans. Ever.
```

**Senior soundbite:** *"Two data structures, composed. HashMap gives O(1) key→node. Doubly-linked list gives O(1) move-to-head (refresh) and O(1) remove-tail (evict). One without the other gives you O(N) on at least one operation. This is THE textbook LRU implementation."*

### M2. Why sentinel head/tail nodes

```
   Without sentinels, addToHead needs a branch for the empty-list case:
     if (head == null) { head = node; tail = node; }
     else { node.next = head; head.prev = node; head = node; }

   With sentinels, it's the SAME 4 lines for every case — empty or full:
     node.prev = head;
     node.next = head.next;
     head.next.prev = node;
     head.next = node;

   Same simplification applies to removeNode and moveToHead.
```

> **Why this is a senior signal:** Sentinels eliminate every null check and every "is this the head?" branch in DLL operations. Writing this without sentinels is where 30 minutes of off-by-one bugs live.

### M3. Eviction policy as a Strategy seam (Step-5 readiness)

```
   Today: LRU. Tomorrow: maybe LFU, maybe TTL.

     interface Cache<K, V>
        ↑       ↑       ↑       ↑
    LRUCache  LFUCache TTLCache W-TinyLFU
    (HashMap  (HashMap (HashMap (LRU + admission
      + DLL)   + freq   + heap    filter)
                buckets)  by exp)

   Each impl uses different internal data structures but all satisfy the
   same Cache contract — get / put / size / clear.
```

---

## Step 1 — Requirements (~4 min)

### Clarifying dialogue

**You:** *"A fixed-capacity cache with `get(key)` and `put(key, value)` — both must be O(1)? Eviction is LRU specifically?"*
**Interviewer:** *"Yes, both O(1), LRU eviction."*
> Signals the HashMap + DLL composition immediately — no other structure gives O(1) on both.

**You:** *"Does `get` on an existing key refresh its recency? Does `put` on an existing key update the value AND refresh recency, without growing the size?"*
**Interviewer:** *"Yes to both."*
> This is the trap most candidates miss — `get` isn't a pure read, it mutates the DLL. And the update-branch needs an early return so size never inflates.

**You:** *"Get on a missing key — return null, or throw?"*
**Interviewer:** *"Return null."*

**You:** *"Concurrent callers — is `synchronized` sufficient, or do you want something more exotic?"*
**Interviewer:** *"Synchronized is fine for now."*
> Signals coarse-grained locking is the right default; per-key/striped locking is a Step-5 answer.

**You:** *"Out of scope for v1 — TTL expiration, LFU/ARC, persistence, distributed coherence?"*
**Interviewer:** *"Correct — those are follow-ups."*

### Requirements to write down

```
IN SCOPE
1. Generic <K, V> cache with a fixed capacity.
2. O(1) get(key) -> value, or null if absent.
3. O(1) put(key, value) — inserts or replaces.
4. Eviction: LRU — at capacity, inserting a new key evicts the LEAST
   recently used entry.
5. get(key) REFRESHES recency — entry becomes most-recently-used.
6. put(existing key) updates value AND refreshes recency, without
   growing size.
7. Thread-safe.

OUT OF SCOPE (all Step-5 extensions)
- TTL-based expiration
- LFU / ARC / W-TinyLFU
- Persistence
- Distributed coherence across machines
- Bulk operations (getAll, putAll)
- Statistics (hit rate, miss count)
```

---

## Step 2 — Entities & relationships (~3 min)

```
Entities
- Cache<K, V>      interface — supports multiple impls later
- LRUCache<K, V>   concrete — HashMap<K, Node<K,V>> + doubly-linked list w/ sentinels
- Node<K, V>       private static — DLL node: key + value + prev/next pointers

Optional bonus class
- LinkedHashMapLRUCache — same contract via Java's built-in LinkedHashMap +
  accessOrder. Worth mentioning to show you know both approaches.

NOT entities
- EvictionPolicy interface — speculative; only one policy exists today (Step 5)
- Statistics / Metrics — not in requirements

Relationships
- LRUCache owns:
    HashMap<K, Node<K, V>>            — O(1) key -> node lookup
    Node head, Node tail (sentinels)  — bounds the doubly-linked list
- Each Node lives in BOTH the HashMap (as a value) and the DLL (via prev/next).
  That dual residency is what makes O(1) operations possible.
```

### Why no `EvictionPolicy` interface in the base?

> *"Today there's exactly one policy — LRU. Adding an interface now would be speculative abstraction — the one-sentence test fails: I can't state a concrete design pressure right now that demands it. If LFU or TTL come up in Step 5, that's when I'd factor it out."*

### Class diagram

```
   +-----------------------------+
   | <<interface>>  Cache<K, V>  |    get(K) -> V
   |                              |    put(K, V)
   +-----------------------------+    size(): int  clear()
            ^             ^
            │             │
   +─────────────────+   +─────────────────────────────────────────+
   |  LRUCache<K, V> |   |  LinkedHashMapLRUCache<K, V> (10-liner) |
   +─────────────────+   +─────────────────────────────────────────+
   | - capacity      |
   | - index:        |
   |     HashMap<K,  |        index ──────────────┐
   |     Node<K,V>>  |                            v
   | - head: Node    |   [head ⇄ Node(a) ⇄ Node(b) ⇄ ... ⇄ Node(z) ⇄ tail]
   | - tail: Node    |    sentinel                                  sentinel
   +─────────────────+
   | + get / put     |
   | + size / clear  |
   +─────────────────+
```

---

## Step 3 — Class design (~8 min)

### LRUCache — state derived from requirements

| Requirement | State |
|-------------|-------|
| O(1) lookup by key | `Map<K, Node<K, V>> index` (HashMap) |
| O(1) move-to-head + remove-tail | Doubly-linked list with sentinel head + tail |
| Bounded capacity | `int capacity` |

### Class outline

```java
public class LRUCache<K, V> implements Cache<K, V> {
    private final int capacity;
    private final Map<K, Node<K, V>> index = new HashMap<>();
    private final Node<K, V> head;   // sentinel — most-recent is head.next
    private final Node<K, V> tail;   // sentinel — least-recent is tail.prev

    public LRUCache(int capacity) {
        this.capacity = capacity;
        this.head = new Node<>(null, null);
        this.tail = new Node<>(null, null);
        head.next = tail;
        tail.prev = head;
    }

    public synchronized V    get(K key)            { /* Step 4 */ }
    public synchronized void put(K key, V value)   { /* Step 4 */ }
    public synchronized int  size()                { return index.size(); }
    public synchronized void clear()                { /* reset both structures */ }

    // private DLL ops
    private void addToHead(Node<K,V> n);
    private void removeNode(Node<K,V> n);
    private void moveToHead(Node<K,V> n);

    static final class Node<K, V> {
        final K key;          // needed so eviction can remove from the index
        V value;
        Node<K,V> prev, next;
        Node(K key, V value) { this.key = key; this.value = value; }
    }
}
```

> **Why the Node carries the KEY (not just the value):** *"When evicting the LRU, we have only the Node (`tail.prev`). To also remove it from the HashMap index, we need its key. Without storing the key on the Node, we'd have to scan the HashMap — destroying the O(1) guarantee."*

### The principle to say aloud — composition, not inheritance

> *"I'm composing TWO data structures inside one class — HashMap for lookups, doubly-linked list for ordering. Each does what it's best at. Extending HashMap instead would muddle the two concerns. Composition is the right tool here."*

---

## Step 4 — Implementation + dry-run (~18 min)

### 4.1 `get` — lookup + recency refresh

```java
public synchronized V get(K key) {
    Node<K, V> node = index.get(key);
    if (node == null) return null;
    moveToHead(node);           // mark as most-recently-used
    return node.value;
}
```

> **Senior callout:** *"Three lines. The trick is `moveToHead` — `get` is NOT a pure read; it MUTATES the DLL ordering. That's why it's synchronized."*

### 4.2 `put` — insert OR update + maybe-evict

```java
public synchronized void put(K key, V value) {
    Node<K, V> existing = index.get(key);
    if (existing != null) {
        existing.value = value;     // replace
        moveToHead(existing);       // refresh recency
        return;
    }
    // New key — evict LRU if at capacity.
    if (index.size() == capacity) {
        Node<K, V> lru = tail.prev;     // least-recently-used
        removeNode(lru);
        index.remove(lru.key);          // uses node.key to clean the index
    }
    Node<K, V> node = new Node<>(key, value);
    addToHead(node);
    index.put(key, node);
}
```

**Three callouts:**

1. *"Update branch returns WITHOUT growing the size. Without this early return, repeatedly updating the same key would inflate the index and trigger spurious evictions."*
2. *"`tail.prev` is the LRU. Sentinels make this clean — no `if (tail == null)` checks."*
3. *"Eviction does BOTH: remove from the DLL AND remove from the HashMap. Both structures must agree at all times — the Node is the single link between them."*

### 4.3 The three DLL helpers — four lines each

```java
private void addToHead(Node<K, V> node) {
    node.prev = head;
    node.next = head.next;
    head.next.prev = node;
    head.next = node;
}

private void removeNode(Node<K, V> node) {
    node.prev.next = node.next;
    node.next.prev = node.prev;
}

private void moveToHead(Node<K, V> node) {
    removeNode(node);
    addToHead(node);
}
```

> **Senior callout:** *"`moveToHead` is just remove + addToHead. Don't write it as 4 inline pointer reassignments — that's where off-by-one bugs live. Compose two correct primitives."*

### 4.4 Dry-run — eviction + refresh (say this at the board)

```
capacity = 3. Initial: empty list = head ⇄ tail. index = {}.

put(a, 1):  list: [head ⇄ a ⇄ tail]                index={a}
put(b, 2):  list: [head ⇄ b ⇄ a ⇄ tail]            index={a, b}
put(c, 3):  list: [head ⇄ c ⇄ b ⇄ a ⇄ tail]        index={a, b, c}

get(a):
   moveToHead(a). list: [head ⇄ a ⇄ c ⇄ b ⇄ tail]   ← a refreshed to MRU
   return 1.

put(d, 4):
   size(3) == 3 → EVICT.
   lru = tail.prev = b (NOT a, because a was just refreshed).
   removeNode(b). index.remove(b). index={a, c, d}.
   addToHead(d). list: [head ⇄ d ⇄ a ⇄ c ⇄ tail].

get(b) → null  (correctly evicted)                                        ✓
get(a) → 1     (was refreshed, not evicted)                                ✓
get(c) → 3     (still in, at tail.prev now)                                ✓
get(d) → 4                                                                  ✓
size() = 3                                                                  ✓
```

### 4.5 Bonus — the LinkedHashMap shortcut

```java
public class LinkedHashMapLRUCache<K, V> implements Cache<K, V> {
    private final int capacity;
    private final LinkedHashMap<K, V> map;

    public LinkedHashMapLRUCache(int capacity) {
        this.capacity = capacity;
        this.map = new LinkedHashMap<>(16, 0.75f, /* accessOrder */ true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > LinkedHashMapLRUCache.this.capacity;
            }
        };
    }
    // get / put / size / clear all delegate to map.
}
```

> **Say this in the interview:** *"In production I'd reach for this — 10 lines, uses the JDK's own LRU machinery. But the from-scratch version is what proves I understand the composition underneath. I'd write the from-scratch one live and mention this as the deliberate production alternative."*

---

## Step 5 — Extensibility (~10 min)

### E1. "Add TTL — entries expire after N seconds even without eviction"

**Problem:** Stale entries hang around forever. A 1-hour-old cached price might no longer be accurate.

**Fix:** Each Node gets an `expiresAt: Instant`. On `get`, check expiration before returning — if expired, remove from BOTH the index and DLL, return null. Optionally a background sweeper periodically scans for expired entries to free memory faster than passive expiration.

**Tradeoff to name:** *"A sweeper adds a thread; passive-only is simpler but lets expired entries linger until touched. Most production caches (Caffeine, Guava) do both."*

### E2. "Add LFU — least-FREQUENTLY-used eviction"

**Problem:** LRU evicts based on recency. A page hit 100× yesterday but unused in the last hour gets evicted before a one-hit page touched 30 seconds ago. For some workloads, LFU is better.

**Fix:** Different impl, same `Cache` interface. LFU keeps `Map<frequency, LinkedHashSet<Node>>` plus a `minFrequency` counter — O(1) per operation but more state to maintain. Or use `W-TinyLFU` (Caffeine's algorithm) — a small LRU admission filter in front of an LFU main store.

### E3. "Per-key locking instead of one global lock"

**Problem:** All operations serialize on the global lock. Reads from unrelated keys block each other.

**Fix:** `ReentrantReadWriteLock` gives only a modest win — `get()` still mutates DLL order, so it needs the write lock too. Better: `ConcurrentHashMap` for the index + striped locks for DLL segments — what production caches actually do.

**Say aloud:** *"`get` is a writer here because it moves the node to head. That means even read-write locks help only marginally. For interview scope `synchronized` is correct; production reaches for Caffeine, which solves this for you."*

### E4. Other one-liners

| Follow-up | Answer |
|-----------|--------|
| "Resize capacity at runtime" | Add `setCapacity(int)` — if shrinking, evict from tail until at new capacity. |
| "Statistics — hit rate, miss count" | `AtomicLong hits, misses` updated in `get`; expose a `stats()` method. |
| "Bulk get / put" | Loop the existing methods inside ONE synchronized block (atomic batch). |
| "Persistence — survive restart" | Snapshot index + DLL ordering to disk periodically; rebuild on startup. |
| "Multi-tier (L1 in-process + L2 Redis)" | Inject a backing `Cache` — miss in L1 checks L2, promotes to L1 on hit. Same interface. |
| "Make it lock-free" | Hard — DLL ops need atomic multi-pointer updates. Out of interview scope; name it as future work. |

---

## Design patterns in play (name these out loud in the interview)

### In the BASE design

**No GoF pattern by name.** This is a pure data-structure-composition problem — the senior signal is the HashMap + DLL design, not a named pattern. Saying this explicitly is itself a signal: it shows you're not pattern-stuffing where none is warranted.

| Principle | Where it lives | One-line justification |
|-----------|----------------|------------------------|
| **Composition over inheritance** | `LRUCache` composes a HashMap + a DLL | *"Extending HashMap would muddle lookup and ordering concerns. Composition keeps them separate."* |
| **Information Expert** | `Node` stores its own `key` | *"Only the Node knows what to remove from the index on eviction — storing the key there avoids an O(N) scan."* |
| **Interface Segregation** | `Cache<K,V>` — 4 narrow methods | *"No fat `getAll`/`stats`/`flush` mixed in — those are Step-5 extensions."* |

### Patterns for Step 5 extensibility

| Follow-up trigger | Pattern | The one-line move |
|-------------------|---------|-------------------|
| "Different eviction policies (LFU, TTL, ARC)" | **Strategy** ⭐ | *"Promote eviction to an `EvictionPolicy` interface, or just add sibling `Cache` implementations — LRU, LFU, TTL all satisfy the same contract."* |
| "Cache-stat observers (hits/misses/evictions)" | **Observer** | *"Publish `onHit`/`onMiss`/`onEvict` events; stats subscribers register independently."* |
| "Multi-tier cache (L1 + L2)" | **Decorator** | *"`L2BackedCache(Cache l1, Cache l2)` wraps an L1 with L2 fallback. Stackable."* |
| "Read-through to underlying store" | **Decorator** | *"`LoadingCache(Cache, Function<K,V> loader)` calls the loader on miss."* |

### Patterns to actively refuse

- **Singleton on LRUCache** — caches are scoped to a service/component; DI a single instance.
- **Builder for the 1-arg `LRUCache(capacity)` ctor** — academic noise.
- **State pattern on Node** — a node has no per-state behavior; plain fields are correct.

### The rule to sound natural

1. **No pattern in the base is the correct answer here** — say so explicitly rather than forcing one in.
2. **Strategy is the natural Step-5 answer** the moment a second eviction policy is mentioned.
3. **Cap Step-5 patterns at 2** — usually Strategy (eviction) + Decorator (tiering) covers everything asked.

---

## What is expected at each level

### Junior (SDE-1)
- Recognizes HashMap alone isn't enough (eviction is O(N)) with a nudge toward a linked list.
- May write the DLL without sentinels, hitting null-check bugs on the empty-list and single-node edge cases.
- Gets `get`/`put` happy-path working; may forget that `get` needs to mutate the DLL (treats it as a pure read).
- Doesn't test the "recency refresh protects from eviction" case unprompted.

### Mid-level (SDE-2) — the target
- Reaches the HashMap + DLL composition unprompted, and explains why HashMap alone or DLL alone isn't sufficient.
- Uses sentinel head/tail nodes from the start — no null-check branches in `addToHead`/`removeNode`.
- Correctly makes `get` synchronized (recognizing it mutates DLL order, not just reads).
- `put` on an existing key returns early after replacing the value — doesn't grow size or double-count.
- Node stores its own key so eviction can clean the HashMap without a scan.
- Runs the eviction + refresh dry-run out loud.

### Senior (SDE-3 / SDE-II)
- Everything mid-level does, faster, with proactive tradeoffs.
- Explicitly states "no GoF pattern in the base" rather than forcing Strategy in prematurely — and names exactly when Strategy would apply (second eviction policy).
- Volunteers the `LinkedHashMap` production alternative unprompted, framing the from-scratch version as proof of understanding, not the production choice.
- Catches the "`get` is a writer" subtlety when discussing concurrency — explains why `ReentrantReadWriteLock` only helps marginally here.
- Discusses capacity=1 and capacity=0 edge cases without prompting.
- Finishes early; uses buffer to discuss TTL/LFU and per-key locking as concrete Step-5 sketches.

---

## Interview deep-dives

### Complexity

| Operation | Time | Notes |
|-----------|------|-------|
| `get(key)` | **O(1)** | HashMap lookup + moveToHead |
| `put` — existing key | **O(1)** | HashMap lookup + moveToHead + value replace |
| `put` — new key, no evict | **O(1)** | HashMap put + addToHead |
| `put` — new key, with evict | **O(1)** | Remove tail + map.remove(key) + addToHead |
| `size()` | **O(1)** | — |
| `clear()` | **O(1)** amortized | Drops references; GC reclaims |
| Storage | **O(capacity)** | One Node per entry + 2 sentinels + HashMap overhead |

> **Senior callout:** *"Every operation is O(1) — that's the whole point. If any operation drops to O(N), the design is wrong. HashMap gives O(1) lookup; the doubly-linked list gives O(1) pointer manipulation; neither alone can do this."*

### Concurrency

| Approach | When | Cost |
|----------|------|------|
| `synchronized` on every method ⭐ | **Default.** Correct + simple. | Serializes all access — fine for a single-app cache. |
| `ReentrantReadWriteLock` | Read-heavy workloads | `get` still mutates DLL → still needs the write lock; modest gain. |
| `ConcurrentHashMap` + striped DLL locks | High contention | Complex; only after profiling shows `synchronized` is the bottleneck. |
| Lock-free (CAS-based) | Extreme throughput | Hard to get right; production caches like Caffeine use sharding instead. |

> **Senior callout:** *"`get` is a writer here because it moves the node to head. That's why even read-write locks help only marginally — for interview scope `synchronized` is correct."*

### The recency-refresh test (mention this)

```java
@Test
void get_refreshes_recency_protecting_from_eviction() {
    Cache<String, Integer> c = new LRUCache<>(3);
    c.put("a", 1); c.put("b", 2); c.put("c", 3);
    c.get("a");                  // a is now MRU
    c.put("d", 4);               // b should be evicted (it's the new LRU)
    assertEquals(1,    c.get("a"));
    assertNull        (c.get("b"));    // evicted
    assertEquals(3,    c.get("c"));
    assertEquals(4,    c.get("d"));
}
```

*"This is the most common interviewer trap — testing that recency refresh actually protects an entry from eviction, not just that eviction happens."*

---

## 30-second summary (memorize for closing)

> *"Generic `Cache<K, V>` interface with one production impl, `LRUCache`. The headline is data structure composition — `HashMap<K, Node>` for O(1) lookup, doubly-linked list with sentinel head/tail for O(1) move-to-head and remove-tail. Each Node lives in BOTH structures simultaneously, which is what makes every operation O(1). `get` is a mutator — it moves the node to head — so both `get` and `put` are synchronized. `put` has three branches: update existing (no size change, early return), new key under capacity (just add), new key at capacity (evict `tail.prev` first). The Node stores its own key so eviction can also clean the HashMap in O(1) — otherwise you'd need an O(N) scan. No GoF pattern in the base — this is pure composition. For production I'd mention Java's `LinkedHashMap` with `accessOrder=true` as a 10-line equivalent; the from-scratch version proves I understand what's inside. Extensions: TTL via `expiresAt` on each node, LFU as a sibling implementation, striped locking for higher concurrency."*

---

## Top mistakes that lose points

- **Storing only the VALUE on the Node, not the key** — eviction becomes O(N) because you have to scan the HashMap to find which key maps to the evicted node.
- **No sentinels** — every add/remove needs null checks for "is this the head?" / "is this the tail?". Off-by-one bugs follow.
- **`get` not synchronized** — `get` mutates the DLL (`moveToHead`); concurrent gets corrupt pointers.
- **`put(existing key)` growing the size** — forgetting the early return after replacing the value. Causes spurious evictions on update.
- **Using ArrayList instead of a DLL** — move-to-head and remove become O(N), destroying the O(1) guarantee.
- **Confusing `head` (MRU) and `tail` (LRU)** — pick a convention at the start and stick to it.
- **No bound check on capacity** — `LRUCache(0)` or `LRUCache(-1)` becomes a bug magnet later.
- **Not testing recency refresh** — the eviction-after-get test is the most common interviewer trap.
- **Showing only the LinkedHashMap version** — the interviewer wants to see you understand the internals, not just the JDK shortcut.

---

## Files in this folder

| File | Purpose |
|------|---------|
| `Cache.java` | Generic interface — get / put / size / clear |
| `LRUCache.java` | **The hot class** — HashMap + DLL composition + sentinel head/tail |
| `LinkedHashMapLRUCache.java` | 10-line "production-clean" variant using JDK's LinkedHashMap + accessOrder |
| `LRUCacheDriver.java` | 6 scenarios — basic / eviction / recency-refresh / update-no-grow / both-impls-agree / 50-thread concurrent burst |

Run:
```bash
mvn -q compile exec:java \
  -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.lrucache.LRUCacheDriver
```
