# Rate Limiter — 45-min LLD Interview Walkthrough

**Target role:** SDE‑2 (Amazon, Adobe, Microsoft, Atlassian, etc.)
**Source method:** Hello Interview *Delivery Framework* applied to the *Rate Limiter* problem breakdown.

> Rate Limiter is the **canonical "two patterns in the base earn their rent" LLD problem** in the HI set. The headline signals are (a) Strategy for swappable algorithms, (b) Factory for heterogeneous config blobs with a discriminator, and (c) per-key locking on the hot path. The HI article scopes thread-safety to Step 5; I've kept it in the base because in a real SDE‑2 interview an unsafe rate limiter will get probed inside the first 5 minutes of follow-up.

> Hello Interview labels this *hard* — and they're right. The math (refill, retryAfterMs) and the concurrency together are more demanding than any of the previous problems.

---

## Time budget (45 min)

| Step | Activity                                                                                  | Budget   | Cumulative |
| ---- | ----------------------------------------------------------------------------------------- | -------- | ---------- |
| 1    | Requirements                                                                              | ~5 min   | 5          |
| 2    | Entities & Relationships                                                                  | ~4 min   | 9          |
| 3    | Class Design (Strategy + Factory; per-key state lives on each algorithm)                  | ~10 min  | 19         |
| 4    | Implementation (TokenBucket end-to-end + SlidingWindowLog highlights + dry-run)           | ~17 min  | 36         |
| 5    | Extensibility (thread-safety justification, new algorithm, memory eviction, distributed)  | ~8 min   | 44         |
| —    | Wrap & questions                                                                          | ~1 min   | 45         |

Step 4 is the longest because there are TWO algorithms and the refill / retry math is the senior signal — don't shortchange the dry-run.

Watch the clock at minute **5** (Step 1 done), minute **19** (start coding), minute **36** (extensibility).

---

## Mental models — internalize these BEFORE you walk in

### M1. The dispatch flow — endpoint → algorithm → per-key check

```
   rateLimiter.allow("client-1", "/search")
            |
            v
   +-----------------------------------+
   | limiters.get("/search")           |    <-- O(1) endpoint lookup
   |   null? → defaultLimiter (fallback)
   +-----------------------------------+
            |
            v
   +-----------------------------------+
   | Limiter (Strategy)                |    <-- TokenBucket OR SlidingWindowLog OR ...
   |   allow("client-1") {             |
   |     bucket = buckets.computeIfAbsent(key, ...)
   |     synchronized(bucket) {        |    <-- per-KEY lock, not per-limiter
   |       refill / check / consume    |
   |     }                             |
   |     return RateLimitResult        |
   |   }                               |
   +-----------------------------------+
            |
            v
   +-----------------------------------+
   |  RateLimitResult                  |    <-- (allowed, remaining, retryAfterMs)
   +-----------------------------------+
```

### M2. TokenBucket lazy-refill math (the bit candidates get wrong)

```
   Bucket state:  tokens (double), lastRefillTime (long)
   Config:        capacity, refillRatePerSecond

   On every allow():
   ----------------------------------------------------------------------
     now           = clock.millis()
     elapsedMs     = now - lastRefillTime
     tokensToAdd   = elapsedMs * refillRatePerSecond / 1000.0
     tokens        = min(capacity, tokens + tokensToAdd)       <-- CAP!
     lastRefillTime = now
                                                                
   Why a DOUBLE for tokens?                                     
     Partial accumulation is real. 100ms at 1/sec → 0.1 tokens. 
     Using an int silently rounds to zero → no refill ever.    
                                                                
   Why CAP at capacity?                                         
     A client idle for 10 minutes with refill=1/sec should NOT  
     be granted 600 tokens. Cap protects the burst guarantee.   
                                                                
   retryAfterMs (when denied):                                  
     tokensNeeded = 1 - tokens             (e.g. 1 - 0.3 = 0.7)
     retryAfterMs = ceil(tokensNeeded * 1000 / refillRatePerSecond)
                    ^^^^^ round UP — never tell a client to retry too soon
```

**Senior soundbite (memorize):** *"Tokens are stored as a double because refill is continuous — 100ms at 1/sec is 0.1 tokens, not zero. The cap at `capacity` is what enforces the burst limit; an idle client doesn't bank tokens forever. And `retryAfterMs` rounds UP — telling someone to retry too soon causes another denial, which is worse for them than a slightly longer wait."*

### M3. Per-key locking — why it's the right granularity

```
   Wrong:                                  Right:
   -------                                 -------
   synchronized(this)                      // ConcurrentHashMap for the keys map
     allow(key) {                          synchronized(bucket) {   // per-KEY
       ...                                   ...
     }                                     }
                                          
   Two unrelated clients block each       Two unrelated clients run in parallel.
   other. ALL traffic serializes on       Only requests for the SAME client
   one mutex.                             serialize — exactly the bar.

   Why synchronize on `bucket` and not on `key`?
     - bucket is the SHARED MUTABLE STATE we need to protect (tokens, lastRefillTime).
     - The lock should live WITH the resource it protects (same principle as Movie
       Ticket's per-Showtime lock and Logger's per-Destination lock).
     - String keys would need an extra "locks map" with its own locking puzzle.
       The bucket itself is the natural monitor.
```

> **The interview rule (same across Movie Ticket, Logger, File System, Rate Limiter):** *the lock lives with the shared resource it's protecting.* On Rate Limiter, that's the per-key bucket.

---

## STEP 1 — Requirements (~5 min)

### What to say out loud (opener)
> "Rate limiter can mean wildly different things — in-process library vs. a distributed service with Redis. Let me clarify scope and the failure modes that matter."

### Probe the 4 themes

| Theme               | Question to ask                                                                                              |
| ------------------- | ------------------------------------------------------------------------------------------------------------ |
| Primary capabilities| "Per-endpoint configuration, multiple algorithms (TokenBucket, SlidingWindowLog, …)? Heterogeneous config blobs with a discriminator?" |
| Rules / completion  | "What does `allow` return — boolean or structured (allowed + remaining + retryAfterMs)? Unknown endpoint → default fallback or reject?" |
| Concurrency         | "Multi-threaded callers — per-key atomicity (exactly N tokens consumed for N successful requests)?"        |
| Scope boundaries    | "Single-process in-memory only? Static config at startup, no hot-reload? No distributed coordination?"      |

### What to write on the board

```
Functional Requirements
1. Per-endpoint configuration loaded ONCE at startup.
   Each config: { endpoint, algorithm, algoConfig: {...} }   <-- discriminated union shape
2. Multiple algorithms supported: TokenBucket, SlidingWindowLog, [more later].
3. allow(clientId, endpoint) → RateLimitResult{ allowed, remaining, retryAfterMs }.
4. Unknown endpoint → fall back to DEFAULT limiter (never reject for missing config).
5. Per-client state is isolated — alice's quota doesn't affect bob's.
6. Per-key atomicity — exactly one "winner" when two threads race for the last token.

Out of Scope (call these out to signal awareness)
- Distributed rate limiting (Redis, coordination across machines)
- Dynamic / hot-reloaded configuration
- Metrics, monitoring, audit log
- Persistent storage of bucket state across restarts
- Eviction of inactive clients (mentioned, will discuss in §5)
```

### Close the step
> "Does this match what you had in mind? The discriminated-union config shape is the load-bearing requirement — it directly motivates the Factory pattern, and the per-algorithm parameters demand Strategy. Both patterns are in the base for this problem, not deferred."

---

## STEP 2 — Entities & Relationships (~4 min)

### What to say out loud
> "Three core types: **RateLimiter** (orchestrator + facade — owns the per-endpoint map and the default), **Limiter** (Strategy interface — `allow(key) → RateLimitResult`; per-key state lives on each implementation), and **RateLimitResult** (immutable value object — three fields). Plus a **LimiterFactory** that turns a raw config blob into the right concrete `Limiter`. Two algorithms ship: **TokenBucketLimiter** and **SlidingWindowLogLimiter**."

### Why no `Request` / `Client` / `Endpoint` class
> "`Request` is external — we receive `(clientId, endpoint)` as strings. `Client` is external too — the id is a lookup key, nothing more. `Endpoint` is a label, not an entity. The interesting per-client state lives ON the limiter implementations, not on a Client class, because the SHAPE of that state varies per algorithm (TokenBucket needs tokens+lastRefill; SlidingWindow needs a timestamp queue)."

### Why per-key state is OWNED BY each algorithm
> "There's no useful 'common state' across algorithms. If I tried to factor it into a base class I'd end up with an abstract `Limiter` with zero fields — that's an interface with extra steps. So `Limiter` is an interface, and each implementation tracks its own state in its own shape."

### What to write on the board

```
Entities
- RateLimiter        (orchestrator + facade: allow(clientId, endpoint))
- LimiterFactory     (Factory pattern: raw config blob → concrete Limiter)
- Limiter            (Strategy interface: allow(key) → RateLimitResult)
   ├── TokenBucketLimiter        (capacity, refillRate, per-key bucket)
   └── SlidingWindowLogLimiter   (maxRequests, windowMs, per-key timestamp queue)
- RateLimitResult    (immutable: allowed, remaining, retryAfterMs)

NOT entities
- Request / Client / Endpoint    (external; strings are sufficient)
- Per-algorithm config classes   (heterogeneous data, raw Map is honest)

Relationships
- RateLimiter   owns   Map<String, Limiter>           (endpoint → limiter; immutable post-ctor)
- RateLimiter   owns   Limiter defaultLimiter
- Each Limiter  owns   ConcurrentHashMap<String, X>    (X is the algorithm-specific state)
- LimiterFactory   stateless; called once per endpoint at startup
```

### Diagram — boxes and arrows

```
                  +--------------------------------+
                  |          RateLimiter           |   <- orchestrator + facade
                  |   allow(clientId, endpoint)    |
                  +--------------------------------+
                          |                  |
                   owns   |                  | owns
                          v                  v
                Map<endpoint, Limiter>   Limiter defaultLimiter
                          |
                          | values (Strategy)
                          v
                 +--------------------+
                 | <<interface>>      |              created by LimiterFactory
                 |     Limiter        |              from config blob:
                 +--------------------+              { endpoint, algorithm, algoConfig }
                 | + allow(key)       |
                 +--------------------+
                       ^         ^
                       |         |
            +-------------+   +-----------------------+
            |TokenBucket  |   |SlidingWindowLog       |
            |Limiter      |   |Limiter                |
            +-------------+   +-----------------------+
            | per-key:    |   | per-key:              |
            |  TokenBucket|   |  Deque<Long>          |
            |   {tokens,  |   |  (timestamps)         |
            |    lastRef} |   |                       |
            +-------------+   +-----------------------+
```

---

## STEP 3 — Class Design (~10 min)

### Work top-down: RateLimiter → LimiterFactory → Limiter → TokenBucketLimiter → SlidingWindowLogLimiter → RateLimitResult.

### RateLimiter — state ↔ requirement table

| Requirement                              | State RateLimiter must own                              |
| ---------------------------------------- | ------------------------------------------------------- |
| Per-endpoint limiter                     | `Map<String, Limiter> limiters` (immutable post-ctor)   |
| Default fallback                         | `Limiter defaultLimiter`                                |

### RateLimiter — behavior table

| Need from requirements              | Method                                                |
| ----------------------------------- | ----------------------------------------------------- |
| Entry point for every request       | `RateLimitResult allow(String clientId, String endpoint)` |

That's it. **One public method.** Resist adding `getConfig`, `updateConfig`, `addEndpoint` — the requirements don't ask for them, and dynamic config is explicitly out of scope.

### RateLimiter — class outline (write this on the board)

```java
public class RateLimiter {
    private final Map<String, Limiter> limiters;     // endpoint → Limiter (immutable)
    private final Limiter defaultLimiter;

    public RateLimiter(List<LimiterConfig> configs, LimiterConfig defaultConfig) { ... }

    public RateLimitResult allow(String clientId, String endpoint) {
        Limiter limiter = limiters.getOrDefault(endpoint, defaultLimiter);
        return limiter.allow(clientId);
    }
}
```

### LimiterFactory — Factory pattern

```java
public class LimiterFactory {
    public Limiter create(LimiterConfig config) {
        switch (config.algorithm()) {
            case "TokenBucket":
                return new TokenBucketLimiter(
                        config.getInt("capacity"),
                        config.getInt("refillRatePerSecond"));
            case "SlidingWindowLog":
                return new SlidingWindowLogLimiter(
                        config.getInt("maxRequests"),
                        config.getLong("windowMs"));
            default:
                throw new IllegalArgumentException("Unknown algorithm: " + config.algorithm());
        }
    }
}
```

> **Why a Factory and not branching in `RateLimiter` directly?** Adding a new algorithm = new `Limiter` class + one new switch case in `LimiterFactory`. Without the factory, the dispatch would live INSIDE `RateLimiter`'s constructor — and the constructor would grow with every algorithm. **Single responsibility.** RateLimiter routes requests; Factory builds limiters.

### Limiter — Strategy interface

```java
public interface Limiter {
    RateLimitResult allow(String key);     // one method, per-key decision
}
```

### TokenBucketLimiter — outline

```java
public class TokenBucketLimiter implements Limiter {
    private final int capacity;
    private final int refillRatePerSecond;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final Clock clock;             // injected — makes refill math testable

    public RateLimitResult allow(String key) {
        TokenBucket bucket = buckets.computeIfAbsent(key, k -> new TokenBucket(capacity, clock.millis()));
        synchronized (bucket) {            // per-KEY lock — only same-client races serialize
            // refill → check → consume → return    (see Step 4)
        }
    }

    static final class TokenBucket {       // mutable per-key state
        double tokens;
        long   lastRefillTime;
    }
}
```

### SlidingWindowLogLimiter — outline

```java
public class SlidingWindowLogLimiter implements Limiter {
    private final int maxRequests;
    private final long windowMs;
    private final ConcurrentHashMap<String, RequestLog> logs = new ConcurrentHashMap<>();
    private final Clock clock;

    public RateLimitResult allow(String key) {
        RequestLog log = logs.computeIfAbsent(key, k -> new RequestLog());
        synchronized (log) {
            // evict-stale → check → record → return    (see Step 4)
        }
    }

    static final class RequestLog { final Deque<Long> timestamps = new ArrayDeque<>(); }
}
```

### RateLimitResult — immutable record

```java
public record RateLimitResult(boolean allowed, int remaining, Long retryAfterMs) {
    public static RateLimitResult allow(int remaining)        { return new RateLimitResult(true,  remaining, null);          }
    public static RateLimitResult deny (long retryAfterMs)    { return new RateLimitResult(false, 0,         retryAfterMs);  }
}
```

> **Why `Long` (boxed) for `retryAfterMs`?** Nullable when allowed — sentinel values like `0` would be ambiguous with "you may retry immediately, you're right at the edge". A boxed `Long` makes the absence explicit at the type level.

### Diagram — class cards

```
+----------------------------------+   +-----------------------------------+
|           RateLimiter             |   |          LimiterFactory          |
+----------------------------------+   +-----------------------------------+
| - limiters: Map<String, Limiter>  |   | + create(LimiterConfig): Limiter  |
| - defaultLimiter: Limiter         |   +-----------------------------------+
+----------------------------------+
| + allow(clientId, endpoint):      |   +-----------------------------------+
|     RateLimitResult               |   | <<interface>> Limiter             |
+----------------------------------+   +-----------------------------------+
                                       | + allow(key): RateLimitResult     |
                                       +-----------------------------------+
                                              ^                ^
                                              |                |
                              TokenBucketLimiter        SlidingWindowLogLimiter
                              - capacity                - maxRequests
                              - refillRate              - windowMs
                              - buckets: ConcurrentHashMap  - logs: ConcurrentHashMap

+-----------------------------+    +------------------------------------------+
|   RateLimitResult (record)  |    |        LimiterConfig (record)            |
+-----------------------------+    +------------------------------------------+
| boolean allowed             |    | String endpoint                          |
| int     remaining           |    | String algorithm                         |
| Long    retryAfterMs        |    | Map<String,Object> algoConfig            |
+-----------------------------+    +------------------------------------------+

RateLimiter --owns--> Map + defaultLimiter
TokenBucketLimiter --owns--> ConcurrentHashMap<String, TokenBucket>   (per-key state)
```

### The principle to verbalize — Strategy + Factory + Information Expert
> "Strategy lets the algorithm vary at runtime; Factory hides the heterogeneous-config-to-concrete-class dispatch in one place; per-key state lives on each Limiter implementation because only it knows what shape that state takes — Information Expert. Three patterns / principles, three separate axes of change."

---

## STEP 4 — Implementation (~17 min)

### Open by asking
> "Real Java or pseudo-code? I'll walk through `TokenBucketLimiter.allow` end-to-end (refill, check, deny-with-retry), then sketch `SlidingWindowLogLimiter.allow`, then dry-run a depletion scenario."

### 4.1 `TokenBucketLimiter.allow` — the canonical method

```java
@Override
public RateLimitResult allow(String key) {
    // First-time clients start with a FULL bucket — they get their burst immediately.
    TokenBucket bucket = buckets.computeIfAbsent(
            key, k -> new TokenBucket(capacity, clock.millis()));

    synchronized (bucket) {
        long now = clock.millis();
        long elapsedMs = now - bucket.lastRefillTime;

        // Lazy refill: how many tokens "should have" arrived since the last touch.
        double tokensToAdd = (elapsedMs * refillRatePerSecond) / 1000.0;
        bucket.tokens = Math.min(capacity, bucket.tokens + tokensToAdd);  // CAP!
        bucket.lastRefillTime = now;

        if (bucket.tokens >= 1.0) {
            bucket.tokens -= 1.0;
            return RateLimitResult.allow((int) Math.floor(bucket.tokens));
        }

        // Deny: tell client exactly how long until 1 full token is available.
        double tokensNeeded = 1.0 - bucket.tokens;
        long retryAfterMs = (long) Math.ceil((tokensNeeded * 1000.0) / refillRatePerSecond);
        return RateLimitResult.deny(retryAfterMs);
    }
}
```

**Five callouts to deliver out loud while writing this:**

1. *"First-time clients get a FULL bucket — `new TokenBucket(capacity, now)`. The first request always succeeds; we don't penalize them for having no history."*

2. *"`computeIfAbsent` is atomic on a ConcurrentHashMap — two threads calling allow for the same brand-new key cannot both create a bucket and race the result."*

3. *"Lazy refill, NOT background-thread refill. We compute how many tokens 'should have' arrived since the last touch. No work happens while the bucket is idle, no thread runs per-client. This is also how production rate limiters work."*

4. *"Cap at `capacity`. Without it, a client idle 10 minutes at refill=1/sec would have 600 tokens — burst limit destroyed."*

5. *"`Math.ceil` on `retryAfterMs`. Round UP, never down. Telling someone to retry too soon causes another denial — strictly worse than a slightly longer wait."*

### 4.2 `SlidingWindowLogLimiter.allow` — the accuracy variant

```java
@Override
public RateLimitResult allow(String key) {
    RequestLog log = logs.computeIfAbsent(key, k -> new RequestLog());

    synchronized (log) {
        long now = clock.millis();
        long cutoff = now - windowMs;

        // Evict stale timestamps from the FRONT — ArrayDeque is O(1) both ends.
        while (!log.timestamps.isEmpty() && log.timestamps.peekFirst() < cutoff) {
            log.timestamps.pollFirst();
        }

        if (log.timestamps.size() < maxRequests) {
            log.timestamps.addLast(now);
            return RateLimitResult.allow(maxRequests - log.timestamps.size());
        }

        // Deny: client can retry when the OLDEST in-window timestamp ages out.
        long oldest = log.timestamps.peekFirst();
        long retryAfterMs = (oldest + windowMs) - now;
        return RateLimitResult.deny(retryAfterMs);
    }
}
```

> **Senior callouts on this one:**
> - *"`ArrayDeque` not `LinkedList` — both ends are O(1), no per-node allocation overhead."*
> - *"Eviction happens on every `allow` call (lazy), not on a timer. Same on-demand model as the token bucket."*
> - *"Memory cost is O(maxRequests) per active client. That's why Token Bucket is usually preferred at scale — constant per-key memory regardless of throughput."*

### 4.3 `LimiterFactory.create` + `RateLimiter` ctor

```java
public Limiter create(LimiterConfig config) {
    switch (config.algorithm()) {
        case "TokenBucket":
            return new TokenBucketLimiter(
                    config.getInt("capacity"),
                    config.getInt("refillRatePerSecond"));
        case "SlidingWindowLog":
            return new SlidingWindowLogLimiter(
                    config.getInt("maxRequests"),
                    config.getLong("windowMs"));
        default:
            throw new IllegalArgumentException("Unknown algorithm: " + config.algorithm());
    }
}

public RateLimiter(List<LimiterConfig> configs, LimiterConfig defaultConfig) {
    LimiterFactory factory = new LimiterFactory();
    Map<String, Limiter> built = new HashMap<>();
    for (LimiterConfig cfg : configs) {
        if (cfg.endpoint() == null) continue;
        built.put(cfg.endpoint(), factory.create(cfg));
    }
    this.limiters       = Map.copyOf(built);          // immutable post-ctor
    this.defaultLimiter = factory.create(defaultConfig);
}
```

> **Eager vs lazy construction:** *"I build every endpoint's limiter at startup. Lazy creation would require double-checked locking on first request — extra complexity for a few-hundred-objects memory savings. At typical scale, eager is correct."*

### 4.4 Verification — dry-run a TokenBucket depletion

```
Setup: capacity=5, refillRatePerSecond=1, clock frozen at t=0.

allow("alice"):
   first request → new TokenBucket(tokens=5, lastRefill=0)
   synchronized(bucket):
     elapsedMs = 0; tokensToAdd = 0; tokens = 5
     5 >= 1 → tokens = 4; return allow(remaining=4)                          ✓

allow("alice") x4 more times (clock still frozen at t=0):
   elapsedMs = 0 each time → no refill
   tokens drops 4 → 3 → 2 → 1 → 0
   returns allow(remaining=3), allow(2), allow(1), allow(0)                  ✓

allow("alice") — 6th request, clock still 0:
   tokens = 0 → 0 >= 1 FALSE → DENY
   tokensNeeded = 1 - 0 = 1
   retryAfterMs = ceil(1 * 1000 / 1) = 1000   ms                              ✓

advance clock by 1500ms → now t=1500:
allow("alice"):
   elapsedMs = 1500 - 0 = 1500
   tokensToAdd = 1500 * 1 / 1000 = 1.5
   tokens = min(5, 0 + 1.5) = 1.5
   lastRefillTime = 1500
   1.5 >= 1 → tokens = 0.5; return allow(remaining=0 — floor(0.5))           ✓
```

> **Subtle point worth saying:** *"After the refill we're at 1.5 tokens, but `remaining` returns `floor(0.5) = 0`. That's by design — you can't make half a request, so the API reports usable whole tokens. The internal state is 0.5, which next refill will compound on."*

### 4.5 Verification — concurrent burst (the load-bearing test)

```
Setup: capacity=10, refillRatePerSecond=1, clock FROZEN at t=0.
       50 threads all call allow("shared-client") simultaneously.

With per-key synchronization:
   - Thread A acquires bucket lock; tokens=10 → 9; allow; release.
   - Thread B acquires; tokens=9 → 8; allow; release.
   - ... 10 threads in total see tokens >= 1, each decrement ...
   - Thread 11 acquires; tokens=0; 0 >= 1 FALSE; DENY (retryAfterMs=1000).
   - Threads 12..50 same: all DENY.

   FINAL:    10 allowed, 40 denied, 50 total                                  ✓

Without per-key synchronization (the bug we're preventing):
   - 50 threads all read tokens=10 simultaneously (no lock).
   - Each decrements based on its STALE view → races.
   - Could be ANYTHING from 10 to 50 successes.
   - Worse: the final `tokens` value is non-deterministic — torn writes.

   FINAL:    indeterminate.

The driver in this folder runs the WITH-lock test and prints:
   allowed = 10   (expect exactly 10)
   denied  = 40   (expect exactly 40)
   per-key locking holds ✓
```

> **This is the single most-important test in this problem.** Mention you'd verify it with a `CountDownLatch` barrier (so all 50 threads start the call simultaneously) and assert exact counts.

---

## STEP 5 — Extensibility (~8 min)

### 5.1 "How would you add a new algorithm — say, Fixed Window Counter?"

> **Problem in current design:** *"None — the design is built for this. Two file changes."*
>
> **Pattern as the fix:** *"Strategy and Factory are already in place. Step 1: implement `FixedWindowCounterLimiter implements Limiter`, tracking `(count, windowStart)` per key. Step 2: add one switch case to `LimiterFactory.create`. Zero changes to `RateLimiter`, zero changes to existing limiters."*
>
> **Tradeoff:** *"If algorithms become pluggable at runtime, evolve the switch to a `Map<String, Function<LimiterConfig, Limiter>>` registry — register builders at startup. For two-to-five algorithms the switch is clearer; for 20+ a registry pays off."*

### 5.2 "How would you handle thread safety?" — *(if you didn't pre-bake it)*

> **Problem in current design:** *"If I'd written `TokenBucketLimiter` with a plain `HashMap` and no `synchronized`, two threads both reading tokens=1, both seeing it ≥ 1, both decrementing → 2 successful requests for 1 token's worth of capacity. Classic check-then-act."*
>
> **Pattern as the fix:** *"Per-KEY locking — `ConcurrentHashMap` for the keys map (so creation is atomic via `computeIfAbsent`); `synchronized(bucket)` for the check-and-update on the per-key state. Different clients never block each other."*
>
> **Why not a global lock on the limiter?** *"A global lock serializes ALL traffic across ALL clients. With per-key locking, only requests for the SAME client serialize — which is the actual correctness requirement."*

```java
// Already in our base code, but the pattern as a one-liner:
TokenBucket bucket = buckets.computeIfAbsent(key, k -> new TokenBucket(capacity, clock.millis()));
synchronized (bucket) { /* refill + check + consume + return */ }
```

### 5.3 "How would you handle memory growth from tracking many clients?"

> **Problem in current design:** *"Per-key state never goes away. With millions of unique client ids, the buckets / logs map grows unbounded → OOM."*
>
> **Pattern as the fix:** *"Eviction policy. Two clean options:*
> - *Background sweeper scanning each map every N seconds, evicting entries whose `lastRefillTime` is more than TTL ago.*
> - *Bounded LRU cache — when at capacity, evict the least recently used entry.*"
>
> **What happens to the evicted client?** *"Their next request looks like a first-time client → they get a fresh full bucket. That's acceptable for inactive clients (by definition they weren't using it). Active clients constantly update their `lastRefillTime` and never get evicted."*

```java
// Sketch — sweeper thread checking lastRefillTime
scheduler.scheduleAtFixedRate(() -> {
    long cutoff = clock.millis() - TimeUnit.MINUTES.toMillis(30);
    buckets.entrySet().removeIf(e -> e.getValue().lastRefillTime < cutoff);
}, 1, 1, TimeUnit.MINUTES);
```

### 5.4 "How would you make this distributed (multiple servers)?"

> **Problem in current design:** *"Per-server state means a client hitting server A and server B each gets their own quota — effectively doubling the limit per server."*
>
> **Pattern as the fix:** *"Redis. Two production patterns:*
> 1. ***Sliding window with sorted sets*** — `ZADD key timestamp timestamp`, `ZREMRANGEBYSCORE` to evict stale, `ZCARD` to count. Atomic via MULTI/EXEC or a Lua script.*
> 2. ***Token bucket with Lua scripts*** — single Lua script reads tokens+lastRefill, computes refill, decrements, writes back. Lua scripts are atomic in Redis.*"
>
> **What our Limiter interface enables:** *"`RedisTokenBucketLimiter implements Limiter` — same interface, same Factory case. RateLimiter's allow() is unchanged. THIS is why Strategy was in the base."*

### 5.5 Other "what-if" answers

| Follow-up                                  | Answer                                                                                              |
| ------------------------------------------ | --------------------------------------------------------------------------------------------------- |
| "Hot-reload config at runtime"             | Replace `final Map<>` with `volatile AtomicReference<Map<>>`. Update atomically; readers see either old or new map, never half-updated. |
| "Per-client config overrides (premium users)" | Two-tier lookup: per-(endpoint, clientId) first, fall back to per-endpoint. Compose two Limiters. |
| "Cost-aware rate limiting (some endpoints expensive)" | Generalize "1 token" to "N tokens" — pass cost to `allow(key, cost)`. Token Bucket already supports this naturally. |
| "Metrics — how many requests denied per endpoint" | Wrap each Limiter in a `MeteredLimiter` (Decorator). Count allow/deny per endpoint label. |
| "Multiple algorithms per endpoint (AND semantics)" | `CompositeLimiter implements Limiter` — runs all child limiters; denies if ANY denies. Atomically commit only on full success. |
| "Different per-endpoint user tiers (free/pro/enterprise)" | Stratify the key — instead of `clientId`, key by `(tier, clientId)` so each tier gets its own bucket. |

---

## Design Patterns — Hello Interview's canonical 8

> **HI's stance:** *"Patterns arise from good design decisions, not the other way around. Most interview designs use zero to two patterns maximum."*
>
> **Rate Limiter is one of the two HI problems where 3 patterns earn rent in the base** (Logger is the other). Each is directly justified by an explicit requirement — this isn't pattern-stuffing.

### The 5-step timing rule

| Step                       | Use a pattern here?                                                                 |
| -------------------------- | ----------------------------------------------------------------------------------- |
| **1. Requirements**        | **Never.**                                                                          |
| **2. Entities**            | **Sometimes** — if a clear seam exists. *Both `Limiter` interface and `LimiterFactory` belong here.* |
| **3. Class Design**        | **YES, when you can state the design pressure in one sentence.** *Rate Limiter passes for THREE patterns.* |
| **4. Implementation**      | **No new patterns.**                                                                |
| **5. Extensibility**       | **YES — registry as evolution of factory; Decorator for metrics; etc.**             |

### Hello Interview's canonical 8 × interviewer trigger

| # | Pattern              | Category   | Trigger phrase                                                                | One-line response                                                                                       |
| - | -------------------- | ---------- | ------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------- |
| 1 | **Strategy** ⭐       | Behavioral | "different algorithms" · "vary at runtime"                                      | *"Promote behavior to an interface; inject the concrete implementation."*                                |
| 2 | **Observer**         | Behavioral | "notify multiple" · "metrics" · "audit"                                         | *"X publishes events; subscribers register independently."*                                              |
| 3 | **State Machine**    | Behavioral | "behavior depends on state"                                                     | *"Each state owns its transitions."*                                                                    |
| 4 | **Factory** ⭐        | Creational | "heterogeneous config" · "discriminator field" · "create by type name"          | *"Centralize creation behind a method that dispatches on the discriminator."*                            |
| 5 | **Builder**          | Creational | "many optional fields"                                                          | *"Builder collects fields incrementally; `build()` validates."*                                          |
| 6 | **Singleton**        | Creational | "exactly one" · "global registry"                                              | *"Resist textbook Singleton; DI a single instance instead."*                                              |
| 7 | **Decorator**        | Structural | "add cross-cutting behavior" · "metrics / logging / retry / caching"            | *"Wrap X in decorators, each adding one concern."*                                                       |
| 8 | **Facade**           | Structural | "single entry point" · "hide complexity"                                        | *"Orchestrators usually ARE facades."*                                                                    |

### Three rules to sound natural

1. **Cap at 3 patterns in the base for this problem** (Strategy + Factory + Facade — each justified).
2. **Always name the concrete win in the same breath.**
3. **Never volunteer a pattern without a trigger.**

### How this maps to Rate Limiter specifically

**Already in the BASE design — call out by name:**

- **Strategy (#1)** ⭐ — `Limiter` interface + `TokenBucketLimiter`, `SlidingWindowLogLimiter` implementations. **Justified by R1+R2:** "different algorithms with algorithm-specific parameters". Name it in Step 2.
- **Factory (#4)** ⭐ — `LimiterFactory` turns the heterogeneous `{ algorithm, algoConfig }` blob into the right concrete `Limiter`. **Justified by R1's discriminated-union config shape.** Name it in Step 2.
- **Facade (#8)** — `RateLimiter` is the only class application code touches; it hides Limiter, LimiterFactory, and per-key locking.
- **Information Expert** (GRASP principle) — per-key state lives on each Limiter implementation because the SHAPE of that state varies per algorithm. There's no useful base-class state to share.
- **Dependency Injection** (principle) — `Clock` injected for testable refill math.
- **Immutability** (principle) — `RateLimitResult` and the `limiters` map. No shared mutable state on the hot read path.

**Reach for these on the matching Step-5 follow-up — 3-beat phrasing (problem → pattern → tradeoff):**

| Follow-up                                  | Pattern (HI's 8)             | Your line                                                                                            |
| ------------------------------------------ | ---------------------------- | ---------------------------------------------------------------------------------------------------- |
| "Add metrics — count allow/deny per endpoint" | **Decorator (#7)**        | *"Wrap each Limiter in `MeteredLimiter(Limiter)`. Counter on each allow/deny call. Stackable with other wrappers."* |
| "Hot-reload config"                        | (atomic reference swap)      | *"Replace `final Map` with `AtomicReference<Map>`; readers see either old or new — never half-applied."* |
| "Pluggable algorithms at runtime"          | (Registry — evolution of #4) | *"Replace the Factory switch with `Map<String, Function<LimiterConfig, Limiter>>`. New algorithms register at startup; switch goes away."* |
| "Composite limits (rate AND concurrency)"  | (Composite over Limiter)     | *"`CompositeLimiter(List<Limiter>)` — allows only when ALL children allow. Returns the longest retryAfterMs among the denials."* |
| "Audit log of denials"                     | **Observer (#2)**            | *"Each Limiter publishes `RequestDenied` events; an audit subscriber records them. Don't log-from-Limiter; coupling explodes."* |
| "Distributed across servers"               | (Repository / Strategy of Strategy) | *"`RedisTokenBucketLimiter implements Limiter` — same interface, atomic via Lua scripts. Slot it in as a new Factory case."* |
| "Builder for complex config DSL"           | **Builder (#5)**             | *"`RateLimiterBuilder` if config-DSL ergonomics grow (per-tier overrides, time-of-day, ...). For static JSON-driven config, the record + Factory is enough."* |

**Patterns to actively refuse:**

- **Singleton on `RateLimiter`** — kills tests; one shared instance is fine via DI without Singleton ceremony.
- **State pattern on `RateLimitResult`** — three immutable fields, no state machine.
- **`Limiter` as abstract base class** — no useful shared state; would be an interface with extra steps.
- **Per-algorithm config CLASSES** (`TokenBucketConfig`, `SlidingWindowConfig`, ...) — heterogeneous data from an external config service is naturally a discriminated `Map`. Typed configs would force a Visitor or instanceof ladder in the Factory.
- **Builder for the 2-arg `RateLimiter(configs, default)` ctor** — academic noise.

### One sentence to say at the end of Step 3

> *"The base design names three patterns out loud: Strategy on Limiter, Factory in LimiterFactory, and Facade on RateLimiter. Each is justified by a specific requirement — Strategy by 'multiple algorithms', Factory by the discriminated-union config shape, Facade by 'one public method, hide the dispatch'. No Observer or Decorator yet; those land in Step 5 if metrics, audit, or wrapping come up."*

---

## Interview deep-dives — the questions you'll definitely get asked

### 1. Complexity (Big-O)

Let `E` = number of endpoints, `K` = active clients per limiter, `W` = max in-window requests per client (SlidingWindow only).

| Operation                                | Time                                              | Space            | Notes                                                                              |
| ---------------------------------------- | ------------------------------------------------- | ---------------- | ---------------------------------------------------------------------------------- |
| `RateLimiter.allow(client, endpoint)`    | **`O(1)`** + cost of `Limiter.allow`              | -                | Map lookup + delegation                                                            |
| `TokenBucket.allow(key)`                 | **`O(1)`** amortized                              | `O(1)` per call  | computeIfAbsent + synchronized block + math                                        |
| `SlidingWindowLog.allow(key)`            | **`O(W)`** worst case (evict-all-then-check)      | `O(W)` per key   | Eviction is amortized O(1) per call (each timestamp evicted once)                  |
| `LimiterFactory.create`                  | **`O(1)`**                                        | -                | Switch                                                                             |
| Constructor                              | **`O(E)`**                                        | `O(E)`           | One pass building the map                                                          |
| Storage — RateLimiter                    | -                                                 | **`O(E)`**       | Endpoint → Limiter map                                                             |
| Storage — TokenBucketLimiter             | -                                                 | **`O(K)`**       | One TokenBucket per active client                                                  |
| Storage — SlidingWindowLogLimiter        | -                                                 | **`O(K · W)`**   | Per-client timestamp queue — this is why TokenBucket scales better                |

> **Senior callout:** *"TokenBucket is `O(1)` per call and `O(K)` total memory regardless of throughput. SlidingWindowLog is `O(W)` per call and `O(K·W)` memory, but gives perfect accuracy with no boundary-burst artifact that fixed-window counters have. The choice between them is space-accuracy tradeoff."*

### 2. Concurrency / thread-safety — the design

| Approach                            | When to use                                  | Cost                                                              |
| ----------------------------------- | -------------------------------------------- | ----------------------------------------------------------------- |
| **Per-key `synchronized(bucket)`** ⭐ | **Our base.** Correct + simple + scales to many clients | Same-client races serialize; different clients are independent |
| Global lock on Limiter / RateLimiter | NEVER — turns rate limiting itself into the bottleneck | All traffic serializes; the rate limiter rate-limits itself |
| Lock-free / CAS on AtomicLong tokens | Extreme throughput, careful refill ordering  | Tricky for refill arithmetic (read-modify-write across two fields) |
| Single-thread coordinator (Redis-style) | Distributed; serialization is on a separate server | Network round-trip per allow call                            |

> **What HI's commenters argue about:** *"Some suggest a single command-queue + single-thread coordinator (Redis-style). Valid for distributed, overkill for in-process — you've turned every allow into a queue enqueue + dequeue, just to serialize work that's already trivially serial per-key. Mention it as an option for the distributed case in §5.4."*

### 3. Testing — what to write tests for

The injected `Clock` is the only reason this is unit-testable without sleeps.

| Test category                | Cases to cover                                                                                              |
| ---------------------------- | ----------------------------------------------------------------------------------------------------------- |
| Burst capacity               | capacity=5 → 5 rapid allows succeed → 6th denied (clock frozen)                                            |
| Refill math                  | Frozen clock at t=0, drain bucket, advance 1.5s → exactly 1 more allow succeeds, internal tokens=0.5      |
| Retry-after math             | Drain bucket, deny → `retryAfterMs` ≈ 1000/refillRate; clock-advance that → next allow succeeds            |
| Cap at capacity              | Frozen clock, idle for 1 hour, allow → `remaining = capacity-1` (NOT capacity + bonus)                     |
| First-time client            | First-ever `allow` for a new key → succeeds with `remaining = capacity-1`                                  |
| **Per-key concurrency**      | 50 threads, frozen clock, capacity 10 → exactly 10 allowed + 40 denied                                     |
| Default fallback             | `allow(client, "/unknown")` → routes to defaultLimiter                                                     |
| Factory error                | Unknown algorithm in config → `IllegalArgumentException` at construction                                   |
| SlidingWindow eviction       | maxRequests=3, fill, advance clock past window → all 3 evict, next 3 allow                                 |

```java
@Test
void fifty_threads_capacity_ten_exactly_ten_succeed() throws Exception {
    MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    TokenBucketLimiter tb = new TokenBucketLimiter(10, 1, clock);
    int N = 50;
    AtomicInteger allowed = new AtomicInteger(), denied = new AtomicInteger();
    ExecutorService pool = Executors.newFixedThreadPool(N);
    CountDownLatch fire = new CountDownLatch(1);

    for (int i = 0; i < N; i++) pool.submit(() -> {
        try { fire.await(); } catch (InterruptedException e) { return; }
        if (tb.allow("shared").allowed()) allowed.incrementAndGet();
        else denied.incrementAndGet();
    });
    fire.countDown();
    pool.shutdown();
    pool.awaitTermination(5, TimeUnit.SECONDS);

    assertEquals(10, allowed.get());
    assertEquals(40, denied.get());
}
```

> **Senior callout:** *"This test is in the driver. Without `synchronized(bucket)`, `allowed.get()` is non-deterministic — typically way above 10 because multiple threads read `tokens=10` simultaneously. The test is the empirical proof of correctness."*

### 4. SOLID mapping

| Letter                       | Where it shows up                                                                                          |
| ---------------------------- | ---------------------------------------------------------------------------------------------------------- |
| **S** Single Responsibility  | RateLimiter = routing. LimiterFactory = creation. Each Limiter = one algorithm. RateLimitResult = one decision. Five reasons to change, five classes. |
| **O** Open/Closed            | Adding a new algorithm = new `Limiter` class + one new switch case in Factory. **Zero changes to RateLimiter, existing limiters, or RateLimitResult.** This is the whole reason for Strategy + Factory. |
| **L** Liskov Substitution    | Any Limiter substitutable behind the interface — same `allow(key)` contract, same exception expectations. New impls must honor the per-key isolation guarantee. |
| **I** Interface Segregation  | `Limiter` has ONE method. No `flush()`, `stats()`, or other concerns mixed in. Cross-cutting concerns are Decorators (Step 5). |
| **D** Dependency Inversion   | `RateLimiter` depends on the `Limiter` interface, not on `TokenBucketLimiter` or `SlidingWindowLogLimiter`. `LimiterFactory` is the composition root that wires up concretes from config — outside the hot path. |

### 5. "Summarize your design in 30 seconds"

> *"Six core types: RateLimiter (orchestrator + facade), LimiterFactory (Factory pattern — turns heterogeneous config blobs into concrete limiters), Limiter (Strategy interface), TokenBucketLimiter and SlidingWindowLogLimiter (the two concrete algorithms), RateLimitResult (immutable record — allowed + remaining + retryAfterMs). Three patterns in the base — Strategy, Factory, Facade — each justified by an explicit requirement: 'multiple algorithms', 'discriminated-union config', and 'one public method'. Per-algorithm state lives on each Limiter because the shape varies — TokenBucket needs `tokens` (a double for fractional refill) and `lastRefillTime`; SlidingWindowLog needs a timestamp deque. Refill is lazy / on-demand — no background threads, no work on idle clients. Per-KEY locking via `ConcurrentHashMap.computeIfAbsent` + `synchronized(bucket)` — different clients never block each other, only same-client races serialize. The 50-thread driver test with a frozen clock proves it: capacity 10, 50 racers, exactly 10 allowed, 40 denied. Extensions: registry as evolution of the factory, Decorator for metrics, Redis-backed Limiter for distributed."*

That's ~55 seconds. Hits: structure, the three justified patterns, the per-algorithm state shape, the lazy-refill insight, the per-key locking choice, and the empirical concurrency proof.

---

## Closing soundbites (memorize these)

- **Opening:** *"In-process library or distributed service? Single-process per the prompt — let me clarify the concurrency bar."*
- **Why per-algorithm state lives on each Limiter:** *"State shape varies — TokenBucket has tokens+lastRefill, SlidingWindow has a timestamp queue. No useful common base. Information Expert: state lives where it's used."*
- **Why tokens is a double:** *"Refill is continuous — 100ms at 1/sec is 0.1 tokens. An int would silently round to zero."*
- **Why cap at capacity:** *"Without the cap, an idle client banks tokens forever — destroys the burst limit."*
- **Why retryAfterMs rounds UP:** *"Telling someone to retry too soon causes another denial — strictly worse than a slightly longer wait."*
- **Why lazy refill (no background thread):** *"Background timers waste work on idle clients. Lazy refill only computes when there's actually a request."*
- **Why per-key locking:** *"Lock at the level of the resource being protected — the per-key bucket. Different clients never block each other."*
- **Why Strategy + Factory in the base:** *"R1+R2 explicitly demand multiple algorithms with heterogeneous configs. Pre-baking these patterns isn't pattern-stuffing — it's responding to stated requirements."*

---

## Top mistakes that lose points

- **Storing tokens as an int** — silent rounding to zero on every <1-token refill; the rate limiter never refills.
- **No cap at capacity** — idle clients bank tokens; burst limit destroyed.
- **Rounding `retryAfterMs` DOWN** — clients retry too soon and get denied again. Always `ceil`.
- **Background thread refilling all buckets every second** — wastes work on inactive clients; lazy refill is strictly better.
- **Global lock on Limiter / RateLimiter** — turns the rate limiter into the bottleneck; different clients should run in parallel.
- **Lock on a String key** — different `new String(key)` instances aren't the same monitor; subtle bugs. Lock on the bucket OBJECT, not on the key.
- **Adding `Request`/`Client`/`Endpoint` classes** — they're external strings, no managed state.
- **Per-algorithm config classes** (`TokenBucketConfig`, `SlidingWindowConfig`) — heterogeneous JSON is naturally a discriminated `Map`; typing it forces Visitor / instanceof.
- **Not handling unknown endpoints** — should fall back to default, not reject. R4.
- **No `clock` injection** — refill math becomes untestable without sleeps.
- **Memory grows unbounded** — every new clientId allocates a bucket forever. Mention eviction in §5.
- **Skipping the empirical concurrency test** — without the 50-thread test, "thread-safe" is just prose.

---

## Files in this folder (your reference implementation)

| File                                                          | What it shows                                                                            |
| ------------------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| `model/RateLimitResult.java`                                  | Immutable record + static `allow` / `deny` factories                                     |
| `model/LimiterConfig.java`                                    | Record matching the external config shape; tolerant `getInt`/`getLong` for JSON numbers  |
| `algorithm/Limiter.java`                                      | Strategy interface — one method, single responsibility                                   |
| `algorithm/TokenBucketLimiter.java`                           | ConcurrentHashMap + synchronized(bucket); lazy refill; double tokens; capped; `ceil` retry |
| `algorithm/SlidingWindowLogLimiter.java`                      | ArrayDeque per key; O(1) eviction from front; perfect accuracy                          |
| `LimiterFactory.java`                                         | Factory pattern with switch on algorithm discriminator                                   |
| `RateLimiter.java`                                            | Facade — eager construction, immutable map, default fallback                            |
| `RateLimiterDriver.java`                                      | 4 scenarios — TokenBucket basics, SlidingWindow basics, multi-endpoint, **50-thread burst** |

Run from the project root:

```bash
mvn -q compile exec:java \
  -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.ratelimiter.RateLimiterDriver
```
