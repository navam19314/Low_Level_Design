# Rate Limiter — 45-min LLD Interview Walkthrough

**Target:** SDE-2 at Amazon, Adobe, Microsoft, Flipkart, etc.

> Rate Limiter is **algorithm-heavy**. The three things interviewers test: (1) you know at least two algorithms and their tradeoffs, (2) you get the refill math right (tokens as `double`, cap at capacity, `ceil` on retry), (3) you know why per-key locking beats a global lock. Get these three and you're set.

---

## Time Budget

| Step | What you're doing                                   | Time   |
|------|-----------------------------------------------------|--------|
| 1    | Requirements                                        | 5 min  |
| 2    | Entities + relationships                            | 4 min  |
| 3    | Class design                                        | 10 min |
| 4    | Code: TokenBucket end-to-end + SlidingWindow sketch | 18 min |
| 5    | Extensibility                                       | 7 min  |

Step 4 is the longest because the refill math and retry math both need a dry-run — don't skip it.

---

## Mental Models — know these before you walk in

### M1. The two algorithms

**Token Bucket** — think of a bucket that refills at a fixed rate.

```
bucket starts full (capacity = 5 tokens)

request arrives → take 1 token → allow
request arrives → take 1 token → allow
...
bucket empty    → deny. tell client: retry in X ms (time to refill 1 token)

while idle → tokens accumulate, capped at capacity
```

Key numbers: `capacity` (max burst), `refillRatePerSecond` (steady-state throughput).

**Sliding Window Log** — remember the exact timestamp of every request in the last N ms.

```
window = 1000ms, maxRequests = 3

t=0   req → log=[0]         size=1 → allow
t=100 req → log=[0,100]     size=2 → allow
t=200 req → log=[0,100,200] size=3 → allow
t=300 req → size=3 already  → deny. retry when t=0 ages out (in 700ms)
t=1001 → t=0 evicted, log=[100,200] size=2 → allow
```

Key number: `maxRequests` per `windowMs`. More accurate than Token Bucket (no boundary burst), costs more memory (O(maxRequests) per client vs O(1)).

### M2. Token Bucket refill math — what candidates get wrong

```
On every allow() call:

now         = clock.millis()
elapsedMs   = now - lastRefillTime
tokensToAdd = elapsedMs * refillRatePerSecond / 1000.0
tokens      = min(capacity, tokens + tokensToAdd)   ← CAP!
lastRefillTime = now

Why double (not int)?
  100ms at 1/s = 0.1 tokens. int rounds to 0 → bucket never refills.

Why cap at capacity?
  Client idle 10 min at refill=1/s → 600 tokens without cap → burst limit gone.

retryAfterMs when denied:
  tokensNeeded = 1.0 - tokens
  retryAfterMs = ceil(tokensNeeded * 1000 / refillRatePerSecond)
  Use ceil — never tell client to retry too soon (causes another denial).
```

### M3. Per-key locking — why it beats a global lock

```
Global lock (wrong):              Per-key lock (right):
synchronized(this) {              Bucket b = map.computeIfAbsent(key, ...);
  allow(key) { ... }              synchronized(b) { ... }
}

All clients block each other.     Only the SAME client's requests serialize.
Rate limiter becomes a bottleneck. Different clients run in parallel.
```

Lock on the **bucket object**, not on the string key. String keys can have multiple instances — locking on them is unreliable.

---

## Step 1 — Requirements (~5 min)

**Say aloud:**
> "Rate limiter can mean in-process library or distributed Redis-backed service. Let me clarify scope."

**Four things to confirm:**

| Theme | Question |
|-------|----------|
| Scope | "Single-process in-memory? Or distributed across servers?" |
| Algorithms | "At minimum Token Bucket + Sliding Window Log? More later?" |
| Response | "Does allow() return boolean, or structured (allowed + remaining + retryAfterMs)?" |
| Fallback | "Unknown endpoint → default limiter or reject?" |

**Write on the board:**
```
Functional Requirements:
1. allow(clientId, endpoint) → RateLimitResult { allowed, remaining, retryAfterMs }
2. Per-endpoint configuration: register("/search", new TokenBucketLimiter(100, 10))
3. Unknown endpoint → fall back to default limiter (never reject for missing config)
4. Per-client isolation — alice's quota doesn't affect bob's
5. Thread-safe — multiple threads call allow() concurrently

Out of Scope: distributed/Redis, hot-reload config, metrics, persistence
```

---

## Step 2 — Entities (~4 min)

**Five things, name them:**

```
RateLimiter          orchestrator + facade — the only class callers touch
LimiterFactory       creates the right Limiter from raw config data
Limiter              Strategy interface — allow(clientId) → RateLimitResult
TokenBucketLimiter   concrete algorithm #1
SlidingWindowLog     concrete algorithm #2
RateLimitResult      value object — (allowed, remaining, retryAfterMs)
```

**Why `LimiterFactory`?**
> "Config arrives as raw JSON — `{ algorithm: 'TokenBucket', algoConfig: { capacity: 100, ... } }`. Something needs to read the algorithm discriminator and call the right constructor. That's the factory. Without it, that switch lives inside `RateLimiter` which violates single responsibility."

**Relationships:**
```
RateLimiter  --uses-->  LimiterFactory at construction time
RateLimiter  --owns-->  Map<String, Limiter>   (endpoint → limiter)
RateLimiter  --owns-->  Limiter defaultLimiter
Each Limiter --owns-->  ConcurrentHashMap<String, PerKeyState>
```

---

## Step 3 — Class Design (~10 min)

### RateLimiter

```java
public class RateLimiter {
    private final Map<String, Limiter> limiters = new HashMap<>();
    private final Limiter defaultLimiter;

    // Config-driven constructor — takes raw JSON-like config, delegates creation to factory
    public RateLimiter(List<Map<String, Object>> configs, Map<String, Object> defaultConfig) {
        LimiterFactory factory = new LimiterFactory();
        for (Map<String, Object> config : configs) {
            String endpoint = (String) config.get("endpoint");
            if (endpoint == null) continue;
            limiters.put(endpoint, factory.create(config));
        }
        this.defaultLimiter = factory.create(defaultConfig);
    }

    public RateLimitResult allow(String clientId, String endpoint) {
        Limiter limiter = limiters.getOrDefault(endpoint, defaultLimiter);
        return limiter.allow(clientId);
    }
}
```

### LimiterFactory

```java
public class LimiterFactory {
    public Limiter create(Map<String, Object> externalConfig) {
        String algorithm = (String) externalConfig.get("algorithm");
        Map<String, Object> algoConfig = (Map<String, Object>) externalConfig.get("algoConfig");

        switch (algorithm) {
            case "TokenBucket":
                return new TokenBucketLimiter(
                        (int) algoConfig.get("capacity"),
                        (int) algoConfig.get("refillRatePerSecond"));
            case "SlidingWindowLog":
                return new SlidingWindowLogLimiter(
                        (int) algoConfig.get("maxRequests"),
                        ((Number) algoConfig.get("windowMs")).longValue());
            default:
                throw new IllegalArgumentException("Unknown algorithm: " + algorithm);
        }
    }
}
```

New algorithm = new class + one new `case`. `RateLimiter` and all existing algorithms never change.

### Limiter (Strategy interface)

```java
public interface Limiter {
    RateLimitResult allow(String clientId);
}
```

### TokenBucketLimiter — outline

```java
public class TokenBucketLimiter implements Limiter {
    private final int capacity;
    private final int refillRatePerSecond;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitResult allow(String clientId) {
        Bucket bucket = buckets.computeIfAbsent(clientId,
                k -> new Bucket(capacity, clock.millis()));
        synchronized (bucket) {  // per-KEY lock
            // refill → check → consume → return  (see Step 4)
        }
    }

    static class Bucket { double tokens; long lastRefillTime; }
}
```

### SlidingWindowLogLimiter — outline

```java
public class SlidingWindowLogLimiter implements Limiter {
    private final int maxRequests;
    private final long windowMs;
    private final ConcurrentHashMap<String, Deque<Long>> logs = new ConcurrentHashMap<>();

    public RateLimitResult allow(String clientId) {
        Deque<Long> log = logs.computeIfAbsent(clientId, k -> new ArrayDeque<>());
        synchronized (log) {
            // evict stale → check → record → return  (see Step 4)
        }
    }
}
```

### RateLimitResult

```java
public class RateLimitResult {
    private final boolean allowed;
    private final int remaining;
    private final Long retryAfterMs;  // null when allowed

    public static RateLimitResult allow(int remaining)    { return new RateLimitResult(true,  remaining, null); }
    public static RateLimitResult deny(long retryAfterMs) { return new RateLimitResult(false, 0, retryAfterMs); }
    // getters
}
```

**Patterns to name here:**
- **Strategy** — `Limiter` interface. Different algorithms swap in without touching `RateLimiter` or `LimiterFactory`.
- **Facade** — `RateLimiter` is the only class callers touch.
- **Factory** — `LimiterFactory` centralises creation logic; callers never `new TokenBucketLimiter(...)` directly.

---

## Step 4 — Implementation (~18 min)

### 4.1 TokenBucketLimiter.allow() — write this in full

```java
@Override
public RateLimitResult allow(String clientId) {
    Bucket bucket = buckets.computeIfAbsent(clientId,
            k -> new Bucket(capacity, clock.millis()));

    synchronized (bucket) {
        long now = clock.millis();
        long elapsedMs = now - bucket.lastRefillTime;

        double tokensToAdd = (elapsedMs * refillRatePerSecond) / 1000.0;
        bucket.tokens = Math.min(capacity, bucket.tokens + tokensToAdd);  // cap!
        bucket.lastRefillTime = now;

        if (bucket.tokens >= 1.0) {
            bucket.tokens -= 1.0;
            return RateLimitResult.allow((int) Math.floor(bucket.tokens));
        }

        long retryAfterMs = (long) Math.ceil((1.0 - bucket.tokens) * 1000.0 / refillRatePerSecond);
        return RateLimitResult.deny(retryAfterMs);
    }
}
```

**Say while writing:**
1. *"First-time client gets a full bucket — `new Bucket(capacity, now)`. First request always succeeds."*
2. *"`computeIfAbsent` is atomic — two threads can't both create a bucket for the same new client."*
3. *"Lazy refill — compute tokens owed since last touch. No background thread, no work on idle clients."*
4. *"Cap at capacity — idle client shouldn't bank unlimited tokens."*
5. *"`ceil` on retryAfterMs — never tell client to retry too soon."*

### 4.2 SlidingWindowLogLimiter.allow() — sketch this

```java
@Override
public RateLimitResult allow(String clientId) {
    Deque<Long> log = logs.computeIfAbsent(clientId, k -> new ArrayDeque<>());
    synchronized (log) {
        long now = clock.millis();
        long cutoff = now - windowMs;

        while (!log.isEmpty() && log.peekFirst() < cutoff) {
            log.pollFirst();  // evict stale timestamps from front
        }

        if (log.size() < maxRequests) {
            log.addLast(now);
            return RateLimitResult.allow(maxRequests - log.size());
        }

        long retryAfterMs = log.peekFirst() + windowMs - now;
        return RateLimitResult.deny(retryAfterMs);
    }
}
```

**Say:** *"ArrayDeque for O(1) front eviction. Eviction is lazy — happens on each allow(), not on a timer."*

### 4.3 Dry-run (do this at the board)

```
TokenBucket: capacity=5, refillRatePerSecond=1, clock frozen at t=0

allow("alice") — first call:
  new Bucket(tokens=5, lastRefillTime=0)
  elapsedMs=0, tokensToAdd=0, tokens=5
  5 >= 1 → tokens=4, return ALLOW(remaining=4)  ✓

allow("alice") x4 more (clock still frozen):
  elapsedMs=0, no refill. tokens: 3 → 2 → 1 → 0
  return ALLOW(3), ALLOW(2), ALLOW(1), ALLOW(0)  ✓

allow("alice") — 6th, tokens=0:
  0 >= 1 → false
  tokensNeeded=1.0, retryAfterMs = ceil(1000/1) = 1000
  return DENY(retryAfterMs=1000)  ✓

clock.advanceMs(1500):
  elapsedMs=1500, tokensToAdd=1.5, tokens=min(5, 0+1.5)=1.5
  1.5 >= 1 → tokens=0.5, return ALLOW(remaining=0)  ✓
  (floor(0.5) = 0 — can't make half a request)
```

---

## Step 5 — Extensibility (~7 min)

### E1. "Add a new algorithm — say, Fixed Window Counter"

**What it is:** count requests in fixed time slots (e.g. 0–1s, 1–2s). Reset counter at window boundary.

**Why Strategy makes this a one-liner:**

```java
public class FixedWindowLimiter implements Limiter {
    private final int maxRequests;
    private final long windowMs;
    private final Clock clock;
    private final ConcurrentHashMap<String, long[]> windows = new ConcurrentHashMap<>();
    //                                                          [0]=count, [1]=windowStart

    @Override
    public RateLimitResult allow(String clientId) {
        long[] w = windows.computeIfAbsent(clientId, k -> new long[]{0, clock.millis()});
        synchronized (w) {
            long now = clock.millis();
            if (now - w[1] >= windowMs) { w[0] = 0; w[1] = now; }  // new window
            if (w[0] < maxRequests) {
                w[0]++;
                return RateLimitResult.allow(maxRequests - (int) w[0]);
            }
            return RateLimitResult.deny(w[1] + windowMs - now);
        }
    }
}
```

Zero changes to `RateLimiter`. Just `rl.register("/api", new FixedWindowLimiter(100, 1000L, clock))`.

**Tradeoff to mention:** Fixed Window has a "boundary burst" problem — a client can use 100 requests in the last 500ms of window 1, then 100 more in the first 500ms of window 2, effectively getting 200 in 1 second. SlidingWindowLog avoids this; Token Bucket partially avoids it.

---

### E2. "Config comes from YAML / JSON at startup"

**The pattern:** add a `LimiterFactory` with a switch on an algorithm discriminator string.

```java
public class LimiterFactory {
    public Limiter create(String algorithm, Map<String, Object> config, Clock clock) {
        switch (algorithm) {
            case "TokenBucket":
                return new TokenBucketLimiter(
                        (int) config.get("capacity"),
                        (int) config.get("refillRatePerSecond"), clock);
            case "SlidingWindowLog":
                return new SlidingWindowLogLimiter(
                        (int) config.get("maxRequests"),
                        ((Number) config.get("windowMs")).longValue(), clock);
            default:
                throw new IllegalArgumentException("Unknown algorithm: " + algorithm);
        }
    }
}
```

**What to say:** *"Strategy + Factory is the seam. Adding a new algorithm = new Limiter class + one new switch case. Zero changes to RateLimiter or any existing algorithm."*

---

### E3. "How would you make it distributed (multiple servers)?"

**The problem:** each server has its own in-memory buckets. A client hitting server A and server B each gets their own quota — effectively double the limit.

**The fix:** `RedisTokenBucketLimiter implements Limiter` — same interface, Redis as the state store.

```java
// Lua script runs atomically on Redis — no race between read and write
String LUA_SCRIPT =
    "local tokens = tonumber(redis.call('GET', KEYS[1]) or ARGV[1]) " +
    "local now = tonumber(ARGV[2]) " +
    // ... refill math, cap, consume, return ...
    "redis.call('SET', KEYS[1], tokens) " +
    "return tokens";
```

**What to say:** *"Same `Limiter` interface — `RateLimiter.allow()` is unchanged. The only new thing is `RedisTokenBucketLimiter`. The Lua script is critical — it makes the read-refill-write atomic on Redis without needing a distributed lock."*

---

### E4. "Memory grows unbounded with millions of clients"

**The problem:** every new `clientId` creates a Bucket that lives forever. With 10M unique IPs, OOM.

**The fix:** background eviction sweeper.

```java
// in TokenBucketLimiter constructor
ScheduledExecutorService sweeper = Executors.newSingleThreadScheduledExecutor();
sweeper.scheduleAtFixedRate(() -> {
    long cutoff = clock.millis() - TimeUnit.MINUTES.toMillis(30);
    buckets.entrySet().removeIf(e -> e.getValue().lastRefillTime < cutoff);
}, 1, 1, TimeUnit.MINUTES);
```

**What happens to evicted clients?** Next request looks like first-time → fresh full bucket. Fine — they were inactive by definition.

**Alternative:** bounded LRU cache. When at capacity, evict least-recently-used entry.

---

### E5. "Different limits for premium vs free users (per-client tier overrides)"

**The fix:** two-tier lookup in `RateLimiter`.

```java
public class RateLimiter {
    private final Map<String, Limiter> limiters = new HashMap<>();          // endpoint → limiter
    private final Map<String, Limiter> overrides = new HashMap<>();         // clientId:endpoint → limiter
    private final Limiter defaultLimiter;

    public void registerOverride(String clientId, String endpoint, Limiter limiter) {
        overrides.put(clientId + ":" + endpoint, limiter);
    }

    public RateLimitResult allow(String clientId, String endpoint) {
        Limiter limiter = overrides.getOrDefault(clientId + ":" + endpoint,
                          limiters.getOrDefault(endpoint, defaultLimiter));
        return limiter.allow(clientId);
    }
}
```

**What to say:** *"Premium users get a per-client override registered at login. Free users hit the endpoint-level limiter. Unknown endpoint falls back to default. Three tiers, two map lookups."*

---

### E6. "Add metrics — count allow/deny per endpoint"

**The pattern:** Decorator. Wrap each registered Limiter without touching its code.

```java
public class MeteredLimiter implements Limiter {
    private final Limiter delegate;
    private final AtomicLong allowed = new AtomicLong();
    private final AtomicLong denied  = new AtomicLong();

    public MeteredLimiter(Limiter delegate) { this.delegate = delegate; }

    @Override
    public RateLimitResult allow(String clientId) {
        RateLimitResult r = delegate.allow(clientId);
        if (r.isAllowed()) allowed.incrementAndGet();
        else               denied.incrementAndGet();
        return r;
    }

    public long getAllowed() { return allowed.get(); }
    public long getDenied()  { return denied.get(); }
}
```

Usage: `rl.register("/search", new MeteredLimiter(new TokenBucketLimiter(100, 10, clock)))`.

Stackable — wrap with logging, tracing, or circuit-breaking decorators independently.

---

### E7. "Expensive endpoints should cost more tokens"

**The fix:** extend `allow()` with a cost parameter.

```java
// On Limiter interface
RateLimitResult allow(String clientId, int cost);

// In TokenBucketLimiter
if (bucket.tokens >= cost) {
    bucket.tokens -= cost;
    return RateLimitResult.allow((int) Math.floor(bucket.tokens));
}
long retryAfterMs = (long) Math.ceil((cost - bucket.tokens) * 1000.0 / refillRatePerSecond);
return RateLimitResult.deny(retryAfterMs);
```

`/search` costs 1 token; `/ml-inference` costs 10. Same bucket, different consumption.

---

### E8. "What if the rate limiter itself crashes / throws?"

**Fail-open vs fail-closed — this is a values question, not a code question.**

- **Fail-open** (return ALLOW on exception): never block legitimate traffic due to limiter bugs. Risk: limits don't hold during failures.
- **Fail-closed** (return DENY on exception): strict protection. Risk: takes down traffic when rate limiter is unhealthy.

Most API gateways choose **fail-open** — a rate limiter outage shouldn't cause a full service outage.

```java
public RateLimitResult allow(String clientId, String endpoint) {
    try {
        Limiter limiter = limiters.getOrDefault(endpoint, defaultLimiter);
        return limiter.allow(clientId);
    } catch (Exception e) {
        log.error("Rate limiter error", e);
        return RateLimitResult.allow(0);  // fail-open
    }
}
```

---

### E9. Thread Safety — full detail (if interviewer digs in)

```java
// Correct — per-key lock on the Bucket object
Bucket bucket = buckets.computeIfAbsent(clientId, k -> new Bucket(...));
synchronized (bucket) { /* refill, check, consume */ }

// Wrong #1 — global lock
synchronized (this) { /* ALL clients block each other */ }

// Wrong #2 — lock on the String key
synchronized (clientId) { /* new String("alice") != "alice" — different monitors */ }
```

**Why `computeIfAbsent` before the synchronized block?**
`ConcurrentHashMap.computeIfAbsent` is atomic — two threads racing on a brand-new key will only create one Bucket. Once the Bucket exists, `synchronized(bucket)` serializes the refill+consume for that specific client. Different clients have different Bucket instances → different monitors → run in parallel.

---

### Which algorithm to pick? (they WILL ask this)

| Scenario | Pick |
|----------|------|
| General API throttling | **Token Bucket** — O(1) memory, handles bursts gracefully, simple math |
| Strict "no more than N in T ms" accuracy | **Sliding Window Log** — exact, no boundary burst, costs O(maxRequests) memory |
| Simple, lowest memory cost | **Fixed Window Counter** — O(1), but has boundary burst problem |
| Smooth traffic (no bursts at all) | **Leaky Bucket** — constant output rate, excess queued or dropped |

**One-liner:** *"Token Bucket for most cases — it allows legitimate burst traffic (e.g. a user making 5 rapid requests is fine) while protecting against sustained overload. Sliding Window Log when you need zero tolerance for boundary exploits."*

---

## Design patterns in play (name these out loud in the interview)

### In the BASE design — mention in Step 2 or Step 3

| Pattern / Principle | Where it lives | One-line justification |
|---------------------|----------------|------------------------|
| **Strategy** ⭐ | `Limiter` interface + 2 impls (`TokenBucketLimiter`, `SlidingWindowLogLimiter`) | *"2 algorithms on day 1 with different math and different memory profiles — that's the one-sentence test."* |
| **Factory** | `LimiterFactory` reads config, constructs the right Limiter | *"Config arrives as raw JSON with an algorithm discriminator; the factory maps it to the right constructor. Callers never `new TokenBucketLimiter(...)` directly."* |
| **Facade** | `RateLimiter` | *"Application code only calls `allow(clientId, endpoint)` — 4 collaborators (factory, limiters map, default limiter, per-key state) hidden inside."* |
| **Dependency Injection** | `Clock` injected into every Limiter | *"Refill math is time-dependent — DI makes it testable with a `MutableClock` instead of `Thread.sleep`."* |
| **Value Object** | `RateLimitResult` (allowed, remaining, retryAfterMs) | *"Immutable structured return type. Beats juggling loose primitives."* |
| **Per-key locking** (concurrency) | `ConcurrentHashMap.computeIfAbsent` + `synchronized(bucket)` | *"Different clients never block each other. Only same-client requests serialize."* |

### Patterns for Step 5 extensibility

| Follow-up trigger | Pattern | The one-line move |
|-------------------|---------|-------------------|
| "Add a new algorithm (Fixed Window Counter)" | **Strategy (extend)** ⭐ | *"New `FixedWindowLimiter implements Limiter`. One switch case in the factory. Zero changes to RateLimiter or existing algorithms."* |
| "Config from YAML at startup" | **Factory (already there — just extend)** | *"That's what LimiterFactory does today; a new algorithm = 1 new class + 1 new switch case."* |
| "Distributed (Redis-backed)" | **Strategy** | *"`RedisTokenBucketLimiter implements Limiter`. Lua script makes read-refill-write atomic on Redis — no distributed lock needed."* |
| "Memory grows with millions of clients" | **Background sweeper** (concurrency, not GoF) | *"`ScheduledExecutorService` evicts buckets where `lastRefillTime < now - TTL`. Active clients never get evicted."* |
| "Add metrics per endpoint" | **Decorator** | *"`MeteredLimiter(Limiter delegate)` wraps each registered limiter, counts allow/deny. Stackable — MetricsDecorator + LoggingDecorator + …"* |
| "Per-user tier overrides (premium vs free)" | **Composition** (two-tier map lookup) | *"Second `overrides` map keyed by `clientId:endpoint`. Two-tier lookup: overrides → endpoint → default."* |
| "Different cost per endpoint" | **Interface extension** | *"Extend `allow(clientId, int cost)`. `/search` costs 1 token, `/ml-inference` costs 10. Same bucket, different consumption."* |
| "Fail-open vs fail-closed" | Design values choice | *"Catch exception in `RateLimiter.allow`; return `allow(0)` (fail-open) for API gateways so limiter bugs don't take down traffic."* |

### Patterns to actively refuse

- **Singleton on RateLimiter** — kills tests; DI a single instance.
- **State pattern on Bucket** — a Bucket has no per-state behavior; it's just mutable data.
- **Observer for metrics** — Decorator is cleaner; Observer would need every Limiter to know about listeners.
- **Builder for the 2-arg `TokenBucketLimiter(capacity, refillRate)` ctor** — academic noise.

### The rule to sound natural

1. **Strategy + Factory are non-negotiable in the base** — 2 algorithms on day 1 AND config-driven construction mandate both.
2. **Cap total patterns at 3** in the base (Strategy + Factory + Facade). Any more is over-engineering for a 45-min round.
3. **Pair each pattern with a concrete win.** *"Factory — because raw JSON needs to be mapped to a class at runtime"* > *"I'd use Factory."*

---

## Common Mistakes That Lose Points

- **`int` tokens** — 100ms at 1/s = 0.1 tokens. Int rounds to 0 → bucket never refills.
- **No cap at capacity** — idle client banks tokens forever, burst limit destroyed.
- **`floor` on retryAfterMs** — client retries too soon, gets denied again. Always `ceil`.
- **Background refill thread** — wastes work on idle clients. Lazy refill is correct.
- **Global lock on the Limiter** — all clients serialize. Per-key lock is the right granularity.
- **Locking on the String key** — unreliable. Lock on the Bucket object.
- **Adding `Client`, `Request`, `Endpoint` classes** — they're just strings, no managed state.
- **Rejecting unknown endpoints** — should fall back to default, not throw.

---

## 30-Second Summary

> *"Five classes: RateLimiter (facade), LimiterFactory (creates the right algorithm from raw config), Limiter (Strategy interface), TokenBucketLimiter, SlidingWindowLogLimiter. RateLimiter takes a list of JSON-like configs at startup; the factory reads the algorithm discriminator and builds the right limiter. allow() looks up the right limiter by endpoint and delegates. TokenBucket: lazy refill, tokens as double (partial refill is real), cap at capacity, ceil on retryAfterMs, retryAfterMs is null when allowed. SlidingWindowLog: ArrayDeque per client, evicts stale timestamps on each call — perfectly accurate but O(maxRequests) memory vs O(1). Thread safety: ConcurrentHashMap for per-client map, synchronized on the Bucket for check-and-consume — different clients never block each other. New algorithm = new Limiter class + one switch case. Everything else stays the same."*

---

## Files in This Package

| File | Purpose |
|------|---------|
| `model/RateLimitResult.java` | Value object — allowed, remaining, retryAfterMs (null when allowed) |
| `algorithm/Limiter.java` | Strategy interface |
| `algorithm/LimiterFactory.java` | Factory — reads algorithm discriminator, constructs the right Limiter |
| `algorithm/TokenBucketLimiter.java` | Lazy refill, double tokens, per-key lock |
| `algorithm/SlidingWindowLogLimiter.java` | ArrayDeque per client, lazy eviction |
| `RateLimiter.java` | Facade — config-driven constructor + allow() |
| `RateLimiterDriver.java` | 5 scenarios: config-driven, token bucket, sliding window, multi-endpoint, 50-thread burst |

```bash
mvn -q compile exec:java \
  -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.ratelimiter.RateLimiterDriver
```
