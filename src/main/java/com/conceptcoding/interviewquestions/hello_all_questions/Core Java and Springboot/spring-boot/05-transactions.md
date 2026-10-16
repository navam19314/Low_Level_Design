# 05 — Transactions (@Transactional) 🔥🔥

> Format: **Say this** = your 30-sec interview answer · **Key points** · **Most asked** (Q → short answer) · **Trap**

---

## 1. Transaction & ACID 🔥

**Say this:** "A transaction is a group of DB operations that run as one unit. Either all of them succeed and commit, or all are undone with a rollback. A bank transfer is the classic case: debit and credit must both happen or neither."

| Property | Meaning |
|---|---|
| **Atomicity** | All or nothing |
| **Consistency** | DB goes from one valid state to another (constraints hold) |
| **Isolation** | Parallel transactions don't see each other's half-done work |
| **Durability** | Once committed, data survives a crash |

**Most asked**
- **Where do you put `@Transactional`?** On service-layer methods (the business unit of work), not controllers or repositories.
- **Do you need `@EnableTransactionManagement` in Boot?** No. Boot auto-configures it.

---

## 2. How @Transactional Works 🔥

**Say this:** "Spring wraps the bean in an AOP proxy. Callers get the proxy, not the real object. The proxy starts a transaction through a `PlatformTransactionManager`, calls the real method, then commits on success or rolls back on a runtime exception. The transaction and its DB connection are bound to the current thread."

```
Caller ──► Proxy (TransactionInterceptor) ──► real method
             ├─ BEGIN
             ├─ call method
             ├─ success          → COMMIT
             └─ RuntimeException → ROLLBACK + rethrow
```

**Key points**
- JPA uses `JpaTransactionManager`. Plain JDBC uses `DataSourceTransactionManager`.
- Boot uses CGLIB (subclass) proxies by default.
- On a class it applies to all public methods. A method-level annotation overrides the class one.

**Most asked**
- **How does Spring implement `@Transactional`?** AOP proxy + `TransactionInterceptor` + `PlatformTransactionManager`.
- **Why is the transaction thread-bound?** The connection is stored in a ThreadLocal, so all repository calls on that thread share it.

---

## 3. Rollback Rules 🔥

**Say this:** "By default Spring rolls back only on unchecked exceptions, meaning `RuntimeException` and `Error`. If a checked exception is thrown, the transaction still commits. To roll back on checked exceptions too, use `rollbackFor = Exception.class`."

| Exception | Default |
|---|---|
| `RuntimeException` / `Error` | Rollback |
| Checked (`IOException`, `Exception`) | **Commit** |

```java
@Transactional(rollbackFor = Exception.class)   // also roll back on checked exceptions
public void placeOrder() throws Exception { ... }

@Transactional
public void transfer() {
    try { repo.debit(); repo.credit(); }       // credit throws
    catch (Exception e) { log.error("x", e); } // ❌ swallowed → proxy sees success → COMMIT
}
```

**Most asked**
- **A checked exception is thrown. Does it roll back?** No, it commits. Add `rollbackFor`.
- **Exception caught inside the method. Does it roll back?** No. The proxy never sees it. Rethrow it, or call `TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()`.
- **`noRollbackFor`?** Skips rollback for a specific exception.

**Trap:** Catching and logging an exception inside a `@Transactional` method commits the partial work without any warning.

---

## 4. Self-Invocation Trap 🔥🔥

**Say this:** "If one method calls a `@Transactional` method in the same class, the call goes through `this` and not through the proxy. So no transaction starts. The fix is to move the method to another bean, call it through an injected self-reference, or use `TransactionTemplate`."

```java
@Service
public class OrderService {
    public void process() { save(); }   // ❌ this.save() → bypasses proxy → no transaction

    @Transactional
    public void save() { ... }
}
```

**Fixes**
1. **Move `save()` to another bean** and inject it. This is the cleanest fix.
2. Inject self: `@Lazy @Autowired OrderService self;` then call `self.save()`.
3. Use `TransactionTemplate` (programmatic).

**Trap:** The same applies to `REQUIRES_NEW`. Calling it from the same class does not create a new transaction.

---

## 5. Other Cases Where It Doesn't Work 🔥

- **Private method**: the proxy can't intercept it. Keep transactional methods `public`.
- **Not a Spring bean** (`new OrderService()`): no proxy is created.
- **New thread / `@Async` / `CompletableFuture`**: the transaction is ThreadLocal, so the new thread runs outside it.
- **Self-invocation** (section 4).
- **Checked exception or swallowed exception** (section 3).

**Most asked**
- **"`@Transactional` isn't working". What do you check?** Self-invocation, private method, bean created with `new`, checked exception, caught exception, work done on another thread.

---

## 6. Propagation 🔥🔥

**Say this:** "Propagation decides what happens when a transactional method is called while a transaction already exists. The default, `REQUIRED`, joins the existing one or creates a new one if there is none. `REQUIRES_NEW` always suspends the current transaction and starts its own, so its work commits on its own. That's what you use for audit logs that must be saved even if the main transaction fails."

| Propagation | Behaviour |
|---|---|
| **`REQUIRED`** (default) | Join existing, else create new |
| **`REQUIRES_NEW`** | Suspend existing, always start a new one |
| **`NESTED`** | Run inside existing using a savepoint; inner can roll back alone (JDBC) |
| `SUPPORTS` | Join if exists, else run without a transaction |
| `NOT_SUPPORTED` | Suspend existing, run without a transaction |
| `MANDATORY` | Must have an existing transaction, else exception |
| `NEVER` | Must not have a transaction, else exception |

```java
@Transactional                                    // tx-1
public void placeOrder(Order o) {
    auditService.log("attempt " + o.getId());     // tx-2 → commits on its own
    orderRepo.save(o);
    throw new RuntimeException("payment failed"); // tx-1 rolls back, audit row stays
}

// in AuditService (separate bean, so no self-invocation)
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void log(String msg) { auditRepo.save(new Audit(msg)); }
```

**Most asked**
- **REQUIRED vs REQUIRES_NEW?** Shared transaction (outer failure rolls back the inner work) vs separate transaction (inner work stays committed). REQUIRES_NEW uses a second connection.
- **REQUIRES_NEW vs NESTED?** NESTED is the same physical transaction with a savepoint, so if the outer rolls back, nested work rolls back too. REQUIRES_NEW is fully independent.
- **What is `UnexpectedRollbackException`?** An inner `REQUIRED` method threw, which marked the shared transaction rollback-only. The outer caught the exception and tried to commit, so Spring threw this.

**Trap:** `REQUIRES_NEW` called from the same class gives no new transaction (self-invocation).

---

## 7. Isolation Levels 🔥

**Say this:** "Isolation controls how much one transaction sees of other running transactions' changes. Higher isolation is safer but slower because of more locking. `Isolation.DEFAULT` uses the database's default: `READ_COMMITTED` for Postgres and Oracle, `REPEATABLE_READ` for MySQL InnoDB."

**Key points**
- **Dirty read**: reading data another transaction hasn't committed yet.
- **Non-repeatable read**: the same row read twice gives different values because another transaction updated it.
- **Phantom read**: the same query returns new or missing rows because another transaction inserted or deleted.

| Level | Dirty | Non-repeatable | Phantom |
|---|---|---|---|
| `READ_UNCOMMITTED` | possible | possible | possible |
| `READ_COMMITTED` | prevented | possible | possible |
| `REPEATABLE_READ` | prevented | prevented | possible* |
| `SERIALIZABLE` | prevented | prevented | prevented |

\* MySQL InnoDB mostly prevents phantoms at `REPEATABLE_READ` too.

**Most asked**
- **How do you set it?** `@Transactional(isolation = Isolation.SERIALIZABLE)`.
- **Default isolation?** The DB default (Postgres `READ_COMMITTED`, MySQL `REPEATABLE_READ`).
- **Why not always SERIALIZABLE?** Too much locking, poor concurrency, more deadlocks and timeouts.

---

## 8. readOnly, TransactionTemplate, Microservices

- **`readOnly = true`**: a performance hint for query methods. Hibernate skips flush and dirty checking. It is not a guarantee that writes are blocked.
- **`TransactionTemplate`**: sets the transaction boundary in code (`template.executeWithoutResult(status -> {...})`). Gives finer control and avoids the proxy traps.
- **Microservices**: `@Transactional` covers only one DB in one service. Across services, use the **Saga pattern** (local transactions + compensating actions).

---

## 📌 Quick Revision
- Transaction = all-or-nothing. **ACID** = Atomic, Consistent, Isolated, Durable.
- Works via an **AOP proxy** + `PlatformTransactionManager` (`JpaTransactionManager`). The transaction is bound to the thread.
- Rolls back on **`RuntimeException`/`Error`** only. **Checked exception → commits** → `rollbackFor = Exception.class`.
- Caught and swallowed exception → **no rollback**.
- **Self-invocation bypasses the proxy** → move to another bean / inject self / `TransactionTemplate`.
- Doesn't work on: private methods, objects created with `new`, new threads/`@Async`.
- **REQUIRED** = join (default). **REQUIRES_NEW** = separate transaction (audit logs). **NESTED** = savepoint.
- Dirty → non-repeatable → phantom. `SERIALIZABLE` prevents all three but is the slowest. `DEFAULT` = DB default.
- `readOnly = true` skips dirty checking. Across microservices → **Saga**.
