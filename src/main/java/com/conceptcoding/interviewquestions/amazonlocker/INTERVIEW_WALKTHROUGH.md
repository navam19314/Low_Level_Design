    # Amazon Locker — 45-min LLD Interview Walkthrough

**Target role:** SDE‑2 (Amazon, Adobe, Microsoft, Atlassian, etc.)
**Source method:** Hello Interview *Delivery Framework* applied to the *Amazon Locker* problem breakdown.

A 45-min round gives you ~10 extra minutes over the 35-min budget — spend most of it on Step 4 (deposit + pickup + expiration flows) and Step 5 (extensibility — two-phase deposit, size fallback, maintenance, concurrency).

> Amazon-specific tip: this exact problem is a classic Amazon onsite. They love it because it tests **Information Expert** ("which class owns this data, therefore owns this rule?") and **tell-don't-ask** in a clear, non-trivial way. Lean into both.

---

## Time budget (45 min)

| Step | Activity                                          | Budget   | Cumulative |
| ---- | ------------------------------------------------- | -------- | ---------- |
| 1    | Requirements                                      | ~5 min   | 5          |
| 2    | Entities & Relationships                          | ~4 min   | 9          |
| 3    | Class Design (state + behavior)                   | ~12 min  | 21         |
| 4    | Implementation (`depositPackage`, `pickup`, expiry, dry run) | ~16 min | 37 |
| 5    | Extensibility (3–4 follow-ups)                    | ~7 min   | 44         |
| —    | Wrap & questions                                  | ~1 min   | 45         |

Watch the clock at minute **5** (Step 1 done), minute **21** (start coding), minute **37** (extensibility).

---

## Mental models — internalize these BEFORE you walk in

These three pictures unlock the implementation. If you can draw them from memory, the deposit/pickup/expiration code falls out almost mechanically.

### M1. Compartment state machine

```
                            markOutOfService()
                +----------------------------------+
                |                                  |
                v                                  |
       +----------------+   markOccupied()  +-----------------+
       |   AVAILABLE    | ----------------> |    OCCUPIED     |
       |  (can deposit) |                   |  (has package)  |
       +----------------+                   +-----------------+
                ^                                  |
                |   markFree()  (pickup OR         |
                |   staff reclaim expired)         |
                +----------------------------------+

       OUT_OF_SERVICE  (offline — skipped by allocation)
              |
              +-- maintenance flow (out of scope, but enum slot reserved)
```

**Why this saves time:** using an **enum** instead of `boolean occupied` means the third state (OUT_OF_SERVICE) drops in cleanly later. "Invalid states unrepresentable" is a senior-level argument the interviewer will reward.

### M2. AccessToken lifecycle

```
   depositPackage()
        |
        v
   +-----------+    7 days       +-----------+    pickup() OR
   |  CREATED  | ============>   |  EXPIRED  | openExpiredCompartments()
   |  (live)   |    (clock)      | (rejected)|         |
   +-----------+                 +-----------+         v
        |                                          (removed from
        | pickup() (before expiration)              tokensByCode)
        v
   (consumed: compartment freed, token removed from map)


   Timeline:
      t=0          t=7d                 t=8d (staff scan)
       |            |                     |
       v            v                     v
   [deposit]----[expiration]----[openExpiredCompartments() reclaims]
```

The token is the **bearer credential** — possession of the code IS authorization. So:
- Map<code, AccessToken> is the only lookup direction at pickup time.
- Token must contain *everything* the locker needs at pickup: which compartment, when it expires.

### M3. The two lookup directions

```
  Driver flow              vs.            Customer flow
  -----------                              ---------------
  Has: a Size                              Has: a code (string)
  Needs: any compartment of that size      Needs: the EXACT compartment

       linear scan                              hash lookup
       List<Compartment>                        Map<String, AccessToken>
       O(n)                                     O(1)
```

**Why this saves time:** once you see the two flows have *opposite* lookup shapes, you know exactly which data structures Locker owns: **a list of compartments** (scanned by size at deposit) and **a map from code to token** (looked up at pickup). No other indexing needed.

---

## STEP 1 — Requirements (~5 min)

### What to say out loud (opener)
> "Before I start designing, let me clarify scope and rules so we're aligned on what 'done' looks like."

### Probe the 4 themes

| Theme               | Question to ask                                                                                       |
| ------------------- | ----------------------------------------------------------------------------------------------------- |
| Primary capabilities| "Driver deposits a package by size, gets a code. Customer redeems the code, the compartment opens — that's the core?" |
| Rules / completion  | "Codes expire after 7 days, right? And only one package per compartment?"                             |
| Error handling      | "Full of that size, invalid code, expired code, empty / null code — what should each return?"        |
| Scope boundaries    | "Backend logic only — no notifications, no payment, no routing, one locker station, no auth on the staff override — confirm?" |

### What to write on the board

```
Functional Requirements
1. Driver deposits a package of given size (SMALL / MEDIUM / LARGE).
2. System allocates an available compartment of matching size OR errors.
3. On successful deposit, system returns a pickup code.
4. Customer redeems the code: system validates and opens that compartment.
5. Codes expire 7 days after deposit. Expired codes are rejected.
6. Staff can open all expired compartments to reclaim them.
7. Invalid / unknown / expired codes return clear errors.

Out of Scope
- UI / rendering
- Delivery routing / logistics
- SMS / email notification delivery
- Payment / pricing
- Failed-attempt lockout
- Multiple locker stations
- Persistence / restart durability
- Concurrency between drivers (discuss as extension)
```

### Close the step
> "Does this match what you had in mind? Anything you'd add before I move to entities?"

---

## STEP 2 — Entities & Relationships (~4 min)

### What to say out loud
> "From these requirements, the core nouns I see are **Locker**, **Compartment**, and **AccessToken**. The orchestrator is **Locker** — it owns the compartments, owns the active tokens, and exposes deposit/pickup/reclaim. **Compartment** is the physical slot — owns its size and its occupancy state. **AccessToken** is the bearer credential — code + expiration + which compartment it unlocks."

### Why no `Package` class
> "I considered adding `Package`, but it has no state we'd actually use and no rules to enforce — the driver just tells us the size. So size becomes an argument, not its own class. Same reasoning for `Customer` and `Driver` — those are actors in the use case, not entities with state."

This is the **mid/senior signal** the article specifically calls out: arriving at the 3-class model through reasoning, not pre-building empty classes.

### What to write on the board

```
Entities
- Locker        (orchestrator: deposit, pickup, reclaim)
- Compartment   (physical slot: size + status)
- AccessToken   (bearer credential: code + expiration + compartment)

Enums
- Size                { SMALL, MEDIUM, LARGE }
- CompartmentStatus   { AVAILABLE, OCCUPIED, OUT_OF_SERVICE }

Relationships
- Locker        owns        List<Compartment>
- Locker        owns        Map<String, AccessToken>     (code -> token)
- AccessToken   references  Compartment                   (NOT owns — the locker owns it)
```

### Diagram — boxes and arrows

```
                  +------------------------------+
                  |           Locker             |   <- orchestrator
                  |  deposit / pickup / reclaim  |
                  +------------------------------+
                       |                  |
                 owns  |                  | owns
                       v                  v
            +------------------+     +------------------------+
            | List<Compartment>|     | Map<code, AccessToken> |
            +------------------+     +------------------------+
                  |                            |
                  | element                    | value
                  v                            v
            +-----------------+         +------------------+
            |  Compartment    | <-----  |   AccessToken    |
            |  id, size,      |  refs   |  code,           |
            |  status         |         |  expiresAt,      |
            +-----------------+         |  compartment ----+
                                        +------------------+
```

Narrate while drawing: *"AccessToken holds a reference to its Compartment so pickup is O(1) — code lookup gives us the token, token gives us the compartment, compartment opens. No scanning."*

---

## STEP 3 — Class Design (~12 min)

### Work top-down: Locker → Compartment → AccessToken.

### Locker — state ↔ requirement table

| Requirement                              | State Locker must own                            |
| ---------------------------------------- | ------------------------------------------------ |
| Allocate by size                         | `List<Compartment> compartments`                 |
| Validate code on pickup                  | `Map<String, AccessToken> tokensByCode`          |
| Expiration depends on "now"              | `Clock` (injected — makes expiration testable)   |
| Generate non-colliding codes             | `Random` (injected — makes codes reproducible)   |

### Locker — behavior table

| Need from requirements              | Method on Locker                            |
| ----------------------------------- | ------------------------------------------- |
| Driver deposits package             | `String depositPackage(Size size)`          |
| Customer picks up                   | `void pickup(String tokenCode)`             |
| Staff reclaims expired              | `void openExpiredCompartments()`            |

### Locker — class outline (write this on the board)

```java
public class AmazonLocker {
    // ----- State -----
    private final List<Compartment>          compartments;
    private final Map<String, AccessToken>   tokensByCode;
    private final Clock                       clock;     // injected
    private final Random                      random;    // injected

    private static final Duration TOKEN_TTL = Duration.ofDays(7);

    // ----- Behavior -----
    public String depositPackage(Size size)   { /* Step 4 */ }
    public void   pickup(String tokenCode)    { /* Step 4 */ }
    public void   openExpiredCompartments()   { /* Step 4 */ }
}
```

> **Sell the injected `Clock`:** *"Injecting `Clock` instead of calling `Instant.now()` directly means I can write deterministic tests for the 7-day expiration without sleeping or mocking statics. Same with `Random` — reproducible codes in tests."*

### Compartment — outline

```java
public class Compartment {
    private final String id;
    private final Size   size;
    private CompartmentStatus status;            // starts AVAILABLE

    public boolean isAvailable();
    public void    markOccupied();
    public void    markFree();
    public void    markOutOfService();
    public void    open();                        // hardware unlock
    // + getters: id, size, status
}
```

### AccessToken — outline (immutable)

```java
public final class AccessToken {
    private final String       code;
    private final Instant      expiresAt;
    private final Compartment  compartment;

    public boolean isExpired(Clock clock);
    // + getters: code, expiresAt, compartment
}
```

> *"Token is immutable — once issued, code/expiration/compartment don't change. State changes happen on Compartment (status) and on Locker (token added/removed from map)."*

### Diagram — class cards (whiteboard-friendly summary)

```
+----------------------------------+   +-------------------------+   +--------------------------+
|           AmazonLocker           |   |      Compartment        |   |       AccessToken        |
+----------------------------------+   +-------------------------+   +--------------------------+
| - compartments: List<Compartment>|   | - id: String            |   | - code: String           |
| - tokensByCode: Map<...>         |   | - size: Size            |   | - expiresAt: Instant     |
| - clock: Clock                   |   | - status: Compartment-  |   | - compartment: Compart.. |
| - random: Random                 |   |          Status         |   +--------------------------+
+----------------------------------+   +-------------------------+   | + isExpired(clock): bool |
| + depositPackage(size): String   |   | + isAvailable(): bool   |   | + getCode()              |
| + pickup(code): void             |   | + markOccupied()        |   | + getCompartment()       |
| + openExpiredCompartments()      |   | + markFree()            |   | + getExpiresAt()         |
+----------------------------------+   | + markOutOfService()    |   +--------------------------+
                                       | + open()                |
                                       +-------------------------+

Locker --owns--> List<Compartment>      AccessToken --refs--> Compartment
Locker --owns--> Map<code, AccessToken>
```

### The principle to verbalize — "Information Expert / Tell, Don't Ask"
> "Compartment owns its `status`, so the rule *'am I available?'* lives on Compartment as `isAvailable()`. Token owns its `expiresAt`, so the rule *'am I expired?'* lives on Token as `isExpired(clock)`. Locker doesn't reach into either to compute these — it asks. That keeps each class's rules in one place, so when expiration policy changes from 7 days to 14, only the Locker's TTL constant moves; when 'available' starts meaning 'available and not OUT_OF_SERVICE', only Compartment changes."

---

## STEP 4 — Implementation (~16 min)

### Open by asking
> "Real Java or pseudo-code? I'd like to walk through deposit, then pickup, then expiration."

### 4.1 `depositPackage` — flow + code

```
   depositPackage(size)
        |
        v
   +----------------------------+
   | scan compartments by size  |
   | find first AVAILABLE       |
   +----------------------------+
                | none -> throw NoSuchElement("no compartment of size ...")
                v
   +----------------------------+
   | compartment.open()         |     <- hardware first (driver opens before token)
   | compartment.markOccupied() |
   +----------------------------+
                v
   +----------------------------+
   | code = unique 6-digit      |
   | expiresAt = now + 7d       |
   | tokensByCode[code] = token |
   +----------------------------+
                v
   return code
```

```java
public String depositPackage(Size size) {
    Compartment compartment = findAvailable(size);
    if (compartment == null) {
        throw new NoSuchElementException("No available compartment of size " + size);
    }

    compartment.open();
    compartment.markOccupied();

    String code = generateUniqueCode();
    Instant expiresAt = clock.instant().plus(TOKEN_TTL);
    AccessToken token = new AccessToken(code, expiresAt, compartment);
    tokensByCode.put(code, token);
    return code;
}

private Compartment findAvailable(Size size) {
    for (Compartment c : compartments) {
        if (c.getSize() == size && c.isAvailable()) return c;
    }
    return null;
}

private String generateUniqueCode() {
    for (int i = 0; i < 10; i++) {                       // retry on collision
        String code = String.format("%06d", random.nextInt(1_000_000));
        if (!tokensByCode.containsKey(code)) return code;
    }
    throw new IllegalStateException("Could not generate a unique code");
}
```

> **Senior-signal callout:** *"Naive random codes can collide with live tokens — 1 in 1M is small but non-zero, and the consequence is a wrong customer opening someone else's compartment. So I generate-and-check against the live map and retry. Bounded retries to avoid an infinite loop if the keyspace ever shrinks."*

### 4.2 `pickup` — flow + code

```
   pickup(code)
        |
        v
   +----------------------------+
   | code null / empty?         |--yes--> throw IllegalArgument
   +----------------------------+
                | no
                v
   +----------------------------+
   | token = tokensByCode[code] |
   | token == null?             |--yes--> throw NoSuchElement
   +----------------------------+
                | no
                v
   +----------------------------+
   | token.isExpired(clock)?    |--yes--> throw IllegalState
   +----------------------------+
                | no
                v
   +----------------------------+
   | compartment.open()         |
   | compartment.markFree()     |
   | tokensByCode.remove(code)  |
   +----------------------------+
```

```java
public void pickup(String tokenCode) {
    if (tokenCode == null || tokenCode.isEmpty()) {
        throw new IllegalArgumentException("Invalid access token code");
    }
    AccessToken token = tokensByCode.get(tokenCode);
    if (token == null) {
        throw new NoSuchElementException("Invalid access token code");
    }
    if (token.isExpired(clock)) {
        throw new IllegalStateException("Access token has expired");
    }
    Compartment compartment = token.getCompartment();
    compartment.open();
    compartment.markFree();
    tokensByCode.remove(tokenCode);
}
```

> *"Three guard clauses, three distinct exception types — the caller can tell apart 'you typed it wrong', 'never existed', and 'you waited too long'."*

### 4.3 `openExpiredCompartments` — staff reclaim

```java
public void openExpiredCompartments() {
    Iterator<Map.Entry<String, AccessToken>> it = tokensByCode.entrySet().iterator();
    while (it.hasNext()) {
        AccessToken token = it.next().getValue();
        if (token.isExpired(clock)) {
            Compartment c = token.getCompartment();
            c.open();
            c.markFree();
            it.remove();                           // <- also clean up the token!
        }
    }
}
```

> **Senior-signal callout:** *"It's tempting to just call `c.open()` here and stop. But if I don't `markFree()` and also remove the token from the map, the compartment looks OCCUPIED forever and the map grows unbounded. The staff reclaim is really the deferred completion of a pickup that never happened — so it has to do the same cleanup."*

### 4.4 `AccessToken.isExpired` & supporting bits

```java
public boolean isExpired(Clock clock) {
    return !clock.instant().isBefore(expiresAt);   // expired when now >= expiresAt
}
```

> *"Using injected `Clock` instead of `Instant.now()` is what lets me unit-test the 7-day boundary by advancing a fake clock."*

### 4.5 Verification — trace a concrete scenario (1–2 min)

```
Setup: compartments = [S1, S2 : SMALL,  M1 : MEDIUM,  L1 : LARGE].  All AVAILABLE.
       clock = fixed @ 2026-06-01T10:00:00Z

depositPackage(SMALL):
   findAvailable(SMALL) -> S1
   S1.open(), S1.markOccupied()
   code = "431130", expiresAt = 2026-06-08T10:00:00Z
   tokensByCode = { "431130" -> token1 }
   return "431130"

pickup("431130"):
   token1 found, not expired
   S1.open(), S1.markFree()
   tokensByCode = {}
   ✓

pickup("000000"):
   token == null -> NoSuchElementException("Invalid access token code")
   ✓

depositPackage(SMALL) x3:
   1st -> S1 (OK).   2nd -> S2 (OK).   3rd -> findAvailable returns null
        -> NoSuchElementException("No available compartment of size SMALL")  ✓

depositPackage(MEDIUM)              -> token "948884" on M1
advance clock 8 days
pickup("948884"):
   token.isExpired -> true -> IllegalStateException("Access token has expired")  ✓
openExpiredCompartments():
   all 3 outstanding tokens are now expired -> S1, S2, M1 freed,
   tokensByCode = {}                                                              ✓
depositPackage(MEDIUM):
   findAvailable(MEDIUM) -> M1 (free again)                                       ✓
```

---

## STEP 5 — Extensibility (~7 min)

You're **pointing, not rewriting** — name the small additions; don't draft full classes unless asked.

### 5.1 "Size fallback — let a SMALL package use a MEDIUM if no SMALL is free"

> *"`findAvailable` already takes a `Size`. I'd change it to iterate from the requested size upward — `SMALL → MEDIUM → LARGE` — returning the first match. Tradeoff: callers can no longer assume exact size, and we waste LARGE slots on SMALL packages, so the order of allocation matters. I'd guard it behind a boolean flag on Locker so the policy is explicit."*

```java
private static final List<Size> FALLBACK_ORDER = List.of(SMALL, MEDIUM, LARGE);

private Compartment findAvailable(Size requested) {
    int start = FALLBACK_ORDER.indexOf(requested);
    for (int i = start; i < FALLBACK_ORDER.size(); i++) {
        Size s = FALLBACK_ORDER.get(i);
        for (Compartment c : compartments) {
            if (c.getSize() == s && c.isAvailable()) return c;
        }
    }
    return null;
}
```

### 5.2 "Two-phase deposit (reserve / confirm)"

> *"Right now `depositPackage` does open + occupy + issue-token in one atomic step. If the driver opens the door but never physically deposits, we've leaked a compartment for 7 days. I'd split it into `reserve(size) -> reservationId` and `confirm(reservationId) -> code`. Reserve marks OCCUPIED with a short timeout (say 60s); a background sweeper releases unreserved compartments. Token is only minted on `confirm`."*

```
   reserve(size)        confirm(reservationId)
        |                       |
        v                       v
   [OCCUPIED w/ TTL]   --->  [OCCUPIED + token issued]
        |                       
        |   (no confirm in 60s)
        v
   [AVAILABLE]  (sweeper reclaim)
```

### 5.3 "Maintenance — broken locker shouldn't be allocated"

> *"Already covered by the enum. `OUT_OF_SERVICE` is a distinct status; `findAvailable` only matches `AVAILABLE`. Adding `Locker.markOutOfService(compartmentId)` exposes it to staff. No other code paths change."*

### 5.4 "Concurrency — two drivers depositing at the same time"

> *"Single-locker, multi-threaded: the race is between `findAvailable` returning a Compartment and another thread marking it OCCUPIED first. Simplest fix: synchronize `depositPackage`, `pickup`, `openExpiredCompartments` on `this`. Throughput is fine because operations are local memory + one hardware unlock. If we needed more, I'd push the lock down to per-compartment with a tryLock and retry."*

### 5.5 Other "what-if" answers (have one-liners ready)

| Follow-up                                  | Answer                                                                                              |
| ------------------------------------------ | --------------------------------------------------------------------------------------------------- |
| "Persist across restart"                   | Inject a `LockerRepository` (DIP). Save on every state change; load list + map on boot.             |
| "SMS notifications when 7d nears"          | Observer / event bus — Locker publishes `PackageDeposited` and a periodic `TokenExpiringSoon`. UI/SMS subscribers wire up independently. |
| "Variable expiration per package"          | TTL becomes a parameter to `depositPackage`. Token already stores `expiresAt`, so nothing else changes. |
| "Multiple locker stations / network"       | Wrap a `LockerNetwork` over a `Map<stationId, AmazonLocker>`. Tokens carry stationId. Out of scope normally — flag it. |
| "Failed-attempt lockout"                   | Add a per-code attempt counter on the token + a `lockedUntil`. Pickup checks it before validating the code. |
| "Auditability — who picked up what when"   | Append-only `Event` log; emit `PackageDeposited` / `PackagePickedUp` / `PackageReclaimed` records.  |

---

## Design Patterns — Hello Interview's canonical 8, and WHEN to mention each

The single biggest pattern mistake at SDE‑2 level isn't *not knowing* patterns — it's **forcing them into the wrong step**. Patterns volunteered in Step 1, 2, or 3 sound rehearsed; the same patterns named in Step 5 sound senior. Amazon bar-raisers especially watch for this.

> **Hello Interview's stance** (worth memorizing): *"Patterns arise from good design decisions, not the other way around. Most interview designs use zero to two patterns maximum."*
>
> **Geography note (matters for you):** US interviews evaluate design quality without explicit pattern names. **India-based interviews (including Amazon India, Adobe Noida/Bangalore, Microsoft IDC) expect candidates to identify patterns by name.** Since you're interviewing in India, err on the side of **explicitly naming** the pattern when it fits — but still only when it fits.

### The 5-step timing rule (universal — applies to every LLD problem)

| Step                       | Mention patterns?              | Why                                                                                          |
| -------------------------- | ------------------------------ | -------------------------------------------------------------------------------------------- |
| **1. Requirements**        | **Never.**                     | You're gathering scope. Naming patterns here = you've decided the design before clarifying.  |
| **2. Entities**            | **Never.** (One exception)    | You're listing nouns. Exception: declaring an interface for "varying behavior later" — say *"interface"*, don't say *"Strategy"*. |
| **3. Class Design**        | **Rarely.** Only if the base design literally instantiates one (e.g., an injected `AllocationStrategy`). | Premature naming = pattern-stuffing. Better: name the **principle** (Info Expert, Tell-Don't-Ask). |
| **4. Implementation**      | **Never.**                     | You're writing method bodies. Patterns aren't a code-shape concern at this resolution.       |
| **5. Extensibility**       | **YES — 90% of mentions belong here.** | Every "what if..." follow-up maps to one pattern. Naming it = senior signal.            |

> **The senior one-liner to drop at the end of Step 3:**
> *"I'm deliberately staying free of GoF patterns at the base level — the design relies on Information Expert and Tell-Don't-Ask. I'll point out where patterns earn their place when we get to extensibility."*
>
> This single sentence proves you know patterns AND know when not to force them. Exactly the SDE‑2 signal Amazon grades on.

### Hello Interview's canonical 8 × interviewer trigger phrase

When the interviewer in Step 5 (or a senior-level Step 3 deep-dive) says something matching the **trigger**, name the **pattern**. That's how it sounds natural.

| # | Pattern              | Category   | Trigger phrase from interviewer                                                | What you say (one sentence)                                                                                       |
| - | -------------------- | ---------- | ------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------- |
| 1 | **Strategy** ⭐       | Behavioral | "different rules" · "variants" · "swap at runtime" · "piles of if/else by type" | *"Promote X to an interface; inject the concrete implementation. Replaces conditionals with polymorphism."*       |
| 2 | **Observer**         | Behavioral | "notify multiple" · "broadcast" · "event" · "when X happens, also do Y, Z, W"  | *"X publishes events; subscribers register independently. Decouples X from how the world reacts."*                |
| 3 | **State Machine**    | Behavioral | "behavior depends on state" · "complex transitions" · "state" repeated in reqs | *"Each state is its own class with its own transitions — and I'd draw the state diagram on the board to show it."* |
| 4 | **Factory** (Method) | Creational | "support different types" · "handle multiple variants" · creation logic varies | *"Centralize creation behind a factory method; callers stop depending on concrete classes."*                       |
| 5 | **Builder**          | Creational | "many optional fields" · "complicated construction" · "configuration object"   | *"Builder collects fields incrementally and validates on `build()` — beats a 12-arg constructor."*                 |
| 6 | **Singleton**        | Creational | "exactly one" · "global" · "shared resource"                                   | *"I'd resist textbook Singleton — it hurts tests and hides dependencies. **Inject a single instance via DI** instead."* |
| 7 | **Decorator**        | Structural | "optional features" · "stack behaviors" · "combine enhancements"               | *"Wrap X in decorators that each add one concern (logging, encryption, retry). Avoids the subclass explosion."*   |
| 8 | **Facade**           | Structural | "hide complexity" · "single entry point" · "wrap subsystem"                    | *"A clean facade over the messy parts. Orchestrators are usually facades — you're often already building one."*    |

> **⭐ Strategy is the #1 priority pattern.** HI's exact words: *"If you learn one pattern from this page, make it this one."* It directly tests polymorphism + composition — the core of OO.

### Three rules that make pattern mentions sound natural (not forced)

1. **Cap at 2 patterns** for the whole interview (HI's "zero to two maximum"). Beyond that = forcing. Pick the two strongest from the follow-ups you actually get.
2. **Always name the concrete win in the same breath.** *"Strategy here because the allocation rule swaps at runtime"* > *"I'd use Strategy."* The win is what separates senior from "I memorized GoF."
3. **Never volunteer a pattern without a trigger.** Trigger = either an interviewer phrase from the table above, or a concrete need you can point to in your own design. Volunteering *"this could be Visitor"* mid-Step-3 is the #1 over-engineering red flag.

### How this maps to Amazon Locker specifically

**What's naturally present in the base design** — name these by *principle*, and for the genuine patterns, by name:

- **Information Expert** (GRASP principle) — `Compartment.isAvailable()`, `AccessToken.isExpired(clock)`. *Name this one out loud; Amazon bar-raisers love it.*
- **Tell, Don't Ask** (principle) — Locker calls `isAvailable()` / `markFree()`; never reads `status` directly.
- **Dependency Injection** (principle) — `Clock` and `Random` injected via constructor for testability.
- **Immutability** (principle) — `AccessToken`'s final fields.
- **Facade (#8)** — *AmazonLocker is a facade* over `List<Compartment>`, the token map, hardware-unlock, and TTL math. HI explicitly notes orchestrators like this are facades most candidates build without naming. **Name it once in Step 2:** *"Locker is the orchestrator — effectively a facade over the compartment array, the token map, and the hardware-unlock API. Callers see one entry point."*
- **State Machine (#3) — lite** — `CompartmentStatus` is a 3-state machine (`AVAILABLE ↔ OCCUPIED`, plus side-state `OUT_OF_SERVICE`). **You already have the state diagram in the Mental Models section — show it.** Don't promote to full State pattern unless states gain per-state behavior.

**What to invoke if the interviewer pulls the matching follow-up in Step 5:**

| Follow-up                                       | Pattern (HI's 8)             | Your line                                                                                            |
| ----------------------------------------------- | ---------------------------- | ---------------------------------------------------------------------------------------------------- |
| "Size fallback / variable allocation rule"      | **Strategy (#1)** ⭐         | *"Promote `findAvailable` to an `AllocationStrategy` interface — `ExactSize` / `SmallestFit` / `LargestFit` implement it. Pluggable at construction."* |
| "Different code formats (6-digit / QR / OTP)"   | **Strategy (#1)** ⭐         | *"Inject a `CodeGenerator` strategy. Default is numeric-6; `QrCodeGenerator` / `OtpGenerator` slot in."* |
| "Pluggable hardware (real lock vs mock)"        | **Strategy (#1)** ⭐         | *"`LockHardware` is an interface; `RealLockHardware` / `FakeLockHardware` implement it. Compartment depends on the interface, not the concrete."* |
| "Notify customer (SMS / email / push)"          | **Observer (#2)**            | *"Locker publishes `PackageDeposited` / `TokenExpiringSoon` events. SMS / Email / Push are independent subscribers."* |
| "Audit / replay"                                | **Observer (#2)** (+ event log) | *"Append-only event log: `Deposited` / `PickedUp` / `Reclaimed`. State is derivable by folding events."* |
| "Add per-state behavior (e.g., RESERVED phase)" | **State Machine (#3)**       | *"Promote `CompartmentStatus` to State classes — each implements its own `deposit`/`pickup`/`expire` transitions. I'd extend the state diagram first."* |
| "Multiple locker stations as one network"       | **Facade (#8)**              | *"`LockerNetwork` is a Facade over `Map<stationId, AmazonLocker>` — routes by stationId and hides the topology from callers."* |
| "Build a locker with many configuration knobs"  | **Builder (#5)**             | *"`AmazonLockerBuilder` collects compartments + TTL + clock + random incrementally; `build()` validates and constructs. Cleaner than a 5-arg constructor."* |
| "Locker shared across services / 'only one'"    | **Singleton (#6)** — refuse | *"I'd avoid Singleton — it kills tests. DI a single instance from the composition root instead."* |
| "Support different locker types via one create flow" | **Factory (#4)**        | *"`LockerFactory.create(LockerKind)` returns the right Locker subtype. Callers stop depending on concrete types."* |

> **For "two-phase deposit (reserve / confirm)":** HI's canonical 8 does NOT include Command. So **describe the technique without leading with the pattern name**: *"I'd add a `reserve(size) → reservationId` step that marks OCCUPIED with a short timeout; a background sweeper releases unreserved compartments; `confirm(reservationId)` mints the token."* If the interviewer asks for the pattern name, *then* mention Command — but it's not in the HI cheat sheet.

> **For "persist across restart":** Same — Repository isn't in HI's canonical 8 (HI considers it more of an architectural pattern than a GoF). Lead with the technique: *"Inject a storage interface that the Locker writes to on every state change and reads on boot. In-memory impl for tests."* Name "Repository" only if the interviewer specifically prompts the term.

**Patterns to actively refuse if the interviewer baits you (Amazon traps):**

- **Singleton on AmazonLocker just because "there's one locker"** — breaks tests, blocks multi-station extension. Refuse as in the table above. *HI explicitly warns: "passing shared objects through constructors is clearer and more testable. Singletons hide dependencies."*
- **Builder for the 1-arg `AmazonLocker(compartments)`** — academic noise. HI warns: most interview problems have simple constructors that suffice.
- **Factory for `Size` / `CompartmentStatus`** — they're enums. Nothing to factory.
- **Decorator on AccessToken** — token is immutable *data*, not a behavior chain.
- **Full State pattern on Compartment today** — 3 states, no per-state behavior beyond the enum. Promote only if the interviewer adds states that own behavior.

---

## Interview deep-dives — the questions you'll definitely get asked

This section covers the five follow-up questions almost every SDE‑2 LLD round asks once Steps 1–5 are done. Amazon bar-raisers especially love #1 (complexity), #2 (concurrency), and #3 (testing).

### 1. Complexity (Big-O) — have this table mentally ready

Let `C` = #compartments, `T` = #live tokens (outstanding deposits).

| Operation                        | Time                          | Space   | Notes                                                                              |
| -------------------------------- | ----------------------------- | ------- | ---------------------------------------------------------------------------------- |
| `depositPackage(size)`           | `O(C)` scan + `O(1)` map insert = **`O(C)`** | O(1) | Linear scan to find first AVAILABLE compartment of that size                       |
| `pickup(code)`                   | **`O(1)`**                    | O(1)    | Map lookup; token holds compartment ref directly — no scan                         |
| `openExpiredCompartments()`      | **`O(T)`**                    | O(1)    | Iterator-based map walk; remove-while-iterating to avoid `ConcurrentModification`  |
| `findAvailable(size)` (internal) | `O(C)`                        | O(1)    | Linear scan                                                                        |
| `generateUniqueCode()` (internal)| `O(retries)` ≤ `O(10)` ≈ **O(1)** | O(1) | Bounded retries on collision against the live map                                  |
| Storage — compartment list       | —                             | **O(C)**| Fixed at construction                                                              |
| Storage — token map              | —                             | **O(T)**| Bounded by `C` (one token per occupied compartment)                                |

> **Senior callout:** *"deposit is O(C), pickup is O(1) — and pickup is the hot path because customers pick up far more often than drivers deposit. If C grows large, I'd maintain `Map<Size, Queue<Compartment>>` of free compartments so deposit also becomes O(1) — at the cost of one extra invariant to keep in sync."*

### 2. Concurrency / thread-safety — the deposit race

**The race:** Two drivers depositing simultaneously can both pass `findAvailable` before either calls `markOccupied`. Same compartment handed to both → second driver's deposit silently overwrites the first.

**Simplest correct fix (interview-ready):** synchronize the three public methods. Operations are short (no I/O on the hot path), so contention is low.

```java
public class AmazonLocker {
    public synchronized String depositPackage(Size size)  { /* ... */ }
    public synchronized void   pickup(String tokenCode)   { /* ... */ }
    public synchronized void   openExpiredCompartments()  { /* ... */ }
}
```

**Higher-throughput option (mention only if pushed):** push the lock down to per-compartment with an atomic status field:

```java
// In Compartment:
private final AtomicReference<CompartmentStatus> status =
        new AtomicReference<>(CompartmentStatus.AVAILABLE);

public boolean tryClaim() {
    return status.compareAndSet(CompartmentStatus.AVAILABLE, CompartmentStatus.OCCUPIED);
}

// In Locker.findAvailable: replace `c.isAvailable()` check with `c.tryClaim()`.
// Compartment is only "found" if we successfully claimed it atomically.
```

> **Senior callout:** *"The race is on `findAvailable → markOccupied`. Method-level `synchronized` on Locker is the right default — operations are short, contention is low, no I/O on the hot path. For multi-region scale I'd push it to a per-compartment compare-and-set on status, but that's optimization."*

### 3. Testing — what to write tests for

The injected `Clock` and `Random` are the entire reason this design is testable. Show your interviewer the test harness from `LockerDriver`'s `MutableClock` — that's how you control time deterministically.

| Test category        | Cases to cover                                                                                              |
| -------------------- | ----------------------------------------------------------------------------------------------------------- |
| Happy path           | Deposit SMALL → code returned → pickup with code → compartment AVAILABLE again, map empty                  |
| Capacity             | Deposit until size exhausted; next deposit of that size throws `NoSuchElementException`                    |
| Invalid code         | `null` → IllegalArgument; empty → IllegalArgument; unknown → NoSuchElement (three **distinct** exceptions) |
| Expiration boundary  | At `expiresAt − 1ms` → pickup OK. At `expiresAt` exactly → IllegalState (the `>=` semantic)                |
| Reclaim cleanup      | Deposit, advance 8 days, reclaim. Compartment AVAILABLE? Yes. Token still in map? **No**. (The #1 bug here.) |
| Code uniqueness      | Seed `Random` to force a collision against an existing live token → `generateUniqueCode` retries           |

```java
@Test
void pickupAtExactExpirationIsRejected() {
    MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    AmazonLocker locker = new AmazonLocker(
            List.of(new Compartment("S1", Size.SMALL)), clock, new Random(0));

    String code = locker.depositPackage(Size.SMALL);
    clock.advanceDays(7);                 // exactly at expiresAt
    assertThrows(IllegalStateException.class, () -> locker.pickup(code));
}

@Test
void reclaimFreesCompartmentAndDropsToken() {
    MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    Compartment c = new Compartment("S1", Size.SMALL);
    AmazonLocker locker = new AmazonLocker(List.of(c), clock, new Random(0));

    String oldCode = locker.depositPackage(Size.SMALL);
    clock.advanceDays(8);
    locker.openExpiredCompartments();

    assertTrue(c.isAvailable());                                       // freed
    assertThrows(NoSuchElementException.class,                          // token gone
            () -> locker.pickup(oldCode));
    assertNotNull(locker.depositPackage(Size.SMALL));                   // can re-use
}

@Test
void distinctErrorsForInvalidCodes() {
    AmazonLocker locker = new AmazonLocker(List.of(), Clock.systemUTC(), new Random());
    assertThrows(IllegalArgumentException.class, () -> locker.pickup(null));
    assertThrows(IllegalArgumentException.class, () -> locker.pickup(""));
    assertThrows(NoSuchElementException.class,   () -> locker.pickup("999999"));
}
```

> **Senior callout:** *"The reason these tests don't need any mocking framework is that Clock and Random are interfaces injected via the constructor. A fake clock is one class; a seeded `Random` is one line. No `Mockito.when(...)` anywhere."*

### 4. SOLID mapping (mention by letter when asked)

| Letter                       | Where it shows up                                                                                          |
| ---------------------------- | ---------------------------------------------------------------------------------------------------------- |
| **S** Single Responsibility  | Locker = workflow + allocation + token lifecycle. Compartment = physical-slot state. AccessToken = bearer credential. Three reasons to change → three classes. |
| **O** Open/Closed            | `findAvailable` is closed for modification but open for extension via an injected `AllocationStrategy`. `CompartmentStatus` enum already absorbs `OUT_OF_SERVICE` without changing existing call sites. |
| **L** Liskov Substitution    | Compartments are uniform — any AVAILABLE one of the right size substitutes for any other. If `LockHardware` becomes an interface, `RealLockHardware` and `FakeLockHardware` substitute cleanly. |
| **I** Interface Segregation  | Compartment exposes 5 focused methods (`isAvailable`, `markOccupied`, `markFree`, `markOutOfService`, `open`) instead of one fat `setStatus`. AccessToken exposes 4 focused getters + `isExpired`. |
| **D** Dependency Inversion   | Locker depends on `Clock` and `Random` (abstractions) injected via the constructor — never on `Instant.now()` or `Math.random()`. Locker also depends on Compartment's *interface* (its methods), not its `status` field. |

### 5. "Summarize your design in 30 seconds" — memorize this script

> *"Three classes: Locker, Compartment, AccessToken. Locker is the orchestrator and effectively a facade — it owns a `List<Compartment>` for size-based allocation and a `Map<code, AccessToken>` for O(1) pickup. Compartment owns its status as a `CompartmentStatus` enum — `AVAILABLE / OCCUPIED / OUT_OF_SERVICE` — which makes maintenance a one-line extension. AccessToken is the immutable bearer credential — code, expiresAt, and a reference to its compartment so pickup doesn't need to scan. Clock and Random are injected, which makes the 7-day expiration and the collision-checked code generation fully testable. The main correctness invariant is that `openExpiredCompartments` mirrors pickup's cleanup — it frees the compartment AND drops the token — otherwise the map leaks. Extensions: Strategy for allocation rules, Observer for notifications, full State Machine if compartments grow per-state behavior."*

That's ~35 seconds. Hits: structure, the two data structures + their complexities, the testability story, the senior trap (`openExpiredCompartments` cleanup), and extensibility — in one breath.

---

## Closing soundbites (memorize these)

- **Opening:** *"Before I design, let me clarify scope and rules."*
- **Why no Package class:** *"Package has no state we'd track and no rules to enforce — size becomes an argument, not its own class."*
- **Moving to entities:** *"Core entities: Locker, Compartment, AccessToken. Locker is the orchestrator. Token is the bearer credential."*
- **Defending information expert:** *"Compartment owns `status`, so `isAvailable()` lives there. Token owns `expiresAt`, so `isExpired()` lives there. Locker just asks."*
- **Before coding:** *"Real Java or pseudo-code? I'll walk through deposit, pickup, then expiration."*
- **On reclaim cleanup:** *"Staff reclaim is the deferred completion of a pickup that never happened — so it has to also markFree and remove the token, otherwise we leak."*
- **On testability:** *"`Clock` is injected so I can test the 7-day boundary without sleeping. `Random` is injected so codes are reproducible in tests."*
- **On extensibility:** *"`CompartmentStatus` as an enum (not a bool) is what makes OUT_OF_SERVICE a one-line addition later."*

---

## Top mistakes that lose points

- **Adding a `Package` class** with no state or rules — pure noise.
- **Adding `Customer` / `Driver`** classes — they're actors, not entities.
- Using `boolean occupied` instead of an enum — paints you into a corner when maintenance comes up.
- Calling `Instant.now()` inside the methods — un-testable.
- `openExpiredCompartments` that opens but doesn't `markFree` or remove tokens — the map leaks, compartments look occupied forever. **This is the #1 senior trap on this question.**
- Generating codes without collision-checking the live map.
- Returning the `AccessToken` object to the driver instead of just the code string — leaks internal type and lets the driver bypass the API.
- Letting `Locker` reach into `Compartment.status` directly to set values — tell, don't ask.
- Scanning `compartments` on pickup to find the compartment — token already references it. O(1) > O(n).
- Skipping the dry run.
- Pattern-stuffing (Singleton on Locker, Factory for 3 sizes, Builder for 2-arg ctors).

---

## Files in this folder (your reference implementation)

| File                                   | What it shows                                                              |
| -------------------------------------- | -------------------------------------------------------------------------- |
| `model/Size.java`                      | Enum — SMALL / MEDIUM / LARGE                                              |
| `model/CompartmentStatus.java`         | Enum — AVAILABLE / OCCUPIED / OUT_OF_SERVICE                               |
| `model/Compartment.java`               | Physical slot — owns id, size, status; `open()` for hardware unlock        |
| `model/AccessToken.java`               | Immutable bearer credential — code, expiresAt, compartment ref             |
| `AmazonLocker.java`                    | Orchestrator — deposit, pickup, openExpired; injected Clock + Random       |
| `LockerDriver.java`                    | Scenario harness — happy path, wrong code, full size, expiration + reclaim |

Run from the project root:

```bash
mvn -q compile exec:java \
  -Dexec.mainClass=com.conceptcoding.interviewquestions.amazonlocker.LockerDriver
```
