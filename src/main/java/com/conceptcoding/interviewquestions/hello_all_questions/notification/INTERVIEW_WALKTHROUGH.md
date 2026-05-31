# Notification System — 45-min LLD Interview Walkthrough

**Target role:** SDE‑2 (Amazon, Adobe, Microsoft, Atlassian, etc.)

> Notification System is the **canonical "Strategy + Observer + two-layer failure isolation" problem**. Three signals separate senior from mid: (a) **Strategy** per channel — one sender impl per `NotificationChannel`, justified by R3's explicit enumeration; (b) **Observer** on outcomes — pluggable listeners receive every delivery result; (c) **failure isolation at TWO layers** — a throwing sender can't take down other channels, AND a throwing listener can't take down other listeners.

---

## Time budget (45 min)

| Step | Activity                                                                                | Budget   | Cumulative |
| ---- | --------------------------------------------------------------------------------------- | -------- | ---------- |
| 1    | Requirements                                                                            | ~5 min   | 5          |
| 2    | Entities & Relationships                                                                | ~4 min   | 9          |
| 3    | Class Design (Strategy + Observer; fan-out shape)                                       | ~10 min  | 19         |
| 4    | Implementation (`send` w/ two-layer try/catch + listener fan-out + dry-run)             | ~17 min  | 36         |
| 5    | Extensibility (retries with Decorator, async dispatch, rate limiting, template rendering) | ~8 min   | 44         |
| —    | Wrap & questions                                                                        | ~1 min   | 45         |

Step 4 is the longest — the failure-isolation pattern lives there.

Watch the clock at minute **5** (Step 1 done), minute **19** (start coding), minute **36** (extensibility).

---

## Mental models — internalize these BEFORE you walk in

### M1. The fan-out flow — one notification, N channels, N results

```
   svc.send(Notification("user-A", "Welcome"))
        |
        v
   +-----------------------------------------+
   | channels = preferences[user-A]          |   <-- user's opt-in set
   |          ?? all configured channels     |       (fallback when unset)
   +-----------------------------------------+
        |
        v
   for each channel in channels:
        |
        v
   +-----------------------------------------+
   |  deliverTo(notification, channel)       |   <-- isolated per channel
   |    sender = sendersByChannel[channel]   |
   |    try { sender.send(notification);     |
   |          → DeliveryResult.sent }        |
   |    catch (Throwable t) {                |
   |          → DeliveryResult.failed(t.msg) |   ← isolation layer 1
   |    }                                    |       (sender throw caught)
   +-----------------------------------------+
        |
        v
   +-----------------------------------------+
   |  fireListeners(result)                  |
   |    for each listener:                   |
   |      try { listener.onDelivered/Failed }|
   |      catch (Throwable t) {              |   ← isolation layer 2
   |          stderr log; continue           |       (listener throw caught)
   |      }                                  |
   +-----------------------------------------+

   Returns:  List<DeliveryResult>  — one per attempted channel,
                                     both successes and failures
```

**Senior soundbite (memorize):** *"Failure isolation lives at TWO layers. A throwing sender becomes a FAILED DeliveryResult — never crashes the other channels for the same notification. A throwing listener is logged-and-skipped — never crashes the other listeners or the caller. Both `try/catch` blocks are around `Throwable`, not just `Exception`, so even OOM-on-a-misbehaving-listener doesn't propagate."*

### M2. Strategy registry as `EnumMap<NotificationChannel, NotificationSender>`

```
   Configuration (passed to ctor):  List<NotificationSender> senders
        |
        v
   Build EnumMap once at ctor:
        for (s in senders) map.put(s.channel(), s)
        sendersByChannel = unmodifiableMap(map)     ← immutable post-ctor
        |
        v
   Lookup at runtime:  sendersByChannel.get(channel)  ← O(1), no scan

   Why EnumMap (not HashMap)?
     - O(1) lookup with ZERO hashing overhead (uses ordinal as array index)
     - Cache-friendly (contiguous array under the hood)
     - The idiomatic Map<EnumType, T> in Java

   Why immutable post-ctor?
     - No locks around iteration / lookup
     - Two threads concurrently calling send() can't race the senders map
     - "Set once at startup" matches the requirement; dynamic add/remove → Step 5
```

### M3. Observer registry as `CopyOnWriteArrayList<NotificationListener>`

```
   Listeners can be added / removed at any time. Fan-out happens on every send.
   We need: SAFE concurrent iteration while another thread mutates.

   Options:
     synchronized(listeners) iteration       → serializes all sends
     ReadWriteLock                           → still serializes iteration vs add
     ConcurrentHashMap.values()              → wrong shape; we need a list
     CopyOnWriteArrayList                    → lock-free iteration ⭐

   CopyOnWriteArrayList semantics:
     - iteration is over a SNAPSHOT taken at iterator-creation time
     - add/remove copies the underlying array (cheap unless huge listener counts)
     - readers (the fan-out loop) NEVER lock; writers (add/remove) take a brief lock

   Cost: each add/remove is O(N) array copy.
   Win:  zero contention on the hot path (send), which is what we optimize.

   For 10s-100s of listeners with rare add/remove, this is the right structure.
```

---

## STEP 1 — Requirements (~5 min)

### What to say out loud (opener)
> "Notification systems have two correctness traps everyone falls into — one bad sender taking down all channels, and one bad listener taking down the rest of the listeners. Let me clarify scope on both."

### Probe the 4 themes

| Theme               | Question to ask                                                                                              |
| ------------------- | ------------------------------------------------------------------------------------------------------------ |
| Primary capabilities| "Multiple channels (email / SMS / push / slack). User preferences per channel. Pluggable observers on outcomes?" |
| Rules / completion  | "Send returns one result per attempted channel — both successes and failures? Failed sends don't block the others?" |
| Error handling      | "Sender exception → reported as FAILED, never propagated? Listener exception → logged, never blocks other listeners?" |
| Concurrency         | "Multi-threaded callers can `send()` concurrently? Listener add/remove safe against in-flight fan-outs?" |
| Scope boundaries    | "Out: actual SMTP/Twilio/FCM integration, async dispatch, retries with backoff, rate limiting, templates, persistence. Confirm?" |

### What to write on the board

```
Functional Requirements
1. Support N channels: EMAIL, SMS, PUSH (add SLACK as a Step-5 example).
2. send(Notification) fans out to all channels in the recipient's preferences
   (or to ALL configured channels if no preferences set).
3. Returns one DeliveryResult per attempted channel — both successes AND failures
   appear in the list.
4. Pluggable observers on outcomes — addListener / removeListener.
5. Failure isolation:
   (a) sender throws → DeliveryResult.failed(...) for that channel only;
       other channels for the SAME notification still deliver.
   (b) listener throws → logged to stderr, the next listener still fires.
6. Per-user channel preferences (opt-in subset of channels).
7. Thread-safe — concurrent senders, concurrent listener add/remove.

Out of Scope
- Retries with exponential backoff (Decorator in Step 5)
- Async dispatch / queue-per-channel (Step 5)
- Rate limiting per channel (Decorator)
- Template rendering / localization
- Multi-tenant / per-customer SLAs
- Persistence of notification history
- Webhook delivery to third parties (out of scope but mention)
```

### Close the step
> "Two load-bearing requirements: failure isolation at both layers, and the fan-out shape (one notification → N attempts → N results). Those are where the senior signal lives."

---

## STEP 2 — Entities & Relationships (~4 min)

### What to say out loud
> "Seven types: **NotificationService** (orchestrator + facade), **Notification** (immutable payload), **DeliveryResult** (per-channel outcome), **NotificationSender** (Strategy interface — one impl per channel), **NotificationListener** (Observer interface), **NotificationChannel** + **DeliveryStatus** (enums). Strategy and Observer both earn rent in the base — explicitly justified by R1 and R4."

### Why no `User` class
> "User preferences are just `userId → Set<Channel>`. There's no user-level behavior to model — user identity is a string opaque to the notification system. If preferences grow rules (DND hours, quiet times, escalation policy), then a User entity earns its place."

### Why Notification is immutable
> "The same Notification is delivered to N channels. Mutability would mean one channel could rewrite the subject before the next channel reads it — race-condition city. Immutable + `Map.copyOf` on metadata in the compact constructor closes that off entirely."

### What to write on the board

```
Entities
- NotificationService    (orchestrator + facade: send, addListener, setPreferences)
- Notification           (immutable record — id + recipient + subject + body + metadata)
- DeliveryResult         (immutable per-channel outcome — sent OR failed; .sent / .failed factories)
- NotificationSender     (Strategy interface — channel() + send(notification))
- NotificationListener   (Observer interface — onDelivered / onFailed)

Enums
- NotificationChannel    { EMAIL, SMS, PUSH, SLACK }
- DeliveryStatus         { SENT, FAILED }

NOT entities
- User                   (preferences keyed by userId string — no behavior)
- Template               (Step 5 — rendering is sender-side for v1)
- Notification queue     (synchronous v1; async is Step 5)

Relationships
- NotificationService owns:
    EnumMap<NotificationChannel, NotificationSender>    sendersByChannel    (immutable post-ctor)
    ConcurrentHashMap<String, Set<NotificationChannel>> preferences         (per-user opt-in)
    CopyOnWriteArrayList<NotificationListener>          listeners            (lock-free fan-out)
- send returns List<DeliveryResult>: one per attempted channel
- Listeners observe; they never block delivery (only catch around them)
```

### Diagram — boxes and arrows

```
                  +----------------------------------------+
                  |          NotificationService            |   <- orchestrator + facade
                  |  send(notification) → List<Result>      |
                  |  setPreferences / addListener / remove  |
                  +----------------------------------------+
                       |               |                |
                  owns | (3 stores)    |                |
                       v               v                v
       EnumMap<Channel, Sender>     ConcurrentHashMap     CopyOnWriteArrayList
       sendersByChannel             <userId, Set<Channel>> NotificationListener
       (immutable post-ctor)        preferences            listeners
                  |
                  v
       +----------------------+
       | <<interface>>        |     impls (one per channel):
       | NotificationSender   | --- EmailSender
       +----------------------+     SmsSender
       | + channel()          |     PushSender
       | + send(notification) |     (SlackSender — Step 5 example)
       +----------------------+

       +----------------------+
       | <<interface>>        |     impls (any observers):
       | NotificationListener | --- AuditListener
       +----------------------+     MetricsListener
       | + onDelivered(result)|     RetryTriggerListener
       | + onFailed(result)   |
       +----------------------+
```

---

## STEP 3 — Class Design (~10 min)

### NotificationService — state ↔ requirement table

| Requirement                              | State NotificationService must own                              |
| ---------------------------------------- | --------------------------------------------------------------- |
| Multiple channels                        | `EnumMap<NotificationChannel, NotificationSender> sendersByChannel` |
| Per-user preferences                     | `ConcurrentHashMap<String, Set<NotificationChannel>> preferences` |
| Observer fan-out                         | `CopyOnWriteArrayList<NotificationListener> listeners`          |
| Time injection                           | `Clock clock`                                                    |

### NotificationService — behavior table

| Need from requirements              | Method                                                  |
| ----------------------------------- | ------------------------------------------------------- |
| Send                                | `List<DeliveryResult> send(Notification notification)`   |
| Preferences                          | `void setPreferences(String userId, Set<Channel> ch)`    |
| Observer subscribe                  | `void addListener(NotificationListener listener)`        |
| Observer unsubscribe                | `void removeListener(NotificationListener listener)`     |

### NotificationService — class outline (write this on the board)

```java
public class NotificationService {
    private final Map<NotificationChannel, NotificationSender> sendersByChannel;
    private final Map<String, Set<NotificationChannel>>         preferences = new ConcurrentHashMap<>();
    private final List<NotificationListener>                    listeners   = new CopyOnWriteArrayList<>();
    private final Clock clock;

    public NotificationService(List<NotificationSender> senders, Clock clock) {
        EnumMap<NotificationChannel, NotificationSender> map = new EnumMap<>(NotificationChannel.class);
        for (NotificationSender s : senders) map.put(s.channel(), s);
        this.sendersByChannel = Collections.unmodifiableMap(map);
        this.clock = clock;
    }

    public List<DeliveryResult> send(Notification notification) { /* Step 4 */ }
    public void setPreferences(String userId, Set<NotificationChannel> channels);
    public void addListener(NotificationListener listener);
    public void removeListener(NotificationListener listener);
}
```

### Strategy interface — one impl per channel

```java
public interface NotificationSender {
    NotificationChannel channel();
    void send(Notification notification);   // MAY throw — service catches
}
```

### Observer interface — pluggable outcome listeners

```java
public interface NotificationListener {
    void onDelivered(DeliveryResult result);
    void onFailed(DeliveryResult result);
}
```

### Records — immutable boundary types

```java
public record Notification(String id, String recipientId, String subject,
                           String body, Map<String, String> metadata, Instant createdAt) {
    public Notification {
        // ... null checks ...
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);   // defensive immutable copy
    }
}

public record DeliveryResult(String notificationId, NotificationChannel channel,
                             DeliveryStatus status, String errorMessage, Instant attemptedAt) {
    public static DeliveryResult sent(String id, NotificationChannel ch, Instant at) { ... }
    public static DeliveryResult failed(String id, NotificationChannel ch, String msg, Instant at) { ... }
    public boolean isSent() { return status == DeliveryStatus.SENT; }
}
```

### The principle to verbalize — Strategy + Observer + Tell-Don't-Ask
> "Strategy lets each Sender own its own vendor integration (SMTP for email, Twilio for SMS, FCM for push) — the service doesn't know what's inside. Observer makes outcome handling pluggable — listeners observe results, never block delivery. Tell-Don't-Ask: the service tells listeners 'this happened'; listeners don't ask 'is this for me?' — that's their own filtering concern."

---

## STEP 4 — Implementation (~17 min)

### Open by asking
> "Real Java or pseudo-code? I'll do `send` first — both failure-isolation layers live there — then the listener fan-out, then dry-run the burst scenario."

### 4.1 `send` — fan-out with two-layer isolation

```java
public List<DeliveryResult> send(Notification notification) {
    Set<NotificationChannel> channels = preferences.getOrDefault(
            notification.recipientId(), sendersByChannel.keySet());

    List<DeliveryResult> results = new ArrayList<>(channels.size());
    for (NotificationChannel channel : channels) {
        DeliveryResult result = deliverTo(notification, channel);  // ← layer 1 (sender catch)
        results.add(result);
        fireListeners(result);                                      // ← layer 2 (listener catch)
    }
    return results;
}

// Failure isolation layer 1 — one channel's throw never crashes the others.
private DeliveryResult deliverTo(Notification notification, NotificationChannel channel) {
    NotificationSender sender = sendersByChannel.get(channel);
    if (sender == null) {
        return DeliveryResult.failed(notification.id(), channel,
                "no sender configured for " + channel, clock.instant());
    }
    try {
        sender.send(notification);
        return DeliveryResult.sent(notification.id(), channel, clock.instant());
    } catch (Throwable t) {
        return DeliveryResult.failed(notification.id(), channel, t.getMessage(), clock.instant());
    }
}

// Failure isolation layer 2 — one listener's throw never breaks the others.
private void fireListeners(DeliveryResult result) {
    for (NotificationListener listener : listeners) {
        try {
            if (result.isSent()) listener.onDelivered(result);
            else                 listener.onFailed(result);
        } catch (Throwable t) {
            System.err.println("notification: listener threw — " + t.getMessage());
        }
    }
}
```

**Four callouts to deliver out loud while writing this:**

1. *"`preferences.getOrDefault(recipientId, sendersByChannel.keySet())` — if a user hasn't set preferences, we default to ALL configured channels. Caller can override per-recipient at registration time."*

2. *"Two try-catches, both around `Throwable` (not `Exception`). A misbehaving sender catching an OOM or AssertionError would still corrupt the loop if I caught Exception. Throwable is the right choice for isolation boundaries."*

3. *"`fireListeners` is called AFTER the sender attempt, AFTER the result is recorded. Listeners observe outcomes — they're not in the critical path. Cleanly separates 'did the delivery happen?' from 'who needs to know about it?'."*

4. *"`CopyOnWriteArrayList` for listeners means this iteration is over a snapshot — totally safe even if another thread is calling `addListener` mid-fan-out. No outer lock on the service."*

### 4.2 `setPreferences` and listener add/remove — trivial but worth showing

```java
public void setPreferences(String userId, Set<NotificationChannel> channels) {
    preferences.put(userId, Set.copyOf(channels));   // defensive immutable copy
}

public void addListener(NotificationListener listener)    { listeners.add(listener); }
public void removeListener(NotificationListener listener) { listeners.remove(listener); }
```

> *"`Set.copyOf` on the channels argument — caller can't mutate our preferences map after the call. Defensive copies at every boundary."*

### 4.3 Concrete senders — adapters around vendor SDKs

```java
public class EmailSender implements NotificationSender {
    public NotificationChannel channel() { return NotificationChannel.EMAIL; }
    public void send(Notification n)     { /* SMTP / SES / SendGrid call */ }
}

public class SmsSender implements NotificationSender {
    public NotificationChannel channel() { return NotificationChannel.SMS; }
    public void send(Notification n) {
        // Twilio / MSG91 — truncate body to 160 chars
    }
}

public class PushSender implements NotificationSender {
    public NotificationChannel channel() { return NotificationChannel.PUSH; }
    public void send(Notification n)     { /* FCM / APNs */ }
}
```

> *"Each sender is essentially an Adapter around a vendor SDK. The Strategy interface is the seam — the service doesn't know whether 'EmailSender' is SMTP, SES, or SendGrid. Swap implementations at composition time without touching the service."*

### 4.4 Verification — dry-run sender-failure isolation

```
Setup: 3 senders — EmailSender (OK), SmsSender that throws "rate-limit exceeded", PushSender (OK).
       No preferences set for user-C → defaults to all 3 channels.

send(Notification("user-C", "Alert", "Suspicious login")):
   channels = {EMAIL, SMS, PUSH}
   deliverTo(n, EMAIL):
     emailSender.send(n)              → OK
     return DeliveryResult.sent(EMAIL)                                  ✓
   deliverTo(n, SMS):
     smsSender.send(n) throws "rate-limit exceeded"
     catch (Throwable):
     return DeliveryResult.failed(SMS, "rate-limit exceeded")           ✓
   deliverTo(n, PUSH):
     pushSender.send(n)               → OK     ← NOT skipped despite SMS throwing
     return DeliveryResult.sent(PUSH)                                   ✓

Returns: [sent(EMAIL), failed(SMS, ...), sent(PUSH)]
Listener invocations: 3 (one per result — both outcomes observed).
```

### 4.5 Verification — listener-failure isolation

```
Setup: 2 listeners on the service:
   - badListener.onDelivered → throws RuntimeException("listener bug")
   - goodListener (CountingListener)

send(Notification("user-D", "Beep")) → 3 channels → 3 results, 3 fireListeners calls.

Per fireListeners call:
   for listener in [badListener, goodListener]:
     try { listener.onDelivered/Failed(result) }
     catch (Throwable t): stderr "notification: listener threw — listener bug"

   First listener throws (logged); the second listener STILL fires.

After all 3 channel attempts:
   goodListener.delivered count = 3    ← every channel observed despite badListener throwing
                                                                                              ✓
```

### 4.6 Verification — concurrent burst (the load-bearing test)

```
Setup: 1 CountingListener attached. 50 threads × 1 notification each × 3 channels = 150 delivery attempts.

All 50 threads release simultaneously via CountDownLatch barrier.
Each thread calls svc.send(notification).
Each send fans out to 3 channels → 3 DeliveryResults.
Each DeliveryResult fires fireListeners → 1 invocation on the counting listener.

After pool.shutdown() + awaitTermination:
   total DeliveryResults returned across all 50 sends: 150     ✓
   listener.delivered count:                            150     ✓
   No lost results, no lost listener invocations.

What makes this test pass without an outer lock on the service:
   - sendersByChannel is immutable post-ctor — iteration is safe
   - preferences is a ConcurrentHashMap — getOrDefault is safe
   - listeners is a CopyOnWriteArrayList — iteration is over a snapshot, safe with concurrent add/remove
```

> **This is the empirical concurrency proof.** Mention you'd verify it with `CountDownLatch` + `AtomicInteger` counter + asserting `expected == actual`.

---

## STEP 5 — Extensibility (~8 min)

### 5.1 "Retries with exponential backoff for transient failures"

> **Problem in current design:** *"A 503 from Twilio is treated as a definitive FAILED. In reality, transient errors should be retried with backoff before giving up."*
>
> **Pattern as the fix:** *"Decorator. Wrap each Sender in `RetryingSender(delegate, RetryPolicy)`. The wrapper catches transient errors (vendor-specific error code matching), waits per policy (exponential + jitter), retries — falls through to FAILED only after retries exhausted. The service doesn't know it's wrapped; the Strategy interface is the seam."*

```java
public class RetryingSender implements NotificationSender {
    private final NotificationSender delegate;
    private final int maxAttempts;
    private final Duration baseBackoff;

    public NotificationChannel channel() { return delegate.channel(); }

    public void send(Notification n) {
        Throwable lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try { delegate.send(n); return; }
            catch (Throwable t) {
                lastError = t;
                if (attempt < maxAttempts) sleepExponential(attempt);
            }
        }
        throw new RuntimeException("retries exhausted", lastError);
    }
}
```

### 5.2 "Async dispatch — `send` shouldn't block the caller"

> **Problem in current design:** *"`send` blocks the caller for the duration of N synchronous vendor calls. For an API handler firing notifications on every request, that's tail-latency hostile."*
>
> **Pattern as the fix:** *"Queue-per-channel + dedicated worker thread (same pattern as the Logger async section). `send` enqueues per channel and returns immediately; workers drain the queues. Bounded queues with explicit overflow policy (drop newest / block / dead-letter)."*
>
> **Tradeoffs to mention:**
> - Worker lifecycle (shutdown drain)
> - Overflow policy decision (drop is usually right for notifications — losing a delivery is preferable to blocking a request)
> - Debuggability: stack traces no longer point to the call site

### 5.3 "Rate limiting per channel (Twilio costs money per SMS)"

> **Problem in current design:** *"No throttling. A bug that fires 100k SMS in a loop bankrupts you and gets the account suspended."*
>
> **Pattern as the fix:** *"**Decorator** again — `RateLimitedSender(delegate, ratePerSecond)`. Wraps the underlying sender with a token bucket (same algorithm as Rate Limiter walkthrough). Composable with retry: `RetryingSender(RateLimitedSender(EmailSender()))`."*

### 5.4 "Template rendering — same notification, different per-channel format"

> **Problem in current design:** *"Each sender does its own ad-hoc formatting in `send`. Real systems have templates per (channel, locale) — e.g., short SMS vs HTML email."*
>
> **Pattern as the fix:** *"Add a `TemplateRenderer` Strategy. Notification gets a `templateKey` instead of raw subject/body. Each sender looks up its (channel-specific, locale-specific) template and renders. Templating itself is a separate concern from sending."*

### 5.5 Other "what-if" answers

| Follow-up                                  | Answer                                                                                              |
| ------------------------------------------ | --------------------------------------------------------------------------------------------------- |
| "Audit log — every delivery"               | Already a listener — `AuditListener` writes to a log table. The Observer is exactly for this.       |
| "Metrics — count of sends per channel"     | Same — `MetricsListener` increments counters per channel + outcome.                                |
| "Multi-tenant — different vendors per tenant" | Route by tenant id: `sendersByTenant` map of strategy registries. Per-tenant SLA enforcement.   |
| "Hot reload of preferences"                | Preferences already in ConcurrentHashMap — `setPreferences` is atomic per user. No work needed.    |
| "Notification de-duplication"              | Add an idempotency key on Notification + cache (same pattern as PaymentGateway). Skip if seen recently. |
| "Quiet hours / Do Not Disturb"             | New entity `UserDndSchedule`; service filters channels based on local time before fan-out.         |
| "Webhook delivery to merchant systems"     | Another sender impl — `WebhookSender` — that POSTs to a configured URL. Same Strategy.             |

---

## Design Patterns — Hello Interview's canonical 8

> **Two patterns earn rent in the base:**
> - **Strategy (#1)** for NotificationSender — justified by R1's channel enumeration.
> - **Observer (#2)** for NotificationListener — justified by R4's pluggable outcomes.
>
> A third (**Decorator #7**) earns rent immediately in Step 5 for retries / rate limiting — name it ONLY when those follow-ups surface.

### How this maps to NotificationSystem specifically

**Already in the BASE design — call out by name:**

- **Strategy (#1)** ⭐ — `NotificationSender` interface; one impl per channel. Name it in Step 2.
- **Observer (#2)** — `NotificationListener` interface; lock-free `CopyOnWriteArrayList`. Name it in Step 2.
- **Facade (#8)** — `NotificationService` is the only class application code touches.
- **Tell-Don't-Ask** (principle) — service tells listeners "this happened"; listeners filter themselves.
- **Dependency Injection** (principle) — Senders + Clock injected.
- **Immutability** (principle) — Notification + DeliveryResult are records.

**Reach for these on Step-5 follow-ups:**

| Follow-up                                  | Pattern (HI's 8)             | Your line                                                                                            |
| ------------------------------------------ | ---------------------------- | ---------------------------------------------------------------------------------------------------- |
| "Retries / rate limiting / logging"        | **Decorator (#7)**           | *"Wrap each Sender — `RetryingSender(delegate, policy)`, `RateLimitedSender(delegate, rps)`. Composable: retry-over-rate-limit-over-vendor."* |
| "Async dispatch"                           | (Queue + worker)             | *"Per-channel bounded queue with a dedicated worker. Same pattern as Logger's async extension."*    |
| "Different templates per channel"          | **Strategy (#1)** ⭐         | *"`TemplateRenderer` Strategy registered per channel; service looks up + renders before sending."*  |
| "Multi-tenant"                             | (Composite Strategy)         | *"`Map<TenantId, NotificationService>` keyed by tenant; route at the boundary."*                    |
| "Dedup / idempotency"                      | (ConcurrentMap + key)         | *"Same `computeIfAbsent` idempotency pattern as PaymentGateway."*                                   |

**Patterns to actively refuse:**

- **Singleton on NotificationService** — kills tests; DI a single instance.
- **State pattern on DeliveryStatus** — two values, no per-state behavior, enum is right.
- **Builder for the 1-arg `NotificationService(senders)` ctor** — academic noise.
- **Chain of Responsibility for fan-out** — wrong shape. CoR is for "the first handler that handles it stops the chain"; we want every handler to run.

### One sentence to say at the end of Step 3

> *"The base design names two patterns out loud — Strategy on NotificationSender, Observer on NotificationListener — plus Facade as a principle on NotificationService. Decorator lands in Step 5 if retry / rate-limit / logging follow-ups come up."*

---

## Interview deep-dives — the questions you'll definitely get asked

### 1. Complexity (Big-O)

Let `C` = number of channels in the recipient's preferences, `L` = number of listeners, `S` = number of senders (constant ≤ #channels).

| Operation                                | Time                                              | Space               | Notes                                                                              |
| ---------------------------------------- | ------------------------------------------------- | ------------------- | ---------------------------------------------------------------------------------- |
| `send(notification)`                     | **`O(C · (1 + L))`** + sender I/O                  | O(C) for results    | C sender calls + C × L listener invocations                                       |
| `deliverTo` (per channel)                | `O(1)` EnumMap lookup + sender cost               | O(1)                | EnumMap.get is array index lookup, no hashing                                     |
| `fireListeners`                          | **`O(L)`**                                        | O(1)                | CopyOnWriteArrayList iteration is over a snapshot                                  |
| `setPreferences`                         | **`O(1)`** amortized                              | O(C)                | ConcurrentHashMap put                                                              |
| `addListener` / `removeListener`         | **`O(L)`**                                        | O(L)                | CopyOnWriteArrayList copies the underlying array on mutation                       |
| Storage — sendersByChannel               | -                                                 | **`O(S)`**          | One sender per configured channel                                                  |
| Storage — preferences                    | -                                                 | **`O(N · C̄)`**     | N users, average C̄ channels per user                                              |
| Storage — listeners                      | -                                                 | **`O(L)`**          |                                                                                    |

> **Senior callout:** *"Send is `O(C · (1 + L))` plus the vendor I/O. The I/O dominates — sender latency (10s-100s of ms) makes the map lookups and listener iteration noise. If listener count grows large enough that `O(L)` per channel matters, the right move is async dispatch — same Step-5 pattern as Logger. CopyOnWriteArrayList's O(L) on add/remove is acceptable because we expect listener-config changes to be rare and at startup."*

### 2. Concurrency / thread-safety

| Approach                                | When to use                                  | Cost                                                              |
| --------------------------------------- | -------------------------------------------- | ----------------------------------------------------------------- |
| **`CopyOnWriteArrayList` for listeners** ⭐ | **Default.** Hot path is reads (fan-out); writes are rare. | O(L) on add/remove; zero on read |
| `ConcurrentHashMap` for preferences      | **Default.** Per-key atomic puts.            | Slight overhead vs HashMap                                       |
| Immutable EnumMap for senders           | **Default.** Set once at ctor.                | None at runtime                                                  |
| Synchronized fan-out                     | Anti-pattern. Serializes all sends.          | Bottleneck across unrelated notifications                         |
| Async queue per channel (Step 5)        | Tail-latency-sensitive callers              | Worker lifecycle + overflow policy                                |

> **The key insight:** *"No outer lock on the service. Three data structures each handle their own concurrency: immutable EnumMap (no mutation), ConcurrentHashMap (per-key atomic), CopyOnWriteArrayList (snapshot iteration). Combined with the per-sender / per-listener try-catch, we get correct concurrent behavior without any explicit synchronized block."*

### 3. Testing — what to write tests for

| Test category                | Cases to cover                                                                                              |
| ---------------------------- | ----------------------------------------------------------------------------------------------------------- |
| Fan-out                      | Notification with no preferences → fans out to all configured channels                                     |
| User preferences             | User opts into EMAIL+PUSH → SMS sender NOT invoked, only 2 DeliveryResults                                 |
| **Sender failure isolation** | One sender throws → that channel's DeliveryResult is FAILED; other channels still deliver                  |
| **Listener failure isolation** | One listener throws → other listeners still fire                                                          |
| Listener fan-out             | Each DeliveryResult fires onDelivered or onFailed exactly once per attached listener                       |
| Add/remove listener          | Listener removed mid-batch is not called for subsequent sends                                              |
| Unknown channel              | Channel in preferences without a configured sender → FAILED with "no sender configured"                    |
| **Concurrent burst**         | N threads × M channels = N·M deliveries; listener count == N·M; no lost results or invocations            |

```java
@Test
void sender_throw_does_not_block_other_channels() {
    NotificationService svc = new NotificationService(List.of(
        new EmailSender(),
        new ThrowingSender(NotificationChannel.SMS, "rate-limit"),
        new PushSender()
    ));
    Notification n = new Notification("nid", "user-1", "subj", "body", Map.of(), Instant.now());
    List<DeliveryResult> results = svc.send(n);

    assertEquals(3, results.size());
    assertTrue(results.stream().anyMatch(r -> r.channel() == NotificationChannel.EMAIL && r.isSent()));
    assertTrue(results.stream().anyMatch(r -> r.channel() == NotificationChannel.SMS && !r.isSent()));
    assertTrue(results.stream().anyMatch(r -> r.channel() == NotificationChannel.PUSH && r.isSent()));
}
```

> **Senior callout:** *"The sender-failure-isolation test is the senior signal. A naïve implementation that loops without try-catch lets the SMS throw skip the PUSH sender. The test pins that behavior down — anyone refactoring `send` later will trip this assertion."*

### 4. SOLID mapping

| Letter                       | Where it shows up                                                                                          |
| ---------------------------- | ---------------------------------------------------------------------------------------------------------- |
| **S** Single Responsibility  | Service = orchestration + fan-out. Each Sender = one channel's I/O. Each Listener = one observation concern. Notification = payload. DeliveryResult = outcome. Five reasons to change, five types. |
| **O** Open/Closed            | Adding a new channel = new Sender class + register at ctor. Adding a new observer = new Listener class + addListener. Service code unchanged. |
| **L** Liskov Substitution    | Any Sender substitutable behind the interface — same `channel()` + `send()` contract, same throws policy (vendor errors via Throwable). |
| **I** Interface Segregation  | Sender has TWO narrow methods. Listener has TWO narrow methods (could split further but the pair is small). No fat interface. |
| **D** Dependency Inversion   | Service depends on Sender + Listener interfaces, not on EmailSender / AuditListener concretes. Senders injected at composition root. |

### 5. "Summarize your design in 30 seconds"

> *"NotificationService is the orchestrator + facade — one public method `send`. Strategy in the base for senders — one impl per channel, routed via an immutable EnumMap. Observer in the base for listeners — pluggable, fired after each delivery attempt; stored in a CopyOnWriteArrayList so the fan-out loop is lock-free even under concurrent add/remove. Failure isolation at TWO layers: `Throwable` catch around each sender call (one channel's throw becomes a FAILED result; others still deliver), and `Throwable` catch around each listener invocation (one listener's throw is logged; others still fire). Per-user preferences in ConcurrentHashMap; defaults to all configured channels when unset. No outer lock on the service — the three data structures each handle their own concurrency. `Clock` injected for deterministic timestamps. The driver test runs 50 threads × 3 channels = 150 deliveries with a counting listener and verifies all 150 listener invocations happen without loss. Extensions: Decorator for retries + rate-limiting, async dispatch per channel for tail latency, Strategy for templates, idempotency cache for de-dup."*

That's ~55 seconds. Hits: Strategy + Observer + two-layer isolation + the empirical 150-count concurrency proof.

---

## Closing soundbites (memorize these)

- **Opening:** *"Two correctness traps — one bad sender taking down all channels, and one bad listener taking down the rest. Both are solved by `try-catch (Throwable)` at the right boundaries."*
- **Why Strategy + Observer in base:** *"Both pass the one-sentence test. Strategy because R1 enumerates channels; Observer because R4 explicitly says listeners are pluggable. Not pattern-stuffing — responding to stated requirements."*
- **Why EnumMap for senders:** *"O(1) lookup with zero hashing — uses the enum's ordinal as an array index. Idiomatic Java for `Map<EnumType, T>`."*
- **Why CopyOnWriteArrayList for listeners:** *"Fan-out is the hot path; add/remove is rare. CoW iterates over a snapshot — zero contention on send. Trade O(L) on add for zero on read; right tradeoff for this workload."*
- **Why Throwable not Exception in the catches:** *"OOM and AssertionError can escape Exception catches. For isolation boundaries we want EVERYTHING contained."*
- **Why fire listeners outside the sender call:** *"Listeners observe outcomes — they're not in the critical path. Separating 'did the delivery happen' from 'who needs to know' keeps responsibilities clean."*
- **On extensibility:** *"Decorator stacks for retries + rate-limiting + logging. Async dispatch for tail latency. Both compose without service changes — Strategy + Observer are the seams that enable it."*

---

## Top mistakes that lose points

- **Catching `Exception` not `Throwable`** in the isolation boundaries — an OOM or AssertionError escapes and the loop breaks.
- **No try-catch around the sender** — one channel's exception crashes the entire send call; caller sees zero deliveries instead of N-1.
- **No try-catch around listeners** — one bad listener kills the others. Listener exceptions are someone else's bug; the service must isolate them.
- **`synchronized(this)` on `send`** — serializes all notifications. The data structures already handle concurrency; outer locks are over-correction.
- **Mutable Notification** — same notification passed to 3 senders, one sender mutates body → other senders see the mutated value. Immutable record + `Map.copyOf` closes this off.
- **`HashMap` for senders** — works but `EnumMap` is the idiomatic choice and faster. Wins style points.
- **`ArrayList` for listeners** — fan-out + concurrent add/remove → ConcurrentModificationException eventually. Use CopyOnWriteArrayList.
- **Fan-out via parallel streams without explicit executors** — uses the common ForkJoinPool, which is wrong for I/O-heavy work and creates surprise dependencies.
- **Observer with one fat method `onDeliveryAttempt`** — caller has to check status. Two methods (`onDelivered`/`onFailed`) make the API self-documenting.
- **Skipping the concurrent burst test** — the empirical 150-count proof is the senior signal.

---

## Files in this folder (your reference implementation)

| File                                                      | What it shows                                                                            |
| --------------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| `model/NotificationChannel.java`                          | Enum — EMAIL / SMS / PUSH / SLACK                                                        |
| `model/DeliveryStatus.java`                               | Enum — SENT / FAILED                                                                     |
| `model/Notification.java`                                 | Immutable record with defensive `Map.copyOf` on metadata                                 |
| `model/DeliveryResult.java`                               | Immutable record + `.sent` / `.failed` factories                                         |
| `sender/NotificationSender.java`                          | Strategy interface — `channel()` + `send()`                                              |
| `sender/EmailSender.java` / `SmsSender.java` / `PushSender.java` | Concrete senders (simulated vendor calls)                                          |
| `listener/NotificationListener.java`                      | Observer interface — `onDelivered` + `onFailed`                                          |
| `NotificationService.java`                                | **The hot class** — EnumMap routing + CopyOnWriteArrayList listeners + two-layer Throwable catch |
| `NotificationServiceDriver.java`                          | 5 scenarios — fan-out / preferences / sender isolation / listener isolation / **50-thread concurrent burst (150/150 ✓)** |

Run from the project root:

```bash
mvn -q compile exec:java \
  -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.notification.NotificationServiceDriver
```
