# Notification Service

> **The brief:** email, SMS and push channels, using **Observer** and **Factory**, plus **retries**.
>
> **The crux (what's really being tested):**
> 1. **Decoupling (Observer):** the order service says *"order shipped"*; it never knows who gets told or how.
> 2. **One sender per channel (Strategy) built by a Factory**, with retries added *around* senders (Decorator), not inside them.
> 3. **Failure isolation + smart retries:** a dead SMS provider never blocks the email; retry only errors that might succeed next time, with exponential backoff.
>
> **Family:** F4 Fan-out pipeline. See [foundations](../../00_AMAZON_LLD_FOUNDATIONS.md).

---

## 1. Plain-language picture

### A newspaper and its subscribers (Observer)
```
Order service:  "ORDER_SHIPPED, user-42, orderId A17"      → publishes to the EventBus. Done.
EventBus:       who subscribed to ORDER_SHIPPED?            → NotificationService, AuditLog
Notification:   template "Your order {orderId} has shipped" → email + SMS + push to user-42
```
The newspaper doesn't know its readers personally; it just prints, and subscribers get a copy. Tomorrow analytics wants shipping events too: it subscribes. **The order service never changes.**

### One courier per channel (Strategy + Factory)
Email goes via SendGrid, SMS via Twilio, push via Firebase. Each is a `NotificationSender`. A **factory** hands you the right courier for a channel, and every courier it hands out is already **wrapped** with a retry policy.

### Retrying like a sensible human (Decorator)
If a call doesn't connect, you try again in a bit, then wait longer, then longer: 100 ms → 200 ms → 400 ms (**exponential backoff**), then give up. But if the number **doesn't exist**, you don't redial it 4 times. So there are two kinds of error:
- **Transient** (timeout, provider busy): retry.
- **Permanent** (invalid number, user unsubscribed): fail immediately.

### One bad courier doesn't stop the others (isolation)
The SMS provider is down? The email and push still go out. Every channel's failure is caught and becomes a `FAILED` result for that channel only.

---

## 2. From story to design

| # | Decision | Rejected | Why |
|---|---|---|---|
| D1 | **EventBus + `EventSubscriber`** (Observer): business code publishes events | `orderService.ship()` calling `notificationService.sendEmail(...)` | Business code shouldn't know about notification channels. New subscribers (audit, analytics) don't touch the publisher. |
| D2 | Events carry **data**; `NotificationService` owns **templates** (`"Order {orderId} shipped"`) | publishers writing message text | Wording changes (and translations) don't need changes in the order service. |
| D3 | `NotificationSender` interface, one class per channel (Strategy) | `switch(channel)` inside `send()` | Each provider has its own SDK and rules (SMS = 160 chars). |
| D4 | **`SenderFactory`** builds the sender for a channel | `new SmsSender()` scattered around | Construction (+ wrapping with retry) in one place. A new channel = 1 class + 1 `case`. |
| D5 | Retries as a **Decorator** (`RetryingSender` wraps any sender) | retry loops copied into every sender | Senders stay simple; one retry policy for all. You can turn it off per channel. |
| D6 | Retry **only** `TransientDeliveryException` | retry everything | Retrying a bad phone number 4 times wastes money and delays the others. |
| D7 | Exponential backoff via an injectable **`Sleeper`** | `Thread.sleep` hard-coded | Tests run instantly and can check the exact waits (100, 200, 400). |
| D8 | Each channel in its own try/catch → `DeliveryResult` per channel | one exception aborts the whole send | Failure isolation; the caller sees exactly which channel failed and why. |
| D9 | `catch (Exception)`, not `Throwable` | `catch (Throwable)` | Never swallow JVM errors (OutOfMemoryError). |
| D10 | `ConcurrentHashMap` / `CopyOnWriteArrayList` | plain collections | Events arrive from many threads while preferences change. |
| D11 | Out of base: async queue, dedup, priority, rate limiting, dead letters, fallback channels | building them all | Follow-ups (§5). |

### Class shape
```
EventBus                                   ← Subject: subscribe(type, subscriber) · publish(event)
«interface» EventSubscriber  onEvent(event)        ← Observer
Event { type, userId, data }

NotificationService implements EventSubscriber
  Map<channel, NotificationSender> · Map<userId, Set<channel>> preferences · templates
  onEvent(event) → render template → send(notification)
  send(notification) → for each preferred channel: deliverTo() → DeliveryResult

«interface» NotificationSender  channel() · send(notification)
  ├── EmailSender  ├── SmsSender  ├── PushSender          (Strategy; real ones wrap vendor SDKs)
  └── RetryingSender(inner, RetryPolicy, Sleeper)         (Decorator)
SenderFactory  create(channel) → RetryingSender(new XSender())          (Factory)
RetryPolicy { maxAttempts, initialBackoffMs, multiplier } · TransientDeliveryException · Sleeper

Notification { id, recipientId, subject, body }   DeliveryResult { channel, SENT | FAILED, error }
```

---

## 3. Patterns that earn their place

| Pattern | Where | Problem it solves | Without it |
|---|---|---|---|
| **Observer** | `EventBus` + `EventSubscriber` | publishers don't know their audience | the order service calls notification, audit and analytics directly, and changes for each new one |
| **Strategy** | `NotificationSender` per channel | different providers behind one `send()` | a `switch(channel)` that grows with every channel |
| **Factory** | `SenderFactory.create(channel)` | builds and wraps senders in one place | construction + retry wrapping copied wherever senders are made |
| **Decorator** | `RetryingSender` | adds retries to *any* sender without editing it | retry loops duplicated in every sender |

**Say:** *"Observer so business services just publish events. Strategy per channel, created by a Factory that wraps each sender in a retrying Decorator. Failures are isolated per channel."*

**Tempting but wrong here:**
- **Singleton `NotificationService`:** create once and inject; tests need fresh instances.
- **Chain of Responsibility for channels:** that would stop at the first handler, but we want **all** preferred channels. (A *fallback* chain, "push, else SMS", is a valid follow-up: Q6.)
- **Builder for `Notification`:** 4 required fields; a constructor is fine.

---

## 4. The 45-minute run

### Clarify (min 0–5)
> **You:** Who triggers notifications? Other services, on events like "order shipped" or "OTP requested"?
> **Interviewer:** Yes.
> **You:** Channels: email, SMS, push, and users can choose which ones?
> **Interviewer:** Yes, with per-user preferences. Default is all.
> **You:** If one channel fails, should the others still go?
> **Interviewer:** Yes.
> **You:** Retries: how many, and should we skip retrying permanent errors like an invalid number?
> **Interviewer:** A few retries with backoff; yes, don't retry permanent errors.
> **You:** Synchronous for now, async queue as an extension?
> **Interviewer:** Fine.

```
In scope:  publish(event) → subscribers (Observer) · templates per event type
           channels email/SMS/push via one sender each (Strategy), built by a Factory
           per-user channel preferences · per-channel failure isolation
           retries: transient only, exponential backoff, max attempts (Decorator)
Out:       async queue, dedup, priority, rate limiting, fallback channels, quiet hours
```

### Timeline
| Min | Do |
|---|---|
| 5–9 | Class shape. Name the 4 patterns and the reason for each (§3). |
| 9–14 | Code step 1: `Event`, `EventSubscriber`, `EventBus`. |
| 14–20 | Code step 2: `NotificationSender` + one concrete sender + `SenderFactory`. |
| 20–27 | Code step 3: `TransientDeliveryException`, `RetryPolicy`, **`RetryingSender`**. |
| 27–35 | Code step 4: **`NotificationService`**: `onEvent`, `send`, `deliverTo`. |
| 35–40 | Dry run: an event with a flaky SMS. |
| 40–45 | Follow-ups: async, duplicates. |

### The code you write, in this order

**Step 1: Observer** ([event/](event/))
```java
public class Event {
    private final String type;                      // "ORDER_SHIPPED"
    private final String userId;
    private final Map<String, String> data;         // {orderId: A17, eta: 2 days}
    // constructor (copies data) + getters
}

public interface EventSubscriber {
    void onEvent(Event event);
}

public class EventBus {
    private final Map<String, List<EventSubscriber>> subscribersByType = new ConcurrentHashMap<>();

    public void subscribe(String eventType, EventSubscriber subscriber) {
        subscribersByType.computeIfAbsent(eventType, t -> new CopyOnWriteArrayList<>()).add(subscriber);
    }

    // one broken subscriber must not stop the others, or the publisher
    public void publish(Event event) {
        for (EventSubscriber s : subscribersByType.getOrDefault(event.getType(), List.of())) {
            try {
                s.onEvent(event);
            } catch (Exception e) {
                System.err.println("Subscriber failed on " + event.getType() + ": " + e.getMessage());
            }
        }
    }
}
```

**Step 2: senders + factory** ([sender/](sender/))
```java
public interface NotificationSender {
    NotificationChannel channel();
    void send(Notification notification);           // may throw; the service isolates it
}

public class SmsSender implements NotificationSender {   // real one wraps Twilio / MSG91
    private static final int SMS_MAX_LEN = 160;
    public NotificationChannel channel() { return NotificationChannel.SMS; }
    public void send(Notification n) {
        String body = n.getBody().length() <= SMS_MAX_LEN ? n.getBody() : n.getBody().substring(0, SMS_MAX_LEN) + "…";
        System.out.printf("  [sms]    → %s : %s%n", n.getRecipientId(), body);
    }
}
// EmailSender, PushSender: same shape

public class SenderFactory {
    private final RetryPolicy retryPolicy;
    private final Sleeper sleeper;

    public SenderFactory(RetryPolicy retryPolicy, Sleeper sleeper) {
        this.retryPolicy = retryPolicy;
        this.sleeper = sleeper;
    }

    public NotificationSender create(NotificationChannel channel) {
        NotificationSender base;
        switch (channel) {
            case EMAIL: base = new EmailSender(); break;
            case SMS:   base = new SmsSender();   break;
            case PUSH:  base = new PushSender();  break;
            default:    throw new IllegalArgumentException("No sender for channel " + channel);
        }
        return new RetryingSender(base, retryPolicy, sleeper);    // every sender gets retries
    }

    public List<NotificationSender> createAll(Collection<NotificationChannel> channels) {
        List<NotificationSender> senders = new ArrayList<>();
        for (NotificationChannel c : channels) senders.add(create(c));
        return senders;
    }
}
```

**Step 3: retries as a Decorator** ([sender/RetryingSender.java](sender/RetryingSender.java))
```java
// timeout, provider 503, rate limited: might work next time
public class TransientDeliveryException extends RuntimeException {
    public TransientDeliveryException(String message) { super(message); }
}

public class RetryPolicy {                          // 4 attempts, 100 ms, ×2 → waits 100, 200, 400
    private final int maxAttempts;
    private final long initialBackoffMs;
    private final int multiplier;
    // constructor (maxAttempts >= 1) + getters
}

public interface Sleeper {
    void sleep(long millis) throws InterruptedException;
    Sleeper REAL = Thread::sleep;                    // tests pass a recording no-op instead
}

public class RetryingSender implements NotificationSender {
    private final NotificationSender inner;
    private final RetryPolicy policy;
    private final Sleeper sleeper;

    public RetryingSender(NotificationSender inner, RetryPolicy policy, Sleeper sleeper) {
        this.inner = inner;
        this.policy = policy;
        this.sleeper = sleeper;
    }

    public NotificationChannel channel() { return inner.channel(); }

    public void send(Notification notification) {
        long backoff = policy.getInitialBackoffMs();
        for (int attempt = 1; ; attempt++) {
            try {
                inner.send(notification);
                return;
            } catch (TransientDeliveryException e) {
                if (attempt >= policy.getMaxAttempts()) throw e;      // out of attempts: give up
                try {
                    sleeper.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
                backoff *= policy.getMultiplier();                    // 100 → 200 → 400
            }
            // any other exception (bad phone number) is permanent: it propagates at once
        }
    }
}
```

**Step 4: the service** ([NotificationService.java](NotificationService.java))
```java
public class NotificationService implements EventSubscriber {

    private final Map<NotificationChannel, NotificationSender> sendersByChannel = new EnumMap<>(NotificationChannel.class);
    private final Map<String, Set<NotificationChannel>> preferences = new ConcurrentHashMap<>();
    private final Map<String, String[]> templates = new ConcurrentHashMap<>();       // eventType → {subject, body}

    public NotificationService(List<NotificationSender> senders) {
        for (NotificationSender s : senders) sendersByChannel.put(s.channel(), s);   // never changed after this
    }

    public void setPreferences(String userId, Set<NotificationChannel> channels) {
        preferences.put(userId, Set.copyOf(channels));
    }

    public void addTemplate(String eventType, String subject, String body) {
        templates.put(eventType, new String[] { subject, body });
    }

    @Override
    public void onEvent(Event event) {                        // the Observer callback
        String[] template = templates.get(event.getType());
        if (template == null) return;                         // no template: we don't notify for this event
        send(new Notification(UUID.randomUUID().toString(), event.getUserId(),
                render(template[0], event.getData()), render(template[1], event.getData())));
    }

    public List<DeliveryResult> send(Notification notification) {
        Set<NotificationChannel> channels = preferences.getOrDefault(
                notification.getRecipientId(), sendersByChannel.keySet());    // no preference = all channels
        List<DeliveryResult> results = new ArrayList<>();
        for (NotificationChannel channel : channels) results.add(deliverTo(notification, channel));
        return results;
    }

    // failure isolation: one channel's exception becomes a FAILED result and never escapes
    private DeliveryResult deliverTo(Notification notification, NotificationChannel channel) {
        NotificationSender sender = sendersByChannel.get(channel);
        if (sender == null) return DeliveryResult.failed(notification.getId(), channel, "no sender for " + channel);
        try {
            sender.send(notification);                         // RetryingSender already retried transient errors
            return DeliveryResult.sent(notification.getId(), channel);
        } catch (Exception e) {
            return DeliveryResult.failed(notification.getId(), channel, e.getMessage());
        }
    }

    private static String render(String text, Map<String, String> data) {
        String out = text;
        for (Map.Entry<String, String> e : data.entrySet()) out = out.replace("{" + e.getKey() + "}", e.getValue());
        return out;
    }
}
```
**Wiring it up:**
```java
SenderFactory factory = new SenderFactory(new RetryPolicy(4, 100, 2), Sleeper.REAL);
NotificationService notifications = new NotificationService(factory.createAll(List.of(EMAIL, SMS, PUSH)));
notifications.addTemplate("ORDER_SHIPPED", "Order {orderId} shipped", "Your order {orderId} is on the way.");
eventBus.subscribe("ORDER_SHIPPED", notifications);
// in the order service, the only line it needs:
eventBus.publish(new Event("ORDER_SHIPPED", "user-42", Map.of("orderId", "A17")));
```

### Dry run
```
publish(ORDER_SHIPPED, user-42, {orderId: A17})
  EventBus → NotificationService.onEvent → template → "Your order A17 is on the way"
           → AuditLog.onEvent (another subscriber; independent)
  send → user-42 has no preference → EMAIL, SMS, PUSH
    EMAIL: RetryingSender → EmailSender ✓                                        SENT
    SMS:   RetryingSender → timeout (1) → wait 100 → timeout (2) → wait 200 → ✓  SENT
    PUSH:  RetryingSender → PushSender ✓                                         SENT
If SMS throws "invalid phone number" → not transient → FAILED at once; EMAIL and PUSH unaffected.
```

---

## 5. Follow-ups: answer out loud first, then open

<details>
<summary><b>Q1. Sending synchronously slows down the order service. Make it async.</b></summary>

`publish` should only **enqueue**. Worker threads deliver, and retries are **scheduled** rather than slept, so no thread sits idle waiting:
```java
private final ExecutorService workers = Executors.newFixedThreadPool(8);
private final ScheduledExecutorService retryScheduler = Executors.newScheduledThreadPool(1);
private final List<Notification> deadLetters = new CopyOnWriteArrayList<>();

public void sendAsync(Notification n, NotificationChannel channel, int attempt, long backoffMs) {
    workers.submit(() -> {
        try {
            sendersByChannel.get(channel).send(n);
        } catch (TransientDeliveryException e) {
            if (attempt < MAX_ATTEMPTS) {
                // schedule the retry instead of sleeping on a worker thread
                retryScheduler.schedule(() -> sendAsync(n, channel, attempt + 1, backoffMs * 2),
                                        backoffMs, TimeUnit.MILLISECONDS);
            } else {
                deadLetters.add(n);                                  // Q5
            }
        } catch (Exception e) {
            deadLetters.add(n);                                      // permanent: don't retry
        }
    });
}
```
In production: Kafka/SQS **one topic per channel**, so a slow SMS provider only backs up the SMS queue and never the email queue.
</details>

<details>
<summary><b>Q2. The same event arrives twice (queues deliver "at least once"). Don't send two OTPs.</b></summary>

Give each event an id and remember the processed ids. `Set.add` is atomic, so even two threads with the same event only send once:
```java
private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();

@Override
public void onEvent(Event event) {
    if (!processedEventIds.add(event.getId())) return;     // already handled
    ...
}
```
In production, store ids in Redis with a TTL (e.g. 24 h: `SET event:<id> 1 NX EX 86400`) so the set doesn't grow forever. Also pass an **idempotency key** to providers that support one.
</details>

<details>
<summary><b>Q3. OTPs must jump ahead of marketing emails.</b></summary>

A priority on the notification (`CRITICAL`, `NORMAL`, `BULK`) and a `PriorityBlockingQueue` that workers take from:
```java
BlockingQueue<Notification> queue = new PriorityBlockingQueue<>(100,
        Comparator.comparingInt((Notification n) -> n.getPriority().ordinal()));   // CRITICAL first
```
Better at scale: **separate queues and workers per priority**, so a million-email campaign can never delay an OTP even if the priority logic has a bug.
</details>

<details>
<summary><b>Q4. Don't spam: max 3 marketing messages a day per user, and nothing between 10 PM and 8 AM.</b></summary>

Check before sending. The per-user cap **is** a rate limiter: reuse the deck's `TokenBucketLimiter`, keyed by userId. Quiet hours: if it's night in the **user's** timezone, schedule for 8 AM instead of dropping it. Critical messages (OTP, fraud alert) bypass both rules.
</details>

<details>
<summary><b>Q5. What happens to notifications that fail after all retries?</b></summary>

A **dead-letter queue**: store the notification, the channel, the error and the attempt count. An alarm fires when it grows; an ops tool can replay it once the provider recovers. Never silently drop. The delivery log (`getDeliveryLog()`) already records every FAILED result.
</details>

<details>
<summary><b>Q6. If push fails, fall back to SMS.</b></summary>

For "reach the user by **any** channel", try channels in order and stop at the first success (this one *is* a chain):
```java
public DeliveryResult sendWithFallback(Notification n, List<NotificationChannel> order) {
    DeliveryResult last = null;
    for (NotificationChannel c : order) {           // e.g. [PUSH, SMS, EMAIL]
        last = deliverTo(n, c);
        if (last.isSent()) return last;
    }
    return last;                                    // every channel failed
}
```
</details>

<details>
<summary><b>Q7. Add WhatsApp.</b></summary>

1. `WhatsAppSender implements NotificationSender` (an Adapter around the WhatsApp Business API).
2. Add `WHATSAPP` to `NotificationChannel`.
3. One `case` in `SenderFactory`.

`NotificationService`, `EventBus` and `RetryingSender` don't change, and WhatsApp automatically gets retries. That's the Factory + Decorator payoff.
</details>

<details>
<summary><b>Q8. 10,000 notifications fail at the same moment and all retry at exactly 100 ms. Problem?</b></summary>

Yes: a **thundering herd**. They all hit the recovering provider at the same instant and knock it over again. Add **jitter** (randomness) to each wait:
```java
long wait = backoff + ThreadLocalRandom.current().nextLong(backoff / 2 + 1);   // 100–150 ms, spread out
sleeper.sleep(wait);
```
Also respect the provider's `Retry-After` header when it sends one.
</details>

<details>
<summary><b>Q9. Templates in Hindi and English, and different text per channel.</b></summary>

Key templates by `(eventType, channel, language)`: an SMS is short, an email has HTML, push has a title. Fall back to English if a language is missing. Keep templates in a database/CMS so the product team can edit wording without a deploy.
</details>

<details>
<summary><b>Q10. Scale to 100 million notifications a day.</b></summary>

Event topics → a notification service that renders and fans out → **one queue per channel** → a worker pool per channel, sized to each **provider's rate limit** (SMS gateways allow N per second) → providers. Delivery receipts (provider webhooks) update the status from SENT to DELIVERED or READ. Store preferences in a fast KV store; dedup ids in Redis with a TTL.
</details>

<details>
<summary><b>Q11. How would you know it's working in production?</b></summary>

- **Metrics per channel and provider:** sent / failed / retried, success rate, p99 time from event to delivery, queue depth, dead-letter size.
- **Alarms:** success rate for a channel drops (the provider is down → switch to a backup provider), the DLQ grows, the queue backs up.
- **Logs:** notificationId + eventId + channel + attempt + provider response, so "I didn't get my OTP" can be traced.
</details>

<details>
<summary><b>Q12. How do you test it?</b></summary>

- **Retry timing without waiting:** a recording `Sleeper` (`waits::add`). A sender that fails twice → waits are exactly `[100, 200]`; one that always fails → `[100, 200, 400]` then FAILED; a permanent error → `[]` (all in the driver).
- **Isolation:** a throwing SMS sender → EMAIL and PUSH are still SENT.
- **Observer:** publishing one event reaches every subscriber; an event with no template sends nothing.
- **Concurrency:** 50 events published at once → exactly 50 deliveries.
</details>

---

## 6. Traps that cost points
1. Business services calling `sendEmail()` directly (no Observer).
2. `switch(channel)` inside the service instead of one sender per channel.
3. Retry loops inside each sender instead of one Decorator.
4. Retrying **everything**, including permanent errors.
5. Fixed delays with no backoff, or backoff with no jitter at scale.
6. One failing channel throwing out of `send()` and killing the others.
7. `catch (Throwable)`.

## 7. Recall check
1. Name the 4 patterns and the one problem each solves here.
2. Why is retry a Decorator rather than code inside `SmsSender`?
3. Transient vs permanent: give 2 examples of each. Which ones are retried?
4. 4 attempts, 100 ms, ×2: what are the waits?
5. How do you stop a duplicate event from sending two OTPs?
6. Why one queue per channel when it's async?

**Rebuild in 10 minutes:** `EventBus` (subscribe / publish with try/catch) · `NotificationSender` · `SenderFactory.create` · `RetryingSender.send` (loop, catch transient, backoff) · `NotificationService.onEvent` + `send` + `deliverTo`.

---

**Files:** `NotificationService` · `event/` (`Event`, `EventSubscriber`, `EventBus`) · `sender/` (`NotificationSender`, `EmailSender`, `SmsSender`, `PushSender`, `SenderFactory`, `RetryingSender`, `RetryPolicy`, `Sleeper`, `TransientDeliveryException`) · `model/` (`Notification`, `NotificationChannel`, `DeliveryResult`, `DeliveryStatus`) · `NotificationServiceDriver` (Observer fan-out, preferences, no template, retry then success, retries run out, permanent not retried, 50-thread test)
```bash
mvn -q compile exec:java -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.pipelines.notification.NotificationServiceDriver
```
