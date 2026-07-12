# Notification System — 45-min LLD Interview Walkthrough

**Target role:** SDE-2 (Amazon, Adobe, Microsoft, Atlassian)

> The canonical **Strategy + Observer + failure-isolation** problem. Three signals separate senior from mid: (1) **Strategy** per channel — one sender per `NotificationChannel`; (2) **Observer** on outcomes — pluggable listeners get every delivery result; (3) **failure isolation at TWO layers** — a throwing sender can't take down the other channels, and a throwing listener can't take down the other listeners.

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
   |   catch { → FAILED(msg) }                 |  ← isolation layer 1 (sender throw caught)
   +------------------------------------------+
        │
        v
   +------------------------------------------+
   | fireListeners(result)                    |
   |   for each listener:                     |
   |     try   { onDelivered / onFailed }      |
   |     catch { log; continue }               |  ← isolation layer 2 (listener throw caught)
   +------------------------------------------+

   Returns: List<DeliveryResult> — one per attempted channel, successes AND failures.
```

**Senior soundbite:** *"Failure isolation lives at TWO layers. A throwing sender becomes a FAILED result — never crashes the other channels for the same notification. A throwing listener is logged-and-skipped — never crashes the other listeners or the caller."*

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

### M3. Observer registry — `CopyOnWriteArrayList<NotificationListener>`

```
   Fan-out (iterate listeners) is the HOT path; add/remove is rare.

     ArrayList              → ConcurrentModificationException if add during a send
     synchronized(list)     → serializes every send
     CopyOnWriteArrayList   → iterate a snapshot, lock-free reads ⭐

   Cost: O(L) array copy on add/remove.  Win: zero contention on send.
   Right tradeoff when listener config is set at startup and rarely changes.
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
> This is the load-bearing requirement: failure isolation layer 1.

**You:** *"Do we notify anyone of outcomes — analytics, audit, retry triggers? Should those be pluggable?"*
**Interviewer:** *"Yes, pluggable observers on delivery outcomes."*
> Signals Observer + failure isolation layer 2 (a bad listener can't break the others).

**You:** *"Concurrency — can multiple threads call `send` at once?"*
**Interviewer:** *"Assume yes, it's a shared service."*
> Signals thread-safe collections, no outer lock.

**You:** *"Out of scope for v1 — real SMTP/Twilio integration, retries, async dispatch, rate limiting, templates, persistence?"*
**Interviewer:** *"Correct — those are follow-ups."*

### Requirements to write down

```
IN SCOPE
1. N channels: EMAIL, SMS, PUSH (SLACK as a Step-5 example).
2. send(notification) fans out to the recipient's preferred channels
   (or ALL configured channels if no preferences set).
3. Returns one DeliveryResult per attempted channel — successes AND failures.
4. Pluggable observers on outcomes — addListener / removeListener.
5. Failure isolation:
   (a) sender throws → FAILED result for THAT channel only; others still deliver.
   (b) listener throws → logged, the next listener still fires.
6. Per-user channel preferences (opt-in subset).
7. Thread-safe — concurrent sends, concurrent listener add/remove.

OUT OF SCOPE (all Step-5 extensions)
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
- NotificationService   orchestrator + facade — send, addListener, setPreferences
- Notification          immutable payload — id, recipientId, subject, body
- DeliveryResult        per-channel outcome — SENT or FAILED, .sent()/.failed() factories
- NotificationSender    Strategy interface — channel() + send(notification)
- NotificationListener  Observer interface — onDelivered / onFailed

Enums
- NotificationChannel   { EMAIL, SMS, PUSH, SLACK }
- DeliveryStatus        { SENT, FAILED }

NOT entities
- User                  preferences keyed by userId string — no user-level behavior
- Template              rendering is sender-side for v1 (Step 5)
- Queue                 synchronous v1; async is Step 5

Relationships
- NotificationService owns:
    EnumMap<Channel, Sender>              sendersByChannel   (built once, never mutated)
    ConcurrentHashMap<userId, Set<Chan>>  preferences        (per-user opt-in)
    CopyOnWriteArrayList<Listener>        listeners          (lock-free fan-out)
- send returns List<DeliveryResult> — one per attempted channel
- Listeners observe; they never block delivery
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
                  |  setPreferences / add/removeListener   |
                  +----------------------------------------+
                       │              │                │
                  owns │              │                │
                       v              v                v
       EnumMap<Channel,Sender>   ConcurrentHashMap    CopyOnWriteArrayList
       sendersByChannel          <userId,Set<Chan>>   <Listener>
       (built once)              preferences          listeners
                       │
                       v
       +----------------------+          +----------------------+
       | <<interface>>        |          | <<interface>>        |
       | NotificationSender   |          | NotificationListener |
       | + channel()          |          | + onDelivered(r)     |
       | + send(notification) |          | + onFailed(r)        |
       +----------------------+          +----------------------+
         EmailSender / SmsSender           AuditListener /
         / PushSender                      MetricsListener / ...
```

---

## Step 3 — Class design (~10 min)

### NotificationService — state derived from requirements

| Requirement | State |
|-------------|-------|
| Multiple channels | `Map<NotificationChannel, NotificationSender> sendersByChannel` (EnumMap) |
| Per-user preferences | `Map<String, Set<NotificationChannel>> preferences` (ConcurrentHashMap) |
| Pluggable observers | `List<NotificationListener> listeners` (CopyOnWriteArrayList) |

### NotificationService — public API

```java
public class NotificationService {
    private final Map<NotificationChannel, NotificationSender> sendersByChannel;
    private final Map<String, Set<NotificationChannel>>        preferences = new ConcurrentHashMap<>();
    private final List<NotificationListener>                   listeners   = new CopyOnWriteArrayList<>();

    public NotificationService(List<NotificationSender> senders) {
        EnumMap<NotificationChannel, NotificationSender> map = new EnumMap<>(NotificationChannel.class);
        for (NotificationSender s : senders) map.put(s.channel(), s);
        this.sendersByChannel = map;   // never mutated after ctor → safe concurrent reads
    }

    public List<DeliveryResult> send(Notification notification);        // Step 4
    public void setPreferences(String userId, Set<NotificationChannel> channels);
    public void addListener(NotificationListener listener);
    public void removeListener(NotificationListener listener);
}
```

### Strategy + Observer interfaces

```java
public interface NotificationSender {
    NotificationChannel channel();
    void send(Notification notification);       // MAY throw — service catches
}

public interface NotificationListener {
    void onDelivered(DeliveryResult result);
    void onFailed(DeliveryResult result);
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

> *"Strategy lets each Sender own its vendor integration (SMTP, Twilio, FCM) — the service doesn't know what's inside. Observer makes outcome handling pluggable — listeners observe, never block delivery. Tell-Don't-Ask: the service tells listeners 'this happened'; a listener filters itself."*

---

## Step 4 — Implementation + dry-run (~17 min)

### 4.1 `send` — fan-out with two-layer isolation (write this FIRST)

```java
public List<DeliveryResult> send(Notification notification) {
    Set<NotificationChannel> channels = preferences.getOrDefault(
            notification.getRecipientId(), sendersByChannel.keySet());

    List<DeliveryResult> results = new ArrayList<>(channels.size());
    for (NotificationChannel channel : channels) {
        DeliveryResult result = deliverTo(notification, channel);   // layer 1
        results.add(result);
        fireListeners(result);                                       // layer 2
    }
    return results;
}

// Layer 1 — one channel's throw becomes a FAILED result, never escapes.
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

// Layer 2 — one listener's throw can't break the others.
private void fireListeners(DeliveryResult result) {
    for (NotificationListener listener : listeners) {
        try {
            if (result.isSent()) listener.onDelivered(result);
            else                 listener.onFailed(result);
        } catch (Exception e) {
            System.err.println("notification: listener threw — " + e.getMessage());
        }
    }
}
```

**Four callouts to deliver out loud:**

1. *"`getOrDefault(recipientId, sendersByChannel.keySet())` — a user who hasn't set preferences defaults to ALL configured channels."*

2. *"Two try-catches around `Exception`. That's deliberate — I catch `Exception`, not `Throwable`, because I do NOT want to swallow `Error`s like OutOfMemory. Catching an OOM inside a listener wouldn't save the JVM anyway; failure isolation is for vendor/business exceptions, not for JVM-fatal errors."*

3. *"`fireListeners` runs AFTER the result is recorded. Listeners observe outcomes — they're not in the delivery critical path."*

4. *"No `synchronized` on the service. `CopyOnWriteArrayList` iterates a snapshot, so a concurrent `addListener` mid-fan-out is safe; the EnumMap is immutable; preferences is a ConcurrentHashMap. Each structure owns its own concurrency."*

### 4.2 Preferences + listener registration

```java
public void setPreferences(String userId, Set<NotificationChannel> channels) {
    preferences.put(userId, Set.copyOf(channels));   // defensive immutable copy
}
public void addListener(NotificationListener listener)    { listeners.add(listener); }
public void removeListener(NotificationListener listener) { listeners.remove(listener); }
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

### 4.5 Dry-run — listener-failure isolation

```
Setup: badListener.onDelivered throws; goodListener counts.

send(...) → 3 channels → 3 results → 3 fireListeners calls.
Per call: badListener throws → logged; goodListener STILL fires.
Final: goodListener.delivered == 3 — every channel observed despite badListener throwing.  ✓
```

### 4.6 Concurrent burst — the empirical proof (mention this)

```
50 threads × 1 notification × 3 channels = 150 deliveries, all released via a CountDownLatch.
Assert: total results == 150 AND listener.delivered == 150.

Passes without an outer lock because:
   sendersByChannel — immutable, safe to read
   preferences      — ConcurrentHashMap
   listeners        — CopyOnWriteArrayList (snapshot iteration)
```

---

## Step 5 — Extensibility (~8 min)

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
| "Audit log of every delivery" | Already a listener — `AuditListener`. That's exactly what Observer is for. |
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
| **Observer** ⭐ | `NotificationListener` interface, fired after each delivery | *"Requirements say outcome handling is pluggable — listeners subscribe independently and observe, never block delivery."* |
| **Facade** | `NotificationService` | *"Application code only touches the service — senders, preferences, listeners are hidden behind `send`."* |
| **Tell, Don't Ask** | Service tells listeners "this happened"; they self-filter | *"The service doesn't ask a listener 'do you care?' — it fires; the listener decides."* |
| **Immutability** | `Notification`, `DeliveryResult` | *"The same Notification hits N channels concurrently — mutability would be a data race."* |

### Patterns for Step 5 extensibility

| Follow-up trigger | Pattern | The one-line move |
|-------------------|---------|-------------------|
| "Retries / rate limiting / logging on a channel" | **Decorator** ⭐ | *"Wrap the Sender — `RetryingSender`, `RateLimitedSender`. Composable: retry-over-rate-limit-over-vendor. Service is oblivious."* |
| "New channel (Slack, WhatsApp, webhook)" | **Strategy (extend)** ⭐ | *"New Sender class + register at ctor. Zero changes to the service or other senders."* |
| "New observer (audit, metrics, retry-trigger)" | **Observer (extend)** | *"New Listener class + `addListener`. Nothing else changes."* |
| "Async dispatch" | **Producer–Consumer** | *"Per-channel bounded queue + worker thread. Same pattern as the Logger async extension."* |
| "Per-channel templates" | **Strategy** | *"`TemplateRenderer` per channel; render before sending."* |
| "De-dup" | Idempotency cache | *"Key on Notification + `computeIfAbsent` — same trick as PaymentGateway."* |

### Patterns to actively refuse

- **Singleton on NotificationService** — kills tests; DI a single instance.
- **State pattern on DeliveryStatus** — two values, no per-state behavior; enum is right.
- **Chain of Responsibility for fan-out** — wrong shape. CoR stops at the first handler; we want EVERY channel to run.
- **Builder for the 1-arg `NotificationService(senders)` ctor** — academic noise.

### The rule to sound natural

1. **Strategy + Observer are non-negotiable in the base** — the requirements mandate both.
2. **Cap the base at those two** (plus Facade as a principle). Decorator lands in Step 5.
3. **Pair each pattern with a concrete win.** *"Observer because outcome handling is pluggable"* > *"I'd use Observer."*

---

## What is expected at each level

### Junior (SDE-1)
- Reaches a Sender interface with a nudge; may hardcode an if/else on channel instead of a map.
- Implements happy-path fan-out; forgets the try-catch, so one sender's throw aborts the whole send.
- No listener isolation; a bad listener takes down the batch.
- No concurrency discussion unless prompted.

### Mid-level (SDE-2) — the target
- Strategy per channel via an EnumMap, Observer for listeners — both named and justified from requirements.
- Both failure-isolation layers present (try-catch around sender AND around each listener).
- `send` returns one result per channel; multi-channel is all-attempted, not fail-fast.
- Immutable Notification; defensive copies at boundaries.
- Runs the sender-isolation dry-run out loud.

### Senior (SDE-3 / SDE-II)
- Everything mid-level, faster, with proactive tradeoffs.
- Catches `Exception`, not `Throwable`, and can explain why (don't swallow JVM Errors).
- No outer lock — justifies each collection choice (immutable EnumMap / ConcurrentHashMap / CopyOnWriteArrayList) against the read-heavy fan-out workload.
- Names Decorator for retries/rate-limiting and async-queue for tail latency before being asked.
- Describes the 50-thread `CountDownLatch` burst test as the empirical concurrency proof.

---

## Interview deep-dives

### Complexity

Let `C` = channels in the recipient's preferences, `L` = listeners.

| Operation | Time | Notes |
|-----------|------|-------|
| `send` | **O(C · (1 + L))** + sender I/O | C sender calls + C×L listener invocations; vendor I/O dominates |
| `deliverTo` (per channel) | **O(1)** lookup + sender cost | EnumMap.get is an array index |
| `addListener` / `removeListener` | **O(L)** | CopyOnWriteArrayList copies the array |
| `setPreferences` | **O(1)** amortized | ConcurrentHashMap put |

> **Senior callout:** *"The vendor I/O (10s–100s of ms) dominates — the map lookups and listener loop are noise. If L grows huge, the fix is async dispatch, not a faster data structure."*

### Concurrency — why no outer lock

| Structure | Choice | Why |
|-----------|--------|-----|
| Senders | Immutable EnumMap | Built at ctor, never mutated → safe concurrent reads |
| Preferences | ConcurrentHashMap | Per-key atomic puts |
| Listeners | CopyOnWriteArrayList | Snapshot iteration; safe with concurrent add/remove |

> *"Three structures each handle their own concurrency. Combined with the per-sender / per-listener try-catch, we get correct concurrent behavior with zero `synchronized` blocks. A `synchronized send` would serialize unrelated notifications — the wrong granularity."*

### The concurrent-burst test (mention this)

```java
@Test
void fifty_threads_each_fan_out_to_three_channels() throws Exception {
    NotificationService svc = new NotificationService(
        List.of(new EmailSender(), new SmsSender(), new PushSender()));
    CountingListener listener = new CountingListener();
    svc.addListener(listener);

    int threads = 50;
    CountDownLatch fire = new CountDownLatch(1);
    AtomicInteger total = new AtomicInteger();
    // ... submit N tasks that await(fire) then svc.send(...) and add results.size()
    fire.countDown();
    // ... awaitTermination ...

    assertEquals(150, total.get());               // 50 × 3 channels
    assertEquals(150, listener.getDelivered());    // no lost invocations
}
```

---

## 30-second summary (memorize for closing)

> *"NotificationService is the orchestrator + facade with one public method, `send`. Strategy in the base — one Sender per channel, routed via an immutable EnumMap. Observer in the base — pluggable listeners fired after each delivery, held in a CopyOnWriteArrayList so fan-out is lock-free under concurrent add/remove. Failure isolation at TWO layers: a try-catch around each sender call (one channel's throw becomes a FAILED result; the others still deliver) and a try-catch around each listener (a bad listener is logged; the others still fire). I catch `Exception`, not `Throwable` — I don't want to swallow JVM Errors like OOM. Per-user preferences in a ConcurrentHashMap, defaulting to all channels when unset. No outer lock — each collection owns its concurrency. Extensions: Decorator for retries and rate-limiting, per-channel async queues for tail latency, a TemplateRenderer Strategy for formatting."*

---

## Top mistakes that lose points

- **No try-catch around the sender** — one channel's exception crashes the whole send; caller gets zero deliveries instead of N-1. The single biggest flunk.
- **No try-catch around listeners** — one bad listener kills the others. Listener bugs are someone else's; the service must isolate them.
- **Catching `Throwable`** — swallows `OutOfMemoryError` / `StackOverflowError`. Catch `Exception`; isolation is for vendor/business errors, not JVM-fatal ones.
- **`synchronized(this)` on `send`** — serializes unrelated notifications. The collections already handle concurrency.
- **Mutable Notification** — same payload to N channels; one sender mutates the body → others see the mutation. Make it immutable.
- **`ArrayList` for listeners** — fan-out + concurrent add/remove → ConcurrentModificationException. Use CopyOnWriteArrayList.
- **`HashMap` for senders** — works, but `EnumMap` is idiomatic and faster.
- **Chain-of-Responsibility for fan-out** — CoR stops at the first handler; you want every channel attempted.

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
| `listener/NotificationListener.java` | Observer interface — `onDelivered` + `onFailed` |
| `NotificationService.java` | **The hot class** — EnumMap routing + CopyOnWriteArrayList listeners + two-layer try-catch |
| `NotificationServiceDriver.java` | 5 scenarios — fan-out / preferences / sender isolation / listener isolation / 50-thread burst (150/150 ✓) |

Run:
```bash
mvn -q compile exec:java \
  -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.notification.NotificationServiceDriver
```
