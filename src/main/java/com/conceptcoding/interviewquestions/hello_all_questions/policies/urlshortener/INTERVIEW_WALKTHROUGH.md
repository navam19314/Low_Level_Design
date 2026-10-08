# URL Shortener (LLD)

> **Why it's in the deck:** a short LLD round, or the code-level half of Amazon's ☆ URL-shortener HLD question. It tests id generation, Base62, the same URL → same code, and one real concurrency trap.
>
> **The crux (what's really being tested):**
> 1. **Id → short code:** Base62 encoding of a number (6 characters ≈ 56 billion codes).
> 2. **Id generation as a Strategy:** a counter (simple, but guessable) vs random with retry (unguessable).
> 3. **Atomic claims:** the same long URL → one code; two different URLs must never end up sharing a code.
>
> **Family:** F5 Swappable policy + concurrency tool B. See [foundations](../../00_AMAZON_LLD_FOUNDATIONS.md). For the system-design half (estimates, key-generation service, caching), see your HLD notes.

---

## 1. Plain-language picture

### Numbers to short codes
Every link gets a number. Write that number in **base 62** (0–9, A–Z, a–z) instead of base 10, so it's short:
```
1,000,000 (decimal)  →  "4C92" (base 62)          62^6 ≈ 56 billion 6-char codes · 62^7 ≈ 3.5 trillion
```
Why not base 64? Its extra characters `+` and `/` aren't safe in URLs.

### Where the number comes from
- **Counter:** 1,000,000, 1,000,001, 1,000,002... Never collides, but the codes are **sequential**: anyone can enumerate every link (`4C92`, `4C93`, ...).
- **Random:** pick a random number; if its code is taken, try again. Unguessable; needs a retry on collision.

### Same URL twice → same code
Shortening `https://example.com/a` twice returns the same code both times. A second map, URL → code, remembers it.

### The trap: two URLs, one code
With random ids, two *different* URLs shortened at the same moment can draw the **same** random code. If the code checks "is it free?" and *then* stores it, both see "free" and the second overwrites the first, so the first person's link now opens someone else's page. Fix: **claim** the code in one step (`putIfAbsent`). If the claim fails, draw again. (This deck had exactly this bug; it's now fixed, with a driver test of 1,000 URLs forced to collide.)

---

## 2. From story to design

| # | Decision | Rejected | Why |
|---|---|---|---|
| D1 | Base62 of a `long` id | hashing the URL (MD5 → first 7 chars) | A hash can collide for different URLs and needs collision handling anyway; ids are simpler and shorter. |
| D2 | `IdGenerationStrategy` (counter / random) | hard-coding one | Counter = simple and dense; random = unguessable. The choice depends on privacy needs. |
| D3 | `codeToUrl` + `urlToCode` (both `ConcurrentHashMap`) | one map | Expand is O(1); the same URL returns the same code. |
| D4 | `urlToCode.computeIfAbsent(url, ...)` | get-then-put | Concurrent `shorten(sameUrl)` → one code (50 threads in the driver → 1). |
| D5 | The strategy's predicate **claims** the code: `codeToUrl.putIfAbsent(code, url) == null` | `!containsKey(code)` then `put` | Two different URLs racing for the same random code: exactly one wins; the other retries. |
| D6 | Aliases validated as Base62 and claimed with `putIfAbsent` | trusting input | A vanity alias can't steal an existing code or contain unsafe characters. |
| D7 | Random ids from `SecureRandom`, with a bounded retry count | infinite retries | Many retries means the code space is too full: lengthen the codes. |

### Class shape
```
UrlShortener                       ← shorten(url) · shortenWithAlias(url, alias) · expand(code) · delete(code)
  ConcurrentHashMap codeToUrl · ConcurrentHashMap urlToCode · IdGenerationStrategy

«interface» IdGenerationStrategy  nextId(Predicate<String> tryClaim)
  ├── CounterIdStrategy (AtomicLong, starts at 1,000,000 for 4-char codes)
  └── RandomIdStrategy (SecureRandom in a range, retry up to N)
Base62Encoder  encode(long) · decode(String)
```

---

## 3. Patterns that earn their place

| Pattern | Problem it solves | Without it |
|---|---|---|
| **Strategy** (`IdGenerationStrategy`) | counter vs random vs (later) range-allocated ids | id logic tangled with storage |

**Say:** *"Base62 of an id. Id generation is a Strategy: a counter is dense but enumerable, random is unguessable with a retry. Claims are atomic with putIfAbsent, so two URLs can't share a code."*

**Tempting but wrong:** a Singleton service, Factory (one implementation chosen at startup is plain construction), Observer for clicks in the base.

---

## 4. The 30–45 minute run

### Clarify (min 0–4)
> **You:** Shorten a long URL to a short code, and expand a code back. The same URL gives the same code?
> **Interviewer:** Yes.
> **You:** Should codes be guessable-sequential or random?
> **Interviewer:** Discuss both.
> **You:** Custom aliases? Delete?
> **Interviewer:** Yes to both.
> **You:** Expiry and analytics: later?
> **Interviewer:** Later.

### Timeline
| Min | Do |
|---|---|
| 4–8 | The Base62 maths (62⁶ ≈ 56 B), counter vs random, the collision trap. |
| 8–13 | Code step 1: **`Base62Encoder`** encode/decode. |
| 13–19 | Code step 2: `IdGenerationStrategy` + `CounterIdStrategy` + `RandomIdStrategy`. |
| 19–30 | Code step 3: **`UrlShortener`**: `shorten` (with the claiming predicate), `shortenWithAlias`, `expand`, `delete`. |
| 30–35 | Dry run; follow-ups. |

### The code you write, in this order

**Step 1: Base62** ([Base62Encoder.java](Base62Encoder.java))
```java
public final class Base62Encoder {
    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE = 62;

    public static String encode(long id) {
        if (id < 0) throw new IllegalArgumentException("id must be >= 0");
        if (id == 0) return "0";
        StringBuilder sb = new StringBuilder();
        while (id > 0) {
            sb.append(ALPHABET.charAt((int) (id % BASE)));    // least-significant digit first...
            id /= BASE;
        }
        return sb.reverse().toString();                       // ...so reverse at the end
    }

    public static long decode(String code) {
        long result = 0;
        for (char c : code.toCharArray()) {
            int digit = ALPHABET.indexOf(c);
            if (digit < 0) throw new IllegalArgumentException("invalid base62 char: " + c);
            result = result * BASE + digit;
        }
        return result;
    }
}
```

**Step 2: id strategies** ([IdGenerationStrategy.java](IdGenerationStrategy.java), [CounterIdStrategy.java](CounterIdStrategy.java), [RandomIdStrategy.java](RandomIdStrategy.java))
```java
public interface IdGenerationStrategy {
    long nextId(Predicate<String> tryClaim);       // tryClaim(code): true = the code is now yours
}

public class CounterIdStrategy implements IdGenerationStrategy {
    private final AtomicLong counter = new AtomicLong(1_000_000L);    // 4-char codes from the start
    public long nextId(Predicate<String> tryClaim) {
        while (true) {                                                  // skips codes taken by aliases
            long candidate = counter.getAndIncrement();
            if (tryClaim.test(Base62Encoder.encode(candidate))) return candidate;
        }
    }
}

public class RandomIdStrategy implements IdGenerationStrategy {
    private final long range;                      // 62^7 for 7-char codes
    private final Random random;                   // SecureRandom: unguessable
    private final int maxAttempts;
    public long nextId(Predicate<String> tryClaim) {
        for (int i = 0; i < maxAttempts; i++) {
            long candidate = (long) (random.nextDouble() * range);
            if (tryClaim.test(Base62Encoder.encode(candidate))) return candidate;
        }
        throw new IllegalStateException("Could not find a free code after " + maxAttempts + " attempts");   // space too full
    }
}
```

**Step 3: the service** ([UrlShortener.java](UrlShortener.java))
```java
public class UrlShortener {
    private final ConcurrentHashMap<String, String> codeToUrl = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> urlToCode = new ConcurrentHashMap<>();
    private final IdGenerationStrategy idStrategy;

    public String shorten(String longUrl) {
        if (longUrl == null || longUrl.isBlank()) throw new IllegalArgumentException("longUrl required");
        return urlToCode.computeIfAbsent(longUrl, url -> {                    // same URL → one code
            // the predicate CLAIMS the code: "free?" and "take it" in one atomic step
            long id = idStrategy.nextId(code -> codeToUrl.putIfAbsent(code, url) == null);
            return Base62Encoder.encode(id);
        });
    }

    public String shortenWithAlias(String longUrl, String alias) {
        // validate: non-blank, base62 characters only
        String existing = codeToUrl.putIfAbsent(alias, longUrl);
        if (existing != null && !existing.equals(longUrl)) throw new IllegalStateException("Alias already in use: " + alias);
        return alias;                                                          // primary code for the URL unchanged
    }

    public String expand(String shortCode) {
        String url = codeToUrl.get(shortCode);
        if (url == null) throw new NoSuchElementException("Unknown short code: " + shortCode);
        return url;
    }

    public boolean delete(String shortCode) {
        String url = codeToUrl.remove(shortCode);
        if (url == null) return false;
        urlToCode.remove(url, shortCode);                // only if this was the URL's primary code
        return true;
    }
}
```
**Shape to remember:** `computeIfAbsent(url)` → strategy loop → `putIfAbsent(code)` claims → Base62.

### Dry run
```
counter at 1,000,000 → shorten(a) → claim "4C92" ✓ → a = 4C92
shorten(a) again → urlToCode has it → 4C92 (no new id)
alias "4C93" → claimed by an alias first → shorten(b): claim "4C93" ✗ → next "4C94" ✓
Random, two URLs draw "Xy7Pq2a" at once → both putIfAbsent → one gets null (wins), the other gets the
winner's URL (loses) → loser draws again. No overwrite.
```

---

## 5. Follow-ups: answer out loud first, then open

<details>
<summary><b>Q1. Links should expire, and we want click counts.</b></summary>

Store a small `Link{url, expiresAt, AtomicLong clicks}` instead of a bare string:
```java
public String expand(String code) {
    Link link = links.get(code);
    if (link == null) throw new NoSuchElementException("Unknown short code: " + code);
    if (link.expiresAt != null && !clock.instant().isBefore(link.expiresAt)) {
        links.remove(code, link);                            // lazy expiry: cleaned on first use after expiry
        throw new NoSuchElementException("Link expired: " + code);
    }
    link.clicks.incrementAndGet();                           // lock-free counter
    return link.url;
}
```
At scale, don't increment on the hot redirect path: publish a click event to a queue and aggregate asynchronously (plus a daily sweeper for expired links nobody visits).
</details>

<details>
<summary><b>Q2. Counter codes are enumerable. Can we keep the counter but hide the order?</b></summary>

Multiplying by a big number modulo 62⁷ *looks* random, but consecutive ids then differ by a constant: two codes reveal the pattern (this deck tried it and threw it away). Real options: **random ids** (already a Strategy here), or **encrypt** the counter with a small reversible cipher (a Feistel network over the id; libraries like Sqids do this). Unique **and** unpredictable.
</details>

<details>
<summary><b>Q3. 20 servers generating ids.</b></summary>

- **Range allocation:** a central counter (DB/ZooKeeper) hands each server a block of 10,000 ids; the server counts through its block locally and asks for a new one when done. No per-request coordination.
- **Snowflake ids:** timestamp + server id + per-server sequence, unique without coordination (longer codes).
- **Random:** claim with a DB unique constraint on the code (`INSERT ... ON CONFLICT DO NOTHING`, retry on conflict).
</details>

<details>
<summary><b>Q4. 301 or 302 redirect?</b></summary>

**301 (permanent):** browsers and CDNs cache it, so fewer hits on your servers, but you lose click analytics and can't change or expire the target reliably. **302 (temporary):** every click comes to you, which allows analytics and expiry. Most shorteners use 302 (or 307) for that reason.
</details>

<details>
<summary><b>Q5. Abuse: phishing links, someone shortening a million URLs.</b></summary>

Rate-limit `shorten` per user/IP (the deck's Rate Limiter); check targets against a malware/phishing list (async, then disable bad codes); reserve words (`admin`, `login`) from aliases; give owners an authenticated delete.
</details>

<details>
<summary><b>Q6. Reads vastly outnumber writes. Make expand fast.</b></summary>

A cache in front (Redis / in-process LRU: the deck's LRU Cache) keyed by code; codes are immutable, so caching is safe (invalidate on delete/expiry). Read replicas for the store; a CDN for the redirect itself if you use 301s.
</details>

<details>
<summary><b>Q7. How do you test it?</b></summary>

Round trip, the same URL → the same code, an alias taken by a different URL → rejected, an unknown code, delete frees the code, Base62 round trips including edge values (0, 61, 62, 62⁸), random retries in a tiny range, 50 threads with the same URL → 1 code, and 1,000 different URLs forced to collide → 0 wrong mappings. All are in the driver.
</details>

---

## 6. Traps
1. `containsKey` then `put` for a new code (the overwrite race).
2. Base64 (`+`, `/` aren't URL-safe).
3. Truncated hashes without collision handling.
4. Sequential counters when privacy matters (enumeration).
5. Get-then-put for the same-URL check.
6. Infinite random retries.

## 7. Recall check
1. Encode 62 and 3,843 in Base62.
2. Counter vs random: one advantage and one drawback each.
3. Explain the two-URLs-one-code race and the one-line fix.
4. Why two maps?
5. 301 vs 302 for a shortener?

<details><summary>Answer to 1</summary>

62 = 1×62 + 0 → **"10"**. 3,843 = 61×62 + 61 → **"zz"**.
</details>

**Rebuild in 10 minutes:** `Base62Encoder` · `IdGenerationStrategy` + counter + random · `shorten` (computeIfAbsent + claiming predicate) · `expand` · alias via `putIfAbsent`.

---

**Files:** `UrlShortener` · `Base62Encoder` · `IdGenerationStrategy` · `CounterIdStrategy` · `RandomIdStrategy` · `UrlShortenerDriver` (round trip, idempotency, alias, unknown, delete, Base62, random retry, 50-thread same URL, 1000-URL collision test)
```bash
mvn -q compile exec:java -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.policies.urlshortener.UrlShortenerDriver
```
