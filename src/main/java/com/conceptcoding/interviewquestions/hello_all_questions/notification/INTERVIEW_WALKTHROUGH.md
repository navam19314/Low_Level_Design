# Notification System — 45-min LLD Interview Walkthrough

**Target role:** SDE-2 (Amazon, Adobe, Microsoft, Atlassian)

> The canonical **Strategy + failure-isolation** problem, trimmed to fit a 45-min live-coding round. Two signals separate senior from mid: (1) **Strategy** per channel — one sender per `NotificationChannel`; (2) **failure isolation** — a throwing sender can't take down the other channels for the same notification. Observer (pluggable listeners), retries, async dispatch, and rate limiting are all **Step-5 talking points**, not code you write in the base 45 min — see the Step 5 section for why and how each would bolt on.

---

## Time budget

| Step | Activity | Budget | Cumulative |
|------|----------|--------|------------|
| 1 | Requirements | ~5 min | 5 |
| 2 | Entities & relationships | ~4 min | 9 |
| 3 | Class design | ~10 min | 19 |
| 4 | Implementation + dry-run | ~17 min | 36 |
| 5 | Extensibility | ~8 min | 44 |
| — | Wrap | ~1 min | 45 |

Step 4 is the longest — the failure-isolation pattern lives there.

---

## Mental models — memorize before you walk in

### M1. Fan-out flow — one notification, N channels, N results

```
   svc.send(notification)
        │
        v
   channels = preferences[recipientId]  ?? all configured channels
        │
        v
   for each channel:
        │
        v
   +------------------------------------------+
   | deliverTo(notification, channel)         |
   |   sender = sendersByChannel[channel]     |
   |   try   { sender.send(n); → SENT }        |
   |   catch { → FAILED(msg) }                 |  ← failure isolation (sender throw caught)
   +------------------------------------------+

   Returns: List<DeliveryResult> — one per attempted channel, successes AND failures.
```

**Senior soundbite:** *"A throwing sender becomes a FAILED result — it never crashes the other channels for the same notification, and the caller always gets N results back for N attempted channels."*

### M2. Strategy registry — `EnumMap<NotificationChannel, NotificationSender>`

```
   ctor gets:  List<NotificationSender> senders
        │  build once
        v
   for (s in senders) map.put(s.channel(), s)     ← keyed by the channel each sender declares
        │
        v
   runtime lookup:  sendersByChannel.get(channel)  ← O(1), no scan

   Why EnumMap? O(1) via the enum's ordinal as an array index — no hashing, cache-friendly,
   the idiomatic Map<EnumType, T>. Built once, never mutated → safe for concurrent reads.
```

---

## Step 1 — Requirements (~5 min)

### Clarifying dialogue

**You:** *"Multiple channels — email, SMS, push? Does a user pick which channels they want?"*
**Interviewer:** *"Yes, several channels, and users have per-channel preferences."*
> Signals Strategy per channel + a preferences map.

**You:** *"When I send, does it fan out to all the user's channels? And what does `send` return?"*
**Interviewer:** *"Fan out to their channels. Return the outcome per channel — successes and failures."*
> Signals `List<DeliveryResult>`, one per attempted channel.

**You:** *"If one channel's sender fails — say Twilio is down — do the other channels still go out?"*
**Interviewer:** *"Yes. One bad channel must not block the others."*
> This is the load-bearing requirement: failure isolation.

**You:** *"Out of scope for v1 — concurrency hardening, real SMTP/Twilio integration, retries, async dispatch, rate limiting, pluggable observers, templates, persistence?"*
**Interviewer:** *"Correct — those are follow-ups."*
> Say this explicitly, even if the interviewer doesn't ask. Naming thread-safety/Observer/retry/async as OUT of scope up front is what buys you the time to actually finish the base implementation. If they push on concurrency, it's a one-line swap (`HashMap` → `ConcurrentHashMap`) — see Step 5.

### Requirements to write down

```
IN SCOPE
1. One Sender per channel (EMAIL, SMS, PUSH); send() fans out to the recipient's
   preferred channels, or ALL configured channels if none are set.
2. Returns one DeliveryResult (SENT/FAILED) per attempted channel — a sender's
   throw becomes a FAILED result for THAT channel only; the others still deliver.
3. Per-user channel preferences (opt-in subset).

OUT OF SCOPE (all Step-5 talking points — name them, don't code them unless asked)
- Thread-safety (ConcurrentHashMap swap)
- Pluggable observers on delivery outcomes (Observer)
- Retries with backoff (Decorator)
- Async dispatch / queue-per-channel
- Rate limiting per channel (Decorator)
- Template rendering / localization
- Persistence of history
- Multi-tenant / per-customer SLAs
```

---

## Step 2 — Entities & relationships (~4 min)

```
Entities
- NotificationService   orchestrator + facade — send, setPreferences
- Notification          immutable payload — id, recipientId, subject, body
- DeliveryResult        per-channel outcome — SENT or FAILED, .sent()/.failed() factories
- NotificationSender    Strategy interface — channel() + send(notification)

Enums
- NotificationChannel   { EMAIL, SMS, PUSH, SLACK }
- DeliveryStatus        { SENT, FAILED }

NOT entities
- User                  preferences keyed by userId string — no user-level behavior
- Template              rendering is sender-side for v1 (Step 5)
- Queue                 synchronous v1; async is Step 5
- NotificationListener  Observer is a Step-5 extension, not base scope (see Step 5)

Relationships
- NotificationService owns:
    EnumMap<Channel, Sender>   sendersByChannel   (built once, never mutated)
    HashMap<userId, Set<Chan>> preferences        (per-user opt-in; ConcurrentHashMap is Step 5)
- send returns List<DeliveryResult> — one per attempted channel
```

### Why no `User` class?

> *"Preferences are just `userId → Set<Channel>`. No user-level behavior to model — identity is an opaque string. If preferences grow rules (DND hours, escalation policy), a User entity earns its place — Step 5."*

### Why is Notification immutable?

> *"The same Notification goes to N channels. If it were mutable, one channel could rewrite the subject before the next reads it — a data race. Immutable closes that off."*

### Class diagram

```
                  +----------------------------------------+
                  |          NotificationService           |   ← orchestrator + facade
                  |  send → List<DeliveryResult>           |
                  |  setPreferences                        |
                  +----------------------------------------+
                       │                          │
                  owns │                          │
                       v                          v
       EnumMap<Channel,Sender>            HashMap
       sendersByChannel                   <userId,Set<Chan>>
       (built once)                       preferences
                       │
                       v
       +----------------------+
       | <<interface>>        |
       | NotificationSender   |
       | + channel()          |
       | + send(notification) |
       +----------------------+
         EmailSender / SmsSender
         / PushSender
```

---

## Step 3 — Class design (~10 min)

### NotificationService — state derived from requirements

| Requirement | State |
|-------------|-------|
| Multiple channels | `Map<NotificationChannel, NotificationSender> sendersByChannel` (EnumMap) |
| Per-user preferences | `Map<String, Set<NotificationChannel>> preferences` (plain HashMap; ConcurrentHashMap is Step 5) |

### NotificationService — public API

```java
public class NotificationService {
    private final Map<NotificationChannel, NotificationSender> sendersByChannel;
    private final Map<String, Set<NotificationChannel>>        preferences = new HashMap<>();

    public NotificationService(List<NotificationSender> senders) {
        EnumMap<NotificationChannel, NotificationSender> map = new EnumMap<>(NotificationChannel.class);
        for (NotificationSender s : senders) map.put(s.channel(), s);
        this.sendersByChannel = map;   // never mutated after ctor → safe concurrent reads
    }

    public List<DeliveryResult> send(Notification notification);        // Step 4
    public void setPreferences(String userId, Set<NotificationChannel> channels);
}
```

### Strategy interface

```java
public interface NotificationSender {
    NotificationChannel channel();
    void send(Notification notification);       // MAY throw — service catches
}
```

### The immutable boundary types

```java
public class Notification {                     // id, recipientId, subject, body — all final
    // ctor validates non-null; getters only
}

public class DeliveryResult {                   // notificationId, channel, status, errorMessage, attemptedAt
    public static DeliveryResult sent(String id, NotificationChannel ch);
    public static DeliveryResult failed(String id, NotificationChannel ch, String msg);
    public boolean isSent();
}
```

### The principle to say aloud

> *"Strategy lets each Sender own its vendor integration (SMTP, Twilio, FCM) — the service doesn't know what's inside. I'm keeping the base to just Strategy + failure isolation; Observer for pluggable outcome-handling is a Step-5 extension I'll add if time allows."*

---

## Step 4 — Implementation + dry-run (~17 min)

### 4.1 `send` — fan-out with failure isolation (write this FIRST)

```java
public List<DeliveryResult> send(Notification notification) {
    Set<NotificationChannel> channels = preferences.getOrDefault(
            notification.getRecipientId(), sendersByChannel.keySet());

    List<DeliveryResult> results = new ArrayList<>(channels.size());
    for (NotificationChannel channel : channels) {
        results.add(deliverTo(notification, channel));
    }
    return results;
}

// One channel's throw becomes a FAILED result, never escapes.
private DeliveryResult deliverTo(Notification notification, NotificationChannel channel) {
    NotificationSender sender = sendersByChannel.get(channel);
    if (sender == null) {
        return DeliveryResult.failed(notification.getId(), channel, "no sender for " + channel);
    }
    try {
        sender.send(notification);
        return DeliveryResult.sent(notification.getId(), channel);
    } catch (Exception e) {
        return DeliveryResult.failed(notification.getId(), channel, e.getMessage());
    }
}
```

**Three callouts to deliver out loud:**

1. *"`getOrDefault(recipientId, sendersByChannel.keySet())` — a user who hasn't set preferences defaults to ALL configured channels."*

2. *"I catch `Exception`, not `Throwable` — I do NOT want to swallow `Error`s like OutOfMemory. Failure isolation is for vendor/business exceptions, not JVM-fatal ones."*

3. *"Preferences is a plain `HashMap` — this base build assumes single-threaded/uncontended access. If concurrent access is required, it's a one-line swap to `ConcurrentHashMap`, which I'd do as a Step-5 extension rather than build it in up front."*

### 4.2 Preferences

```java
public void setPreferences(String userId, Set<NotificationChannel> channels) {
    preferences.put(userId, Set.copyOf(channels));   // defensive immutable copy
}
```

### 4.3 Concrete senders — adapters around vendor SDKs

```java
public class EmailSender implements NotificationSender {
    public NotificationChannel channel() { return NotificationChannel.EMAIL; }
    public void send(Notification n)     { /* SMTP / SES / SendGrid */ }
}
public class SmsSender implements NotificationSender {
    public NotificationChannel channel() { return NotificationChannel.SMS; }
    public void send(Notification n)     { /* Twilio — truncate body to 160 chars */ }
}
```

> *"Each sender is essentially an Adapter around a vendor SDK. The Strategy interface is the seam — the service doesn't know whether EmailSender is SMTP or SES. Swap at composition time without touching the service."*

### 4.4 Dry-run — sender-failure isolation (say this at the board)

```
Setup: EmailSender (OK), SmsSender that throws "rate-limit", PushSender (OK).
       No preferences for user-C → defaults to all 3 channels.

send(Notification("user-C", "Alert", ...)):
   channels = {EMAIL, SMS, PUSH}
   deliverTo(EMAIL): email OK           → SENT(EMAIL)                    ✓
   deliverTo(SMS):   sms throws         → catch → FAILED(SMS, "rate-limit")  ✓
   deliverTo(PUSH):  push OK            → SENT(PUSH)   ← NOT skipped!    ✓

Returns: [SENT(EMAIL), FAILED(SMS), SENT(PUSH)]
```

---

## Step 5 — Extensibility (~8 min)

### E0a. "Thread-safety — can multiple threads call `send` / `setPreferences` concurrently?"

**Fix:** Swap `preferences` from `HashMap` to `ConcurrentHashMap` — one line, no other change. `sendersByChannel` is already safe: it's an `EnumMap` built once in the constructor and never mutated after, so concurrent reads need no lock. No `synchronized` needed anywhere — each collection owns its own concurrency.

```
Empirical proof (only write this if asked): 50 threads × 1 notification × 3 channels =
150 deliveries, released via a CountDownLatch. Assert total results == 150.
```

### E0b. "Pluggable observers on delivery outcomes (audit, metrics, retry triggers)"

**Fix:** Observer. A `NotificationListener` interface (`onDelivered` / `onFailed`) held in a `CopyOnWriteArrayList`, fired after each `deliverTo` call — service doesn't block on listeners. Wrap the listener loop in its own try-catch so one bad listener can't break the others or the caller. This is the pattern the base build deliberately skips to save ~8-10 min of coding time; name it here instead.

```java
public interface NotificationListener {
    void onDelivered(DeliveryResult result);
    void onFailed(DeliveryResult result);
}
// service: List<NotificationListener> listeners = new CopyOnWriteArrayList<>();
// after deliverTo(...): for (listener : listeners) { try { ... } catch (Exception e) { log; continue; } }
```

### E1. "Retries with exponential backoff for transient failures"

**Problem:** A 503 from Twilio is treated as definitive FAILED. Transient errors should be retried.

**Fix:** Decorator. Wrap each Sender — the service never knows.

```java
public class RetryingSender implements NotificationSender {
    private final NotificationSender delegate;
    private final int maxAttempts;

    public NotificationChannel channel() { return delegate.channel(); }

    public void send(Notification n) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try { delegate.send(n); return; }
            catch (RuntimeException e) {
                last = e;
                if (attempt < maxAttempts) sleepExponential(attempt);   // + jitter
            }
        }
        throw last;   // exhausted → service records FAILED
    }
}
```

### E2. "Async dispatch — `send` shouldn't block the caller"

**Problem:** `send` blocks the caller through N synchronous vendor calls — tail-latency hostile for an API handler.

**Fix:** Queue-per-channel + a worker thread (same shape as the Logger async section). `send` enqueues and returns; workers drain. Bounded queue with an explicit overflow policy (drop-newest is usually right — losing a notification beats blocking a request).

### E3. "Rate limiting per channel (Twilio charges per SMS)"

**Fix:** Decorator again — `RateLimitedSender(delegate, ratePerSecond)` with a token bucket (same algorithm as the Rate Limiter problem). Composes with retry: `RetryingSender(RateLimitedSender(new SmsSender()))`.

### E4. "Template rendering — per-channel format"

**Fix:** A `TemplateRenderer` Strategy. Notification carries a `templateKey` instead of raw subject/body; each sender renders its (channel, locale)-specific template. Templating is a separate concern from sending.

### E5. Other one-liners

| Follow-up | Answer |
|-----------|--------|
| "Audit log of every delivery" | Add the Observer extension (E0) — `AuditListener`. That's exactly what Observer is for. |
| "Metrics per channel" | Same — `MetricsListener` increments counters per channel + outcome. |
| "De-duplication" | Idempotency key on Notification + a seen-recently cache (`computeIfAbsent`). |
| "Quiet hours / DND" | A `UserDndSchedule`; filter channels by local time before fan-out. |
| "Webhook delivery to merchants" | Another sender — `WebhookSender` POSTs to a configured URL. Same Strategy. |
| "Multi-tenant, different vendors per tenant" | `Map<TenantId, NotificationService>`; route at the boundary. |

---

## Design patterns in play (name these out loud in the interview)

### In the BASE design — mention in Step 2 or Step 3

| Pattern / Principle | Where it lives | One-line justification |
|---------------------|----------------|------------------------|
| **Strategy** ⭐ | `NotificationSender` interface + one impl per channel | *"Requirements enumerate channels — I need one impl per channel on day 1. Each owns its own vendor integration."* |
| **Facade** | `NotificationService` | *"Application code only touches the service — senders and preferences are hidden behind `send`."* |
| **Immutability** | `Notification`, `DeliveryResult` | *"The same Notification hits N channels concurrently — mutability would be a data race."* |

### Patterns for Step 5 extensibility

| Follow-up trigger | Pattern | The one-line move |
|-------------------|---------|-------------------|
| "Pluggable observers (audit, metrics, retry-trigger)" | **Observer** ⭐ | *"`NotificationListener` interface fired after each `deliverTo`, held in a `CopyOnWriteArrayList`, its own try-catch layer. Service is oblivious to who's listening."* |
| "Retries / rate limiting / logging on a channel" | **Decorator** ⭐ | *"Wrap the Sender — `RetryingSender`, `RateLimitedSender`. Composable: retry-over-rate-limit-over-vendor. Service is oblivious."* |
| "New channel (Slack, WhatsApp, webhook)" | **Strategy (extend)** ⭐ | *"New Sender class + register at ctor. Zero changes to the service or other senders."* |
| "Async dispatch" | **Producer–Consumer** | *"Per-channel bounded queue + worker thread. Same pattern as the Logger async extension."* |
| "Per-channel templates" | **Strategy** | *"`TemplateRenderer` per channel; render before sending."* |
| "De-dup" | Idempotency cache | *"Key on Notification + `computeIfAbsent` — same trick as PaymentGateway."* |

### Patterns to actively refuse

- **Singleton on NotificationService** — kills tests; DI a single instance.
- **State pattern on DeliveryStatus** — two values, no per-state behavior; enum is right.
- **Chain of Responsibility for fan-out** — wrong shape. CoR stops at the first handler; we want EVERY channel to run.
- **Builder for the 1-arg `NotificationService(senders)` ctor** — academic noise.

### The rule to sound natural

1. **Strategy is non-negotiable in the base** — the requirements mandate one impl per channel.
2. **Cap the base at Strategy + failure isolation** (plus Facade as a principle). Observer, Decorator, async all land in Step 5 — say them, don't code them, unless time remains.
3. **Pair each pattern with a concrete win.** *"Observer because outcome handling needs to stay pluggable"* > *"I'd use Observer."*

---

## What is expected at each level

### Junior (SDE-1)
- Reaches a Sender interface with a nudge; may hardcode an if/else on channel instead of a map.
- Implements happy-path fan-out; forgets the try-catch, so one sender's throw aborts the whole send.
- No concurrency discussion unless prompted.

### Mid-level (SDE-2) — the target
- Strategy per channel via an EnumMap — named and justified from requirements.
- Failure isolation present (try-catch around each sender call, one FAILED result doesn't abort the rest).
- `send` returns one result per channel; multi-channel is all-attempted, not fail-fast.
- Immutable Notification; defensive copies at boundaries.
- Runs the sender-isolation dry-run out loud.
- Names Observer/Decorator/async as Step-5 extensions without spending base-build time coding them.

### Senior (SDE-3 / SDE-II)
- Everything mid-level, faster, with proactive tradeoffs.
- Catches `Exception`, not `Throwable`, and can explain why (don't swallow JVM Errors).
- Names the `HashMap` → `ConcurrentHashMap` swap, Decorator for retries/rate-limiting, Observer for pluggable outcomes, and async-queue for tail latency before being asked — and codes whichever one the interviewer picks if time remains.
- Describes the 50-thread `CountDownLatch` burst test as the empirical concurrency proof, if thread-safety comes up.

---

## Interview deep-dives

### Complexity

Let `C` = channels in the recipient's preferences.

| Operation | Time | Notes |
|-----------|------|-------|
| `send` | **O(C)** + sender I/O | C sender calls; vendor I/O dominates |
| `deliverTo` (per channel) | **O(1)** lookup + sender cost | EnumMap.get is an array index |
| `setPreferences` | **O(1)** amortized | HashMap put |

> **Senior callout:** *"The vendor I/O (10s–100s of ms) dominates — the map lookup is noise. If a listener fan-out (Step 5) grows huge, the fix is async dispatch, not a faster data structure."*

### Concurrency — the Step-5 swap

| Structure | Base build | If asked about concurrency |
|-----------|-----------|------------------------------|
| Senders | Immutable EnumMap | No change — built at ctor, never mutated → already safe for concurrent reads |
| Preferences | Plain HashMap | Swap to `ConcurrentHashMap` — one line |

> *"I'd rather ship a correct single-threaded design in the time I have than half-build thread-safety. The swap is one line because `sendersByChannel` was already immutable and `preferences` was already isolated behind `setPreferences`/`send` — no `synchronized` needed either way."*

### The concurrent-burst test (Step 5 — only if thread-safety comes up)

```java
@Test
void fifty_threads_each_fan_out_to_three_channels() throws Exception {
    NotificationService svc = new NotificationService(
        List.of(new EmailSender(), new SmsSender(), new PushSender()));

    int threads = 50;
    CountDownLatch fire = new CountDownLatch(1);
    AtomicInteger total = new AtomicInteger();
    // ... submit N tasks that await(fire) then svc.send(...) and add results.size()
    fire.countDown();
    // ... awaitTermination ...

    assertEquals(150, total.get());               // 50 × 3 channels
}
```

---

## 30-second summary (memorize for closing)

> *"NotificationService is the orchestrator + facade with one public method, `send`. Strategy in the base — one Sender per channel, routed via an immutable EnumMap. Failure isolation — a try-catch around each sender call means one channel's throw becomes a FAILED result while the others still deliver. I catch `Exception`, not `Throwable` — I don't want to swallow JVM Errors like OOM. Per-user preferences in a plain HashMap, defaulting to all channels when unset. Extensions I'd add next, in priority order: swap HashMap for ConcurrentHashMap if concurrent access is needed, Observer for pluggable outcome listeners, Decorator for retries and rate-limiting, per-channel async queues for tail latency, a TemplateRenderer Strategy for formatting."*

---

## Top mistakes that lose points

- **No try-catch around the sender** — one channel's exception crashes the whole send; caller gets zero deliveries instead of N-1. The single biggest flunk.
- **Catching `Throwable`** — swallows `OutOfMemoryError` / `StackOverflowError`. Catch `Exception`; isolation is for vendor/business errors, not JVM-fatal ones.
- **Mutable Notification** — same payload to N channels; one sender mutates the body → others see the mutation. Make it immutable.
- **`HashMap` for senders** — works, but `EnumMap` is idiomatic and faster.
- **Chain-of-Responsibility for fan-out** — CoR stops at the first handler; you want every channel attempted.
- **Building thread-safety/Observer/retry/async into the base build** — burns the time budget before `send` even compiles. Name them as Step-5 extensions; code them only if asked.

---

## Files in this folder

| File | Purpose |
|------|---------|
| `model/NotificationChannel.java` | Enum — EMAIL / SMS / PUSH / SLACK |
| `model/DeliveryStatus.java` | Enum — SENT / FAILED |
| `model/Notification.java` | Immutable payload — id, recipientId, subject, body |
| `model/DeliveryResult.java` | Immutable per-channel outcome + `sent()` / `failed()` factories |
| `sender/NotificationSender.java` | Strategy interface — `channel()` + `send()` |
| `sender/EmailSender.java` · `SmsSender.java` · `PushSender.java` | Concrete senders (simulated vendor calls) |
| `NotificationService.java` | **The hot class** — EnumMap routing + single try-catch isolation layer |
| `NotificationServiceDriver.java` | 3 scenarios — fan-out / preferences / sender isolation |

Thread-safety (`ConcurrentHashMap` + the 50-thread burst test), Observer (`NotificationListener`), retry (`RetryingSender`), async dispatch, and rate limiting are intentionally **not** files in this folder — they're Step-5 talking points above. Add them as new files only if you're practicing the extended round.

Run:
```bash
mvn -q compile exec:java \
  -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.notification.NotificationServiceDriver
```
