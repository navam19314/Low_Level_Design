# 05 — Collections 🔥🔥

> Format: **Say this** = your 30-sec interview answer · **Key points** · **Most asked** (Q → short answer) · **Trap**

---

## 1. Hierarchy

**Say this:** "The Collections Framework in `java.util` gives ready-made data structures. `Collection` is the root for single elements and has three main children — List, Set and Queue. Map is separate because it stores key–value pairs, not single elements."

```
Iterable → Collection
             ├── List  : ArrayList, LinkedList, Vector → Stack
             ├── Set   : HashSet → LinkedHashSet, TreeSet
             └── Queue : PriorityQueue, Deque → ArrayDeque, LinkedList
Map (separate): HashMap → LinkedHashMap, TreeMap, Hashtable, ConcurrentHashMap
```

- **List** = ordered, index-based, duplicates allowed.
- **Set** = unique elements only.
- **Queue** = holds elements for processing (FIFO or priority). **Deque** = both ends → queue and stack.
- **Map** = key → value, keys unique.

**Most asked**
- **Why doesn't Map extend Collection?** It stores pairs, not single elements — `add(E)` makes no sense for it.
- **`Collection` vs `Collections`?** Interface vs utility class (`sort`, `unmodifiableList`, `synchronizedList`).

---

## 2. ArrayList vs LinkedList 🔥

**Say this:** "ArrayList is a resizable array, so `get(i)` is O(1). LinkedList is a doubly linked list, so getting by index is O(n). In practice I use ArrayList almost always — it is cache-friendly and uses less memory. For queue/stack I use ArrayDeque, not LinkedList."

| | ArrayList | LinkedList |
|---|---|---|
| Internal | Dynamic array | Doubly linked list |
| `get(i)` | **O(1)** | O(n) |
| Add at end | O(1) amortized | O(1) |
| Insert/remove in middle | O(n) (shift) | O(n) to find, O(1) to link |
| Memory | Less | More (prev + next pointers) |

**Key points:** default capacity **10** (allocated on first `add`), grows **1.5x** (`old + old/2`) and copies to a new array.

**Most asked**
- **ArrayList vs Vector?** Vector is synchronized (slow) and grows 2x. Legacy — don't use.

---

## 3. HashMap Internals 🔥🔥

**Say this:** "HashMap stores entries in an array of buckets. On `put`, it computes the key's hash, finds the bucket with `(n-1) & hash`, and if the bucket has entries it uses `equals()` to find the same key and replace the value — otherwise it adds a new node. Collisions form a linked list, which becomes a Red-Black tree when it gets long. When size crosses capacity × 0.75, the table doubles. Average get/put is O(1)."

```
[0] → null-key entry
[1] → (K1,V1) → (K5,V5)     ← collision = linked list
[3] → Red-Black Tree        ← list ≥ 8 and table ≥ 64
```
Each node: `hash, key, value, next`.

**`put(key, value)` steps**
1. `hash = h ^ (h >>> 16)` → **hash spreading**: mixes high bits into low bits to reduce collisions.
2. `index = (n - 1) & hash` → same as `hash % n` because n is a power of 2.
3. Empty bucket → put node.
4. Else walk the bucket: same `hash` **and** `equals()` true → **replace value**; not found → add at **tail**.
5. Bucket size ≥ **8** → treeify (only if table ≥ **64**, else resize instead).
6. `size > threshold` → **resize** (double).

**`get(key)`:** hash → index → walk list/tree with `equals()` → value or null.

| Key numbers | Value |
|---|---|
| Default capacity | **16** |
| Load factor | **0.75** |
| Resize threshold | 16 × 0.75 = **12** |
| Resize | **Doubles** capacity |
| Treeify | **8** (and capacity ≥ **64**) |
| Untreeify | **6** |

**Most asked**
- **Why is capacity a power of 2?** So `(n-1) & hash` is a fast modulo and uses all bits evenly. Also on resize each entry stays at the same index or moves to `index + oldCap`.
- **Why load factor 0.75?** Good balance between memory and collisions.
- **Null key?** One allowed, always in bucket **0**. Many null values allowed.
- **Java 7 vs Java 8?** Java 7: list only, insert at **head**, worst O(n), concurrent resize could cause an infinite loop. Java 8: list + **tree**, insert at **tail**, worst **O(log n)**.
- **Mutable key changed after `put()`?** hashCode changes → lookup goes to the wrong bucket → `get()` returns null, entry is "lost". Use immutable keys (String, Integer, records).
- **hashCode() always returns the same value?** Still works, but all entries go to one bucket → O(n) (O(log n) after treeify).
- **Why override both `equals()` and `hashCode()`?** hashCode finds the bucket, equals finds the key inside it. Equal objects **must** have equal hash codes, else duplicates / failed lookups.

**Trap:** HashMap is **not** thread-safe even in Java 8 — concurrent puts can lose data. Use ConcurrentHashMap.

---

## 4. LinkedHashMap & LRU Cache 🔥

**Say this:** "LinkedHashMap is a HashMap plus a doubly linked list through all entries, so it keeps insertion order — or access order if I pass `true` in the constructor. With access order and `removeEldestEntry`, it becomes an LRU cache in a few lines."

```java
class LRUCache<K, V> extends LinkedHashMap<K, V> {
    private final int capacity;
    LRUCache(int capacity) { super(capacity, 0.75f, true); this.capacity = capacity; } // true = access order
    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) { return size() > capacity; }
}
```

**Most asked**
- **LRU without LinkedHashMap?** HashMap<key, node> + your own doubly linked list → O(1) get/put; move node to head on access, remove tail when full.

---

## 5. TreeMap

**Say this:** "TreeMap keeps keys sorted — natural order or a Comparator — using a Red-Black tree, so all operations are O(log n). It also gives navigation methods like `floorKey`, `ceilingKey`, `firstKey`. No null key."

| | HashMap | LinkedHashMap | TreeMap |
|---|---|---|---|
| Order | None | Insertion / access | Sorted |
| get/put | O(1) | O(1) | O(log n) |
| Null key | 1 allowed | 1 allowed | ❌ (NPE) |

---

## 6. HashSet

**Say this:** "HashSet is backed by a HashMap — the element is the key and the value is a dummy object `PRESENT`. LinkedHashSet is backed by LinkedHashMap (insertion order), TreeSet by TreeMap (sorted)."

**Most asked**
- **How does HashSet prevent duplicates?** `add(e)` calls `map.put(e, PRESENT)`; if the key already exists, put returns the old value (not null) → `add()` returns false.

**Trap:** Custom objects without `equals()`/`hashCode()` → "duplicates" get added.

---

## 7. ConcurrentHashMap 🔥🔥

**Say this:** "ConcurrentHashMap is a thread-safe HashMap that doesn't lock the whole map. In Java 8+, reads take no lock, writing to an empty bucket uses CAS, and writing to a non-empty bucket locks only that bucket's first node with `synchronized`. So many threads can read and write different buckets at the same time."

**Key points**
- **Java 7:** map split into **16 Segments**, each with its own lock → max 16 concurrent writers.
- **Java 8+:** segments removed → **lock-free reads** (`volatile`), **CAS** on empty bucket, `synchronized` on **bucket head**. Treeifies like HashMap; resize is shared by multiple threads.
- Iterators are **weakly consistent** — no `ConcurrentModificationException`.

| | HashMap | Hashtable | synchronizedMap | ConcurrentHashMap |
|---|---|---|---|---|
| Thread-safe | ❌ | ✅ | ✅ | ✅ |
| Locking | — | Whole map | Whole map | **Per bucket** |
| Performance | Fast | Slow | Slow | **Fast** |
| Null key/value | ✅ | ❌ | ✅ | ❌ |
| Iterator | Fail-fast | Fail-fast | Fail-fast | Weakly consistent |

**Most asked**
- **Why no nulls in CHM?** Ambiguity — `get()` returns null: is the key missing or the value null? You can't check with `containsKey()` because another thread may change the map in between.
- **Is `if (!map.containsKey(k)) map.put(k, v)` safe?** No — check-then-act is two operations. Use atomic methods: `putIfAbsent`, `computeIfAbsent(k, x -> new ArrayList<>())`, `merge(k, 1, Integer::sum)`.
- **Is `size()` exact?** It is an estimate under concurrent updates.

---

## 8. Fail-Fast vs Fail-Safe 🔥

**Say this:** "Fail-fast iterators, like ArrayList's and HashMap's, throw `ConcurrentModificationException` if the collection is structurally modified during iteration. They detect it with a `modCount` counter. Fail-safe iterators, like CopyOnWriteArrayList and ConcurrentHashMap, don't throw — they work on a snapshot or a concurrent structure, so they may not see the latest changes."

```java
for (String s : list) if (s.equals("A")) list.remove(s);   // ❌ CME
list.removeIf(s -> s.equals("A"));                          // ✅ (or iterator.remove())
```

**Most asked**
- **How to remove while iterating?** `iterator.remove()` or `removeIf()`.
- **CopyOnWriteArrayList?** Every write copies the array → good for many reads, few writes (e.g. listeners).

**Trap:** CME can happen in a **single thread** — it is about modification during iteration, not only multithreading.

---

## 9. Comparable vs Comparator 🔥

**Say this:** "Comparable is implemented by the class itself and defines its one natural order with `compareTo()`. Comparator is a separate object or lambda with `compare()`, so I can have many custom orders without touching the class."

| | Comparable | Comparator |
|---|---|---|
| Package | `java.lang` | `java.util` |
| Method | `compareTo(T o)` | `compare(T a, T b)` |
| Where | Inside the class | Separate class / lambda |
| Orders | One (natural) | Many |

```java
list.sort(Comparator.comparing(Emp::getSalary).reversed().thenComparing(Emp::getName));
```

**Most asked**
- **Return value meaning?** Negative → first comes before, 0 → equal, positive → after.

**Trap:** Don't write `return a - b;` — it can **overflow** for large/negative ints. Use `Integer.compare(a, b)`.

---

## 10. PriorityQueue & ArrayDeque

**Say this:** "PriorityQueue is a binary min-heap — `poll()` always gives the smallest element; for a max-heap I pass `Comparator.reverseOrder()`. `offer`/`poll` are O(log n), `peek` is O(1). For stack and queue I use ArrayDeque — it is faster than legacy `Stack` and `LinkedList`."

```java
PriorityQueue<Integer> max = new PriorityQueue<>(Comparator.reverseOrder());
Deque<Integer> stack = new ArrayDeque<>(); stack.push(1); stack.pop();
```

**Trap:** Iterating or printing a PriorityQueue is **not** sorted — only `poll()` order is. No nulls in PriorityQueue/ArrayDeque. (`offer/poll/peek` return false/null; `add/remove/element` throw.)

---

## 11. Collection Traps 🔥

```java
List<Integer> a = Arrays.asList(1, 2, 3);
a.set(0, 9);   // ✅ allowed
a.add(4);      // ❌ UnsupportedOperationException (fixed size, backed by array)

List<Integer> b = List.of(1, 2, 3);   // fully immutable; List.of(null) → NPE
List<Integer> c = Collections.unmodifiableList(orig); // read-only VIEW: changes to orig show in c

List<Integer> list = new ArrayList<>(List.of(10, 20, 30));
list.remove(1);                    // removes INDEX 1 → [10, 30]
list.remove(Integer.valueOf(10));  // removes VALUE 10
```

**Most asked**
- **Immutable vs unmodifiable?** Immutable never changes. Unmodifiable view only blocks changes through that reference.

---

## 12. Big-O Cheat Sheet

| Collection | Add | Remove | Get / Search |
|---|---|---|---|
| ArrayList | O(1)* | O(n) | O(1) index / O(n) search |
| LinkedList | O(1) ends | O(1) ends | O(n) |
| HashMap / HashSet | O(1) | O(1) | O(1) (worst O(log n)) |
| LinkedHashMap | O(1) | O(1) | O(1) |
| TreeMap / TreeSet | O(log n) | O(log n) | O(log n) |
| PriorityQueue | O(log n) | O(log n) poll | O(1) peek |
| ArrayDeque | O(1) ends | O(1) ends | O(n) search |

*amortized

---

## 📌 Quick Revision
- **List** = ordered + duplicates. **Set** = unique. **Queue** = FIFO/priority. **Map** = key → value (not a Collection).
- ArrayList: array, default 10, grows **1.5x**, O(1) get — default choice. LinkedList = doubly linked list.
- HashMap: hash spread `h ^ (h >>> 16)`, index `(n-1) & hash`. Same key = same hash **and** `equals()`. One null key → bucket 0.
- Java 7 → head insert, list only, O(n) worst. Java 8 → tail insert, tree, O(log n) worst.
- Use immutable keys; override **both** `equals()` and `hashCode()`.
- LinkedHashMap (access order + `removeEldestEntry`) = **LRU cache**. TreeMap = sorted, O(log n), no null key.
- HashSet = HashMap with dummy value; `put` returns old value → duplicate rejected.
- CHM Java 8: **lock-free reads, CAS on empty bucket, synchronized on bucket head**. No nulls. Use `computeIfAbsent`/`merge`.
- Fail-fast (`modCount` → CME) vs fail-safe (snapshot/concurrent). Remove via `iterator.remove()`/`removeIf`.
- Comparable = one natural order; Comparator = many. `Integer.compare`, not `a - b`.
- PriorityQueue = min-heap (iteration not sorted). ArrayDeque for stack/queue.
- `Arrays.asList` = fixed size. `List.of` = immutable, no nulls. `unmodifiableList` = view.

**Memory hook (HashMap):** *"16 → 0.75 → 12 → double; 8 trees, 6 lists, 64 minimum"*
