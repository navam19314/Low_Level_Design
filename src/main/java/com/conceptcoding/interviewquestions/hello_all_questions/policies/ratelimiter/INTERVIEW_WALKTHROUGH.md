# Rate Limiter

> 30 min round. Amazon ★, Ethos list. **Plan:** requirements 5 min → code 10–15 min → follow-ups 10 min.

---

## 0. The idea in plain words

**Every client gets a jar of tokens** (say 5).
- Each request **takes 1 token**. No token left → **rejected** (HTTP 429).
- Tokens **drip back** (say 1 per second), but the jar never holds more than 5.

So a client can do a quick **burst** of 5, then only **1 per second**. Each client has their own jar.

**Picture this before coding** (jar of 5, refill 1/second):
```
7 requests at once      → ✓ ✓ ✓ ✓ ✓ ✗ ✗
bob's first request     → ✓ (his own jar)
0.5 s later             → ✗ (only half a token)
1 s later               → ✓ then ✗
60 s idle, 10 requests  → 5 pass (the jar caps at 5)
```

**Only 2 things to remember:**
1. **Lazy refill.** No timer thread. When a client calls, add `secondsPassed × refillRate` tokens, capped at the jar size.
2. **One lock.** "Is there a token?" and "take it" happen together, so two requests can't grab the last token.

---

## 1. Requirements (~5 min)

**Ask:** limit per user, API key or IP? How many, and over what time? Allow short bursts? One server or many?

```
1. allow(clientId) → true (serve) / false (429)
2. Each client: a burst of up to N, refilling at R per second
3. Thread-safe
4. Easy to swap the algorithm
```

---

## 2. Design (~3 min)

```
RateLimiter (interface)          allow(clientId) → boolean
   └── TokenBucketLimiter        capacity, refillPerSecond, clock, buckets: clientId → Bucket
          Bucket                 tokens (double), lastRefillMs
```
- **Strategy:** `RateLimiter` is the interface, and each algorithm is a class. Say: *"Callers only ask 'allow?'. I can swap token bucket for a sliding window without touching them."*
- **`Clock` is injected,** so tests can move time instead of sleeping.

---

## 3. Code (~10–15 min)

```java
public interface RateLimiter {
    boolean allow(String clientId);
}

public class TokenBucketLimiter implements RateLimiter {
    private final int capacity;
    private final double refillPerSecond;
    private final Clock clock;
    private final Map<String, Bucket> buckets = new HashMap<>();

    public TokenBucketLimiter(int capacity, double refillPerSecond, Clock clock) {
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
        this.clock = clock;
    }

    @Override
    public synchronized boolean allow(String clientId) {
        long now = clock.millis();
        Bucket bucket = buckets.get(clientId);
        if (bucket == null) {                                  // new client: full jar
            bucket = new Bucket(capacity, now);
            buckets.put(clientId, bucket);
        }

        double secondsPassed = (now - bucket.lastRefillMs) / 1000.0;          // 1. refill
        bucket.tokens = Math.min(capacity, bucket.tokens + secondsPassed * refillPerSecond);
        bucket.lastRefillMs = now;

        if (bucket.tokens >= 1) {                              // 2. take a token, if there is one
            bucket.tokens -= 1;
            return true;
        }
        return false;
    }

    private static class Bucket {
        double tokens;
        long lastRefillMs;
        Bucket(double tokens, long lastRefillMs) { this.tokens = tokens; this.lastRefillMs = lastRefillMs; }
    }
}
```
**Explain while writing:**
- *"`double` tokens: after half a second there's half a token. An `int` would lose it."*
- *"`Math.min` caps the jar, so a client idle for an hour still gets only 5, not 3600."*
- *"`synchronized`: two requests with 1 token left. The first takes it, and the second sees 0."*

---

## 4. Follow-ups (~10 min)

### 1. "One lock for all clients is slow"
Clients never share tokens, so **lock only that client's bucket**:
```java
private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

public boolean allow(String clientId) {                       // no synchronized on the method
    Bucket bucket = buckets.computeIfAbsent(clientId, k -> new Bucket(capacity, clock.millis()));
    synchronized (bucket) {
        // the same refill + take as before
    }
}
```
**Say:** *"`computeIfAbsent` creates the bucket atomically, so two threads never make two. After that, Alice and Bob never wait for each other."*

### 2. "The limit must be exact" (a second Strategy)
Keep each client's request **times** from the last window; drop the old ones; allow if fewer than N remain.
```java
public class SlidingWindowLimiter implements RateLimiter {
    private final int maxRequests;
    private final long windowMs;
    private final Clock clock;
    private final Map<String, Deque<Long>> logs = new HashMap<>();

    public synchronized boolean allow(String clientId) {
        long now = clock.millis();
        Deque<Long> log = logs.get(clientId);
        if (log == null) {
            log = new ArrayDeque<>();
            logs.put(clientId, log);
        }
        while (!log.isEmpty() && log.peekFirst() <= now - windowMs) log.pollFirst();   // drop old times
        if (log.size() < maxRequests) {
            log.addLast(now);
            return true;
        }
        return false;
    }
}
```
**Say:** *"It's exact, but it stores up to N timestamps per client. Token bucket uses 2 numbers, so it's my default."*

### 3. "10 servers behind a load balancer"
**Problem:** each server has its own jars, so a client gets 10× the limit.
**Fix:** keep the jar in **Redis**, shared by all servers. A small **Lua script** does the same 2 steps (refill, then take) **atomically**, because Redis runs one script at a time.
```
script(clientKey):
    read tokens, lastRefill from Redis
    tokens = min(capacity, tokens + elapsed × rate)     -- use Redis's clock, servers' clocks differ
    allowed = tokens >= 1;  if allowed: tokens -= 1
    save tokens, now;  set a TTL so idle clients disappear
    return allowed
```
**Say:** *"It's the same algorithm, just shared. If Redis is down, I **fail open** (let traffic through and alert), because a broken limiter shouldn't take the whole API down."*

### Quick answers (just say them)
| They ask | You say |
|---|---|
| "Simplest algorithm?" | Fixed window: one counter per minute. Simple, but it allows 2× the limit at a window edge. |
| "Which would you pick?" | Token bucket: it allows bursts and needs 2 numbers per client. Sliding window when the limit must be exact. |
| "Premium users get more" | A different limiter per plan: `Map<plan, RateLimiter>`. |
| "Limits come from config" | A Factory turns `"TOKEN_BUCKET"` into `new TokenBucketLimiter(...)`. |
| "Expensive endpoints" | `allow(clientId, cost)`: take `cost` tokens instead of 1. |
| "Tell them when to retry" | Wait = (1 − tokens) / refillRate → the `Retry-After` header. |
| "Millions of clients, memory?" | Delete buckets that are full again: they're the same as a new client. In Redis, a TTL does this. |

---

```bash
mvn -q compile exec:java -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.policies.ratelimiter.RateLimiterDriver
```
