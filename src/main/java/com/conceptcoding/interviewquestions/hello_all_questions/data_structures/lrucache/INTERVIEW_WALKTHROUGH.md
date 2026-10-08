# LRU Cache

> **Usually a short round** (20–30 min) or the first half of one. It's also LeetCode 146, so they expect **working O(1) code** fast, then follow-ups (LFU, TTL, thread safety).
>
> **The crux (what's really being tested):**
> 1. **Two data structures composed:** a `HashMap` for O(1) *find*, plus a doubly linked list for O(1) *reorder* and *evict*.
> 2. **Pointer surgery without bugs:** sentinel head/tail nodes remove every null check.
>
> **Pattern:** a `Cache<K,V>` interface, so LRU / LFU / TTL are swappable implementations.

---

## 1. Plain-language picture

### A small bookshelf
You have a shelf that holds **3 books**. Every time you read a book you put it back at the **left end**. When a 4th book arrives and the shelf is full, you remove the book at the **right end**: the one you haven't touched for the longest. That's **Least Recently Used**.
```
read A, B, C        [C B A]          left = most recent, right = least recent
read A              [A C B]          A moves to the left
add D (full!)       [D A C]          B (right end) is thrown out
```

### Why two structures?
- **Finding** book A on the shelf: scanning is O(n). A `HashMap<key, node>` gives you its exact position in O(1).
- **Moving** A to the front, and **removing** from the back: an array would need shifting (O(n)). A **doubly linked list** does it with 4 pointer changes (O(1)), *if* you already hold the node. And the map gives you the node.

**Each structure fixes the other's weakness.** That composition is the whole answer.

### Sentinels: the two bookends
Put a dummy node at each end (`head` and `tail`). Real nodes always sit *between* them, so "insert at front" and "remove a node" never hit a null neighbour. No special cases for an empty or single-element list.
```
head ⇄ [C] ⇄ [B] ⇄ [A] ⇄ tail          newest = head.next, oldest = tail.prev
```

---

## 2. From story to design

| # | Decision | Rejected | Why |
|---|---|---|---|
| D1 | `HashMap<K, Node>` + doubly linked list | a list alone (O(n) find), a map alone (no order), a `TreeMap` by timestamp (O(log n)) | Only this combination gives O(1) for both `get` and `put`. |
| D2 | **Doubly** linked | singly linked | Removing a node needs its `prev`; a singly linked list would have to search for it. |
| D3 | **Sentinel** head/tail | null checks for first/last | Fewer branches, fewer bugs under interview pressure. |
| D4 | The node stores its **key** | value only | When evicting `tail.prev`, you must remove it from the map too, so you need its key. |
| D5 | `put` on an existing key = update value + move to front, **no eviction** | treating it as new | The size doesn't change; evicting would wrongly drop another entry. |
| D6 | `Cache<K,V>` interface | the concrete class only | LRU, LFU and TTL are swappable for callers (Q1, Q2). |
| D7 | `synchronized` methods | no locking | Even `get` **writes** (it reorders the list), so readers must lock too. |
| D8 | Mention `LinkedHashMap(accessOrder=true)` | pretending it doesn't exist | It's the production answer in 10 lines; build from scratch to show you understand it. |

---

## 3. Patterns that earn their place

| Pattern | Problem it solves | Without it |
|---|---|---|
| **Strategy / interface** (`Cache<K,V>`) | LRU vs LFU vs TTL behind one contract | callers depend on one eviction policy |
| **Decorator** (follow-up) | add hit/miss stats or TTL around *any* cache | stats copied into every implementation |

**Tempting but wrong:** Singleton cache (tests share state), Observer for evictions (only if a real listener exists).

---

## 4. The run (20–30 minutes)

### Clarify (2 min)
> **You:** Fixed capacity; `get` returns the value or null; `put` inserts or updates; when full, evict the least recently used. Both `get` and `put` count as "use"?
> **Interviewer:** Yes. O(1) for both.
> **You:** Thread-safe?
> **Interviewer:** Yes, mention how.

### The code ([LRUCache.java](LRUCache.java))
```java
public interface Cache<K, V> {
    V get(K key);
    void put(K key, V value);
    int size();
    void clear();
}

public class LRUCache<K, V> implements Cache<K, V> {

    private final int capacity;
    private final Map<K, Node<K, V>> index = new HashMap<>();
    private final Node<K, V> head;     // sentinel: newest is head.next
    private final Node<K, V> tail;     // sentinel: oldest is tail.prev

    public LRUCache(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        this.capacity = capacity;
        this.head = new Node<>(null, null);
        this.tail = new Node<>(null, null);
        head.next = tail;
        tail.prev = head;
    }

    @Override
    public synchronized V get(K key) {
        Node<K, V> node = index.get(key);
        if (node == null) return null;
        moveToHead(node);                      // reading counts as "use"
        return node.value;
    }

    @Override
    public synchronized void put(K key, V value) {
        Node<K, V> existing = index.get(key);
        if (existing != null) {                // update: no size change, no eviction
            existing.value = value;
            moveToHead(existing);
            return;
        }
        if (index.size() == capacity) {        // full: evict the least recently used
            Node<K, V> lru = tail.prev;
            removeNode(lru);
            index.remove(lru.key);             // why the node stores its key
        }
        Node<K, V> node = new Node<>(key, value);
        addToHead(node);
        index.put(key, node);
    }

    @Override public synchronized int size() { return index.size(); }

    @Override
    public synchronized void clear() {
        index.clear();
        head.next = tail;
        tail.prev = head;
    }

    private void addToHead(Node<K, V> node) {  // insert right after head
        node.prev = head;
        node.next = head.next;
        head.next.prev = node;
        head.next = node;
    }

    private void removeNode(Node<K, V> node) { // unlink: neighbours point at each other
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    private void moveToHead(Node<K, V> node) {
        removeNode(node);
        addToHead(node);
    }

    static final class Node<K, V> {
        final K key;
        V value;
        Node<K, V> prev, next;
        Node(K key, V value) { this.key = key; this.value = value; }
    }
}
```
**Shape to remember:** `get` = find + move to head. `put` = existing? update + move. Full? remove `tail.prev` from the list **and** the map. Then add to head + map.

**The production version** ([LinkedHashMapLRUCache.java](LinkedHashMapLRUCache.java)), to mention after:
```java
map = new LinkedHashMap<>(16, 0.75f, true) {                 // true = order by access, not insertion
    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > capacity;
    }
};
```

### Dry run (capacity 2)
```
put(a,1)   head ⇄ a ⇄ tail
put(b,2)   head ⇄ b ⇄ a ⇄ tail
get(a)     head ⇄ a ⇄ b ⇄ tail            → 1
put(c,3)   full → evict tail.prev = b → head ⇄ c ⇄ a ⇄ tail
get(b)     → null
put(a,9)   existing → value 9, move to head → head ⇄ a ⇄ c ⇄ tail (size still 2)
```

---

## 5. Follow-ups: answer out loud first, then open

<details>
<summary><b>Q1. Now make it LFU: evict the least frequently used (ties → least recently used).</b></summary>

Three maps keep everything O(1): value per key, **count** per key, and for each count a `LinkedHashSet` of keys (oldest first, for the tie-break). Track `minCount`.
```java
public class LFUCache<K, V> implements Cache<K, V> {
    private final int capacity;
    private final Map<K, V> values = new HashMap<>();
    private final Map<K, Integer> counts = new HashMap<>();                    // key → how often used
    private final Map<Integer, LinkedHashSet<K>> buckets = new HashMap<>();    // count → keys, oldest first
    private int minCount = 0;                                                  // smallest count present

    public LFUCache(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        this.capacity = capacity;
    }

    @Override
    public synchronized V get(K key) {
        if (!values.containsKey(key)) return null;
        touch(key);
        return values.get(key);
    }

    @Override
    public synchronized void put(K key, V value) {
        if (values.containsKey(key)) {
            values.put(key, value);
            touch(key);
            return;
        }
        if (values.size() == capacity) {
            // least frequently used; ties → least recently used (first in the LinkedHashSet)
            K victim = buckets.get(minCount).iterator().next();
            buckets.get(minCount).remove(victim);
            values.remove(victim);
            counts.remove(victim);
        }
        values.put(key, value);
        counts.put(key, 1);
        buckets.computeIfAbsent(1, c -> new LinkedHashSet<>()).add(key);
        minCount = 1;                                             // a brand-new key is always the least used
    }

    // move key from bucket c to bucket c+1
    private void touch(K key) {
        int c = counts.get(key);
        counts.put(key, c + 1);
        buckets.get(c).remove(key);
        if (c == minCount && buckets.get(c).isEmpty()) minCount++;
        buckets.computeIfAbsent(c + 1, x -> new LinkedHashSet<>()).add(key);
    }
    // + size(), clear()
}
```
- **Why `minCount` only ever goes up by 1 in `touch`:** the key moved from `c` to `c + 1`, so if bucket `c` is now empty, the new minimum is `c + 1`.
- **Why it resets to 1 on insert:** the new key has count 1.
- **LRU vs LFU:** LRU suits "recent things matter" (sessions). LFU suits "popular things matter" (a CDN), but old popular items can stick around forever (fix: decay the counts).
</details>

<details>
<summary><b>Q2. Entries should expire after 60 seconds (TTL).</b></summary>

Store the expiry with each value and check it **on read** (lazy expiry, like the rate limiter's refill):
```java
public class TtlCache<K, V> {
    private static final class Entry<V> {
        final V value; final Instant expiresAt;
        Entry(V value, Instant expiresAt) { this.value = value; this.expiresAt = expiresAt; }
    }
    private final Map<K, Entry<V>> map = new HashMap<>();
    private final Duration ttl;
    private final Clock clock;

    public TtlCache(Duration ttl, Clock clock) { this.ttl = ttl; this.clock = clock; }

    public synchronized void put(K key, V value) { map.put(key, new Entry<>(value, clock.instant().plus(ttl))); }

    public synchronized V get(K key) {
        Entry<V> e = map.get(key);
        if (e == null) return null;
        if (!clock.instant().isBefore(e.expiresAt)) {      // lazy expiry: checked on read
            map.remove(key);
            return null;
        }
        return e.value;
    }
}
```
Expired-but-never-read entries waste memory, so add a periodic sweeper, or combine with LRU (the `Entry` lives in the LRU node, so eviction removes them eventually).
</details>

<details>
<summary><b>Q3. Many threads, and the single lock is a bottleneck.</b></summary>

- **Lock striping:** split into N independent LRU caches (segments) by `hash(key) % N`, each with its own lock. Threads on different segments never wait. The trade-off: LRU becomes approximate (per segment, not global).
- **Read-mostly:** a `ReadWriteLock` doesn't help, because `get` writes (it reorders the list).
- **Production:** Caffeine (Java) records accesses in buffers and reorders in batches, which is how it stays fast.

Say why `synchronized` is right for the interview: correct, obvious, and each operation is O(1) and short.
</details>

<details>
<summary><b>Q4. Track the hit rate without touching the cache class.</b></summary>

**Decorator** around any `Cache<K,V>`:
```java
public class StatsCache<K, V> implements Cache<K, V> {
    private final Cache<K, V> inner;
    private final AtomicLong hits = new AtomicLong(), misses = new AtomicLong();
    public StatsCache(Cache<K, V> inner) { this.inner = inner; }

    public V get(K key) {
        V v = inner.get(key);
        if (v == null) misses.incrementAndGet(); else hits.incrementAndGet();
        return v;
    }
    public void put(K key, V value) { inner.put(key, value); }
    public int size() { return inner.size(); }
    public void clear() { inner.clear(); }
    public double hitRate() { long h = hits.get(), t = h + misses.get(); return t == 0 ? 0 : (double) h / t; }
}
```
</details>

<details>
<summary><b>Q5. The cache sits in front of a slow database. What can go wrong?</b></summary>

- **Stale data:** on a DB write, delete the cache key (cache-aside) or write both (write-through).
- **Cache stampede:** a hot key expires and 1,000 requests hit the DB at once. Let one thread load it while the others wait (a per-key lock or `computeIfAbsent`), or refresh it before it expires.
- **Null values:** `get` returning `null` can't tell "not cached" from "cached null". Store a sentinel "NOT_FOUND" value to cache misses too.
</details>

<details>
<summary><b>Q6. A cache shared by 20 servers.</b></summary>

An in-process cache is per server. For a shared one, use Redis/Memcached (Redis has `maxmemory-policy allkeys-lru`, an *approximate* LRU that samples keys). Shard by `hash(key)` with **consistent hashing**, so adding a node moves only ~1/N of the keys.
</details>

---

## 6. Traps that cost points
1. Forgetting to remove the evicted key from the **map** (the node stores its key for this).
2. Evicting on `put` of an **existing** key.
3. `get` not moving the node to the front.
4. Singly linked list, so removal is O(n).
5. No sentinels: null-pointer bugs at the ends.
6. Thinking reads don't need the lock (`get` reorders the list).

## 7. Recall check
1. Why both a HashMap and a doubly linked list? What does each give?
2. Why does the node store its key?
3. Write `addToHead` and `removeNode` from memory (4 + 2 lines).
4. LFU: what are the three maps, and when does `minCount` change?
5. Why can't `get` use a read lock?

**Rebuild in 8 minutes:** the `Node` class · sentinels · `addToHead` · `removeNode` · `get` · `put` (existing / evict / insert).

---

**Files:** `Cache` (interface) · `LRUCache` (from scratch) · `LinkedHashMapLRUCache` (production) · `LRUCacheDriver` (basic, eviction, get refreshes recency, update doesn't grow, both versions agree, concurrent burst)
```bash
mvn -q compile exec:java -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.data_structures.lrucache.LRUCacheDriver
```
