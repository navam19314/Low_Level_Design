# 08 — AOP, Caching, Async, Scheduling & Kafka ⭐

> Format: **Say this** = your 30-sec interview answer · **Key points** · **Most asked** (Q → short answer) · **Trap**

---

## 1. What is AOP? ⭐

**Say this:** "AOP separates cross-cutting concerns — logic needed in many places like logging, security, transactions and metrics — from business logic. We write that logic once in an aspect, and Spring applies it automatically to the methods we select. This removes duplicate code and keeps service classes clean."

| Term | Meaning |
|---|---|
| **Aspect** | Class holding the cross-cutting logic (`@Aspect`) |
| **Advice** | **What** to do and **when** (before / after / around) |
| **Join Point** | Where advice can run — in Spring AOP, always a **method execution** |
| **Pointcut** | Expression that selects **which** methods get the advice |

**Advice types:** `@Before` · `@After` (always, like `finally`) · `@AfterReturning` (on success) · `@AfterThrowing` (on exception) · `@Around` (wraps the call — most powerful; **must call `pjp.proceed()` and return its result**).

```java
@Aspect @Component
public class TimingAspect {
    @Around("execution(* com.app.service.*.*(..))")   // or "@annotation(com.app.LogTime)"
    public Object time(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        try { return pjp.proceed(); }                    // run the real method
        finally { log.info("{} took {} ms", pjp.getSignature(), System.currentTimeMillis() - start); }
    }
}
```

**Most asked**
- **Aspect in one line?** Aspect = Pointcut (where) + Advice (what + when).
- **What if `@Around` doesn't call `proceed()`?** The real method never runs; caller gets `null` if you don't return the result.
- **Spring AOP vs AspectJ?** Spring AOP = runtime proxies, method calls on beans only. AspectJ = compile/load-time weaving, also fields and constructors.

---

## 2. How Spring AOP Works + Limitations 🔥

**Say this:** "At startup Spring wraps the target bean in a proxy and injects the proxy everywhere. A call goes caller → proxy (runs advice) → real method. So advice only runs when the call goes through the proxy."

**Key points**
- **JDK dynamic proxy** = implements the bean's interface. **CGLIB** = subclass of the class. **Spring Boot defaults to CGLIB** (`spring.aop.proxy-target-class=true`).
- Spring features built on AOP: `@Transactional`, `@Cacheable`, `@Async`, `@PreAuthorize`, `@Retryable` — so all traps below apply to them too.

**Most asked**
- **Why does `@Transactional` not work when called from the same class?** `this.method()` calls the real object and skips the proxy → no advice. Fix: move the method to another bean.
- **Which methods are NOT intercepted?** Self-invoked, `private`, `final` (CGLIB can't override), `static`, and objects created with `new` (not beans).
- **JDK proxy vs CGLIB?** Interface-based vs subclass-based. CGLIB can't proxy `final` classes/methods.

**Trap:** Final class with an aspect → startup error. Final method → advice silently skipped.

---

## 3. Caching ⭐

**Say this:** "Spring Cache stores the result of an expensive call (DB, remote API) so the next call with the same key is served from memory. It is AOP-based — a proxy checks the cache before calling the method. Enable with `@EnableCaching`; the provider is pluggable — in-memory map, Caffeine, or Redis."

| Annotation | What it does |
|---|---|
| `@Cacheable` | Key in cache → return it, method **not** called. Else call and store |
| `@CachePut` | **Always** runs the method and updates the cache |
| `@CacheEvict` | Removes the entry; `allEntries = true` clears the cache |

```java
@Cacheable(value = "products", key = "#id")
Product get(Long id) { return repo.findById(id).orElseThrow(); }

@CacheEvict(value = "products", key = "#id")
void delete(Long id) { repo.deleteById(id); }
```

**Redis:** `spring-boot-starter-data-redis` + `spring.cache.type=redis` → shared across all instances. TTL: `spring.cache.redis.time-to-live=10m`.

**Most asked**
- **`@Cacheable` vs `@CachePut`?** Cacheable skips the method on a hit; CachePut always runs it and refreshes the cache.
- **Local cache vs Redis?** Local is per-instance (pods see different data); Redis is shared.
- **Which pattern does `@Cacheable` use?** Cache-aside — fill cache on miss. (Write-through = write cache and DB together.)

**Trap:** Self-invocation → no caching · stale data if writes don't evict/update (always add TTL) · cache stampede when a hot key expires → `@Cacheable(sync = true)`.

---

## 4. @Async ⭐

**Say this:** "`@Async` runs a method on a separate thread from a pool, so the caller returns immediately. Enable with `@EnableAsync`. Return `void` for fire-and-forget or `CompletableFuture<T>` if the caller needs the result. Boot gives a default executor, but in production I define my own bounded pool."

```java
@Bean("emailExecutor")
ThreadPoolTaskExecutor emailExecutor() {
    var ex = new ThreadPoolTaskExecutor();
    ex.setCorePoolSize(5); ex.setMaxPoolSize(10); ex.setQueueCapacity(100);
    ex.setThreadNamePrefix("email-");
    return ex;
}

@Async("emailExecutor")
public CompletableFuture<Boolean> send(String to) { ...; return CompletableFuture.completedFuture(true); }
```

**Most asked**
- **Why is my `@Async` method running synchronously?** Self-invocation (proxy bypassed), or the method is private, or `@EnableAsync` is missing.
- **What happens to exceptions?** In `void` methods they are lost → configure `AsyncUncaughtExceptionHandler`. With `CompletableFuture`, use `exceptionally()`.
- **Does the transaction / security context carry over?** No — `@Transactional`, `SecurityContext`, MDC/ThreadLocal don't move to the new thread.

---

## 5. @Scheduled

**Say this:** "`@Scheduled` runs a method automatically at a fixed interval or cron time. Enable with `@EnableScheduling`."

| Option | Meaning |
|---|---|
| `fixedRate = 5000` | Every 5s, measured **from the start** of the previous run |
| `fixedDelay = 5000` | 5s **after the previous run finishes** |
| `cron = "0 0 2 * * *"` | Calendar-based, 6 fields (seconds first) → daily 2 AM |

**Most asked**
- **fixedRate vs fixedDelay?** Start-to-start vs end-to-start.
- **Job runs 3 times with 3 pods — fix?** Every instance runs it → use **ShedLock** (DB/Redis lock so only one runs).

**Trap:** Scheduler is single-threaded by default → one slow job delays others. Increase `spring.task.scheduling.pool.size`.

---

## 6. Kafka Basics 🔥

**Say this:** "Kafka is a distributed, log-based event streaming platform. Producers write events to topics, consumers read them. Messages are stored on disk for a retention period, so they can be replayed. A topic is split into partitions, which are the unit of parallelism."

| Term | Meaning |
|---|---|
| **Broker** | A Kafka server; a cluster has many |
| **Topic** | Named stream of messages (e.g. `orders`) |
| **Partition** | Ordered, append-only log inside a topic |
| **Offset** | Message position within a partition (consumer's bookmark) |
| **Consumer group** | Consumers sharing work; each partition → **one** consumer in the group |
| **Replication** | Partition copied to N brokers; **leader** serves, followers take over on failure |

**Key rules**
1. **One partition → only one consumer per group** (one consumer can read many partitions).
2. **Ordering only within a partition** → send related events with the **same key** (e.g. `orderId`).
3. **Consumers > partitions → extra consumers sit idle.** Max parallelism = partition count.

```java
kafkaTemplate.send("orders", e.orderId(), e);          // key → same partition → ordered

@KafkaListener(topics = "orders", groupId = "billing")
void onOrder(OrderEvent e) { billingService.process(e); }
```

**Most asked**
- **How do you scale consumers?** Add partitions and consumers in the same group (up to partition count).
- **How to keep order for one customer?** Use customerId as the key.
- **Two groups on the same topic?** Each group gets all messages independently (own offsets).

---

## 7. Kafka Delivery, Retries & Kafka vs RabbitMQ 🔥

**Say this:** "Spring Kafka is at-least-once by default — the offset is committed after the listener finishes, so a crash can cause redelivery. That's why consumers must be idempotent. Messages that keep failing go to a Dead Letter Topic after retries."

| Semantic | Meaning | How |
|---|---|---|
| **At-most-once** | May lose, never duplicate | Commit **before** processing |
| **At-least-once** | Never lose, may duplicate | Commit **after** processing (**default**) |
| **Exactly-once** | No loss, no duplicate | Idempotent producer + Kafka transactions + `read_committed` (Kafka→Kafka only) |

**Retries + DLT:** `DefaultErrorHandler` retries with a `BackOff`, then `DeadLetterPublishingRecoverer` sends to `<topic>.DLT` (or `@RetryableTopic` for non-blocking retries). Don't retry bad/undeserializable messages.

| Kafka | RabbitMQ |
|---|---|
| Distributed log, messages **retained** → replay | Queue, message **deleted after ack** |
| Consumer **pulls**, tracks own offset | Broker **pushes** |
| Very high throughput, event streaming | Low latency, task queues |
| Simple routing (topic + key) | Rich routing (exchanges) |

**Most asked**
- **How do you make a consumer idempotent?** Store processed `eventId` or use a DB unique constraint — duplicates do nothing.
- **Exactly-once with a DB write?** Kafka EOS doesn't cover external systems → at-least-once + idempotent consumer (or outbox pattern).
- **What is a DLT?** Topic for messages that still fail after retries, so they don't block the partition and can be replayed later.

---

## 📌 Quick Revision
- **AOP** = cross-cutting logic once. Aspect = Pointcut (where) + Advice (what/when). `@Around` must `proceed()`.
- Spring AOP = runtime **proxies**; Boot default **CGLIB**. Not intercepted: self-invocation, private, final, static, non-beans.
- `@Transactional`, `@Cacheable`, `@Async`, `@PreAuthorize`, `@Retryable` are all AOP.
- `@Cacheable` = hit skips method; `@CachePut` = always run + update; `@CacheEvict` = remove. Redis = shared; set TTL.
- `@Async` + `@EnableAsync` → `void` / `CompletableFuture`; own pool. Traps: self-invocation, lost exceptions, no tx/security context.
- `@Scheduled`: fixedRate (start-to-start), fixedDelay (end-to-start), cron (6 fields). Cluster → **ShedLock**.
- Kafka: topic → partitions → offsets. One partition = one consumer per group. Order only within partition → use **key**.
- Spring Kafka = **at-least-once** → idempotent consumers. Failures → retries + **DLT**.
- Kafka = retained log, replay, high throughput. RabbitMQ = queue, delete on ack, rich routing.
