# 06 — Java 8+ Features, Streams & Generics 🔥

> Format: **Say this** = your 30-sec interview answer · **Key points** · **Most asked** (Q → short answer) · **Trap**

---

## 1. Java 8 Features (one line)

**Say this:** "Java 8 brought functional programming to Java — lambdas, functional interfaces, method references, the Streams API, Optional, default/static methods in interfaces, and the new `java.time` Date/Time API (immutable and thread-safe)."

---

## 2. Lambda Expressions 🔥

**Say this:** "A lambda is a short anonymous function — no name, no class — that I can pass as an argument or store in a variable. It implements a functional interface. It replaces bulky anonymous classes, e.g. `(a, b) -> a.length() - b.length()` for a Comparator."

```java
int x = 10;
Runnable r = () -> System.out.println(x);   // ✅ x is effectively final
// x = 20;                                  // ❌ now the lambda won't compile
```

**Most asked**
- **Why must captured locals be effectively final?** The lambda gets a **copy** of the value and may run later or on another thread — if the variable could change, the copy would be stale (and racy).
- **Lambda vs anonymous class?** Lambda works only for functional interfaces, `this` = enclosing class, no extra `.class` file (`invokedynamic`). Anonymous class works for any interface/abstract class, `this` = itself, can have fields.

---

## 3. Functional Interfaces 🔥

**Say this:** "A functional interface has exactly one abstract method. It can have any number of default and static methods. It is the target type of a lambda. `@FunctionalInterface` is optional but makes the compiler enforce the rule."

| Interface | Method | Input → Output | Example |
|---|---|---|---|
| `Predicate<T>` | `test` | T → boolean | `s -> s.isEmpty()` |
| `Function<T,R>` | `apply` | T → R | `s -> s.length()` |
| `Consumer<T>` | `accept` | T → void | `System.out::println` |
| `Supplier<T>` | `get` | () → T | `() -> new ArrayList<>()` |
| `BiFunction<T,U,R>` | `apply` | (T, U) → R | `(a, b) -> a + b` |
| `UnaryOperator<T>` | `apply` | T → T | `s -> s.toUpperCase()` |
| `BinaryOperator<T>` | `apply` | (T, T) → T | `Integer::sum` |

**Most asked**
- **`Comparator` has `equals()` too — still functional?** Yes. Public `Object` methods don't count.
- **Chaining?** `Predicate`: `and/or/negate`. `Function`: `f.andThen(g)` = f then g; `f.compose(g)` = g then f.

**Trap:** Primitive versions (`IntPredicate`, `ToIntFunction`) exist to avoid boxing.

---

## 4. Method References

**Say this:** "A method reference (`::`) is a shorter lambda that just calls an existing method."

| Type | Example | Lambda |
|---|---|---|
| Static | `Integer::parseInt` | `s -> Integer.parseInt(s)` |
| Instance of a specific object | `System.out::println` | `x -> System.out.println(x)` |
| Instance of an arbitrary object of a type | `String::toUpperCase` | `s -> s.toUpperCase()` |
| Constructor | `ArrayList::new` | `() -> new ArrayList<>()` |

---

## 5. Streams API 🔥🔥

**Say this:** "A stream is a pipeline to process data from a source like a collection, in a functional style — filter, map, collect. It doesn't store data and doesn't modify the source. It's lazy — nothing runs until a terminal operation is called — and it's single-use."

| Intermediate (lazy, returns Stream) | Terminal (runs pipeline, gives result) |
|---|---|
| `filter`, `map`, `flatMap`, `distinct` | `collect`, `toList()` (16), `forEach`, `reduce` |
| `sorted`, `limit`, `skip`, `peek` | `count`, `min`, `max` |
| `mapToInt`, `boxed`, `takeWhile` (9) | `findFirst`, `findAny`, `anyMatch`, `allMatch`, `noneMatch` |

**Lazy + short-circuit:** each element flows through the whole pipeline one by one, and it stops as soon as the answer is known.
```java
Stream.of(1, 2, 3, 4)
      .filter(x -> { System.out.println("filter " + x); return x > 1; })
      .map(x -> x * 2)
      .findFirst();
// prints: filter 1, filter 2 → stops. 3 and 4 are never touched.
```

**Key points**
- **Short-circuit ops:** `limit`, `findFirst`, `findAny`, `anyMatch`, `allMatch`, `noneMatch`, `takeWhile` — work even on infinite streams (`Stream.iterate(1, n -> n + 1).limit(5)`).
- **Single-use:** calling a second terminal op → `IllegalStateException: stream has already been operated upon or closed`.
- **Collection vs Stream:** Collection stores data, eager, iterate many times · Stream processes data, lazy, single use, internal iteration.
- **`findFirst` vs `findAny`:** `findFirst` respects order; `findAny` returns any element — faster in parallel streams.

**Most asked**
- **`map` vs `flatMap`?** `map` = one-to-one. `flatMap` = each element → a stream, then flatten into one. `[[1,2],[3,4]]` → `map(List::size)` = `[2,2]`; `flatMap(List::stream)` = `[1,2,3,4]`.
- **`reduce`?** Combines all elements into one: `reduce(0, Integer::sum)`. Without identity → returns `Optional` (stream may be empty).
- **Does a stream modify the source?** No.

---

## 6. Collectors 🔥

**Say this:** "Collectors are ready-made recipes for `collect()` — to gather results into a List, Set, Map, String or a summary like count or average."

```java
.collect(Collectors.toList())                                              // List
.collect(Collectors.toMap(Emp::getId, Emp::getName))                      // Map<id, name>
.collect(Collectors.toMap(Emp::getDept, Emp::getName, (a, b) -> a))       // keep first on duplicate key
.collect(Collectors.groupingBy(Emp::getDept))                             // Map<Dept, List<Emp>>
.collect(Collectors.groupingBy(Emp::getDept, Collectors.counting()))      // Map<Dept, Long>
.collect(Collectors.groupingBy(Emp::getDept, Collectors.averagingDouble(Emp::getSalary)))
.collect(Collectors.groupingBy(Emp::getDept, Collectors.maxBy(Comparator.comparingDouble(Emp::getSalary))))
.collect(Collectors.partitioningBy(e -> e.getSalary() > 50000))           // Map<Boolean, List<Emp>>
.collect(Collectors.joining(", "))                                        // "a, b, c"
```

**Most asked**
- **`groupingBy` vs `partitioningBy`?** `groupingBy` = any key, many groups. `partitioningBy` = predicate, exactly 2 groups (`true`/`false`, both always present).
- **`Stream.toList()` vs `Collectors.toList()`?** `toList()` (16) is unmodifiable. `Collectors.toList()` has no guarantee (currently `ArrayList`).

**Trap:** `toMap` with duplicate keys → `IllegalStateException`. Fix: pass a merge function `(old, neu) -> old`.

---

## 7. Parallel Streams

**Say this:** "A parallel stream splits data into chunks and processes them on multiple threads using the shared `ForkJoinPool.commonPool()`. It helps only for large, CPU-heavy work with no shared state."

**Key points**
- **Use:** large data, CPU-bound, stateless operations, order doesn't matter.
- **Avoid:** small data (overhead > gain), I/O or blocking calls (blocks the common pool for the whole app), `LinkedList` source (hard to split), shared mutable state.

**Trap:** `forEach` in parallel → random order (use `forEachOrdered`). Adding to a shared `ArrayList` → not thread-safe; use `collect()`.

---

## 8. Stream Coding Questions 🔥🔥

```java
// Assume: List<Employee> emps (name, dept, salary); List<Integer> nums; String str; List<String> words, names

// 1. Even numbers                          [1,2,3,4] → [2,4]
nums.stream().filter(n -> n % 2 == 0).toList();

// 2. Sum of squares                        [1,2,3] → 14
nums.stream().mapToInt(n -> n * n).sum();

// 3. Max / Min                             [3,9,1] → 9
nums.stream().max(Integer::compare).get();

// 4. Second highest number                 [5,9,9,3] → Optional[5]
nums.stream().distinct().sorted(Comparator.reverseOrder()).skip(1).findFirst();

// 5. Find duplicates                       [1,2,2,3,3] → [2,3]
Set<Integer> seen = new HashSet<>();
nums.stream().filter(n -> !seen.add(n)).collect(Collectors.toSet());

// 6. Character frequency                   "aab" → {a=2, b=1}
str.chars().mapToObj(c -> (char) c)
   .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

// 7. First non-repeated character          "swiss" → Optional[w]
str.chars().mapToObj(c -> (char) c)
   .collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()))
   .entrySet().stream().filter(e -> e.getValue() == 1).map(Map.Entry::getKey).findFirst();

// 8. Group employees by dept               → {IT=[Amit, Ravi], HR=[Neha]}
emps.stream().collect(Collectors.groupingBy(Employee::getDept));

// 9. Count per dept                        → {IT=2, HR=1}
emps.stream().collect(Collectors.groupingBy(Employee::getDept, Collectors.counting()));

// 10. Highest paid per dept                → {IT=Optional[Ravi], HR=Optional[Neha]}
emps.stream().collect(Collectors.groupingBy(Employee::getDept,
        Collectors.maxBy(Comparator.comparingDouble(Employee::getSalary))));

// 11. Second highest salary                [50k,90k,90k,70k] → Optional[70k]
emps.stream().map(Employee::getSalary).distinct()
    .sorted(Comparator.reverseOrder()).skip(1).findFirst();

// 12. Average salary per dept              → {IT=75000.0, HR=50000.0}
emps.stream().collect(Collectors.groupingBy(Employee::getDept,
        Collectors.averagingDouble(Employee::getSalary)));

// 13. Sort by salary desc, then name       → [Ravi(90k), Amit(60k), Neha(60k)]
emps.stream().sorted(Comparator.comparing(Employee::getSalary).reversed()
        .thenComparing(Employee::getName)).toList();

// 14. Top 3 salaries                       → 3 highest-paid employees
emps.stream().sorted(Comparator.comparing(Employee::getSalary).reversed()).limit(3).toList();

// 15. Names joined                         → "Amit, Ravi, Neha"
emps.stream().map(Employee::getName).collect(Collectors.joining(", "));

// 16. Partition by salary > 50k            → {false=[Neha], true=[Amit, Ravi]}
emps.stream().collect(Collectors.partitioningBy(e -> e.getSalary() > 50000));

// 17. Reverse each word                    "hello java" → "olleh avaj"
Arrays.stream(str.split(" ")).map(w -> new StringBuilder(w).reverse().toString())
      .collect(Collectors.joining(" "));

// 18. Longest string                       [hi, hello, hey] → Optional[hello]
words.stream().max(Comparator.comparingInt(String::length));

// 19. Flatten list of lists                [[1,2],[3,4]] → [1,2,3,4]
nested.stream().flatMap(List::stream).toList();

// 20. Names starting with 'A', uppercased  [Amit, Ravi, Anil] → [AMIT, ANIL]
names.stream().filter(n -> n.startsWith("A")).map(String::toUpperCase).toList();
```

---

## 9. Optional 🔥

**Say this:** "Optional is a container that may or may not hold a value. It forces the caller to handle the 'no value' case and helps avoid NullPointerException. It's meant for method return types."

```java
Optional<String> name = Optional.ofNullable(getName());
name.orElse("Unknown");                         // default (always evaluated)
name.orElseGet(() -> fetchDefault());           // lazy default
name.orElseThrow(() -> new NotFoundException());
name.map(String::toUpperCase).ifPresent(System.out::println);
```

**Most asked**
- **`orElse` vs `orElseGet`?** `orElse(x)` always evaluates `x`, even if a value is present. `orElseGet(supplier)` runs only when empty → use for expensive defaults (DB call).
- **Where not to use it?** Not as fields, method parameters, or in collections — only return types.

**Trap:** `Optional.of(null)` → NPE (use `ofNullable`). `get()` on empty → `NoSuchElementException`.

---

## 10. Java 9 → 21 Highlights 🔥

| Version | Feature | Example |
|---|---|---|
| 9 | Immutable collection factories | `List.of()`, `Set.of()`, `Map.of()` |
| 10 | `var` (local type inference) | `var list = new ArrayList<String>();` |
| 11 | String methods | `isBlank()`, `strip()`, `lines()`, `repeat()` |
| 14 | Switch expressions | `case SAT, SUN -> "Weekend";` (returns value, no fall-through, `yield`) |
| 15 | Text blocks | `"""` multi-line string `"""` |
| 16 | **Records**, pattern matching `instanceof` | `if (o instanceof String s) s.length();` |
| 17 | **Sealed classes** | `sealed interface Shape permits Circle, Square` |
| 21 | Pattern matching for `switch`, record patterns | `case Circle c -> ...` |
| 21 | **Virtual threads** | `Executors.newVirtualThreadPerTaskExecutor()` |

**LTS versions:** 8, 11, 17, 21, 25.

**Most asked**
- **`var` — is Java dynamically typed now?** No. Type is fixed at compile time. Only for local variables (not fields/params/return types); must be initialized.

**Trap:** `List.of(...)` is immutable (`add` → `UnsupportedOperationException`) and doesn't allow `null`.

---

## 11. Records (Java 16) 🔥

**Say this:** "A record is an immutable data carrier. The compiler generates private final fields, a canonical constructor, accessors, `equals`, `hashCode` and `toString`. Perfect for DTOs."

```java
record Point(int x, int y) {
    Point { if (x < 0) throw new IllegalArgumentException(); }  // compact constructor for validation
}
new Point(1, 2).x();   // 1 — accessor is x(), NOT getX()
```

**Most asked**
- **Can a record extend a class?** No — it already extends `Record` and is implicitly `final`. It **can** implement interfaces.
- **Can it have extra fields?** No extra instance fields; static fields and methods are fine.

**Trap:** Only shallowly immutable — a `List` component can still be modified. Use `List.copyOf()` in the constructor.

---

## 12. Sealed Classes (Java 17)

**Say this:** "Sealed classes restrict which classes can extend or implement them using `permits`. So the compiler knows all subtypes — and a `switch` over them can be exhaustive without a `default`."

```java
sealed interface Shape permits Circle, Square {}
final class Circle implements Shape {}
final class Square implements Shape {}

String d = switch (shape) {      // Java 21: no default needed
    case Circle c -> "circle";
    case Square s -> "square";
};
```

**Trap:** Permitted subclasses must be `final`, `sealed`, or `non-sealed`.

---

## 13. Generics 🔥

**Say this:** "Generics let classes and methods work with any type using a type parameter like `<T>`, with type checking at compile time. Benefits: type safety, no casting, reusable code. They're implemented by **type erasure** — the generic type info is removed after compilation, so at runtime `List<String>` and `List<Integer>` are both just `List`. This was done for backward compatibility."

**Key points**
- **Can't do (because of erasure):** `new T()`, `new T[10]`, `List<int>` (use `Integer`), `instanceof List<String>`, overload `m(List<String>)` and `m(List<Integer>)`.
- **Bounded type:** `<T extends Number>` lets you call `Number` methods on `T`.
- **Invariance:** `List<String>` is **not** a `List<Object>` (else you could add an `Integer` to it). Arrays **are** covariant → `ArrayStoreException` at runtime.
- **Raw type** (`List` without `<>`): no type checks, legacy only — avoid.

**Wildcards + PECS (Producer Extends, Consumer Super)**

| Wildcard | Accepts | Read | Write |
|---|---|---|---|
| `<?>` | any type | as `Object` | ❌ (only `null`) |
| `<? extends T>` | T or child | ✅ as T | ❌ |
| `<? super T>` | T or parent | only as `Object` | ✅ T |

**Most asked**
- **Why can't you add to `List<? extends Number>`?** It could really be a `List<Double>`; adding an `Integer` would break it.
- **PECS example?** `Collections.copy(List<? super T> dest, List<? extends T> src)` — read from src, write to dest.

---

## 📌 Quick Revision
- **Lambda** = anonymous function implementing a **functional interface** (one abstract method). Captured locals must be **effectively final**.
- **Predicate** T→boolean · **Function** T→R · **Consumer** T→void · **Supplier** ()→T · Unary/BinaryOperator = same types.
- **Method ref** `::` — static, specific object, arbitrary object of a type, constructor.
- **Streams:** don't store, don't modify source, **lazy**, **single-use**. Intermediate vs terminal. Short-circuit stops early.
- `map` = one-to-one, `flatMap` = one-to-many + flatten. `reduce` = combine into one.
- `groupingBy` (many groups) vs `partitioningBy` (true/false). **`toMap` duplicate keys → merge function.**
- **Parallel streams** → common ForkJoinPool; avoid for I/O, small data, shared state.
- **Optional** → return types only; `orElseGet` for expensive defaults; never `Optional.of(null)`.
- **Records** (16) = immutable data class. **Sealed** (17) = restricted inheritance. Pattern-matching `switch` + **virtual threads** (21).
- LTS: **8, 11, 17, 21, 25**.
- **Generics:** compile-time safety, **type erasure**, invariance, **PECS** — extends = read, super = write.
