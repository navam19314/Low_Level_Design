# 03 — String, Object Class, Immutability & Keywords 🔥

> Format: **Say this** = your 30-sec interview answer · **Key points** · **Most asked** (Q → short answer) · **Trap**

---

## 1. `static` 🔥

**Say this:** "`static` means the member belongs to the class, not to any object — there is one shared copy. Static methods are called using the class name and can't use `this` or instance members directly. A static block runs once, when the class is loaded."

**Most asked**

- **Why is** `main` **static?** So the JVM can call it without creating an object.
- **Can we overload** `main`**?** Yes, but the JVM only calls `public static void main(String[] args)`.
- **Can we override** `main` **/ any static method?** No. Static methods are **hidden**, not overridden — the call depends on the reference type, not the object.
- **Can a static method use instance variables?** Not directly — it needs an object reference.

---



## 2. `final` vs `finally` vs `finalize` 🔥

**Say this:** "`final` is a keyword: a final variable can't be reassigned, a final method can't be overridden, and a final class can't be extended — like `String`. `finally` is the block after try/catch that always runs, used for cleanup. `finalize()` was an old GC hook on `Object` — it's deprecated and should not be used."

```java
final List<String> list = new ArrayList<>();
list.add("A");              // ✅ content can change
list = new ArrayList<>();   // ❌ reference can't change
```

**Most asked**

- **Does** `finally` **always run?** Yes, even after `return` — except `System.exit()` or JVM crash.
- **Why is** `finalize()` **deprecated?** No guarantee when (or if) it runs, slow, unsafe. Deprecated in Java 9, marked for removal in Java 18. Use try-with-resources instead.

**Trap:** `final` reference ≠ immutable object.

---



## 3. Initialization Order 🔥 (output question)

**Say this:** "Static blocks run once when the class loads — parent first, then child. Then for every `new`: parent instance block, parent constructor, child instance block, child constructor."

```java
class Parent {
    static { System.out.println("1 Parent static"); }
    { System.out.println("3 Parent instance"); }
    Parent() { System.out.println("4 Parent constructor"); }
}
class Child extends Parent {
    static { System.out.println("2 Child static"); }
    { System.out.println("5 Child instance"); }
    Child() { System.out.println("6 Child constructor"); }
}
new Child();   // 1 2 3 4 5 6
new Child();   // 3 4 5 6   (static runs only once)
```

**Key points:** static fields and static blocks run in the order written. Instance blocks run after `super()` returns, before the rest of the constructor.

---



## 4. `equals()` & `hashCode()` Contract 🔥

**Say this:** "`==` compares references; `equals()` compares content. `Object` is the parent of every class, and its default `equals()` is just `==`. HashMap and HashSet use `hashCode()` to find the bucket and `equals()` to find the key inside it. So the rule is: if two objects are equal, they must have the same hashCode — always override both together."

```java
class Emp {
    int id;
    @Override public boolean equals(Object o) {
        return o instanceof Emp e && id == e.id;   // Java 16+ pattern matching
    }
    @Override public int hashCode() { return Objects.hash(id); }
}
```

**Key points:** Equal objects → same hashCode (must). Same hashCode → not necessarily equal (collision).

**Most asked**

- **Override only** `equals()`**?** Equal objects get different hashCodes → different buckets → **duplicates in HashSet**, `map.get()` returns **null**.
- **Override only** `hashCode()`**?** Same bucket, but `equals()` is still `==` → still treated as different keys.
- **Object class methods?** `equals`, `hashCode`, `toString`, `getClass`, `clone`, `wait/notify/notifyAll`, `finalize` (deprecated).

**Trap:** Using a mutable object as a HashMap key — if a field used in `hashCode()` changes, the entry is lost.

---



## 5. String vs StringBuilder vs StringBuffer 🔥

**Say this:** "String is immutable — every change creates a new object. StringBuilder is mutable and not synchronized, so it's the fastest — use it for building strings in a loop. StringBuffer is also mutable but synchronized, so thread-safe and slower — rarely used today."


|              | String                | StringBuilder               | StringBuffer                        |
| ------------ | --------------------- | --------------------------- | ----------------------------------- |
| Mutable?     | No                    | Yes                         | Yes                                 |
| Thread-safe? | Yes (immutable)       | No                          | Yes (synchronized)                  |
| Speed        | Slow for many changes | Fastest                     | Slower                              |
| Use when     | Fixed text            | Many changes, single thread | Many changes, shared across threads |


**Most asked**

- **Why not** `s += i` **in a loop?** Creates a new String each time → O(n²) copying + garbage. Use `StringBuilder.append()`.
- **Does** `StringBuilder` **override** `equals()`**?** No — compare with `sb1.toString().equals(sb2.toString())` or `sb1.compareTo(sb2) == 0` (Java 11+).

---



## 6. Why String is Immutable 🔥

**Say this:** "Four reasons. One, the String pool — many references share one literal, so if one could change it, all would change. Two, security — class names, file paths, DB URLs and usernames can't be changed after they're validated. Three, it's thread-safe for free. Four, the hashCode is cached, which makes String a perfect HashMap key."

**Most asked**

- **Why** `char[]` **for passwords instead of String?** A String stays in memory (maybe in the pool) until GC and can show up in heap dumps. A `char[]` can be wiped right after use: `Arrays.fill(pwd, '0')`.
- **Compile-time vs runtime concatenation?**

```java
String a = "ab";
String b = "a" + "b";   // a == b → true   (compile-time constant, pooled)
String x = "a";
String c = x + "b";     // a == c → false  (runtime → new object)
final String y = "a";
String d = y + "b";     // a == d → true   (final → compile-time constant)
```

**Trap:** `c.intern() == a` is `true` — `intern()` returns the pooled copy.

---



## 7. Immutable Class 🔥

**Say this:** "An immutable object can't change after it's created — like `String`, `Integer`, `LocalDate`. Steps: make the class `final`, all fields `private final`, no setters, set everything in the constructor, and make **defensive copies** of mutable fields — both when taking them in and when returning them."

```java
public final class Employee {
    private final String name;
    private final List<String> skills;

    public Employee(String name, List<String> skills) {
        this.name = name;
        this.skills = List.copyOf(skills);       // copy in
    }
    public String getName() { return name; }
    public List<String> getSkills() { return skills; }   // already unmodifiable
    // with a mutable copy (new ArrayList<>(skills)), return new ArrayList<>(skills) — copy out
}
```

**Most asked**

- **Benefits?** Thread-safe without locks, safe HashMap keys, can be cached and shared freely.
- **Why make the class** `final`**?** A subclass could add mutable state or override getters.
- **Easiest way in modern Java?** `record` (Java 16+) — fields are `private final`, no setters. Still needs a defensive copy for mutable fields.

**Trap:** Forgetting the defensive copy → caller changes the original list → class is no longer immutable.

---



## 8. Wrapper Classes & Integer Cache 🔥

**Say this:** "Wrapper classes like `Integer` wrap primitives into objects, because collections and generics only work with objects. Autoboxing converts `int` to `Integer` automatically using `Integer.valueOf()`, which caches values from -128 to 127. So `==` works inside that range by luck, and fails outside it."

```java
Integer a = 127, b = 127;   a == b        // true  (same cached object)
Integer c = 128, d = 128;   c == d        // false (two new objects)
                            c.equals(d)   // true
```

**Most asked**

- **Which wrappers cache?** `Byte`, `Short`, `Integer`, `Long` (-128..127), `Character` (0..127), `Boolean`. Not `Float`/`Double`.
- **Unboxing null?** `Integer x = null; int y = x;` → **NullPointerException**. Common with `map.get()` returning null.

**Trap:** Always compare wrappers with `.equals()`, never `==`.

---



## 9. Enum

**Say this:** "An enum is a special class with a fixed set of constants — each constant is a single object created by the JVM. It can have fields, a private constructor and methods. Enums can't extend a class (they already extend `java.lang.Enum`) but can implement interfaces."

```java
enum Status {
    ACTIVE("A"), INACTIVE("I");
    private final String code;
    Status(String code) { this.code = code; }
}
```

**Most asked**

- **Best way to make a Singleton?** Enum with one constant — thread-safe, and safe against reflection and serialization by default.
- **Compare enums with** `==` **or** `equals()`**?** `==` is fine (only one instance each) and null-safe.

---



## 10. Static Nested vs Inner Class

**Say this:** "A static nested class doesn't need an outer object and can only access the outer class's static members. A non-static inner class needs an outer object and holds a hidden reference to it — which can cause a **memory leak** if the inner object lives longer. So prefer static nested unless you really need the outer instance."

```java
Outer.StaticNested sn = new Outer.StaticNested();   // no Outer object
Outer.Inner in = new Outer().new Inner();           // needs Outer object
```

---



## 11. Serialization 🔥

**Say this:** "Serialization converts an object into a byte stream to save it or send it over the network; deserialization rebuilds it. The class must implement `Serializable`, a marker interface. `transient` fields are skipped and come back as default values, and `static` fields are not serialized because they belong to the class. In real projects we usually use JSON with Jackson instead — Java serialization has security risks."

**Most asked**

- **Why declare** `serialVersionUID`**?** If not declared, the JVM generates it from the class structure — any change (new field) changes it → `InvalidClassException` when reading old data. A fixed UID keeps old data compatible.
- **Is the constructor called on deserialization?** No — only the no-arg constructor of the first non-Serializable parent runs.
- **Field type not Serializable?** `NotSerializableException` (unless marked `transient`).

---



## 12. Shallow vs Deep Copy 🔥

**Say this:** "Shallow copy copies the fields as they are, so nested objects are **shared** — changing one copy's address changes the other's. Deep copy also copies the nested objects, so both are fully independent. Default `clone()` is shallow. I prefer a **copy constructor** — no `Cloneable`, no checked exception, no casting, and it works with `final` fields."

```java
Person(Person other) {
    this.name = other.name;                            // String is immutable → safe to share
    this.address = new Address(other.address.city);    // new object → deep copy
}
```

**Most asked**

- **Problems with** `Cloneable`**?** `clone()` lives in `Object` (protected), shallow by default, throws checked exception, skips constructors, doesn't work with `final` fields.

---



## 13. Reflection (short)

**Say this:** "Reflection lets code inspect and change classes, methods and fields at runtime — even private ones via `setAccessible(true)`. Spring uses it for dependency injection, Hibernate for mapping entities, Jackson for JSON and JUnit for finding tests. Downsides: slower, breaks encapsulation, no compile-time checks."

**Most asked**

- **Ways to create an object?** `new`, reflection (`getDeclaredConstructor().newInstance()`), `clone()`, deserialization. (Factory/builder use `new` inside.)
- **3 ways to get a** `Class` **object?** `obj.getClass()`, `User.class`, `Class.forName("com.app.User")`.

---

