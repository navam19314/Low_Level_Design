# LRU Cache — 45-min LLD Interview Walkthrough

**Target role:** SDE‑2 (Amazon, Adobe, Microsoft, Atlassian, etc.)

> LRU Cache is the **single most-asked LLD interview question** at SDE‑2 level. The headline senior signal is the **HashMap + doubly-linked-list composition** giving O(1) for get / put / evict. Get the data structures right, write it cleanly with sentinel head/tail nodes, mention `LinkedHashMap` as the production-clean alternative — that's the full senior signal.

> Often this question is asked in a 30–45 min slot that blends DSA + LLD. Be ready for both framings.

---

## Time budget (45 min)

| Step | Activity                                                                                | Budget   | Cumulative |
| ---- | --------------------------------------------------------------------------------------- | -------- | ---------- |
| 1    | Requirements                                                                            | ~4 min   | 4          |
| 2    | Entities & Relationships (Cache interface; LRU as a strategy)                           | ~3 min   | 7          |
| 3    | Class Design (HashMap + DLL composition, sentinel nodes)                                | ~8 min   | 15         |
| 4    | Implementation (`get`, `put`, the three DLL helpers + dry-run)                          | ~18 min  | 33         |
| 5    | Extensibility (TTL, LFU, ARC, distributed, thread-safety variants)                      | ~10 min  | 43         |
| —    | Wrap & questions                                                                        | ~2 min   | 45         |

Step 4 is the longest — DLL operations need precise pointer wrangling.

Watch the clock at minute **4**, minute **15** (start coding), minute **33** (extensibility).

---

## Mental models — internalize these BEFORE you walk in

### M1. The two-data-structures trick

```
   We need O(1) for ALL of: get, put, evict-LRU.

   HashMap alone:               O(1) get/put, but eviction = O(N) (scan for oldest)
   DoublyLinkedList alone:      O(N) get (linear scan for key)
   Combine the two:             O(1) get (map lookup) + O(1) move-to-head (DLL) ⭐

                                              key → Node lookup
                                              ┌─────────────────┐
                                              │  HashMap<K,Node>│
                                              └────────┬────────┘
                                                       │
                                                       ▼ (each Node lives in BOTH)
              MRU                                                                    LRU
              ▼                                                                      ▼
       [head ⇄ Node(a) ⇄ Node(b) ⇄ Node(c) ⇄ Node(d) ⇄ Node(e) ⇄ tail]
       sentinel                                                       sentinel

   On get(b):    HashMap → b's Node → moveToHead(b) → b now sits next to head
   On put(f) when capacity is reached:
                 lru = tail.prev (= e here) → removeNode + map.remove(e.key)
                 then addToHead(new f)
   Every operation is O(1). No scans. Ever.
```

**Senior soundbite (memorize):** *"Two data structures, composed. HashMap gives O(1) key→node. Doubly-linked list gives O(1) move-to-head (refresh) and O(1) remove-tail (evict). One without the other gives you O(N) on at least one operation. The combination is THE textbook LRU implementation."*

### M2. Why sentinel head/tail nodes

```
   Without sentinels:                       With sentinels:
   ------------------                       ----------------
                                                                   
   addToHead(node):                         addToHead(node):
     if (head == null) {                      node.prev = head
       head = node                            node.next = head.next
       tail = node                            head.next.prev = node
     } else {                                 head.next = node
       node.next = head
       head.prev = node                       — same 4 lines for EVERY case
       head = node                              (empty list or full)
     }                                          — no null checks needed
                                                — easier to get right under pressure

   Same simplification on removeNode and moveToHead.
```

> **Why this is a senior signal:** *Sentinel head/tail nodes are the cleanest pattern for doubly-linked-list operations in Java. They eliminate every null check, every "is this the head?" branch. Writing this without sentinels gets you 30 minutes of off-by-one bugs.*

### M3. Eviction policy as a Strategy seam (Step-5 readiness)

```
   Today: LRU. Tomorrow: maybe LFU, maybe TTL.

   By making the EVICTION POLICY pluggable, we don't have to rewrite Cache:

     interface Cache<K, V>
        ↑       ↑       ↑       ↑
     LRUCache  LFUCache TTLCache W-TinyLFU
     (HashMap  (HashMap (HashMap (LRU + admission
       + DLL)    + freq   + heap   filter)
                 buckets)  by exp)

   Each implementation uses different data structures internally, but they all
   satisfy the same Cache contract — get / put / size / clear.
```

---

## STEP 1 — Requirements (~4 min)

### What to say out loud (opener)
> "LRU at interview scope is usually a small problem with a big senior signal — the data structure composition. Let me clarify what I should optimize for."

### Probe the 4 themes

| Theme               | Question to ask                                                                                              |
| ------------------- | ------------------------------------------------------------------------------------------------------------ |
| Primary capabilities| "Fixed-capacity cache with `get(key) → value` and `put(key, value)`. Both must be O(1)? Eviction is LRU?" |
| Rules               | "On `put` of an existing key, value replaces and key's recency refreshes? Same key never grows the size?" |
| Error handling      | "Get on a missing key returns null (not exception)? Capacity 0 / negative rejected at construction?" |
| Concurrency         | "Multi-threaded callers — `synchronized` is fine, OR do you want lock-free?" |

### What to write on the board

```
Functional Requirements
1. Generic <K, V> cache with a fixed CAPACITY.
2. O(1) get(key) → value, or null if absent.
3. O(1) put(key, value) — inserts or replaces.
4. Eviction: LRU — when at capacity and inserting a new key, evict the
   LEAST recently used entry.
5. get(key) REFRESHES recency — the entry now counts as most-recently-used.
6. put(existing-key) updates value AND refreshes recency, without growing size.
7. Thread-safe.

Out of Scope
- TTL-based expiration (Step 5 — distinct eviction policy)
- LFU / ARC / W-TinyLFU (Step 5)
- Persistence
- Distributed coherence across multiple machines
- Bulk operations (getAll, putAll)
- Statistics (hit rate, miss count)
```

### Close the step
> "Three load-bearing requirements: O(1) on BOTH `get` and `put`, recency refresh on `get`, and the eviction trigger. Those three together force the HashMap + DLL composition."

---

## STEP 2 — Entities & Relationships (~3 min)

### What to say out loud
> "**Three classes max**: a generic `Cache<K, V>` interface, an `LRUCache<K, V>` implementation that owns the HashMap + doubly-linked-list internally, and a private `Node<K, V>` for the DLL. The interface makes alternative eviction policies pluggable later (Step 5)."

### Why no `EvictionPolicy` interface in the base
> "Today there's exactly one policy — LRU. Adding an EvictionPolicy interface now would be speculative abstraction. If LFU or TTL come up in Step 5, we'd factor it out then. One-sentence test fails: 'I can't state a concrete design pressure NOW that demands the abstraction' — so skip it."

### What to write on the board

```
Entities
- Cache<K, V>          (interface — supports multiple impls later)
- LRUCache<K, V>       (concrete — HashMap<K, Node<K,V>> + doubly-linked list w/ sentinels)
- Node<K, V>           (private static — DLL node holding key + value + prev/next pointers)

Optional bonus class:
- LinkedHashMapLRUCache (the same contract via Java's built-in LinkedHashMap + accessOrder)
  Useful to mention in interview to show you know both approaches.

NOT entities
- EvictionPolicy interface  (speculative — Step 5)
- Statistics / Metrics      (not in requirements)

Relationships
- LRUCache owns:
    HashMap<K, Node<K, V>>           — O(1) key→node lookup
    Node head, Node tail (sentinels) — bounds the doubly-linked list
- Each Node lives in BOTH the HashMap (as a value) and the DLL (chained via prev/next).
  That dual residency is what makes O(1) operations possible.
```

### Diagram — boxes and arrows

```
   +-----------------------------+
   | <<interface>>  Cache<K, V>  |    + get(K) → V
   |                              |    + put(K, V)
   +-----------------------------+    + size(): int  + clear()
            ▲             ▲
            │             │
   +─────────────────+   +─────────────────────────────────────────┐
   |  LRUCache<K, V> |   |  LinkedHashMapLRUCache<K, V> (10-liner) │
   +─────────────────+   +─────────────────────────────────────────┘
   | - capacity      |
   | - index:        |
   |     HashMap<K,  |        index ──────────────┐
   |     Node<K,V>>  |                            ▼
   | - head: Node    |   [head ⇄ Node(a) ⇄ Node(b) ⇄ ... ⇄ Node(z) ⇄ tail]
   | - tail: Node    |    sentinel                                  sentinel
   +─────────────────+
   | + get / put     |
   | + size / clear  |
   +─────────────────+
```

---

## STEP 3 — Class Design (~8 min)

### LRUCache — state ↔ requirement table

| Requirement                              | State LRUCache must own                                  |
| ---------------------------------------- | -------------------------------------------------------- |
| O(1) lookup by key                        | `Map<K, Node<K, V>> index` (HashMap)                     |
| O(1) move-to-head + remove-tail           | Doubly-linked list with sentinel head + tail              |
| Bounded capacity                         | `int capacity`                                            |

### Class outline (write this on the board)

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
    public synchronized void clear()               { /* reset both structures */ }

    // private DLL ops
    private void addToHead(Node<K,V> n);
    private void removeNode(Node<K,V> n);
    private void moveToHead(Node<K,V> n);

    static final class Node<K, V> {
        final K key;          // ← needed so eviction can remove from the index
        V value;
        Node<K,V> prev, next;
        Node(K key, V value) { this.key = key; this.value = value; }
    }
}
```

> **Why the Node carries the KEY (not just the value):** *when evicting the LRU, we have only the Node (`tail.prev`). To also remove it from the HashMap index, we need its key. Without storing the key on the Node, we'd have to scan the HashMap — destroying the O(1) guarantee.*

### The principle to verbalize — composition, not inheritance
> "I'm composing TWO data structures inside one class — HashMap for lookups, doubly-linked list for ordering. Each does what it's best at. Trying to do this with inheritance — say, extending HashMap — would muddle the two concerns. Composition is the right tool here."

---

## STEP 4 — Implementation (~18 min)

### Open by asking
> "Real Java or pseudo-code? I'll do `get` first, then `put` (it has the eviction branch), then the three DLL helpers, then dry-run."

### 4.1 `get` — lookup + recency refresh

```java
public synchronized V get(K key) {
    Node<K, V> node = index.get(key);
    if (node == null) return null;
    moveToHead(node);           // mark as most-recently-used
    return node.value;
}
```

> **Senior callout:** *"Three lines. The trick is the `moveToHead` — `get` is NOT a pure read; it MUTATES the DLL ordering. That's why it's synchronized."*

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
        index.remove(lru.key);          // ← uses node.key to clean the index
    }
    Node<K, V> node = new Node<>(key, value);
    addToHead(node);
    index.put(key, node);
}
```

**Three callouts:**

1. *"Update branch returns WITHOUT growing the size. Without this early return, repeatedly updating the same key would inflate the index and trigger spurious evictions."*

2. *"`tail.prev` is the LRU. Sentinels make this clean — no `if (tail == null)` checks."*

3. *"Eviction does BOTH: remove from the DLL AND remove from the HashMap. Both structures must agree at all times. Single source of truth: the Node — once it's gone from both, it's gone."*

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

### 4.4 Verification — dry-run eviction + refresh

```
capacity = 3. Initial: empty list = head ⇄ tail. index = {}.

put(a, 1):
   not in index. size(0) < 3 → no evict. addToHead(a). index={a:Na}.
   list: [head ⇄ a ⇄ tail]

put(b, 2):  list: [head ⇄ b ⇄ a ⇄ tail],  index={a, b}
put(c, 3):  list: [head ⇄ c ⇄ b ⇄ a ⇄ tail],  index={a, b, c}

get(a):
   index.get(a) = Na. moveToHead(Na).
   list: [head ⇄ a ⇄ c ⇄ b ⇄ tail]   ← a refreshed to MRU
   return 1.

put(d, 4):
   not in index. size(3) == 3 → EVICT.
   lru = tail.prev = b (NOT a, because a was just refreshed).
   removeNode(b). index.remove(b).
   index={a, c, d}.
   addToHead(d). list: [head ⇄ d ⇄ a ⇄ c ⇄ tail].

get(b) → null  (correctly evicted)                                          ✓
get(a) → 1     (was refreshed, not evicted)                                 ✓
get(c) → 3     (was MRU until d arrived, now at tail.prev — but still in)  ✓
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

> **Say this in the interview:** *"In production I'd reach for this LinkedHashMap version — it's 10 lines and uses the JDK's own LRU machinery. But the from-scratch version is what proves I understand the underlying composition. I'd write the from-scratch one in this interview and call out the LinkedHashMap option as a deliberate alternative."*

---

## STEP 5 — Extensibility (~10 min)

### 5.1 "Add TTL — entries expire after N seconds even without eviction"

> **Problem in current design:** *"Stale entries hang around forever. A 1-hour-old cached price might no longer be accurate."*
>
> **Pattern as the fix:** *"Each Node gets an `expiresAt: Instant`. On `get`, check expiration before returning — if expired, remove from BOTH the index and DLL, return null. Optionally a background sweeper periodically scans for expired entries to free memory faster than passive expiration."*
>
> **Tradeoff:** *"Sweeper adds a thread; passive-only is simpler but lets expired entries linger until they're touched. Most production caches (Caffeine, Guava) do both."*

### 5.2 "Add LFU — least-FREQUENTLY-used eviction"

> **Problem in current design:** *"LRU evicts based on recency. A page hit 100× yesterday but unused in the last hour gets evicted before a one-hit page touched 30 seconds ago. For some workloads, LFU is better."*
>
> **Pattern as the fix:** *"Different impl, same Cache interface. LFU keeps `Map<frequency, LinkedHashSet<Node>>` plus a minFrequency counter. O(1) per operation but more state to maintain. Or use `W-TinyLFU` (Caffeine's algorithm) — a small LRU admission filter in front of an LFU main store."*

### 5.3 "Per-key locking instead of one global lock"

> **Problem in current design:** *"All operations serialize on the global lock. Reads from unrelated keys block each other."*
>
> **Pattern as the fix:** *"Replace the synchronized methods with `ReentrantReadWriteLock` (read parallel, write exclusive) — modest win because get() still mutates DLL order. Better: `ConcurrentHashMap` for the index + `striped locks` for the DLL segments. Production caches use this. For interview scope, synchronized is the right starting point."*

### 5.4 Other "what-if" answers

| Follow-up                                  | Answer                                                                                              |
| ------------------------------------------ | --------------------------------------------------------------------------------------------------- |
| "Resize capacity at runtime"               | Add `setCapacity(int)` — if shrinking, evict from tail until at new capacity.                       |
| "Statistics — hit rate, miss count"        | Add `AtomicLong hits, misses` — update in `get`. Expose `stats()` method.                          |
| "Bulk get / put"                           | Loop the existing methods inside ONE synchronized block (atomic batch). Or expose `getAll(Collection)`. |
| "Persistence — survive restart"            | Snapshot index + DLL ordering to disk periodically; rebuild on startup. Or use a write-through cache. |
| "Multi-tier (L1 in-process + L2 Redis)"    | Inject a backing `Cache` — on miss in L1, check L2; on hit, promote to L1. Same Cache interface.   |
| "Make it lock-free"                        | Hard — DLL operations need atomic multi-pointer updates. Real lock-free caches use specialized structures (e.g., concurrent skiplists). Out of interview scope; mention as future work. |

---

## Design Patterns — Hello Interview's canonical 8

> **No GoF pattern in the base.** This is a pure data-structure-composition problem — the senior signal is the HashMap+DLL design, not any named pattern.

**Reach for these on Step-5 follow-ups:**

| Follow-up                                  | Pattern (HI's 8)             | Your line                                                                                            |
| ------------------------------------------ | ---------------------------- | ---------------------------------------------------------------------------------------------------- |
| "Different eviction policies"              | **Strategy (#1)** ⭐         | *"Promote eviction to an `EvictionPolicy` interface. LRU, LFU, TTL all implement it."*               |
| "Cache-stat observers"                     | **Observer (#2)**            | *"Publish `onHit`/`onMiss`/`onEvict` events. Stats subscribers register independently."*            |
| "Multi-tier cache"                         | **Decorator (#7)**           | *"`L2BackedCache(Cache l1, Cache l2)` wraps an L1 with L2 fallback. Stackable."*                    |
| "Read-through to underlying store"         | **Decorator (#7)**           | *"`LoadingCache(Cache, Function<K,V> loader)` calls the loader on miss."*                          |

**Patterns to refuse:**

- **Singleton on LRUCache** — caches are usually scoped to a service/component; DI a single instance.
- **Builder for the 1-arg `LRUCache(capacity)` ctor** — academic noise.

### One sentence to say at the end of Step 3

> *"The base design has no GoF pattern by name — the senior signal is the HashMap + doubly-linked-list composition. Patterns like Strategy and Decorator come in at Step 5 when eviction policies vary or when we add tiered caching."*

---

## Interview deep-dives — the questions you'll definitely get asked

### 1. Complexity (Big-O)

| Operation                                | Time           | Space          | Notes                                                                              |
| ---------------------------------------- | -------------- | -------------- | ---------------------------------------------------------------------------------- |
| `get(key)`                                | **`O(1)`**     | O(1) per call  | HashMap lookup + moveToHead                                                        |
| `put(key, value)` — existing              | **`O(1)`**     | O(1)           | HashMap lookup + moveToHead + value replace                                        |
| `put(key, value)` — new, no evict         | **`O(1)`**     | O(1)           | HashMap put + addToHead                                                            |
| `put(key, value)` — new, with evict       | **`O(1)`**     | O(1)           | All three: remove tail, map.remove(key), addToHead                                 |
| `size()` / `clear()`                      | **`O(1)`** / `O(N)`             | O(1)           |                                                                                    |
| Storage                                  | -              | **`O(capacity)`** | One Node per entry + 2 sentinels + HashMap overhead                              |

> **Senior callout:** *"Every operation is O(1) — that's the WHOLE POINT. If any operation drops to O(N), the design is wrong. The HashMap gives O(1) lookup; the doubly-linked list gives O(1) pointer manipulation; neither could do this alone."*

### 2. Concurrency / thread-safety

| Approach                                | When to use                                  | Cost                                                              |
| --------------------------------------- | -------------------------------------------- | ----------------------------------------------------------------- |
| **`synchronized` on every method** ⭐    | **Default.** Correct + simple                | Serializes all access — fine for single-app cache                 |
| `ReentrantReadWriteLock`                | Read-heavy workloads                         | `get` still mutates DLL → still needs write lock; modest gain    |
| `ConcurrentHashMap` + striped DLL locks | High contention                              | Complex; only after profiling shows synchronized is the bottleneck |
| Lock-free (true CAS-based)              | Extreme throughput                           | Hard to get right; production caches like Caffeine don't even bother — they use sharding |

> **Senior callout:** *"`get` is a writer here because it moves the node to head. That means even read-write locks help only marginally. For interview scope `synchronized` is correct; for production reach for Caffeine which solves all of this for you."*

### 3. Testing — what to write tests for

| Test category                | Cases to cover                                                                                              |
| ---------------------------- | ----------------------------------------------------------------------------------------------------------- |
| Basic put/get                | After put(a,1), get(a) returns 1                                                                            |
| Get on missing               | get(x) returns null (no exception)                                                                          |
| **Eviction at capacity**     | capacity=3, insert 4 keys → first is evicted                                                                |
| **Recency refresh**          | put a/b/c, get a, put d → b is evicted (not a)                                                              |
| Update existing              | put(a,1) then put(a,2) → size unchanged, value updated, a is MRU                                            |
| Capacity 1 corner case       | put(a), put(b) → only b survives                                                                            |
| Clear                        | put a/b/c, clear, get(a) → null, size==0                                                                    |
| **Concurrent burst**         | 50 threads × 100 ops → capacity invariant holds; no NullPointerException; no corruption                    |
| Capacity validation         | new LRUCache(0) or new LRUCache(-1) → IllegalArgumentException                                              |

```java
@Test
void get_refreshes_recency_protecting_from_eviction() {
    Cache<String, Integer> c = new LRUCache<>(3);
    c.put("a", 1); c.put("b", 2); c.put("c", 3);
    c.get("a");                  // a is now MRU
    c.put("d", 4);               // b should be evicted (it's the new LRU)
    assertEquals(1,    c.get("a"));
    assertNull       (c.get("b"));    // evicted
    assertEquals(3,    c.get("c"));
    assertEquals(4,    c.get("d"));
}
```

### 4. SOLID mapping

| Letter                       | Where it shows up                                                                                          |
| ---------------------------- | ---------------------------------------------------------------------------------------------------------- |
| **S** Single Responsibility  | LRUCache = LRU implementation. Cache interface = contract. Node = DLL element. Three reasons to change. |
| **O** Open/Closed            | Adding LFU / TTL = new class implementing Cache. LRUCache unchanged. Eviction policy as a Strategy is one refactor away. |
| **L** Liskov Substitution    | Both LRUCache and LinkedHashMapLRUCache substitute behind the Cache interface — same get/put semantics.   |
| **I** Interface Segregation  | Cache has 4 narrow methods. No fat `getAll`/`stats`/`flush` mixed in — those are extensions.              |
| **D** Dependency Inversion   | Application code depends on Cache, not LRUCache. Test doubles substitute trivially (e.g., NoOpCache).     |

### 5. "Summarize your design in 30 seconds"

> *"Generic `Cache<K, V>` interface with one production impl: `LRUCache`. The headline is the data structure composition — HashMap<K, Node> for O(1) lookup, doubly-linked list with sentinel head/tail for O(1) move-to-head and remove-tail. Each Node lives in BOTH structures simultaneously, which is what makes every operation O(1). `get` is a mutator — moves the node to head — so the cache synchronizes get AND put. `put` has three branches: update existing (no size change), new key under capacity (just add), or new key at capacity (evict tail.prev first). The Node stores its KEY so eviction can also clean the HashMap in O(1). For production, I'd mention Java's `LinkedHashMap` with `accessOrder=true` as a 10-line equivalent — the from-scratch version proves I understand what's inside. Extensions: TTL via `expiresAt` on each node, LFU as a separate implementation, ReadWriteLock or striped locking for higher concurrency."*

That's ~50 seconds. Hits: the composition, O(1) for everything, sentinel pattern, why Node stores the key, LinkedHashMap as the production alternative.

---

## Closing soundbites (memorize these)

- **Opening:** *"LRU is small in scope but the senior signal is sharp — get the data structures right and the rest follows."*
- **Why HashMap + DLL:** *"HashMap gives O(1) lookup; DLL gives O(1) pointer manipulation. Together = O(1) on EVERY operation."*
- **Why sentinel head/tail:** *"Eliminates every null check in DLL ops. Add/remove is the same 4 lines for empty list, full list, or anything in between."*
- **Why Node stores the key:** *"On eviction we have only the Node (tail.prev). To also remove from the index, we need its key. Without storing it, eviction becomes O(N)."*
- **Why `get` is synchronized:** *"`get` is a MUTATOR — it moves the node to head. Concurrent gets can corrupt the DLL pointers; one synchronized block keeps it consistent."*
- **Production alternative:** *"Java's `LinkedHashMap` with `accessOrder=true` is a 10-line LRU. I'd mention this in interview to show I know both, then implement from scratch to prove I understand what's inside."*
- **On extensibility:** *"TTL adds `expiresAt` per Node. LFU is a new Cache impl with frequency buckets. Multi-tier is a Decorator. Eviction policy as a Strategy if multiple are needed."*

---

## Top mistakes that lose points

- **Storing only the VALUE on the Node, not the key** — eviction becomes O(N) because you have to scan the HashMap to find which key maps to the evicted node.
- **No sentinels** — every add/remove needs null checks for "is this the head?" / "is this the tail?". 30 minutes of off-by-one bugs.
- **`get` not synchronized** — `get` mutates the DLL (moveToHead); concurrent gets corrupt pointers.
- **`put(existing key)` growing the size** — forgetting the early return after replacing value. Causes spurious evictions on update.
- **Using ArrayList instead of DLL** — move-to-head and remove become O(N). Linear scans destroy the O(1) guarantee.
- **Confusing `head` (MRU) and `tail` (LRU)** — pick a convention at the start of the room and stick to it.
- **No bound check on capacity in the constructor** — `LRUCache(0)` or `LRUCache(-1)` becomes a bug-magnet later.
- **Not testing the recency refresh** — the eviction-after-get test is the most common interviewer trap.
- **Implementing LinkedHashMap version WITHOUT the from-scratch version** — interviewer wants to see you understand the internals, not just the JDK shortcut.

---

## Files in this folder (your reference implementation)

| File                                       | What it shows                                                                            |
| ------------------------------------------ | ---------------------------------------------------------------------------------------- |
| `Cache.java`                               | Generic interface — get / put / size / clear                                             |
| `LRUCache.java`                            | **The hot class** — HashMap + DLL composition + sentinel head/tail                       |
| `LinkedHashMapLRUCache.java`               | 10-line "production-clean" variant using JDK's LinkedHashMap + accessOrder               |
| `LRUCacheDriver.java`                      | 6 scenarios — basic / eviction / recency-refresh / update-no-grow / both-impls-agree / **50-thread concurrent burst** |

Run from the project root:

```bash
mvn -q compile exec:java \
  -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.lrucache.LRUCacheDriver
```
