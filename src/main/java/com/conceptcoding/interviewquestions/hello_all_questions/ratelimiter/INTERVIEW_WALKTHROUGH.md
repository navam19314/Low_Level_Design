# Rate Limiter

> **Amazon:** ★ the most reported LLD (3 times in 2026, across LLD, Hiring Manager and Bar Raiser rounds). The follow-up is almost always *"make it distributed"*.
>
> **The crux (what's really being tested):**
> 1. The **algorithm math**: time-based refill done lazily, with no background thread.
> 2. **Per-client locking**: one client's traffic never blocks another's.
>
> **Family:** F5 Swappable policy (see [foundations](../00_AMAZON_LLD_FOUNDATIONS.md)) + per-key state (concurrency tool A).
> **Pattern:** Strategy. That's the only one the base design needs.

---

## 1. Plain-language picture

A rate limiter answers one question for every incoming request: **"Is this client allowed to make a request right now?"** It exists to protect the server from overload and stop one client hogging capacity.

### Token Bucket: a jar of tokens

```
Every client has a jar.         Jar holds at most 5 tokens  (capacity  = max burst)
Each request costs 1 token.     A tap drips 1 token/second  (refill rate = steady speed)
Jar empty → "wait, try again in X ms"
```

The key insight: **nobody needs to stand at the tap.** When a client comes back after 3 seconds, you work out that 3 tokens *would have* dripped in, add them (never above 5), then decide. The work happens only when a request arrives (**lazy refill**). With a million idle clients, you do zero work for them.

Why it's the default choice: it **allows short bursts** (a user clicking fast 5 times is fine) but **caps the long-run rate**.

### Sliding Window Log: a guestbook

```
Rule: max 3 requests in any 1-second window.
Keep the timestamp of every allowed request.
New request → first cross out entries older than 1 second,
              then: fewer than 3 left? allow and write it down. Otherwise deny.
```

It's exact (no loophole at window edges) but costs memory: it stores up to N timestamps per client, where a bucket stores 2 numbers.

### Where it sits
```
request → [ RateLimiter.allow(client, endpoint) ] → ALLOW → handle request
                                                  → DENY  → HTTP 429 + "Retry-After: X ms"
```

---

## 2. From story to design

Each decision: **choice → alternative rejected → why.** This reasoning is what you reuse on new problems.

| # | Decision | Rejected | Why |
|---|---|---|---|
| D1 | Core API: `allow(clientId, endpoint) → RateLimitResult` | returning `boolean` | The caller needs `retryAfterMs` for the HTTP `Retry-After` header and `remaining` for `X-RateLimit-Remaining`. |
| D2 | Algorithm behind a `Limiter` interface (**Strategy**) | one class with `if (algo == "TOKEN")` | 2 algorithms on day 1, and more get asked as follow-ups. A new algorithm is a new class. |
| D3 | Each limiter owns its own `Map<clientId, state>` | a `Client` class with fields for every algorithm | The state shape differs: a bucket is `(tokens, lastRefill)`, a log is a `Deque<Long>`. Keep it with the algorithm that understands it. |
| D4 | **Lazy refill** on each request | a background thread topping up every bucket | No wasted work on idle clients, and no timer thread to manage. |
| D5 | `tokens` is a `double` | `int` | 100 ms at 1 token/s = 0.1 tokens. An `int` rounds this to 0, so the bucket never refills under steady traffic. |
| D6 | Cap tokens at `capacity` | no cap | A client idle for 10 min would bank 600 tokens, and the burst limit would mean nothing. |
| D7 | Lock **per client**: `synchronized(bucket)` | `synchronized` method (global lock) / `synchronized(clientId)` | A global lock makes every client wait on every other. Locking a `String` is unreliable: two equal strings can be different objects. |
| D8 | `ConcurrentHashMap<endpoint, Limiter>` + a default limiter | throwing for an unknown endpoint | An unconfigured endpoint should still be protected, not crash. The map is read by many threads, so it's concurrent. |
| D9 | Inject `java.time.Clock` | calling `System.currentTimeMillis()` directly | Tests can move time forward instantly instead of `Thread.sleep`. |
| D10 | **Not** classes: `Client`, `Request`, `Endpoint` | modelling them | They have no behaviour or state of their own. They're just strings. |

### Class shape
```
RateLimiter                          ← the service callers use
  Map<String endpoint, Limiter>
  Limiter defaultLimiter
  allow(clientId, endpoint) → picks limiter → limiter.allow(clientId)

«interface» Limiter                  ← Strategy
  RateLimitResult allow(String clientId)
     ├── TokenBucketLimiter        Map<clientId, Bucket{double tokens; long lastRefillTime}>
     └── SlidingWindowLogLimiter   Map<clientId, Deque<Long> timestamps>

RateLimitResult { boolean allowed; int remaining; Long retryAfterMs /* null if allowed */ }
```

---

## 3. Patterns that earn their place

| Pattern | In the base? | Problem it solves here | Without it |
|---|---|---|---|
| **Strategy** (`Limiter`) | **Yes** | Token Bucket and Sliding Window are different algorithms answering the same `allow()` question | a growing `if/else` in `RateLimiter`; every new algorithm edits the class that runs on every request |
| **Factory** | **No.** Follow-up Q5, only if limits come from a config file | turns `{"algorithm": "TOKEN_BUCKET", ...}` into the right `Limiter` | nothing goes wrong when limits are hard-coded: `new TokenBucketLimiter(10, 1)` is fine |
| **Decorator** | **No.** Follow-up Q10 | adds metrics/logging around any limiter without editing it | counters copied into every limiter class |

**Strategy vs Factory isn't a choice; they do different jobs.** Strategy is *how* a request is limited (runs on every request). Factory is *which* limiter object gets created (runs once at startup). Factory creates a Strategy, so you only need it when an algorithm *name* arrives as text.

**Say:** *"Two algorithms with the same `allow()` contract, so Strategy. A new algorithm is a new class; `RateLimiter` never changes."*

`RateLimiter` is also a Facade (the one service class callers use). No need to name it.

**Tempting but wrong here:**
- **State:** a bucket doesn't behave differently in different "states"; it's just numbers.
- **Singleton:** one instance is fine, but create it once and inject it. `getInstance()` makes tests share state.
- **Observer:** nothing needs to react to "a request was allowed".

---

## 4. The 35-minute Amazon run

### Clarify (min 0–4)
> **You:** Is this in-process, inside one service, or a distributed limiter shared by many servers?
> **Interviewer:** Start in-process. We may talk distributed later.
> **You:** Which algorithms? I'd do Token Bucket and Sliding Window Log.
> **Interviewer:** Good.
> **You:** Limits per endpoint, per client? And what if an endpoint has no config?
> **Interviewer:** Per endpoint, per client. Unknown endpoint gets a default.
> **You:** Should a denial tell the client when to retry?
> **Interviewer:** Yes.

```
In scope:  allow(clientId, endpoint) → {allowed, remaining, retryAfterMs}
           per-endpoint limiter + default · per-client isolation · thread-safe
           Token Bucket + Sliding Window Log
Out:       distributed, config files, metrics, persistence
```

### Timeline
| Min | Do |
|---|---|
| 4–8 | Write the class shape above. Say the crux: *"Refill math done lazily, and per-client locking."* Say *"Strategy."* |
| 8–10 | Code step 1: `Limiter` + `RateLimitResult`. |
| 10–22 | Code step 2: **`TokenBucketLimiter` in full.** This is the class they judge you on. |
| 22–25 | Code step 3: `RateLimiter` (~15 lines). |
| 25–29 | Code step 4: `SlidingWindowLogLimiter`. Skip it if you're behind; describe it in words instead. |
| 29–32 | Dry-run the token bucket out loud. Point at the lock. |
| 32–35 | Follow-ups. |

**Short on time?** Drop the `Clock` and call `System.currentTimeMillis()`, then say *"I'd inject a Clock so tests can control time."* You keep the signal and save 2 minutes.

### The code you write, in this order

**Step 1: the Strategy interface + the result** ([algorithm/Limiter.java](algorithm/Limiter.java), [model/RateLimitResult.java](model/RateLimitResult.java))
```java
public interface Limiter {
    RateLimitResult allow(String clientId);
}

public class RateLimitResult {
    private final boolean allowed;
    private final int remaining;
    private final Long retryAfterMs;                 // null when allowed

    private RateLimitResult(boolean allowed, int remaining, Long retryAfterMs) {
        this.allowed = allowed;
        this.remaining = remaining;
        this.retryAfterMs = retryAfterMs;
    }

    public static RateLimitResult allow(int remaining)    { return new RateLimitResult(true, remaining, null); }
    public static RateLimitResult deny(long retryAfterMs) { return new RateLimitResult(false, 0, retryAfterMs); }

    public boolean isAllowed() { return allowed; }
}
```

**Step 2: Token Bucket, the crux** ([algorithm/TokenBucketLimiter.java](algorithm/TokenBucketLimiter.java))
```java
public class TokenBucketLimiter implements Limiter {

    private final int capacity;
    private final int refillRatePerSecond;
    private final Clock clock;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public TokenBucketLimiter(int capacity, int refillRatePerSecond, Clock clock) {
        this.capacity = capacity;
        this.refillRatePerSecond = refillRatePerSecond;
        this.clock = clock;
    }

    @Override
    public RateLimitResult allow(String clientId) {
        // atomic: two threads can't both create a bucket for a new client
        Bucket bucket = buckets.computeIfAbsent(clientId, k -> new Bucket(capacity, clock.millis()));

        synchronized (bucket) {                              // per-client lock
            long now = clock.millis();
            long elapsedMs = now - bucket.lastRefillTime;

            // lazy refill: add what would have dripped in since last time, capped
            double tokensToAdd = (elapsedMs * refillRatePerSecond) / 1000.0;
            bucket.tokens = Math.min(capacity, bucket.tokens + tokensToAdd);
            bucket.lastRefillTime = now;

            if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0;
                return RateLimitResult.allow((int) Math.floor(bucket.tokens));
            }
            // time until 1 full token exists; ceil so the client never retries too early
            long retryAfterMs = (long) Math.ceil((1.0 - bucket.tokens) * 1000.0 / refillRatePerSecond);
            return RateLimitResult.deny(retryAfterMs);
        }
    }

    static class Bucket {                                    // mutable per-client state
        double tokens;
        long lastRefillTime;

        Bucket(double tokens, long lastRefillTime) {
            this.tokens = tokens;
            this.lastRefillTime = lastRefillTime;
        }
    }
}
```
**Shape to remember: get-or-create → lock → refill → cap → consume or compute retry.**

**Step 3: the service** ([RateLimiter.java](RateLimiter.java))
```java
public class RateLimiter {

    private final Map<String, Limiter> limiters = new ConcurrentHashMap<>();
    private final Limiter defaultLimiter;

    public RateLimiter(Limiter defaultLimiter) {
        this.defaultLimiter = defaultLimiter;
    }

    public void register(String endpoint, Limiter limiter) {
        limiters.put(endpoint, limiter);
    }

    public RateLimitResult allow(String clientId, String endpoint) {
        Limiter limiter = limiters.getOrDefault(endpoint, defaultLimiter);
        return limiter.allow(clientId);                      // Strategy: we don't know or care which algorithm
    }
}
```

**Step 4: second algorithm, if time allows** ([algorithm/SlidingWindowLogLimiter.java](algorithm/SlidingWindowLogLimiter.java))
```java
public class SlidingWindowLogLimiter implements Limiter {

    private final int maxRequests;
    private final long windowMs;
    private final Clock clock;
    private final ConcurrentHashMap<String, Deque<Long>> logs = new ConcurrentHashMap<>();

    public SlidingWindowLogLimiter(int maxRequests, long windowMs, Clock clock) {
        this.maxRequests = maxRequests;
        this.windowMs = windowMs;
        this.clock = clock;
    }

    @Override
    public RateLimitResult allow(String clientId) {
        Deque<Long> log = logs.computeIfAbsent(clientId, k -> new ArrayDeque<>());

        synchronized (log) {
            long now = clock.millis();
            long cutoff = now - windowMs;
            while (!log.isEmpty() && log.peekFirst() <= cutoff) {   // evict timestamps outside the window
                log.pollFirst();
            }
            if (log.size() < maxRequests) {
                log.addLast(now);
                return RateLimitResult.allow(maxRequests - log.size());
            }
            return RateLimitResult.deny(log.peekFirst() + windowMs - now);   // when the oldest ages out
        }
    }
}
```
**`<=` not `<`:** a request made exactly `windowMs` ago is outside the window. With `<`, the `retryAfterMs` you return would be 1 ms too early, and the client retrying on time would be denied again.

**Demo, only if they ask to see it run:**
```java
RateLimiter rl = new RateLimiter(new TokenBucketLimiter(10, 1, Clock.systemUTC()));
rl.register("/upload", new SlidingWindowLogLimiter(2, 60_000, Clock.systemUTC()));
for (int i = 0; i < 3; i++) System.out.println(rl.allow("alice", "/upload"));
// ALLOW(remaining=1)  ALLOW(remaining=0)  DENY(retryAfterMs≈60000)
```

### Dry run (capacity 5, refill 1/s, time frozen at 0)
```
req 1      new Bucket(5, t=0) → 5 ≥ 1 → tokens 4 → ALLOW(remaining 4)
req 2..5   no time passed → 3, 2, 1, 0         → ALLOW(3), (2), (1), (0)
req 6      tokens 0 → retry = ceil(1.0 × 1000 / 1) → DENY(retryAfterMs 1000)
t=1500     +1.5 tokens → 1.5 → consume → 0.5    → ALLOW(remaining 0)   floor(0.5)=0
```

### "Which algorithm would you pick?" They will ask.
| Algorithm | Idea | Memory per client | Weakness |
|---|---|---|---|
| **Fixed Window Counter** | count per clock-minute, reset at :00 | O(1) | **edge burst:** 100 at 0:59 + 100 at 1:00 = 200 in 2 seconds |
| **Sliding Window Log** | keep every timestamp | O(N) | memory heavy at high limits |
| **Sliding Window Counter** | `prevCount × (fraction of previous window still inside) + currCount` | O(1) | approximate (assumes even spread). The usual production pick. |
| **Token Bucket** | jar + tap | O(1) | allows bursts up to `capacity` (usually a feature) |
| **Leaky Bucket** | queue drained at a fixed rate | O(queue) | no bursts at all, adds latency |

> *"Token Bucket by default: O(1), burst-friendly, simple. Sliding Window Log when the limit must be exact. Sliding Window Counter when memory matters at scale."*

---

## 5. Follow-ups: answer out loud first, then open

Most likely first: Q1, Q2, Q3/Q4. Notice how most answers are **"a new class implementing `Limiter`"**. That's the Strategy payoff, so say it.

<details>
<summary><b>Q1. Two requests from the same client arrive at the same instant. What happens?</b></summary>

Both call `computeIfAbsent` and get the **same** `Bucket` (it's atomic, so only one bucket is created). Both reach `synchronized(bucket)`; one waits. The first sees 1 token and consumes it; the second sees 0 and is denied.

Without the lock:
```
Thread A: reads tokens = 1          Thread B: reads tokens = 1
Thread A: 1 ≥ 1 → tokens = 0        Thread B: 1 ≥ 1 → tokens = 0
→ both ALLOWED with 1 token: the classic check-then-act race
```
Different clients have different bucket objects, so they never wait on each other. Proof: the driver's 50-thread test with capacity 10 gives exactly 10 allowed.
</details>

<details>
<summary><b>Q2. Make it distributed: 10 API servers behind a load balancer.</b></summary>

**Problem:** each server has its own in-memory buckets, so a client gets 10× the limit.
**Fix:** move the bucket to **Redis**, with the refill-check-consume as a **Lua script**. Redis runs a script without interleaving other commands, so the script is the lock. It's just another Strategy, and `RateLimiter` doesn't change.

```java
// sketch: needs a Redis client such as Jedis
public class RedisTokenBucketLimiter implements Limiter {

    private static final String SCRIPT = """
        local capacity = tonumber(ARGV[1])
        local rate     = tonumber(ARGV[2])
        local t   = redis.call('TIME')                              -- Redis's clock, not the app server's
        local now = t[1] * 1000 + math.floor(t[2] / 1000)
        local b      = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
        local tokens = tonumber(b[1]) or capacity                   -- new client: full bucket
        local ts     = tonumber(b[2]) or now
        tokens = math.min(capacity, tokens + (now - ts) * rate / 1000)
        local allowed, retry = 0, math.ceil((1 - tokens) * 1000 / rate)
        if tokens >= 1 then tokens = tokens - 1; allowed = 1; retry = 0 end
        redis.call('HSET', KEYS[1], 'tokens', tokens, 'ts', now)
        redis.call('PEXPIRE', KEYS[1], math.ceil(capacity * 1000 / rate) * 2)   -- idle keys disappear
        return {allowed, math.floor(tokens), retry}
        """;

    private final Jedis redis;
    private final String endpoint;                        // part of the key
    private final int capacity;
    private final int refillRatePerSecond;

    public RedisTokenBucketLimiter(Jedis redis, String endpoint, int capacity, int refillRatePerSecond) {
        this.redis = redis;
        this.endpoint = endpoint;
        this.capacity = capacity;
        this.refillRatePerSecond = refillRatePerSecond;
    }

    @Override
    public RateLimitResult allow(String clientId) {
        @SuppressWarnings("unchecked")
        List<Long> r = (List<Long>) redis.eval(SCRIPT,
                List.of("rl:" + endpoint + ":" + clientId),
                List.of(String.valueOf(capacity), String.valueOf(refillRatePerSecond)));
        return r.get(0) == 1L ? RateLimitResult.allow(r.get(1).intValue())
                              : RateLimitResult.deny(r.get(2));
    }
}
```
**Points that show depth:**
- **Same algorithm**, now in Lua. The script being atomic replaces `synchronized(bucket)`.
- **Clock skew:** app servers' clocks differ, so use Redis `TIME` inside the script.
- **TTL** on every key, so idle clients don't fill Redis (that's Q6, solved for free).
- **Cost:** +1 network hop per request (~1 ms). Redis down → fail-open (Q7).
- **Cheaper alternative:** each server keeps a local limiter at `limit / N`. No network hop, but inaccurate when the load balancer spreads traffic unevenly.
</details>

<details>
<summary><b>Q3. Add a Fixed Window Counter algorithm.</b></summary>

A new class, registered for an endpoint. **Zero changes** to `RateLimiter` or the existing limiters (Open/Closed). It uses the same `computeIfAbsent` + `synchronized` pattern.

```java
public class FixedWindowLimiter implements Limiter {

    private final int maxRequests;
    private final long windowMs;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public FixedWindowLimiter(int maxRequests, long windowMs, Clock clock) {
        this.maxRequests = maxRequests;
        this.windowMs = windowMs;
        this.clock = clock;
    }

    @Override
    public RateLimitResult allow(String clientId) {
        Window w = windows.computeIfAbsent(clientId, k -> new Window());
        synchronized (w) {
            long now = clock.millis();
            long windowStart = now - (now % windowMs);      // e.g. start of the current minute
            if (windowStart != w.start) {                   // a new window began: reset
                w.start = windowStart;
                w.count = 0;
            }
            if (w.count < maxRequests) {
                w.count++;
                return RateLimitResult.allow(maxRequests - w.count);
            }
            return RateLimitResult.deny(windowStart + windowMs - now);   // until the next window
        }
    }

    static class Window { long start = -1; int count; }
}
// usage
rl.register("/login", new FixedWindowLimiter(5, 60_000, clock));
```
**Mention the weakness:** 5 requests at 0:59 plus 5 at 1:00 gives 10 in 2 seconds (edge burst).
</details>

<details>
<summary><b>Q4. "Fix the edge burst without storing every timestamp." (Sliding Window Counter)</b></summary>

Keep **two counters**, the previous and current fixed windows, and **estimate** the sliding count:
```
estimate = prevCount × (fraction of the previous window still inside the last windowMs) + currCount

limit 10/sec. Previous second had 10 requests. Now we're 0.5 s into the current second, with 2 requests.
estimate = 10 × 0.5 + 2 = 7  → 3 more allowed
```
```java
public class SlidingWindowCounterLimiter implements Limiter {

    private final int maxRequests;
    private final long windowMs;
    private final Clock clock;
    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();

    public SlidingWindowCounterLimiter(int maxRequests, long windowMs, Clock clock) {
        this.maxRequests = maxRequests;
        this.windowMs = windowMs;
        this.clock = clock;
    }

    @Override
    public RateLimitResult allow(String clientId) {
        Counter c = counters.computeIfAbsent(clientId, k -> new Counter());
        synchronized (c) {
            long now = clock.millis();
            long windowStart = now - (now % windowMs);
            if (windowStart != c.windowStart) {
                // the old window counts as "previous" only if it is the one right before this one
                c.prevCount = (windowStart - c.windowStart == windowMs) ? c.currCount : 0;
                c.currCount = 0;
                c.windowStart = windowStart;
            }
            // fraction of the previous window still inside the sliding window that ends now
            double prevWeight = (double) (windowMs - (now - windowStart)) / windowMs;
            double estimated = c.prevCount * prevWeight + c.currCount;

            if (estimated + 1 <= maxRequests) {
                c.currCount++;
                return RateLimitResult.allow((int) (maxRequests - estimated - 1));
            }
            // approximate: by the next window the estimate has dropped (say this out loud)
            return RateLimitResult.deny(windowStart + windowMs - now);
        }
    }

    static class Counter { long windowStart = -1; int prevCount; int currCount; }
}
```
**Say:** *"O(1) memory like Fixed Window, nearly as smooth as the log. It assumes the previous window's requests were spread evenly, which is why it's an estimate."*
</details>

<details>
<summary><b>Q5. "Limits come from a config file." (this is where Factory comes in)</b></summary>

Config arrives as text with an algorithm **name**. Something must map that name to a class: that's the Factory. It **creates** Strategies; `RateLimiter` and the limiters don't change.

```java
public class LimiterFactory {

    private final Clock clock;

    public LimiterFactory(Clock clock) { this.clock = clock; }

    // config example: {"algorithm": "TOKEN_BUCKET", "capacity": 10, "refillRatePerSecond": 2}
    public Limiter create(Map<String, Object> config) {
        String algorithm = (String) config.get("algorithm");
        switch (algorithm) {
            case "TOKEN_BUCKET":
                return new TokenBucketLimiter(
                        ((Number) config.get("capacity")).intValue(),
                        ((Number) config.get("refillRatePerSecond")).intValue(), clock);
            case "SLIDING_WINDOW_LOG":
                return new SlidingWindowLogLimiter(
                        ((Number) config.get("maxRequests")).intValue(),
                        ((Number) config.get("windowMs")).longValue(), clock);
            default:
                throw new IllegalArgumentException("Unknown algorithm: " + algorithm);
        }
    }
}
// at startup
LimiterFactory factory = new LimiterFactory(Clock.systemUTC());
RateLimiter rl = new RateLimiter(factory.create(defaultConfig));
for (Map<String, Object> cfg : endpointConfigs) {
    rl.register((String) cfg.get("endpoint"), factory.create(cfg));
}
```
- **Why `(Number)`:** JSON parsers may give an `Integer` or a `Long`; `Number` handles both.
- **New algorithm** = new `Limiter` class + one `case`. The `switch` lives in one place only.
</details>

<details>
<summary><b>Q6. Memory keeps growing: millions of clients.</b></summary>

The per-client maps never shrink. Add a sweeper to `TokenBucketLimiter` that removes idle buckets:
```java
private static final long IDLE_TTL_MS = 30 * 60 * 1000L;
private final ScheduledExecutorService sweeper = Executors.newSingleThreadScheduledExecutor();

// in the constructor
sweeper.scheduleAtFixedRate(this::evictIdle, 1, 1, TimeUnit.MINUTES);

private void evictIdle() {
    long cutoff = clock.millis() - IDLE_TTL_MS;
    buckets.entrySet().removeIf(e -> {
        Bucket b = e.getValue();
        synchronized (b) { return b.lastRefillTime < cutoff; }   // read under the bucket's lock
    });
}
```
- **Why eviction is invisible:** pick a TTL ≥ the time to refill a full bucket (`capacity / rate`). An evicted client would have had a full bucket anyway, and comes back to a new full one.
- **Sliding Window Log is worse:** a limit of 1000/min × 1M clients = 1 billion `Long`s, many GB. At that scale use Sliding Window Counter (2 numbers per client).
- **In Redis (Q2):** the key TTL does this for free.
</details>

<details>
<summary><b>Q7. What if the limiter itself throws, or Redis is down?</b></summary>

A product decision. Say both options and pick one:
- **Fail-open** (allow on error): a limiter bug never takes down the API. Usual choice for API gateways.
- **Fail-closed** (deny on error): for abuse-sensitive endpoints, like login or OTP.

```java
public RateLimitResult allow(String clientId, String endpoint) {
    Limiter limiter = limiters.getOrDefault(endpoint, defaultLimiter);
    try {
        return limiter.allow(clientId);
    } catch (Exception e) {                                  // Exception, not Throwable
        // fail-open: a limiter bug or Redis outage must not take the whole API down
        System.err.println("Rate limiter failed for " + endpoint + ": " + e.getMessage());
        return RateLimitResult.allow(0);
    }
}
```
In production, also increment an error metric and alarm on it. Silent fail-open means no rate limiting and nobody knows.
</details>

<details>
<summary><b>Q8. Premium users get higher limits than free users.</b></summary>

Look up `tier:endpoint` first, then fall back to the endpoint, then the default. The tier comes from the authenticated request. The algorithm classes don't change.
```java
public RateLimitResult allow(String clientId, String tier, String endpoint) {
    Limiter limiter = limiters.get(tier + ":" + endpoint);           // e.g. "PREMIUM:/search"
    if (limiter == null) limiter = limiters.getOrDefault(endpoint, defaultLimiter);
    return limiter.allow(clientId);
}
// setup
rl.register("/search", new TokenBucketLimiter(10, 1, clock));            // free
rl.register("PREMIUM:/search", new TokenBucketLimiter(100, 10, clock));  // premium
```
</details>

<details>
<summary><b>Q9. Some endpoints are expensive: <code>/ml-inference</code> should cost 10 tokens.</b></summary>

Add a `cost` parameter. A `default` method keeps every existing caller working:
```java
public interface Limiter {
    RateLimitResult allow(String clientId, int cost);
    default RateLimitResult allow(String clientId) { return allow(clientId, 1); }
}
```
In `TokenBucketLimiter`, only the "1" becomes `cost`:
```java
if (cost > capacity) throw new IllegalArgumentException("cost " + cost + " > capacity " + capacity);
// ... same refill code ...
if (bucket.tokens >= cost) {
    bucket.tokens -= cost;
    return RateLimitResult.allow((int) Math.floor(bucket.tokens));
}
long retryAfterMs = (long) Math.ceil((cost - bucket.tokens) * 1000.0 / refillRatePerSecond);
return RateLimitResult.deny(retryAfterMs);
```
**Catch the edge case:** if `cost > capacity`, the request can *never* succeed. Reject it up front instead of telling the client to retry forever.
</details>

<details>
<summary><b>Q10. Count allows and denies per endpoint without touching any limiter class.</b></summary>

**Decorator:** a `Limiter` that wraps another `Limiter`, does the same thing, and also counts.
```java
public class MeteredLimiter implements Limiter {

    private final Limiter inner;
    private final AtomicLong allowed = new AtomicLong();
    private final AtomicLong denied = new AtomicLong();

    public MeteredLimiter(Limiter inner) { this.inner = inner; }

    @Override
    public RateLimitResult allow(String clientId) {
        RateLimitResult result = inner.allow(clientId);     // same behaviour...
        if (result.isAllowed()) allowed.incrementAndGet();  // ...plus counting
        else denied.incrementAndGet();
        return result;
    }

    public long getAllowed() { return allowed.get(); }
    public long getDenied()  { return denied.get(); }
}
// usage
rl.register("/search", new MeteredLimiter(new TokenBucketLimiter(100, 10, clock)));
```
It stacks with other wrappers (logging, tracing), and works around *any* algorithm.
</details>

<details>
<summary><b>Q11. Change a limit at runtime without restarting.</b></summary>

The endpoint map is already a `ConcurrentHashMap`, so registering a new limiter is safe while traffic flows:
```java
rl.register("/search", new TokenBucketLimiter(200, 20, clock));   // atomically replaces the old one
```
**Trade-off:** that endpoint's per-client state resets (everyone gets a full new bucket). If that's not acceptable, make `capacity` and `refillRatePerSecond` `volatile` fields with setters on the existing limiter, so the state is kept.
</details>

<details>
<summary><b>Q12. How would you know it's working in production?</b></summary>

- **Metrics:** allow/deny count per endpoint (Q10), 429 rate, p99 latency of `allow()` itself, limiter errors.
- **Alarms:** 429 rate suddenly jumps (a limit set too low, or an attack), or drops to 0 (the limiter is failing open).
- **Logs:** sampled denials with clientId and endpoint, to see who's being throttled.
</details>

<details>
<summary><b>Q13. How do you test the refill math and the locking?</b></summary>

Time: a `Clock` you move by hand, so there's no `Thread.sleep` (the full `MutableClock` is in the driver).
```java
MutableClock clock = new MutableClock(0);
TokenBucketLimiter tb = new TokenBucketLimiter(5, 1, clock);
for (int i = 0; i < 5; i++) tb.allow("alice");          // empty the bucket
assert !tb.allow("alice").isAllowed();                  // 6th denied
clock.advanceMs(1000);
assert tb.allow("alice").isAllowed();                   // exactly 1 token refilled
```
Concurrency: 50 threads released at the same instant, with the clock frozen, must give **exactly** `capacity` allows.
```java
CountDownLatch start = new CountDownLatch(1);
AtomicInteger allowed = new AtomicInteger();
ExecutorService pool = Executors.newFixedThreadPool(50);
for (int i = 0; i < 50; i++) {
    pool.submit(() -> {
        start.await();                                  // everyone waits at the gate
        if (limiter.allow("shared").isAllowed()) allowed.incrementAndGet();
        return null;
    });
}
start.countDown();                                      // release all 50 at once
pool.shutdown();
pool.awaitTermination(5, TimeUnit.SECONDS);
// assert allowed.get() == 10   (capacity 10)
```
</details>

---

## 6. Traps that cost points
1. `int` tokens: fractional refills vanish, so the bucket never refills.
2. No cap at `capacity`: idle clients bank unlimited burst.
3. `floor` instead of `ceil` on retry: the client retries too early and gets denied again.
4. A background refill thread: wasted work, plus a thread you must manage.
5. A `synchronized` method or `synchronized(clientId)`: either a global bottleneck or a broken lock.
6. Adding a Factory before anyone mentioned config: time spent with no payoff.
7. Drawing Redis and load balancers before `allow()` is coded.

---

## 7. Recall check (next day, no peeking)
1. Why must `tokens` be a `double`? Give the exact numbers.
2. Write the 5 steps of `TokenBucketLimiter.allow()` in order.
3. What do you lock, why not the whole limiter, and why not the `clientId` string?
4. Strategy vs Factory here: what does each do, and when does Factory become worth adding?
5. Token Bucket vs Sliding Window Log vs Sliding Window Counter: memory, bursts, and when to pick each.
6. In the distributed version, what moves to Redis, what replaces `synchronized`, and whose clock is used?
7. Fail-open or fail-closed for a public API? For OTP sending?

**Rebuild in 10 minutes:** `Limiter` interface · `RateLimitResult(allowed, remaining, retryAfterMs)` · `TokenBucketLimiter` with `Bucket{double tokens; long lastRefillTime}` · `computeIfAbsent` + `synchronized(bucket)` · `RateLimiter` with a `ConcurrentHashMap` of endpoints + a default.

---

**Files:** `RateLimiter` (service) · `algorithm/` (`Limiter`, `TokenBucketLimiter`, `SlidingWindowLogLimiter`) · `model/RateLimitResult` · `RateLimiterDriver` (token bucket, sliding window, multi-endpoint, 50-thread test)
```bash
mvn -q compile exec:java -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.ratelimiter.RateLimiterDriver
```
