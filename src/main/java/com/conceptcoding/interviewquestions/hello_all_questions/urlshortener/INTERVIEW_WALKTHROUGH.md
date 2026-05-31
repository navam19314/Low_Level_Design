# URL Shortener (LLD) — 45-min LLD Interview Walkthrough

**Target role:** SDE‑2 (Amazon, Adobe, Microsoft, Atlassian, etc.)

> URL Shortener is **TWO problems with the same name** — the HLD version (sharded DB, distributed ID generation, cache, QPS) and the LLD version (this one — base-62 encoding, in-process bidirectional map, ID-generation Strategy). At a 45-min LLD round, you build the LLD version and call out the HLD axis as "out of scope here."
>
> Three senior signals: (a) **base-62 encoder/decoder** as a utility, (b) **`IdGenerationStrategy`** with counter vs random+retry impls, (c) **bidirectional maps + `computeIfAbsent` for idempotency** — same URL submitted twice returns the same short code.

---

## Time budget (45 min)

| Step | Activity                                                                                | Budget   | Cumulative |
| ---- | --------------------------------------------------------------------------------------- | -------- | ---------- |
| 1    | Requirements                                                                            | ~4 min   | 4          |
| 2    | Entities & Relationships                                                                | ~3 min   | 7          |
| 3    | Class Design (Strategy + encoder + bidirectional map)                                   | ~8 min   | 15         |
| 4    | Implementation (`shorten` w/ idempotency, base-62, both strategies + dry-run)           | ~18 min  | 33         |
| 5    | Extensibility (TTL, analytics, distributed, vanity codes, abuse)                        | ~10 min  | 43         |
| —    | Wrap & questions                                                                        | ~2 min   | 45         |

Step 4 is the longest — base-62 encoder + the two Strategy impls + idempotency proof all converge.

---

## Mental models — internalize these BEFORE you walk in

### M1. The shorten/expand pipeline

```
   shorten("https://example.com/very/long/url"):
        |
        v
   +--------------------------------------+
   | urlToCode.computeIfAbsent(url, ...)  |   ← per-key atomic (idempotency)
   |    nextId = idStrategy.nextId(...)   |   ← Strategy
   |    code = Base62Encoder.encode(id)   |   ← utility
   |    codeToUrl[code] = url             |
   |    return code                       |
   +--------------------------------------+
        |
        v
   "4C92"   (returned to caller; SAME input url → SAME code on every retry)


   expand("4C92"):
        |
        v
   +-------------------------+
   | codeToUrl.get("4C92")   |   O(1) lookup
   |   null? → throw         |
   +-------------------------+
        |
        v
   "https://example.com/very/long/url"


   Idempotency invariant:
       For any fixed url U, every call shorten(U) returns the SAME short code.
       Implemented via urlToCode.computeIfAbsent — atomic per-key.
```

**Senior soundbite (memorize):** *"Idempotency is the load-bearing requirement. `urlToCode.computeIfAbsent(url, ...)` means 50 concurrent callers shortening the SAME url get back IDENTICAL codes — exactly one id minted, exactly one entry stored. Same pattern as PaymentGateway's idempotency key."*

### M2. Base-62 encoding

```
   Alphabet: 0-9 A-Z a-z  →  62 characters, URL-safe (no + / =)

   Encode  (long id  →  short code):
     while id > 0:
        digit = id % 62
        prepend ALPHABET[digit] to result
        id /= 62

     0       →  "0"
     1       →  "1"
     61      →  "z"
     62      →  "10"
     1000000 →  "4C92"     ← 4 chars only — that's the win

   Decode  (short code  →  long id):
     result = 0
     for each char c in code (left to right):
        result = result * 62 + ALPHABET.indexOf(c)

   Why base-62 specifically:
     base-10  → digits only; 10 chars per byte of data (verbose)
     base-16  → 16 chars per byte (still verbose)
     base-62  → 6 chars covers 62^6 = 56 BILLION ids   ⭐
     base-64  → 6 chars covers 68 billion — but '+' and '/' aren't URL-safe
                                          → percent-encoded → URL gets longer

   ⇒ base-62 hits the URL-safe + dense sweet spot.
```

### M3. Counter vs Random — the Strategy trade-off

```
   COUNTER:                          RANDOM+RETRY:
   --------                          --------------
   atomic.incrementAndGet()          long candidate = random.nextLong(range)
   guaranteed unique                  may collide; retry on collision (bounded loop)
   predictable: code N+1 = code N+1   unpredictable: ID-space looks scrambled
   risk: enumerable URLs              risk: collision rate grows w/ load factor
   ideal: internal-only shorteners    ideal: public shorteners (security)

   Defending the Strategy at interview:
     "Either approach has correctness issues at extreme load. Counter wraps around;
     random saturates. The Strategy abstraction lets us swap when we need to —
     and for the realistic interview-scope, counter is the cleanest default."
```

---

## STEP 1 — Requirements (~4 min)

### What to say out loud (opener — DO clarify LLD vs HLD!)
> "'URL Shortener' is asked as both LLD and HLD with the same name — let me confirm scope. Are we designing the in-process class structure + encoding algorithm + bidirectional map (LLD), or the distributed system with sharded storage + QPS estimates (HLD)?"

### Probe the 4 themes

| Theme               | Question to ask                                                                                              |
| ------------------- | ------------------------------------------------------------------------------------------------------------ |
| Primary capabilities| "shorten(longUrl) → shortCode; expand(shortCode) → longUrl; basic CRUD." |
| Rules               | "Idempotent — same url submitted twice returns the SAME short code? Custom vanity aliases supported?" |
| Error handling      | "Unknown shortCode → throw or return null? Reserved/colliding aliases → reject?" |
| Concurrency         | "Multiple callers shortening same url concurrently → exactly one mint?" |

### What to write on the board

```
Functional Requirements
1. shorten(longUrl) → shortCode (base-62, URL-safe).
2. expand(shortCode) → longUrl, or throws if unknown.
3. IDEMPOTENT: same longUrl submitted N times → SAME short code (no duplicate mints).
4. Optional custom alias / vanity code (shortenWithAlias(url, "myalias")).
5. delete(shortCode) — for take-downs / expiration.
6. Thread-safe — concurrent shortenings safe.

Out of Scope
- Distributed storage / sharding / replication (HLD axis)
- Analytics: click counts, geo data, referer (Step 5)
- TTL / expiration policy (Step 5)
- Authentication / abuse prevention (Step 5)
- HTTP / web UI — pure library
- Rate limiting per user / IP
- URL validation beyond non-empty (real impl would parse + verify)
```

### Close the step
> "Two load-bearing requirements: idempotency on the same URL, and base-62 encoding for the short codes. Plus the Strategy seam for ID generation since counter and random+retry are both reasonable production choices."

---

## STEP 2 — Entities & Relationships (~3 min)

### What to say out loud
> "Five types: **UrlShortener** (facade), **Base62Encoder** (utility — encode/decode), **IdGenerationStrategy** (interface), **CounterIdStrategy** + **RandomIdStrategy** (two impls). All state lives on UrlShortener: two ConcurrentHashMaps for the bidirectional mapping."

### Why no `Url` or `ShortCode` class
> "Both are just strings. Wrapping in a class would be ceremony — no behavior to add. If we later needed `Url` validation (URI parsing, scheme normalization), then a class earns its place."

### What to write on the board

```
Entities
- UrlShortener          (orchestrator + facade: shorten / expand / delete / shortenWithAlias)
- Base62Encoder         (static utility: encode(long) + decode(String))
- IdGenerationStrategy  (interface — Strategy; one method nextId)
- CounterIdStrategy     (impl — atomic incrementing long)
- RandomIdStrategy      (impl — random long with bounded collision-retry)

NOT entities
- Url, ShortCode        (just String)
- IdSpace, Bucket       (premature for v1)

Relationships
- UrlShortener owns:
    ConcurrentHashMap<String, String> codeToUrl    (expand lookup)
    ConcurrentHashMap<String, String> urlToCode    (idempotency — same url → same code)
    IdGenerationStrategy idStrategy                 (Strategy, injected)
- Base62Encoder is a utility — no instances
```

### Diagram — boxes and arrows

```
                  +--------------------------------+
                  |          UrlShortener          |   <- facade
                  | shorten / expand / delete /    |
                  | shortenWithAlias               |
                  +--------------------------------+
                       |          |          |
                  owns | (2 maps + strategy)|
                       v          v          v
       ConcurrentHashMap<code,url>  ConcurrentHashMap<url,code>  IdGenerationStrategy
       codeToUrl (forward)           urlToCode (idempotency)      ── CounterIdStrategy
                                                                  ── RandomIdStrategy

   +-------------------+               +-----------------------+
   | Base62Encoder     |               | <<interface>>         |
   | static encode()   |               | IdGenerationStrategy  |
   | static decode()   |               | + nextId(isAvailable) |
   +-------------------+               +-----------------------+
```

---

## STEP 3 — Class Design (~8 min)

### UrlShortener — class outline (write on the board)

```java
public class UrlShortener {
    private final ConcurrentHashMap<String, String> codeToUrl = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> urlToCode = new ConcurrentHashMap<>();
    private final IdGenerationStrategy idStrategy;

    public UrlShortener()                                 { this(new CounterIdStrategy()); }
    public UrlShortener(IdGenerationStrategy idStrategy)  { this.idStrategy = idStrategy; }

    public String  shorten(String longUrl);
    public String  shortenWithAlias(String longUrl, String alias);
    public String  expand(String shortCode);
    public boolean delete(String shortCode);
    public int     size();
}
```

### Base62Encoder — utility

```java
public final class Base62Encoder {
    private static final String ALPHABET =
        "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE = 62;

    private Base62Encoder() {}    // no instances

    public static String encode(long id) {
        if (id < 0) throw new IllegalArgumentException("id must be >= 0");
        if (id == 0) return "0";
        StringBuilder sb = new StringBuilder();
        while (id > 0) {
            sb.append(ALPHABET.charAt((int) (id % BASE)));
            id /= BASE;
        }
        return sb.reverse().toString();
    }

    public static long decode(String code) {
        long result = 0;
        for (char c : code.toCharArray()) {
            int d = ALPHABET.indexOf(c);
            if (d < 0) throw new IllegalArgumentException("not base62: " + c);
            result = result * BASE + d;
        }
        return result;
    }
}
```

### IdGenerationStrategy — Strategy interface

```java
public interface IdGenerationStrategy {
    long nextId(Predicate<String> isAvailable);
}

public class CounterIdStrategy implements IdGenerationStrategy {
    private final AtomicLong counter = new AtomicLong(1_000_000);  // start at 1M
    public long nextId(Predicate<String> isAvailable) {
        while (true) {
            long c = counter.getAndIncrement();
            if (isAvailable.test(Base62Encoder.encode(c))) return c;
        }
    }
}

public class RandomIdStrategy implements IdGenerationStrategy {
    private final long range;
    private final Random random;
    public long nextId(Predicate<String> isAvailable) {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            long c = (long) (random.nextDouble() * range);
            if (isAvailable.test(Base62Encoder.encode(c))) return c;
        }
        throw new IllegalStateException("no free id after " + MAX_ATTEMPTS + " attempts");
    }
}
```

> **Senior callout — the Predicate trick:** *"`nextId` takes a `Predicate<String> isAvailable` callback so the Strategy can test for collisions against the storage layer WITHOUT being coupled to it. The shortener provides the predicate at the call site — `code -> !codeToUrl.containsKey(code)`. Strategy doesn't know what `codeToUrl` is."*

### The principle to verbalize — Strategy + Information Expert
> "Strategy lets the ID generation vary at construction. Base62Encoder is a utility because it's stateless — encoder.encode(id) is a pure function. Information Expert: the shortener owns the maps and knows about idempotency; the strategy owns ID generation policy; the encoder owns the alphabet math. Three concerns, three classes."

---

## STEP 4 — Implementation (~18 min)

### Open by asking
> "Real Java or pseudo-code? I'll do `shorten` first — it's where idempotency + ID strategy + encoder all converge — then base-62, then dry-run the concurrent same-URL scenario."

### 4.1 `shorten` — the atomic idempotency entry point

```java
public String shorten(String longUrl) {
    if (longUrl == null || longUrl.isBlank())
        throw new IllegalArgumentException("longUrl required");

    return urlToCode.computeIfAbsent(longUrl, url -> {
        long id = idStrategy.nextId(code -> !codeToUrl.containsKey(code));
        String code = Base62Encoder.encode(id);
        codeToUrl.put(code, url);
        return code;
    });
}
```

**Four callouts:**

1. *"`urlToCode.computeIfAbsent(url, ...)` is atomic per key. Two threads calling shorten(SAME_URL) simultaneously cannot both invoke the lambda — Java guarantees the lambda runs at most ONCE per key. Same pattern as PaymentGateway's idempotency."*

2. *"The lambda mints an id, encodes it, writes the FORWARD map, then returns the code. The computeIfAbsent writes the REVERSE map — same atomic call. Both maps stay in sync."*

3. *"The Strategy doesn't know about codeToUrl directly. We pass a lambda — `code -> !codeToUrl.containsKey(code)`. Decoupled."*

4. *"Strategy.nextId may LOOP internally on collision (RandomIdStrategy). The Counter never collides by construction. Either way, the result is a base-62 code we can store."*

### 4.2 `shortenWithAlias` — vanity codes

```java
public String shortenWithAlias(String longUrl, String alias) {
    // validate alias = base-62 characters only
    String existing = codeToUrl.putIfAbsent(alias, longUrl);
    if (existing != null && !existing.equals(longUrl)) {
        throw new IllegalStateException("Alias already in use: " + alias);
    }
    // NOTE: deliberately don't update urlToCode — primary code is whatever was set first
    return alias;
}
```

> **Senior callout:** *"`putIfAbsent` returns null if the alias was free (we won), or returns the EXISTING url if it was taken. We then reject if the existing url is DIFFERENT — that's the conflict case. If existing == longUrl, this is an idempotent re-registration (return the alias). Note we deliberately don't update urlToCode — vanity codes are aliases, not primary codes."*

### 4.3 `expand` — trivial lookup

```java
public String expand(String shortCode) {
    String url = codeToUrl.get(shortCode);
    if (url == null) throw new NoSuchElementException("Unknown short code: " + shortCode);
    return url;
}
```

### 4.4 Base62Encoder — full implementation

```java
public static String encode(long id) {
    if (id < 0) throw new IllegalArgumentException();
    if (id == 0) return "0";
    StringBuilder sb = new StringBuilder();
    while (id > 0) {
        sb.append(ALPHABET.charAt((int) (id % 62)));
        id /= 62;
    }
    return sb.reverse().toString();        // ← REVERSE — digits come out LSB first
}

public static long decode(String code) {
    long result = 0;
    for (char c : code.toCharArray()) {
        int digit = ALPHABET.indexOf(c);
        if (digit < 0) throw new IllegalArgumentException();
        result = result * 62 + digit;       // ← MSB first this direction
    }
    return result;
}
```

> **Senior callout — the reverse:** *"In encode, the modulo gives us the LEAST significant digit first. We append in order then reverse the StringBuilder at the end. Forgetting the reverse is the #1 base-N encoding bug."*

### 4.5 Verification — base-62 round-trip

```
encode(0)          → "0"           decode("0")           → 0
encode(1)          → "1"           decode("1")           → 1
encode(61)         → "z"           decode("z")           → 61
encode(62)         → "10"          decode("10")          → 62        ← carry to next digit
encode(1_000_000)  → "4C92"        decode("4C92")        → 1,000,000
encode(62^8)       → "100000000"   decode("100000000")   → 218 trillion

Round-trip property: decode(encode(x)) == x for all valid x.    ✓
```

### 4.6 Verification — idempotency under contention

```
Setup: UrlShortener with CounterIdStrategy. 50 threads await CountDownLatch.
       All 50 call shorten("https://example.com/contended") simultaneously.

Inside computeIfAbsent("https://example.com/contended", lambda):
   Java ConcurrentHashMap internals:
     - Hash to bucket.
     - Bucket lock (briefly).
     - Check if value exists for key:
       - If YES: return existing (the other 49 threads).
       - If NO : run lambda, set value, return.

   Result:
     ONE thread runs the lambda → mints id 1_000_000 → encodes "4C92" → stores both maps.
     49 threads find the entry already populated → return "4C92" directly.

Driver assertion after the burst:
   distinct codes returned: 1     ✓ (all 50 saw "4C92")
   shortener.size():        1     ✓ (exactly one mapping)
```

---

## STEP 5 — Extensibility (~10 min)

### 5.1 "Add TTL / expiration — short codes auto-expire after N days"

> **Problem in current design:** *"Codes live forever — bloats memory; can also leak old URLs that should've been forgotten."*
>
> **Pattern as the fix:** *"Replace `codeToUrl: Map<String, String>` with `Map<String, UrlEntry>` where `UrlEntry { String url, Instant expiresAt }`. On `expand`, check expiration and lazy-delete. A background sweeper periodically scans for expired entries to free memory faster than passive expiration. Same pattern as Amazon Locker's expired-compartment cleanup."*

### 5.2 "Analytics — count clicks per code"

> **Problem in current design:** *"`expand` doesn't track who clicked or when."*
>
> **Pattern as the fix:** *"Observer pattern. `UrlShortener` publishes `UrlExpanded(code, timestamp, userAgent)` events. Subscribers: ClickCounter, GeoLogger, AbuseDetector — register independently. Same pattern as NotificationSystem's Observer."*

### 5.3 "Distribute across multiple servers" (this is the HLD pivot)

> **Problem in current design:** *"Single-process in-memory. Multi-server means concurrent shortening could mint the same id from two servers."*
>
> **Pattern as the fix:** *"This is the HLD axis. For LLD scope I'd mention:*
> - *Sharded counter: each server reserves a range (e.g., Twitter Snowflake — server-id bits in the high bits of the id)*
> - *Central counter service: each shortening call hits the counter service; bottleneck.*
> - *Random+retry against a shared store: works at low load factor; degrades at high.*
>
> *In practice, real shorteners (bit.ly, t.co) use sharded counters with a base-N alphabet that includes server-id bits. But that's a system-design conversation, not LLD."*

### 5.4 "Abuse / spam — block malicious URLs"

> **Problem in current design:** *"Anyone can shorten any URL. Risk of phishing / malware redirection."*
>
> **Pattern as the fix:** *"Add a `UrlValidator` Strategy injected at construction — `validate(url) → boolean or ValidationResult`. Impls: `RegexBlocklistValidator`, `SafeBrowsingApiValidator`, etc. `shorten` calls it before minting; reject + log abuse on failure. Same pattern as PaymentGateway's fraud-check extension."*

### 5.5 Other "what-if" answers

| Follow-up                                  | Answer                                                                                              |
| ------------------------------------------ | --------------------------------------------------------------------------------------------------- |
| "Custom domains per customer"              | Multi-tenant — `Map<TenantId, UrlShortener>` keyed by tenant. Each tenant has independent maps.    |
| "Soft delete / archival"                   | Add a `deletedAt` timestamp; expand skips deleted but archive retains for legal hold.              |
| "QR codes alongside short URLs"            | After shortening, optionally render a QR code from the short URL — separate utility class.        |
| "Bulk shorten / expand"                    | Loop the existing methods atomically per item (`shortenAll(List<String>)` returns `List<String>`). |
| "Persistence"                              | Inject `UrlRepository`; write on shorten / delete; replay on boot.                                 |

---

## Design Patterns — Hello Interview's canonical 8

> **One pattern earns rent in the base:**
> - **Strategy (#1)** for IdGenerationStrategy — justified by counter vs random as legitimate alternatives.

### How this maps to UrlShortener specifically

**Already in the BASE design — call out by name:**

- **Strategy (#1)** ⭐ — IdGenerationStrategy with two impls. Name it in Step 2.
- **Facade (#8)** — UrlShortener is the only class application code touches.
- **Information Expert** (GRASP) — shortener owns the maps + idempotency; strategy owns ID gen policy; encoder owns the alphabet math.
- **Dependency Injection** (principle) — Strategy injected via constructor.

**Reach for these on Step-5 follow-ups:**

| Follow-up                                  | Pattern (HI's 8)             | Your line                                                                                            |
| ------------------------------------------ | ---------------------------- | ---------------------------------------------------------------------------------------------------- |
| "Analytics / click tracking"               | **Observer (#2)**            | *"Publish UrlExpanded events; click counters and abuse detectors subscribe independently."*         |
| "Abuse / spam filtering"                   | **Strategy (#1)** ⭐         | *"`UrlValidator` Strategy — regex blocklist, Safe Browsing API. Called before shorten()."*          |
| "Different alphabets (base-58, base-32)"   | (Encoder Strategy)            | *"Promote Base62Encoder to an Encoder interface; Base58 (Bitcoin-style) and Base32 (no confusable chars) slot in."* |
| "Multi-tenant"                             | (Composite over shorteners)  | *"`Map<TenantId, UrlShortener>` keyed by tenant; route at API boundary."*                          |
| "Persistence"                              | (Repository — not HI's 8)    | *"Inject UrlRepository; write on every shorten/delete."*                                            |

**Patterns to refuse:**

- **Singleton on UrlShortener** — kills tests; DI a single instance.
- **Builder for the 1-arg `UrlShortener(strategy)` ctor** — academic noise.
- **Full GoF State pattern on a code's lifecycle** — code is either present or deleted; no behavior per state.

### One sentence to say at the end of Step 3

> *"Base design names Strategy by name (IdGenerationStrategy) plus Facade as a principle. Observer (analytics), UrlValidator Strategy (abuse prevention), and Encoder Strategy (different alphabets) all land in Step 5 if their follow-ups surface."*

---

## Interview deep-dives — the questions you'll definitely get asked

### 1. Complexity (Big-O)

| Operation                                | Time                                              | Space               | Notes                                                                              |
| ---------------------------------------- | ------------------------------------------------- | ------------------- | ---------------------------------------------------------------------------------- |
| `shorten` — first time for this URL       | **`O(L + 1)`** where L = encode digits ≈ 6        | O(1) per call       | nextId + encode + 2 map puts                                                       |
| `shorten` — same URL again (cache hit)   | **`O(1)`**                                        | O(0) per call       | computeIfAbsent returns existing                                                   |
| `shorten` — RandomIdStrategy at low LF    | **`O(1)`** amortized                              | O(1)                | Collision retries are rare below 70% load factor                                   |
| `shorten` — RandomIdStrategy at high LF   | **`O(1/(1-LF))`** expected                        | O(1)                | Approaches infinity as keyspace saturates                                          |
| `expand`                                  | **`O(1)`**                                        | O(1)                | Single map get                                                                      |
| `delete`                                  | **`O(1)`**                                        | O(1)                | Two map removes                                                                     |
| Base62 encode (L digits)                  | **`O(L)`** ≈ O(log id)                            | O(L)                | StringBuilder + reverse                                                            |
| Base62 decode (L digits)                  | **`O(L)`**                                        | O(1)                |                                                                                    |
| Storage — codeToUrl + urlToCode           | -                                                 | **`O(N)`** each     | N unique URLs                                                                       |

> **Senior callout:** *"All hot-path operations are O(1) except the encode/decode which is O(log id). For ids up to 62^9 ≈ 13 quadrillion that's 9 character ops — negligible. The double map is the space cost we pay for both directions of lookup (idempotency on the way in, expand on the way out)."*

### 2. Concurrency / thread-safety

| Approach                                | When to use                                  | Cost                                                              |
| --------------------------------------- | -------------------------------------------- | ----------------------------------------------------------------- |
| **`ConcurrentHashMap.computeIfAbsent`** ⭐ | **Default for idempotency.** Atomic per key. | Negligible — per-bucket micro-lock inside the map                |
| `AtomicLong counter`                     | **Default for CounterIdStrategy.** Lock-free. | Single CAS per id                                                |
| `synchronized` on the shortener          | NEVER — turns all ops into a bottleneck      | Wastes the per-key atomicity of the maps                          |
| Distributed counter / Redis ATOMIC INCR   | Multi-server setup                           | Network round-trip per shorten — significant latency             |

> **The key insight:** *"Idempotency uses `computeIfAbsent` for per-url atomicity. ID generation uses `AtomicLong` for per-id atomicity. Neither needs an outer lock on the shortener. Same composition as PaymentGateway: per-key atomic data structures + atomic primitives."*

### 3. Testing — what to write tests for

| Test category                | Cases to cover                                                                                              |
| ---------------------------- | ----------------------------------------------------------------------------------------------------------- |
| Round-trip                   | shorten(url) → code; expand(code) → url                                                                     |
| **Idempotency**              | shorten(SAME url) 3 times → exactly one code; size == 1                                                     |
| Vanity alias                 | shortenWithAlias(url, "abc") works; shortenWithAlias(other, "abc") rejects                                  |
| **Vanity idempotency**       | shortenWithAlias(SAME url, SAME alias) twice → returns same alias both times                                |
| Unknown expand               | expand("doesnotexist") → throws NoSuchElement                                                              |
| Delete frees the code        | shorten, delete, shorten same url → new code minted                                                         |
| **Base-62 round-trip**       | encode(0/1/61/62/1M/62^8) → decode → original                                                              |
| **Random strategy retries**  | RandomIdStrategy with tiny range and seeded random; pack 9/10 ids without throwing                          |
| **Concurrent same-URL**      | 50 threads shorten same url → exactly 1 distinct code; size == 1                                            |
| Concurrent different URLs    | 50 threads × distinct urls → 50 distinct codes; size == 50                                                  |

```java
@Test
void fifty_threads_same_url_one_code() throws Exception {
    UrlShortener s = new UrlShortener();
    Set<String> seen = ConcurrentHashMap.newKeySet();
    int N = 50;
    ExecutorService pool = Executors.newFixedThreadPool(N);
    CountDownLatch fire = new CountDownLatch(1);
    for (int i = 0; i < N; i++) pool.submit(() -> {
        try { fire.await(); } catch (InterruptedException e) { return; }
        seen.add(s.shorten("https://x.com/same"));
    });
    fire.countDown();
    pool.shutdown();
    pool.awaitTermination(5, TimeUnit.SECONDS);

    assertEquals(1, seen.size());
    assertEquals(1, s.size());
}
```

### 4. SOLID mapping

| Letter                       | Where it shows up                                                                                          |
| ---------------------------- | ---------------------------------------------------------------------------------------------------------- |
| **S** Single Responsibility  | UrlShortener = orchestration + idempotency. Base62Encoder = alphabet math. IdGenerationStrategy = ID policy. Three reasons to change. |
| **O** Open/Closed            | New ID strategy = new class. New alphabet = new encoder. UrlShortener code unchanged.                     |
| **L** Liskov Substitution    | Any IdGenerationStrategy substitutable behind the interface — same `nextId(Predicate)` contract.          |
| **I** Interface Segregation  | IdGenerationStrategy has ONE method. Base62Encoder has TWO static methods. Both narrow.                  |
| **D** Dependency Inversion   | UrlShortener depends on the Strategy interface, not on Counter or Random concrete. Injected at ctor.      |

### 5. "Summarize your design in 30 seconds"

> *"Five classes: UrlShortener (facade), Base62Encoder (static utility), IdGenerationStrategy (interface), CounterIdStrategy (monotonic AtomicLong), RandomIdStrategy (random+retry). Two ConcurrentHashMaps inside the shortener — codeToUrl for expand, urlToCode for idempotency. Idempotency via `urlToCode.computeIfAbsent` — same long-URL submitted 50 times returns the same short code with exactly ONE id minted; the other 49 callers get the cached code. Base-62 alphabet (0-9A-Za-z) is URL-safe and dense — 6 chars = 56 billion ids. ID generation is pluggable: counter for predictable internal codes, random for unpredictable public ones; both implement the same Strategy interface that takes an `isAvailable` predicate so the Strategy stays decoupled from storage. Vanity aliases via `shortenWithAlias` use `putIfAbsent` for atomic alias claiming. All hot-path ops are O(1) except encode which is O(log id). Extensions: TTL via `UrlEntry { url, expiresAt }`, Observer for click analytics, UrlValidator Strategy for abuse prevention. Note: this is the LLD framing — the HLD version (distributed counter, sharded DB, cache) is a separate problem with the same name."*

That's ~55 seconds. Hits: bidirectional map + idempotency + base-62 + Strategy + the LLD-vs-HLD framing.

---

## Closing soundbites (memorize these)

- **Opening:** *"URL Shortener has LLD and HLD versions — let me clarify which scope. For LLD: encoding + class design + idempotency. For HLD: distributed counter + sharding + cache."*
- **Why base-62:** *"URL-safe (no + / =), dense (62^6 = 56 billion ids in 6 chars). Base-64 has the same density but '+' and '/' aren't URL-safe."*
- **Why `computeIfAbsent`:** *"Atomic per key. 50 concurrent callers shortening the same URL get back the SAME code with exactly ONE mint. Same pattern as PaymentGateway's idempotency."*
- **Why the Strategy takes a Predicate:** *"Decouples ID generation from storage. Strategy doesn't know about codeToUrl; shortener passes `code -> !codeToUrl.containsKey(code)` as the callback."*
- **Counter vs Random tradeoff:** *"Counter: guaranteed unique, predictable (enumerable). Random: unpredictable, may collide (bounded retry). Pick based on whether ID-space adversarial enumeration is a concern."*
- **Encode reverse trick:** *"Modulo gives LSB first; reverse the StringBuilder at the end. Forgetting the reverse is the #1 base-N encoding bug."*
- **On extensibility:** *"TTL via UrlEntry struct; Observer for analytics; UrlValidator Strategy for abuse; Encoder Strategy if you need base-58 or base-32 too."*

---

## Top mistakes that lose points

- **Floating-point in the encoder** — base-62 is integer arithmetic. Floats lose precision at large ids.
- **Forgetting to reverse the StringBuilder in encode** — digits come out LSB-first; you'll produce gibberish without the reverse.
- **Idempotency via "check map then put"** — classic check-then-act race. Use `computeIfAbsent`.
- **NO idempotency** — every call mints a fresh id even for the same URL; codeToUrl explodes and the same URL has 1000 codes.
- **Storing the long URL twice** — codeToUrl stores it; urlToCode stores a reference TO the code (not a duplicate). Memory waste otherwise.
- **No collision check on the counter strategy** — fine in practice (counter is monotonic), but the predicate adds defense if multiple strategies share the id-space.
- **Hard-coded ALPHABET inside both encode AND decode** — defining it once at top of class avoids inconsistency.
- **Promoting `Url` and `ShortCode` to classes** — string with no behavior. Ceremony.
- **Confusing LLD and HLD framings** — clarify scope in the opening. Don't drift into distributed-system territory uninvited.
- **Skipping the concurrent same-URL test** — the empirical idempotency proof is the senior signal.

---

## Files in this folder (your reference implementation)

| File                                       | What it shows                                                                            |
| ------------------------------------------ | ---------------------------------------------------------------------------------------- |
| `Base62Encoder.java`                       | Static utility — encode(long) + decode(String). URL-safe alphabet.                       |
| `IdGenerationStrategy.java`                | Strategy interface — `nextId(Predicate<String> isAvailable)`                              |
| `CounterIdStrategy.java`                   | Monotonic AtomicLong; guaranteed unique by construction                                  |
| `RandomIdStrategy.java`                    | Random within bounded range; retries on collision                                        |
| `UrlShortener.java`                        | **The hot class** — bidirectional ConcurrentHashMaps + `computeIfAbsent` idempotency + vanity aliases |
| `UrlShortenerDriver.java`                  | 8 scenarios — round-trip, idempotency, vanity, unknown expand, delete, base62 round-trip, random collision retry, **50-thread concurrent same-URL** |

Run from the project root:

```bash
mvn -q compile exec:java \
  -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.urlshortener.UrlShortenerDriver
```
