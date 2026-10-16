# 02 — OOP & SOLID 🔥

> Format: **Say this** = your 30-sec interview answer · **Key points** · **Most asked** (Q → short answer) · **Trap**

---

## 1. The 4 Pillars 🔥

**Say this:** "OOP has four pillars. Encapsulation hides data behind methods. Abstraction shows only what an object does, not how. Inheritance lets a class reuse another class's code. Polymorphism lets one call behave differently based on the actual object."

| Pillar | Meaning | How in Java |
|---|---|---|
| Encapsulation | Hide state, control access | `private` fields + getters/setters with validation |
| Abstraction | Hide implementation, expose behaviour | Abstract classes, interfaces |
| Inheritance | IS-A reuse | `extends` (class), `implements` (interface) |
| Polymorphism | One interface, many forms | Overloading (compile time), overriding (runtime) |

**Most asked**
- **Encapsulation vs Abstraction?** Encapsulation hides *data* (how it's protected); abstraction hides *complexity* (what vs how). Encapsulation is about access control, abstraction is about design.
- **Compile-time vs runtime polymorphism?** Overloading is resolved by the compiler; overriding is resolved at runtime by the actual object type (dynamic dispatch).
- **Why no multiple inheritance of classes?** Diamond problem — if B and C both override `A.m()` and D extends both, which `m()` does D get? Java avoids this ambiguity; it allows multiple *interfaces* instead.

**Trap:** Fields and static methods are **not** polymorphic — they are picked by the reference type, not the object type.

---

## 2. Overloading vs Overriding 🔥

**Say this:** "Overloading is the same method name with different parameters in the same class — decided at compile time. Overriding is a subclass giving its own version of a parent method with the same signature — decided at runtime based on the actual object."

| Rule | Overloading | Overriding |
|---|---|---|
| Parameters | Must differ | Must be same |
| Return type | Anything | Same or **covariant** (subtype) |
| Access | Anything | Same or **wider** (not narrower) |
| Checked exceptions | Anything | Same, narrower or none (no new/broader) |
| Binding | Compile time | Runtime |
| `static` | Can overload | **Hidden**, not overridden |
| `private` / `final` | Can overload | Cannot override |

```java
class A { static void s() { print("A"); } void m() { print("A"); } }
class B extends A { static void s() { print("B"); } void m() { print("B"); } }
A obj = new B();
obj.m();   // B  (overriding → runtime type)
obj.s();   // A  (method hiding → reference type)
```

**Most asked**
- **Can we override static methods?** No — it's method hiding; the call is chosen by reference type.
- **Can we override private / final methods?** No. Private isn't inherited; final is blocked.
- **Can we overload by return type only?** No — compile error.
- **Can we overload `main()`?** Yes, but JVM calls only `main(String[])`.
- **Overload resolution order?** Exact match → widening (`int`→`long`) → boxing (`int`→`Integer`) → varargs.
- **Can constructors be overridden?** No — they aren't inherited (only overloaded).
- **Can a child call the parent's overridden version?** Yes — `super.m()`.
- **`@Override` use?** Compiler checks you really override — catches typos/signature mismatch.

**Trap:** `m(null)` with `m(Object)` and `m(String)` calls `m(String)` (most specific). With `m(String)` and `m(Integer)` → ambiguous, compile error.

---

## 3. Abstract Class vs Interface 🔥

**Say this:** "Use an abstract class when classes share state and code — it's an IS-A base with constructors and fields. Use an interface to define a capability or contract that unrelated classes can implement. A class can extend only one class but implement many interfaces. Since Java 8, interfaces can also have default and static methods."

| | Abstract class | Interface |
|---|---|---|
| Methods | Abstract + concrete | Abstract + `default` + `static` (8) + `private` (9) |
| Fields | Any (instance state) | Only `public static final` constants |
| Constructor | Yes | No |
| Inheritance | Single (`extends`) | Multiple (`implements`) |
| Access | Any modifier | Methods `public` by default |
| Use | Shared code/state, IS-A | Capability/contract, CAN-DO |

**Diamond with default methods** — if two interfaces have the same default method, the class **must** override it:
```java
interface A { default void hi() { print("A"); } }
interface B { default void hi() { print("B"); } }
class C implements A, B {
    public void hi() { A.super.hi(); }   // pick one explicitly
}
```

**Most asked**
- **Why default methods?** To add methods to existing interfaces (e.g., `List.forEach`, `stream()`) without breaking all implementers.
- **Can an abstract class have a constructor?** Yes — called via `super()` from the subclass. You still can't `new` it.
- **Abstract class with no abstract methods?** Allowed — just prevents instantiation.
- **Sealed classes (Java 17)?** `sealed ... permits X, Y` restricts which classes can extend/implement — controlled hierarchy.
- **Resolution rules?** Class method wins over interface default → more specific interface wins → else must override.

**Trap:** Interface default methods can't override `Object` methods like `equals`/`hashCode` — compile error.

---

## 4. Composition vs Inheritance 🔥

**Say this:** "Prefer composition over inheritance. Inheritance is tight coupling — a change in the parent can break children, and you get everything the parent has. Composition means a class HAS-A another object and delegates to it, so it's flexible, testable and can be swapped at runtime."

**Key points**
- **Association** — objects use each other, independent lifecycles (Teacher ↔ Student).
- **Aggregation** — weak HAS-A, part can live alone (Department has Professors).
- **Composition** — strong HAS-A, part dies with whole (House has Rooms).

**Most asked**
- **When to use inheritance?** Only for a true IS-A where the subclass fully honours the parent's contract (Liskov).
- **Why is composition easier to test?** Dependencies are injected, so you can pass mocks.
- **Classic bad example?** `Stack extends Vector` — Stack inherits `add(index)` and breaks LIFO.

---

## 5. Access Modifiers

| Modifier | Class | Package | Subclass (other pkg) | Everywhere |
|---|---|---|---|---|
| `private` | ✅ | ❌ | ❌ | ❌ |
| default (none) | ✅ | ✅ | ❌ | ❌ |
| `protected` | ✅ | ✅ | ✅ (via inheritance) | ❌ |
| `public` | ✅ | ✅ | ✅ | ✅ |

**Most asked**
- **Top-level class modifiers?** Only `public` or default.
- **Can a constructor be private?** Yes — Singleton, utility classes, factories.

---

## 6. SOLID Principles 🔥

**Say this:** "SOLID is five design principles to keep code easy to change. Single responsibility, open for extension but closed for modification, subtypes must be replaceable, small focused interfaces, and depend on abstractions not concrete classes. Together they reduce coupling and make code testable."

| Principle | Meaning | Bad → Good |
|---|---|---|
| **S** — Single Responsibility | One class, one reason to change | `Invoice` that calculates + prints + saves → `Invoice`, `InvoicePrinter`, `InvoiceRepository` |
| **O** — Open/Closed | Add behaviour by new code, not editing old | `if (type == CARD) ... else if (UPI)` → `PaymentStrategy` interface + new class per type |
| **L** — Liskov Substitution | Subclass must work wherever parent works | `Penguin extends Bird` with `fly()` throwing → split `Bird` / `FlyingBird` |
| **I** — Interface Segregation | No client forced to use methods it doesn't need | Fat `Worker { work(); eat(); }` → `Workable`, `Eatable` |
| **D** — Dependency Inversion | Depend on abstractions, inject them | `new MySqlRepo()` inside service → constructor-inject `Repo` interface |

**Most asked**
- **Liskov example?** `Square extends Rectangle` — `setWidth(5); setHeight(10)` expects area 50, Square gives 100. Breaks caller's expectations → don't inherit.
- **How does Spring help with D?** IoC container injects interface implementations — classes never `new` their dependencies.
- **DIP vs DI?** DIP is the principle (depend on abstractions); DI is the technique (pass dependencies in).
- **Which pattern shows O?** Strategy / Decorator — add a new class, no edits to existing code.

**Trap:** A subclass that throws `UnsupportedOperationException` for a parent method is a Liskov violation.

---

## 7. Marker vs Functional Interface

- **Marker** — no methods, just tags a class for JVM/framework: `Serializable`, `Cloneable`, `RandomAccess`. Annotations are the modern alternative.
- **Functional** — exactly one abstract method (defaults/statics allowed), can be a lambda target: `Runnable`, `Comparator`, `Function`. `@FunctionalInterface` makes the compiler enforce it.

---

## 📌 Quick Revision
- 4 pillars: Encapsulation (hide data), Abstraction (hide complexity), Inheritance (IS-A), Polymorphism (overload = compile, override = runtime).
- No multiple class inheritance → **diamond problem**; multiple interfaces allowed.
- Overriding: same signature, covariant return, wider access, no broader checked exceptions. static → **hiding**; private/final → can't override.
- Overload resolution: **exact → widening → boxing → varargs**.
- Abstract class = state + constructor + single inheritance. Interface = contract, multiple, `default`/`static` (8), `private` (9).
- Default-method clash → override and call `A.super.m()`.
- **Prefer composition** over inheritance. Association < Aggregation < Composition (lifecycle).
- Access: private < default < protected < public.
- SOLID: SRP, OCP (Strategy), LSP (Square/Rectangle, Penguin), ISP (small interfaces), DIP (inject abstractions).
- Goal: low coupling, high cohesion.
- Marker = no methods (`Serializable`). Functional = one abstract method (lambda target).
