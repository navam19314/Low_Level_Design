# Payment Gateway

> **Why it's in the deck:** the **idempotency** lesson: "the user tapped Pay twice / the network retried, so charge exactly once". Also a payment state machine and a processor per method. Amazon's ★ "Payments / FastTag" HLD question is built on exactly this.
>
> **The crux (what's really being tested):**
> 1. **Idempotency:** the same key → one charge, even with 50 simultaneous retries; and the slow bank call must not hold any shared lock.
> 2. **The payment state machine:** PENDING → PROCESSING → SUCCESS/FAILED → refund only from SUCCESS.
> 3. **Processor per method (Strategy):** card / UPI / net-banking behind one interface.
>
> **Family:** F5 Swappable policy + enum state machine. See [foundations](../../00_AMAZON_LLD_FOUNDATIONS.md). The same idempotency idea appears in Movie Ticket, Food Delivery and Notification follow-ups.

---

## 1. Plain-language picture

### The double tap
You tap **Pay ₹499**. The screen freezes; you tap again. Or the app times out and retries. Without protection, that's ₹998. With an **idempotency key** (a unique id the app attaches to *this checkout attempt*), the gateway says: *"I've seen this key: here's the same result as last time"*. It never calls the bank twice.

### Fifty retries at once
All 50 arrive while the first is still talking to the bank. The first one **claims** the key (`putIfAbsent`: exactly one caller wins). The other 49 **wait for its answer** (a `CompletableFuture`) instead of starting their own charge. Everyone gets the same result; the bank was called once.

**The subtle part:** the bank call takes ~2 seconds. If it ran *inside* `computeIfAbsent`, the map would hold a lock on part of itself the whole time, so payments for *other* keys that happen to land in that part would freeze. So: claim the key quickly, then do the slow work **outside** the map.

### Same key, different amount
Key `idem-x` was used for ₹499. Now `idem-x` arrives for ₹999. That's a client bug (a reused key). Returning the ₹499 result would hide it, so **reject** it, as Stripe and Razorpay do.

### The payment's life
```
PENDING → PROCESSING → SUCCESS → REFUND_PENDING → REFUNDED
                    ↘ FAILED (final)
```
Refund a FAILED payment? Refund twice? Both are rejected by the transition table.

### Money
Always in the **smallest unit** (paise for ₹, cents for $), as a `long`. ₹499.00 = 49,900 paise.

---

## 2. From story to design

| # | Decision | Rejected | Why |
|---|---|---|---|
| D1 | `idempotencyCache.putIfAbsent(key, InFlight)`; duplicates `join()` the first call's future | `computeIfAbsent(key, k -> callBank())` | `computeIfAbsent` holds a map lock during the slow bank call and can stall unrelated keys. |
| D2 | Store the original **request** with the key; a mismatch → reject | returning the cached result anyway | Surfaces client bugs instead of charging the wrong amount silently. |
| D3 | `Payment` owns a transition table (`EnumMap`); `transitionTo` is synchronized | status set freely | Status can't regress (SUCCESS → PENDING) or skip steps; a refund race is safe. |
| D4 | `PaymentProcessor` (Strategy) with `supports(method)` | `switch(method)` | New method = new class; real ones are Adapters around vendor SDKs. |
| D5 | Normal declines come back as a `ProcessorResponse`; exceptions → FAILED | exceptions for declines | A declined card isn't exceptional; the state machine is driven by data. |
| D6 | `catch (Exception)` at the processor boundary | `catch (Throwable)` | Isolate processor bugs; never swallow JVM errors. |
| D7 | Amounts in paise (`long`) | `double` rupees | Exact. |
| D8 | `Clock` injected | `Instant.now()` | Deterministic timestamps in tests. |

### Class shape
```
PaymentGateway                         ← pay(request) → PaymentResult · refund(paymentId) · getPayment
  List<PaymentProcessor> · Map<key, InFlight{request, CompletableFuture<result>}> · Map<id, Payment> · Clock

Payment { id, key, customer, amountPaise, currency, method, status, timestamps, error }
  transitionTo(next) (table-checked, synchronized) · markFailure
PaymentStatus { PENDING, PROCESSING, SUCCESS, FAILED, REFUND_PENDING, REFUNDED }
PaymentRequest { key, customer, amountPaise, currency, method, description }  sameChargeAs(other)
PaymentResult  { paymentId, key, status, errorCode, errorMessage, processedAt }

«interface» PaymentProcessor  supports(method) · process(request) → ProcessorResponse{success, code, message}
  ├── CardProcessor (declines above an issuer limit)  ├── UpiProcessor  └── NetBankingProcessor
```

---

## 3. Patterns that earn their place

| Pattern | Problem it solves | Without it |
|---|---|---|
| **Strategy** (`PaymentProcessor`) | one interface per payment network | a growing `switch(method)` |
| **Adapter** (real processors) | wrap Razorpay/Stripe/NPCI SDKs into our interface | vendor types leaking into the gateway |
| **Enum state machine** (`Payment`) | legal status moves only | status regressions, double refunds |

**Say:** *"Idempotency: putIfAbsent claims the key atomically; duplicates wait on the first call's future; the bank call is outside any lock; a reused key with different contents is rejected. Processors are a Strategy, and the payment is a guarded state machine."*

**Tempting but wrong:** a Singleton gateway; the State pattern for payment status (no per-state behaviour, so a table is enough); `synchronized pay()` (one global lock: every payment waits on every bank call).

---

## 4. The 45-minute run

### Clarify (min 0–5)
> **You:** Merchants send payment requests (amount, customer, method). We route them to the right processor and return success or failure with a reason?
> **Interviewer:** Yes.
> **You:** Clients retry on timeouts. Must a retry never double-charge? Do they send an idempotency key?
> **Interviewer:** Yes, there's a key.
> **You:** Refunds: full only, and only for successful payments?
> **Interviewer:** Full for now.
> **You:** Methods: card, UPI, net banking?
> **Interviewer:** Yes.

### Timeline
| Min | Do |
|---|---|
| 5–9 | The double-tap story; the state diagram; say "putIfAbsent + future, outside the lock". |
| 9–14 | Code step 1: `PaymentStatus`, `PaymentRequest`, `PaymentResult`, `Payment` (transition table). |
| 14–18 | Code step 2: `PaymentProcessor` + `CardProcessor`. |
| 18–32 | Code step 3: **`PaymentGateway.pay`** (idempotency) + `processNew` + `refund`. |
| 32–37 | Dry run: 3 concurrent duplicates; then the same key with a different amount. |
| 37–45 | Follow-ups: timeouts, partial refunds, many servers. |

### The code you write, in this order

**Step 1: model** ([model/](model/))
```java
public enum PaymentStatus { PENDING, PROCESSING, SUCCESS, FAILED, REFUND_PENDING, REFUNDED }

public class Payment {
    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED = new EnumMap<>(PaymentStatus.class);
    static {
        ALLOWED.put(PaymentStatus.PENDING,        EnumSet.of(PaymentStatus.PROCESSING, PaymentStatus.FAILED));
        ALLOWED.put(PaymentStatus.PROCESSING,     EnumSet.of(PaymentStatus.SUCCESS, PaymentStatus.FAILED));
        ALLOWED.put(PaymentStatus.SUCCESS,        EnumSet.of(PaymentStatus.REFUND_PENDING));
        ALLOWED.put(PaymentStatus.REFUND_PENDING, EnumSet.of(PaymentStatus.REFUNDED, PaymentStatus.SUCCESS));
        ALLOWED.put(PaymentStatus.FAILED,         EnumSet.noneOf(PaymentStatus.class));
        ALLOWED.put(PaymentStatus.REFUNDED,       EnumSet.noneOf(PaymentStatus.class));
    }
    private final String id, idempotencyKey, customerId, currency;
    private final long amountPaise;
    private final PaymentMethod method;
    private PaymentStatus status = PaymentStatus.PENDING;
    private Instant lastUpdatedAt;
    private String errorCode, errorMessage;

    public synchronized void transitionTo(PaymentStatus next, Instant now) {
        if (!ALLOWED.get(status).contains(next)) {
            throw new IllegalStateException("Invalid transition " + status + " → " + next + " for payment " + id);
        }
        status = next;
        lastUpdatedAt = now;
    }

    public synchronized void markFailure(String code, String message, Instant now) {
        transitionTo(PaymentStatus.FAILED, now);
        errorCode = code;
        errorMessage = message;
    }
    // + constructor from PaymentRequest, synchronized getters
}

public class PaymentRequest {                         // amountPaise > 0 validated in the constructor
    private final String idempotencyKey, customerId, currency, description;
    private final long amountPaise;
    private final PaymentMethod method;

    public boolean sameChargeAs(PaymentRequest other) {   // same key + different contents = client bug
        return customerId.equals(other.customerId) && amountPaise == other.amountPaise
                && currency.equals(other.currency) && method == other.method;
    }
    // + accessors
}
// PaymentResult: immutable {paymentId, key, status, errorCode, errorMessage, processedAt} + success()/failed()
```

**Step 2: processors** ([processor/](processor/))
```java
public interface PaymentProcessor {
    boolean supports(PaymentMethod method);
    ProcessorResponse process(PaymentRequest request);    // declines are responses, not exceptions
    // ProcessorResponse { success, errorCode, errorMessage } with ok() / fail(code, msg)
}

public class CardProcessor implements PaymentProcessor {  // real one: an Adapter around Razorpay/Stripe
    private static final long ISSUER_LIMIT_PAISE = 100_000L;
    public boolean supports(PaymentMethod m) { return m == PaymentMethod.CARD; }
    public ProcessorResponse process(PaymentRequest r) {
        return r.amountPaise() > ISSUER_LIMIT_PAISE
                ? ProcessorResponse.fail("CARD_DECLINED", "amount exceeds issuer limit")
                : ProcessorResponse.ok();
    }
}
```

**Step 3: the gateway, the crux** ([PaymentGateway.java](PaymentGateway.java))
```java
public class PaymentGateway {
    private final List<PaymentProcessor> processors;
    private final ConcurrentHashMap<String, InFlight> idempotencyCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Payment> payments = new ConcurrentHashMap<>();
    private final Clock clock;

    public PaymentResult pay(PaymentRequest request) {
        InFlight mine = new InFlight(request);
        InFlight existing = idempotencyCache.putIfAbsent(request.idempotencyKey(), mine);   // atomic claim
        if (existing != null) {                                   // retry or concurrent duplicate
            if (!existing.request.sameChargeAs(request)) {
                throw new IllegalArgumentException("Idempotency key " + request.idempotencyKey()
                        + " was already used for a different payment");
            }
            return existing.result.join();                        // wait for the first call's answer
        }
        try {                                                     // we own the key; slow work OUTSIDE the map
            PaymentResult result = processNew(request);
            mine.result.complete(result);
            return result;
        } catch (RuntimeException e) {
            mine.result.completeExceptionally(e);
            throw e;
        }
    }

    private PaymentResult processNew(PaymentRequest request) {
        Payment payment = new Payment(UUID.randomUUID().toString(), request, clock.instant());
        payments.put(payment.getId(), payment);
        PaymentProcessor processor = selectProcessor(request.method());
        if (processor == null) {
            payment.markFailure("NO_PROCESSOR", "No processor for method " + request.method(), clock.instant());
            return PaymentResult.failed(payment.getId(), request.idempotencyKey(), payment.getErrorCode(), payment.getErrorMessage(), payment.getLastUpdatedAt());
        }
        payment.transitionTo(PaymentStatus.PROCESSING, clock.instant());
        ProcessorResponse response;
        try {
            response = processor.process(request);
        } catch (Exception e) {
            payment.markFailure("PROCESSOR_THREW", e.getMessage(), clock.instant());
            return PaymentResult.failed(payment.getId(), request.idempotencyKey(), payment.getErrorCode(), payment.getErrorMessage(), payment.getLastUpdatedAt());
        }
        if (response.success()) {
            payment.transitionTo(PaymentStatus.SUCCESS, clock.instant());
            return PaymentResult.success(payment.getId(), request.idempotencyKey(), payment.getLastUpdatedAt());
        }
        payment.markFailure(response.errorCode(), response.errorMessage(), clock.instant());
        return PaymentResult.failed(payment.getId(), request.idempotencyKey(), payment.getErrorCode(), payment.getErrorMessage(), payment.getLastUpdatedAt());
    }

    public PaymentResult refund(String paymentId) {
        Payment payment = getPayment(paymentId);
        synchronized (payment) {                                  // two refund clicks: only one passes the table
            payment.transitionTo(PaymentStatus.REFUND_PENDING, clock.instant());
            payment.transitionTo(PaymentStatus.REFUNDED, clock.instant());   // real life: call the processor's refund API
        }
        return new PaymentResult(payment.getId(), payment.getIdempotencyKey(), payment.getStatus(), null, null, payment.getLastUpdatedAt());
    }

    private static final class InFlight {                         // one per key: original request + its result
        final PaymentRequest request;
        final CompletableFuture<PaymentResult> result = new CompletableFuture<>();
        InFlight(PaymentRequest request) { this.request = request; }
    }
    // + selectProcessor (first that supports the method), getPayment
}
```
**Shape to remember:** `putIfAbsent` → existing? (same charge? `join` : reject) : process outside the lock → complete the future.

### Dry run
```
Three threads, key "idem-burst", ₹25:
  T1 putIfAbsent → null → owns the key → bank call (slow) ...
  T2 putIfAbsent → T1's InFlight → same charge → join() ... waits
  T3 same → waits
  T1: SUCCESS → complete(result) → T2 and T3 wake with the SAME result. Bank called once.
Later: key "idem-burst", ₹99 → existing, sameChargeAs false → IllegalArgumentException
refund(id) → SUCCESS → REFUND_PENDING → REFUNDED;  refund(id) again → REFUNDED → REFUND_PENDING invalid → rejected
```
The driver fires 50 threads with one key: 1 processor call, 50 identical results.

---

## 5. Follow-ups: answer out loud first, then open

<details>
<summary><b>Q1. The idempotency cache grows forever.</b></summary>

Keys only need to live as long as clients might retry: typically 24 hours. Store `{key, request fingerprint, result, createdAt}` in Redis or a DB with a **TTL** and evict after it. A retry after expiry is treated as a new payment, which is why clients must reuse the key only for genuine retries of the same attempt.
</details>

<details>
<summary><b>Q2. Many gateway servers: the in-memory map doesn't help.</b></summary>

Put the key in the database with a **unique constraint**:
```sql
INSERT INTO idempotency (key, request_hash, status) VALUES (?, ?, 'IN_PROGRESS');   -- duplicate key → someone owns it
-- winner: call the processor, then UPDATE idempotency SET status='DONE', result=? WHERE key=?
-- loser: SELECT result; if still IN_PROGRESS, return "processing" (HTTP 409) or poll briefly
```
Same idea as the in-memory version: atomic claim, then the slow work, then publish the result. Compare `request_hash` to reject reused keys.
</details>

<details>
<summary><b>Q3. The bank call times out. Did the payment go through?</b></summary>

You **don't know**. A timeout is not a failure. Mark the payment `UNKNOWN` (a new state), never auto-retry with a new charge, and **reconcile**: query the processor's status API with the same reference, or wait for its webhook / end-of-day settlement file. Telling the user "failed" when the bank actually charged them is the worst outcome. The base code treats a thrown exception as FAILED; say that's a simplification.
</details>

<details>
<summary><b>Q4. Partial refunds (refund ₹300 of a ₹1,000 order, then ₹700).</b></summary>

Track the refunded amount; never exceed what was paid. Each refund also gets its own idempotency key:
```java
private final long paidPaise;
private long refundedPaise;
private final Map<String, Long> refundsByKey = new HashMap<>();     // refund key → amount

public synchronized long refund(String refundKey, long amountPaise) {
    if (refundsByKey.containsKey(refundKey)) return refundsByKey.get(refundKey);   // retried refund: don't pay twice
    if (amountPaise <= 0) throw new IllegalArgumentException("Refund must be > 0");
    if (refundedPaise + amountPaise > paidPaise) {
        throw new IllegalStateException("Refund exceeds remaining " + (paidPaise - refundedPaise) + " paise");
    }
    refundedPaise += amountPaise;
    refundsByKey.put(refundKey, amountPaise);
    return amountPaise;
}
```
The status becomes PARTIALLY_REFUNDED until `refundedPaise == paidPaise`, then REFUNDED.
</details>

<details>
<summary><b>Q5. One card processor is down. Fail over.</b></summary>

Several processors support CARD. A **routing Strategy** ranks them (by success rate over the last 5 minutes, or by cost) and skips any whose **circuit breaker** is open (too many recent failures → stop sending for 30 s). Fail over only on errors that guarantee no charge happened (connection refused). **Never** fail over after a timeout (Q3), or you may charge twice.
</details>

<details>
<summary><b>Q6. Keep the books right.</b></summary>

A **double-entry ledger**: every money movement is two rows that sum to zero (customer −₹499, merchant +₹499; a refund reverses them). Balances are derived from the ledger, never edited directly. A daily job compares the ledger with the processors' settlement files and flags any mismatch.
</details>

<details>
<summary><b>Q7. How would you know it's working in production?</b></summary>

- **Metrics:** success rate per processor and method, p99 latency, decline reasons, idempotency hits (retries), payments in UNKNOWN, refund volume.
- **Alarms:** a success-rate drop for one processor (fail over), a growing UNKNOWN count (reconcile), and any ledger mismatch.
- **Logs:** paymentId + idempotency key + processor reference on every state change. Never log card numbers (PCI).
</details>

<details>
<summary><b>Q8. How do you test it?</b></summary>

Happy path, the same key three times → 1 processor call, a card decline with its reason, refund, invalid transitions (refund FAILED, double refund), 50 threads with one key → 1 call and 50 identical results, and a key reused with a different amount → rejected. All are in the driver.
</details>

---

## 6. Traps
1. No idempotency, or check-then-insert (two threads both see "not seen").
2. Doing the slow bank call inside `computeIfAbsent` or a global lock.
3. Returning a cached result for a reused key with a different amount.
4. Treating a timeout as a failure and retrying with a new charge.
5. `double` money; refunds allowed from any status.
6. Logging sensitive card data.

## 7. Recall check
1. Walk through 3 concurrent `pay()` calls with the same key.
2. Why `putIfAbsent` + a future rather than `computeIfAbsent`?
3. What happens when a key is reused for a different amount, and why reject it?
4. Draw the state table. Which transitions does `refund` use?
5. A timeout: what status, and what do you do next?

**Rebuild in 12 minutes:** `Payment` transition table + `transitionTo` · `PaymentProcessor` + `CardProcessor` · `PaymentGateway.pay` (putIfAbsent → join/reject or process + complete) · `processNew` · `refund`.

---

**Files:** `PaymentGateway` · `model/` (`Payment`, `PaymentStatus`, `PaymentMethod`, `PaymentRequest`, `PaymentResult`) · `processor/` (`PaymentProcessor`, `CardProcessor`, `UpiProcessor`, `NetBankingProcessor`) · `PaymentGatewayDriver` (happy path, idempotency, decline, refund, invalid transitions, 50-thread same key, reused key)
```bash
mvn -q compile exec:java -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.policies.paymentgateway.PaymentGatewayDriver
```
