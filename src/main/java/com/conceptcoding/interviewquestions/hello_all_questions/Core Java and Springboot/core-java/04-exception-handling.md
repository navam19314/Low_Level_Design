# 04 — Exception Handling ⭐

> Format: **Say this** = your 30-sec interview answer · **Key points** · **Most asked** (Q → short answer) · **Trap**

---

## 1. Hierarchy & Error vs Exception

**Say this:** "`Throwable` is the root. It has two children: `Error`, which is a serious JVM problem we shouldn't catch, and `Exception`, which is an application problem we should handle. `RuntimeException` and its subclasses are unchecked; all other exceptions are checked."

```
              Throwable
             /         \
        Error           Exception
  OutOfMemoryError     IOException, SQLException        (checked)
  StackOverflowError     \
                          RuntimeException               (unchecked)
                          NullPointerException, IllegalArgumentException
```

**Most asked**
- **Error vs Exception?** Error = JVM/system problem, not recoverable, don't catch. Exception = app problem, usually recoverable.
- **Can we catch an Error?** Technically yes, but you shouldn't.

---

## 2. Checked vs Unchecked 🔥

**Say this:** "Checked exceptions are checked by the compiler — you must catch them or declare them with `throws`. They're for external problems like file or network failures. Unchecked exceptions extend `RuntimeException`, aren't checked at compile time, and usually mean a bug or bad input."

| | Checked | Unchecked |
|---|---|---|
| Extends | `Exception` | `RuntimeException` |
| Checked at | Compile time | Runtime |
| Must handle/declare? | Yes | No |
| Cause | External (file, network, DB) | Bugs, bad input |
| Examples | `IOException`, `SQLException` | `NullPointerException`, `IllegalArgumentException` |

**Most asked**
- **Checked or unchecked for a custom exception?** Checked if the caller can really recover (e.g. retry). Unchecked if it can't (bad input, business rule failed). Spring and modern code prefer **unchecked** — less boilerplate, and Spring rolls back `@Transactional` only on unchecked by default.
- **Is `Error` checked?** No — `Error` and `RuntimeException` are both unchecked.

---

## 3. `throw` vs `throws`

`throw` = inside the method body, throws **one** exception object. `throws` = in the method signature, **declares** one or more exception types the method may throw.

---

## 4. try / catch / finally 🔥

**Say this:** "`try` holds risky code, `catch` handles a specific exception, and `finally` always runs for cleanup — whether an exception happened or not. Catch the child exception before the parent, otherwise it's a compile error."

**Key points**
- Multi-catch (Java 7): `catch (IOException | SQLException e)` — types can't be parent/child.
- `try` without `catch` is allowed: `try-finally` or try-with-resources.

```java
try { return 1; } finally { return 2; }   // returns 2 → finally's return overrides (also swallows any exception)

int x = 1;
try { return x; } finally { x = 99; }     // returns 1 → return value saved before finally runs
```

**Most asked**
- **Does `finally` run if `try` has `return`?** Yes — it runs before the method actually returns.
- **When does `finally` NOT run?** `System.exit()`, JVM crash/kill, or the thread never leaves `try` (infinite loop).

**Trap:** Changing a primitive in `finally` doesn't change the returned value. But if you return an object and mutate it in `finally`, the caller sees the change (same reference).

---

## 5. try-with-resources (Java 7) 🔥

**Say this:** "We declare resources in the `try(...)` parentheses and Java closes them automatically, even if an exception happens. The resource must implement `AutoCloseable`."

```java
try (Connection con = ds.getConnection();
     PreparedStatement ps = con.prepareStatement(sql)) {
    ps.executeUpdate();
}   // ps closed first, then con
```

**Key points**
- Multiple resources close in **reverse order** of declaration.
- If both the `try` body and `close()` throw → the body's exception is thrown; the close exception is **suppressed** (`e.getSuppressed()`).
- Java 9+: can use an existing effectively-final variable: `try (br) { ... }`.

---

## 6. Exceptions in Method Overriding 🔥

**Say this:** "An overriding method can't throw a new or broader **checked** exception. It can throw the same, a narrower one, none at all, or any unchecked exception. That's because code using the parent reference only catches what the parent declared."

```java
class P { void read() throws IOException {} }
class C extends P {
    void read() throws FileNotFoundException {}  // ✅ narrower
    // void read() throws Exception {}            // ❌ broader → compile error
}
```

**Most asked**
- **Parent throws nothing — can child throw a checked exception?** No. Only unchecked.
- **Why this rule?** `P p = new C(); try { p.read(); } catch (IOException e)` must still compile and work.

---

## 7. Custom Exception & Chaining

**Say this:** "For business errors I create a custom exception, usually extending `RuntimeException`. When I wrap a low-level exception, I always pass it as the **cause**, so the original stack trace isn't lost — that's exception chaining."

```java
class OrderFailedException extends RuntimeException {
    OrderFailedException(String msg, Throwable cause) { super(msg, cause); }
}

try { payment.charge(); }
catch (IOException e) { throw new OrderFailedException("Order failed", e); }  // e → getCause()
```

**Trap:** `throw new OrderFailedException("Order failed")` without `e` loses the root cause.

---

## 8. Best Practices

- Catch **specific** exceptions, not `Exception` / `Throwable`.
- Never swallow: `catch (Exception e) {}` — at least log it.
- Log **or** rethrow — not both (duplicate logs).
- Don't use exceptions for normal flow control; don't `return` from `finally`.
- Use try-with-resources for anything closeable; wrap with cause when translating exceptions.

---

## 📌 Quick Revision
- `Throwable → Error | Exception → RuntimeException`. **Error** = don't catch.
- **Checked** = compile time, must handle/declare, external. **Unchecked** = `RuntimeException`, bugs. Spring prefers unchecked.
- **`throw`** = one object in body. **`throws`** = declare in signature.
- Catch child before parent. Multi-catch types can't be related.
- `return` in finally overrides. Return value saved before finally. Finally skipped only on `System.exit()` / JVM crash.
- **try-with-resources:** `AutoCloseable`, auto close, reverse order, suppressed exceptions.
- **Overriding:** child can't throw new/broader **checked** exceptions.
- **Custom exception:** extend `RuntimeException`. **Chaining:** always pass the cause.
