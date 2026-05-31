# Payment Gateway — 45-min LLD Interview Walkthrough

**Target role:** SDE‑2 (Amazon, Adobe, Microsoft, Atlassian, etc.)

> Payment Gateway is the **canonical "idempotency + state machine + Strategy" problem**. Three signals separate senior from mid: (a) **idempotency via `computeIfAbsent` on a concurrent map** so 50 concurrent retries cause exactly ONE processor call, (b) **a guarded state machine on `Payment`** so refunds can't move FAILED → REFUNDED (or anything else illegal), and (c) **Strategy** for the processor selection — and naming all three patterns honestly without over-engineering.

---

## Time budget (45 min)

| Step | Activity                                                                                | Budget   | Cumulative |
| ---- | --------------------------------------------------------------------------------------- | -------- | ---------- |
| 1    | Requirements                                                                            | ~5 min   | 5          |
| 2    | Entities & Relationships                                                                | ~4 min   | 9          |
| 3    | Class Design (Strategy + state machine on Payment)                                      | ~10 min  | 19         |
| 4    | Implementation (`pay` w/ idempotency + state transitions + refund + dry-run)            | ~17 min  | 36         |
| 5    | Extensibility (retries, webhooks, multi-processor fallback, fraud detection chain)      | ~8 min   | 44         |
| —    | Wrap & questions                                                                        | ~1 min   | 45         |

Step 4 is the longest — idempotency + state machine + refund flow share the spotlight.

Watch the clock at minute **5** (Step 1 done), minute **19** (start coding), minute **36** (extensibility).

---

## Mental models — internalize these BEFORE you walk in

### M1. Idempotency via concurrent `computeIfAbsent` — the senior trick

```
   Two browsers retry the same purchase 50 times because of a flaky network.
   All 50 requests carry the SAME idempotencyKey.

   ConcurrentHashMap.computeIfAbsent(key, k -> processNew(request))
      ↑                                ↑
      atomic-per-key                  runs at MOST ONCE per key

   Thread A: enters computeIfAbsent("idem-123")  → runs processNew, charges card
   Thread B: enters computeIfAbsent("idem-123")  → BLOCKS until A completes
                                                   → returns A's cached result
   Thread C-Z (47 more):                          → each sees the cache populated
                                                   → return cached result without
                                                     touching the processor

   processor.process() called: EXACTLY ONCE
   PaymentResult returned by gateway: 50 IDENTICAL results

   Cost: zero outer locks on the gateway. The map's per-key atomicity is enough.
```

### M2. Payment state machine — refunds can't cheat

```
                  transitionTo(PROCESSING)         transitionTo(SUCCESS)
   PENDING ───────────────────────────→ PROCESSING ──────────────────→ SUCCESS
       │                                      │                              │
       │              transitionTo(FAILED)    │                              │ transitionTo(REFUND_PENDING)
       │ ─────────────────────────────────────┴─────→ FAILED ✗ terminal      │
       │                                                                     v
       │                                                              REFUND_PENDING
       │                                                                     │
       │                                                                     │ transitionTo(REFUNDED)
       │                                                                     v
       │                                                              REFUNDED ✗ terminal
       v
   Map<PaymentStatus, Set<PaymentStatus>> ALLOWED_TRANSITIONS
   transitionTo(next) throws IllegalStateException if next ∉ allowed[status]

   ⇒ refund(FAILED payment)     → throws (FAILED is terminal, has no outbound edges)
   ⇒ refund(REFUNDED payment)   → throws (REFUNDED is terminal)
   ⇒ refund(PENDING payment)    → throws (no PENDING → REFUND_PENDING edge)
   ⇒ refund(SUCCESS payment)    → ✓ legal
```

**Senior soundbite (memorize):** *"Payment's status is the only mutable bit on the class, and it's guarded by an `EnumMap<Status, EnumSet<Status>>` of allowed transitions. Every move calls `transitionTo` which throws on illegal targets — so SUCCESS can't regress to PENDING, FAILED is terminal, refunds can only originate from SUCCESS. The state machine is invariant — not enforced by callers."*

### M3. Strategy for processor selection — one impl per payment method

```
   Gateway receives PaymentRequest(method=CARD)
        |
        v
   for (PaymentProcessor p : processors)
       if (p.supports(method)) return p;
   return null;     ← caller marks FAILED with NO_PROCESSOR

   Adding a new method (e.g., WALLET) is ONE new class:
      class WalletProcessor implements PaymentProcessor {
          public boolean supports(PaymentMethod m) { return m == WALLET; }
          public ProcessorResponse process(...) { ... }
      }
   plus ONE line at construction (add to the processors list).
   Gateway code: zero changes.

   Why an interface (Strategy) and not a switch on PaymentMethod?
     - Each processor has its own state (auth token, retry policy, vendor SDK).
     - Each processor's `process` has its own failure modes (CARD_DECLINED vs
       UPI_TIMEOUT). They don't share a base.
     - The switch couples Gateway to every processor — Strategy decouples them.
```

---

## STEP 1 — Requirements (~5 min)

### What to say out loud (opener)
> "Payment Gateway has two correctness traps everyone falls into — double-charging on retries, and refunds that can corrupt status. Both are solved by idempotency + a state machine. Let me clarify scope on both."

### Probe the 4 themes

| Theme               | Question to ask                                                                                              |
| ------------------- | ------------------------------------------------------------------------------------------------------------ |
| Primary capabilities| "Pay (charge), refund, query payment status. Multiple methods (card / UPI / netbanking)?"                    |
| Rules / completion  | "Idempotency key per request? Same key submitted twice → at most ONE actual charge? State machine on payment status — no refunds on FAILED?" |
| Error handling      | "Processor failures → FAILED with specific error code? Unknown method → reject? Concurrent retries?" |
| Concurrency         | "Multi-threaded — exactly one processor call per idempotency key under contention?" |
| Scope boundaries    | "Out: webhook delivery, reconciliation, multi-currency FX, persistence, distributed. Confirm?" |

### What to write on the board

```
Functional Requirements
1. pay(PaymentRequest) → PaymentResult. Idempotency key on the request.
2. Same idempotency key submitted N times → exactly ONE processor call;
   subsequent calls return the cached result.
3. Three payment methods: CARD, UPI, NETBANKING. Each routes to its own processor.
4. State machine on Payment:
     PENDING → PROCESSING → SUCCESS | FAILED
     SUCCESS → REFUND_PENDING → REFUNDED
     FAILED and REFUNDED are terminal — no further transitions.
5. refund(paymentId) only valid when status == SUCCESS; rejected otherwise.
6. Distinct exception types so callers can branch on failure mode.
7. Thread-safe under concurrent retries on the same key.

Out of Scope
- Webhook delivery to merchant systems (Observer in Step 5)
- Multi-processor fallback (e.g., card fails → try wallet) — Step 5
- Multi-currency FX rates
- Fraud detection pipeline — Step 5 (mention Chain of Responsibility)
- Persistence / distributed
- Reconciliation / settlement files
- Refunds with partial amounts (full refund only in v1)
```

### Close the step
> "Two load-bearing requirements: idempotency under concurrent retries, and the state-machine guard on refund. Those are where this problem's senior signal lives."

---

## STEP 2 — Entities & Relationships (~4 min)

### What to say out loud
> "Six types: **PaymentGateway** (orchestrator + facade), **Payment** (mutable — owns the state machine), **PaymentRequest** / **PaymentResult** (immutable DTOs at the boundary), **PaymentProcessor** (Strategy interface), **PaymentStatus** / **PaymentMethod** (enums). Strategy is in the base because the requirements enumerate three processors and adding a fourth is a one-class change."

### Why no `IdempotencyKey` class
> "It's a string. The validation (non-null, non-empty) lives on PaymentRequest's constructor. Promoting it to a class would be pure ceremony — no behavior to add."

### Why Payment is mutable but PaymentRequest / PaymentResult are records
> "Payment IS the state machine — its status evolves through the lifecycle. Mutation is the whole point. Request and Result are snapshots at the API boundary — caller submits one, receives one. Immutability for both prevents accidental tampering between the gateway and external callers."

### What to write on the board

```
Entities
- PaymentGateway      (orchestrator + facade: pay, refund, getPayment)
- Payment             (MUTABLE — owns the state machine; transitionTo guards)
- PaymentRequest      (immutable record — input DTO)
- PaymentResult       (immutable record — output DTO; factory methods .success/.failed)
- PaymentProcessor    (interface — Strategy; one impl per method)
- (impls: CardProcessor, UpiProcessor, NetBankingProcessor)

Enums
- PaymentStatus       { PENDING, PROCESSING, SUCCESS, FAILED, REFUND_PENDING, REFUNDED }
- PaymentMethod       { CARD, UPI, NETBANKING }

NOT entities
- IdempotencyKey      (it's a String)
- Currency            (single-currency v1; long cents elsewhere)
- Customer            (external; customerId is a String reference)
- Money               (long cents + currency String is enough)

Relationships
- PaymentGateway owns:
    ConcurrentHashMap<String, PaymentResult> idempotencyCache    (key = idempotencyKey)
    ConcurrentHashMap<String, Payment>       payments            (key = paymentId)
    List<PaymentProcessor>                   processors          (Strategy registry)
- Payment ↔ PaymentRequest: gateway constructs Payment from Request once.
- Payment ↔ PaymentResult: gateway derives Result from Payment.
- Processor selection: routed by PaymentMethod via processor.supports(method).
```

### Diagram — boxes and arrows

```
                  +------------------------------+
                  |        PaymentGateway        |   <- orchestrator + facade
                  |   pay / refund / getPayment  |
                  +------------------------------+
                       |               |
                  owns | (3 maps + list of processors)
                       v               v
        ConcurrentHashMap<String, PaymentResult>     idempotencyCache (atomic dedup)
        ConcurrentHashMap<String, Payment>           payments (lifecycle store)
        List<PaymentProcessor>                       processors (Strategy registry)
                                  |
                                  v
                       +----------------------+
                       | <<interface>>        |    impls (one per method):
                       | PaymentProcessor     | <-- CardProcessor
                       +----------------------+    UpiProcessor
                       | + supports(method)   |    NetBankingProcessor
                       | + process(request)   |
                       +----------------------+

Payment (mutable; owns state machine)
   PENDING ── transitionTo ──→ PROCESSING ── transitionTo ──→ SUCCESS ── transitionTo ──→ REFUND_PENDING ──→ REFUNDED
                                       ╰─→ FAILED (terminal)
```

---

## STEP 3 — Class Design (~10 min)

### PaymentGateway — state ↔ requirement table

| Requirement                              | State PaymentGateway must own                              |
| ---------------------------------------- | ---------------------------------------------------------- |
| Idempotency on same-key retries          | `ConcurrentHashMap<String, PaymentResult> idempotencyCache` |
| Payment lifecycle store                  | `ConcurrentHashMap<String, Payment> payments`              |
| Strategy registry                        | `List<PaymentProcessor> processors`                        |
| Time injection (test determinism)        | `Clock clock`                                              |

### PaymentGateway — behavior table

| Need from requirements              | Method                                                  |
| ----------------------------------- | ------------------------------------------------------- |
| Charge — idempotent                  | `PaymentResult pay(PaymentRequest request)`             |
| Refund — state-machine-guarded      | `PaymentResult refund(String paymentId)`                |
| Inspect a payment                    | `Payment getPayment(String paymentId)`                  |

### PaymentGateway — class outline (write this on the board)

```java
public class PaymentGateway {
    private final List<PaymentProcessor> processors;
    private final ConcurrentHashMap<String, PaymentResult> idempotencyCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Payment>       payments         = new ConcurrentHashMap<>();
    private final Clock clock;

    public PaymentResult pay(PaymentRequest request) {
        // atomic per-key dedup — same key, same lambda invocation, same result
        return idempotencyCache.computeIfAbsent(request.idempotencyKey(), key -> processNew(request));
    }

    public PaymentResult refund(String paymentId) { /* state-machine-guarded */ }

    private PaymentResult processNew(PaymentRequest request) { /* Step 4 */ }
}
```

### Payment — the state machine class

```java
public class Payment {
    // EnumMap + EnumSet — right Map and Set types for enum keys
    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(...);
    static {
        ALLOWED_TRANSITIONS.put(PENDING,        EnumSet.of(PROCESSING, FAILED));
        ALLOWED_TRANSITIONS.put(PROCESSING,     EnumSet.of(SUCCESS,    FAILED));
        ALLOWED_TRANSITIONS.put(SUCCESS,        EnumSet.of(REFUND_PENDING));
        ALLOWED_TRANSITIONS.put(REFUND_PENDING, EnumSet.of(REFUNDED));
        ALLOWED_TRANSITIONS.put(FAILED,         EnumSet.noneOf(PaymentStatus.class));   // terminal
        ALLOWED_TRANSITIONS.put(REFUNDED,       EnumSet.noneOf(PaymentStatus.class));   // terminal
    }

    private final String id;
    // ... other final fields ...
    private PaymentStatus status;          // ONLY mutable field

    public void transitionTo(PaymentStatus next, Instant now) {
        if (!ALLOWED_TRANSITIONS.get(status).contains(next)) {
            throw new IllegalStateException("Invalid transition " + status + " → " + next);
        }
        this.status = next;
        this.lastUpdatedAt = now;
    }
}
```

### PaymentProcessor — Strategy interface

```java
public interface PaymentProcessor {
    boolean supports(PaymentMethod method);
    ProcessorResponse process(PaymentRequest request);

    record ProcessorResponse(boolean success, String errorCode, String errorMessage) {
        public static ProcessorResponse ok() { return new ProcessorResponse(true, null, null); }
        public static ProcessorResponse fail(String code, String message) {
            return new ProcessorResponse(false, code, message);
        }
    }
}
```

### Records — immutable DTOs

```java
public record PaymentRequest(
    String idempotencyKey, String customerId, long amountCents,
    String currency, PaymentMethod method, String description) { /* + null checks */ }

public record PaymentResult(
    String paymentId, String idempotencyKey, PaymentStatus status,
    String errorCode, String errorMessage, Instant processedAt) {
    public static PaymentResult success(...) { ... }
    public static PaymentResult failed(...)  { ... }
}
```

### The principle to verbalize — Strategy + Information Expert
> "Strategy lets each processor own its own external integration, error codes, and retry policy — the gateway doesn't know what's inside. The state machine lives on Payment because only Payment knows its own status — Information Expert. The gateway never reads `payment.status` to decide what's allowed; it just calls `payment.transitionTo(...)` and lets the payment object enforce the rule."

---

## STEP 4 — Implementation (~17 min)

### Open by asking
> "Real Java or pseudo-code? I'll do `pay` first — it's where idempotency + state-machine + Strategy all converge — then `refund` to show the state-machine guard in action, then dry-run a 50-thread same-key burst."

### 4.1 `pay` — the atomic idempotency entry point

```java
public PaymentResult pay(PaymentRequest request) {
    return idempotencyCache.computeIfAbsent(request.idempotencyKey(), key -> processNew(request));
}
```

> **The whole method is one line.** `ConcurrentHashMap.computeIfAbsent` is atomic per key — Java guarantees the lambda runs at most once per key under any contention. The rest is just `processNew` which does the real work.

> **Senior callout:** *"Two threads with the same key entering `pay` simultaneously cannot both invoke `processNew`. The first acquires the per-bucket lock inside the map, runs the lambda, populates the cache. The second waits, then reads the cached value and returns it. ONE processor call, two identical results. No outer lock on the gateway, no double-charge."*

### 4.2 `processNew` — runs at most once per idempotency key

```java
private PaymentResult processNew(PaymentRequest request) {
    Instant now = clock.instant();
    Payment payment = new Payment(UUID.randomUUID().toString(), request, now);
    payments.put(payment.getId(), payment);

    PaymentProcessor processor = selectProcessor(request.method());
    if (processor == null) {
        payment.markFailure("NO_PROCESSOR", "No processor for " + request.method(), clock.instant());
        return PaymentResult.failed(payment.getId(), request.idempotencyKey(),
                payment.getErrorCode(), payment.getErrorMessage(), payment.getLastUpdatedAt());
    }

    payment.transitionTo(PaymentStatus.PROCESSING, clock.instant());
    ProcessorResponse response;
    try {
        response = processor.process(request);
    } catch (Throwable t) {
        // Processor exceptions become FAILED — never propagate.
        payment.markFailure("PROCESSOR_THREW", t.getMessage(), clock.instant());
        return PaymentResult.failed(...);
    }

    if (response.success()) {
        payment.transitionTo(PaymentStatus.SUCCESS, clock.instant());
        return PaymentResult.success(payment.getId(), request.idempotencyKey(), payment.getLastUpdatedAt());
    }
    payment.markFailure(response.errorCode(), response.errorMessage(), clock.instant());
    return PaymentResult.failed(...);
}
```

**Three callouts to deliver out loud:**

1. *"Processor exceptions are caught and converted to FAILED — they do NOT propagate. A vendor SDK throwing must not crash the gateway thread or the caller. The gateway's contract is 'returns a result or throws only for invalid input'."*

2. *"State transitions are sequential — PENDING → PROCESSING → SUCCESS/FAILED. Each call to `transitionTo` is checked against the allowed-transitions adjacency. If we ever rewrote `processNew` and accidentally tried PROCESSING → REFUNDED, the state machine would throw immediately."*

3. *"`payments.put` happens BEFORE the processor call. So a thread looking up the payment mid-processing sees it in PROCESSING state. That's correct — the lifecycle is observable from the moment we commit to it."*

### 4.3 `refund` — state-machine-guarded

```java
public PaymentResult refund(String paymentId) {
    Payment payment = payments.get(paymentId);
    if (payment == null) throw new NoSuchElementException("Unknown payment id");

    synchronized (payment) {
        payment.transitionTo(PaymentStatus.REFUND_PENDING, clock.instant());  // throws if status != SUCCESS
        payment.transitionTo(PaymentStatus.REFUNDED,       clock.instant());  // throws if double-refund
    }
    return new PaymentResult(payment.getId(), payment.getIdempotencyKey(),
            payment.getStatus(), null, null, payment.getLastUpdatedAt());
}
```

> **Senior callout:** *"`refund` does nothing but call `transitionTo` twice. The state machine on Payment is what rejects illegal refunds. `refund(FAILED)` throws because FAILED has no outbound edges. `refund(REFUNDED)` throws because REFUNDED is terminal. `refund(PENDING)` throws because PENDING → REFUND_PENDING is not in the allowed-transitions map. The gateway has no `if status == SUCCESS` checks — they live on Payment, which is correct."*

### 4.4 Strategy selection

```java
private PaymentProcessor selectProcessor(PaymentMethod method) {
    for (PaymentProcessor p : processors) {
        if (p.supports(method)) return p;
    }
    return null;
}
```

> *"Linear scan over a list of three. If processor count grows, replace with a `Map<PaymentMethod, PaymentProcessor>` built at construction — O(1) lookup. For three, the list is right-sized."*

### 4.5 Verification — dry-run the 50-thread same-key scenario

```
Setup: gateway with a CountingCardProcessor (tracks invocation count).
       50 threads await on CountDownLatch, then all call pay(req) with idempotencyKey="idem-burst".

t=0  fire.countDown()  → all 50 threads release simultaneously
t=1  Thread A enters idempotencyCache.computeIfAbsent("idem-burst", ...)
     → no cache entry → lambda executes → processNew runs → processor.process() called
t=2  Threads B–Z enter computeIfAbsent("idem-burst", ...)
     → BLOCK on the per-bucket lock (Java ConcurrentHashMap internals)
t=3  Thread A completes processNew → returns PaymentResult.success → cache populated
t=4  Threads B–Z unblock → see the cache entry → return it (without invoking processNew)

Counts after termination:
   processor.count = 1                                  (lambda ran once)
   successful results = 50                              (every caller got the cached SUCCESS)
   distinct paymentIds in results = 1                   (all 50 reference the same Payment)

The driver in this folder runs exactly this — output confirms:
   processor invocations: 1 (expect 1)
   successful results returned: 50 (expect 50)
   ✓ idempotency holds under contention
```

> **This is the single most-important test in this problem.** Mention you'd verify it with a `CountDownLatch` barrier and assert `processor.count == 1` AND `all 50 results refer to the same paymentId`.

### 4.6 Verification — refund state machine

```
Setup: gateway, three payments — one SUCCESS (UPI), one FAILED (card over limit), one already REFUNDED.

refund(success.id):
   payment.transitionTo(REFUND_PENDING)  ✓ (SUCCESS → REFUND_PENDING allowed)
   payment.transitionTo(REFUNDED)        ✓
   Payment.status = REFUNDED                                            ✓

refund(failed.id):
   payment.transitionTo(REFUND_PENDING)  ✗ throws IllegalStateException
   "Invalid transition FAILED → REFUND_PENDING for payment <uuid>"
   Payment.status remains FAILED (unchanged)                            ✓

refund(refunded.id):                        ← already refunded
   payment.transitionTo(REFUND_PENDING)  ✗ throws IllegalStateException
   "Invalid transition REFUNDED → REFUND_PENDING for payment <uuid>"   ✓
```

---

## STEP 5 — Extensibility (~8 min)

### 5.1 "How do you handle webhook delivery to merchant systems?"

> **Problem in current design:** *"Payment success / failure is returned synchronously to the caller, but merchant backends often need a separate async callback to update their order systems."*
>
> **Pattern as the fix:** *"Observer. Add `PaymentListener` interface with `onPaymentSucceeded(Payment)` and `onPaymentFailed(Payment)`. PaymentGateway publishes events after each state transition. Webhook delivery, email notification, analytics ingestion all subscribe independently — same fire-outside-lock pattern as Inventory's alerts. The gateway never knows what listeners do."*

### 5.2 "Multi-processor fallback — card fails, try wallet"

> **Problem in current design:** *"Today one method → one processor. Real systems retry across processors when the primary fails (e.g., Visa down → try Razorpay → try PayU)."*
>
> **Pattern as the fix:** *"Promote `selectProcessor` to a Strategy — `ProcessorSelector` interface with `FirstMatch` (current) and `FallbackChain` (try each in order) implementations. Or wrap the existing strategy in a `RetryingProcessor` Decorator that delegates to multiple processors in sequence on failure."*

### 5.3 "Fraud detection pipeline"

> **Problem in current design:** *"A real gateway runs fraud rules (velocity checks, geo anomalies, blacklist lookups) BEFORE charging."*
>
> **Pattern as the fix:** *"**Chain of Responsibility**. `PaymentHandler` interface; concrete handlers `ValidationHandler → FraudCheckHandler → ProcessingHandler → LedgerHandler` arranged as a chain. Each handler either rejects (returns FAILED with reason) or delegates to the next. Decouples fraud rules from the core charging logic."*

### 5.4 "Retries with exponential backoff for transient failures"

> **Problem in current design:** *"A 503 from the vendor is treated as a definitive FAILED. In reality, transient errors should be retried."*
>
> **Pattern as the fix:** *"Wrap each PaymentProcessor in a `RetryingProcessor(PaymentProcessor delegate, RetryPolicy policy)` Decorator. The wrapper catches transient errors (using error codes the vendor SDK exposes), waits per policy (exponential backoff), retries — and falls through to FAILED only after the policy is exhausted. Idempotency keys mean retries are safe."*

### 5.5 Other "what-if" answers

| Follow-up                                  | Answer                                                                                              |
| ------------------------------------------ | --------------------------------------------------------------------------------------------------- |
| "Partial refunds (refund $5 of a $20 charge)" | Add `refund(paymentId, amountCents)` and `Map<String, Long> refundedAmounts` on Payment. Track cumulative refunds; reject when cumulative exceeds original. |
| "Multi-currency"                           | `Money(amountCents, currency)` record. PaymentProcessor decides whether to accept; FX is an FxRateProvider Strategy. |
| "Audit log — every state change"           | `Payment.transitionTo` emits an event to an internal event log. Same Observer pattern.            |
| "Reconciliation with processor settlement files" | Out of scope, but mention — daily batch job matches `payments` against vendor files, flags discrepancies. |
| "Distributed across pods"                  | Idempotency cache moves to Redis (key = idempotencyKey, value = serialized PaymentResult, TTL = 24h). `computeIfAbsent` semantics via Lua script. |
| "Hot config reload (block a card BIN)"     | Inject a `FraudPolicy` that's an `AtomicReference<RuleSet>` — atomic swap on update.              |

---

## Design Patterns — Hello Interview's canonical 8

> **Three patterns earn rent in the base:**
> - **Strategy (#1)** for PaymentProcessor — justified by R3's explicit enumeration of methods.
> - **State Machine (#3)** on Payment — justified by R4's enumeration of allowed transitions.
> - **Facade (#8)** on PaymentGateway — the orchestrator.

### How this maps to PaymentGateway specifically

**Already in the BASE design — call out by name:**

- **Strategy (#1)** ⭐ — PaymentProcessor with three impls. Name it in Step 2.
- **State Machine (#3)** — Payment owns `EnumMap<Status, EnumSet<Status>>` of allowed transitions. **Draw the state diagram in Step 3.**
- **Facade (#8)** — PaymentGateway is the only class application code touches.
- **Information Expert** (GRASP) — the state machine lives on Payment because only Payment knows its status. Gateway never reads `payment.status` to decide what's allowed.
- **Dependency Injection** (principle) — `Clock` injected; List of processors injected.
- **Immutability** (principle) — Request, Result are records. Payment's non-status fields are all final.

**Reach for these on Step-5 follow-ups:**

| Follow-up                                  | Pattern (HI's 8)             | Your line                                                                                            |
| ------------------------------------------ | ---------------------------- | ---------------------------------------------------------------------------------------------------- |
| "Webhooks / notifications"                 | **Observer (#2)**            | *"PaymentGateway publishes events; webhook deliverers, email, analytics subscribe independently."* |
| "Retries with backoff"                     | **Decorator (#7)**           | *"`RetryingProcessor(PaymentProcessor delegate, RetryPolicy policy)` wraps any processor."*       |
| "Fraud check pipeline"                     | **Chain of Responsibility** (not HI's 8) | *"PaymentHandler chain — Validation → Fraud → Processing → Ledger. Mention by name; CoR isn't in HI's 8 but it's the right pattern for sequential filters."* |
| "Multi-processor fallback"                 | **Strategy (#1)** ⭐         | *"`ProcessorSelector` interface — `FirstMatch` (current default) and `FallbackChain` implementations."* |
| "Different routing per merchant tier"      | **Strategy (#1)** ⭐         | *"`RoutingStrategy` chooses processor based on merchant tier, time of day, vendor uptime."*       |

**Patterns to actively refuse:**

- **Singleton on PaymentGateway** — kills tests; DI a single instance instead.
- **Builder for the 2-arg `PaymentGateway(processors, clock)` ctor** — academic noise.
- **Full GoF State pattern** (one class per status) — overkill when the state machine is 6 states with no per-state behavior. `EnumMap<Status, EnumSet<Status>>` is correct here.
- **Visitor over Payment** — wrong shape; the machine is mutable state, not a heterogeneous tree.

### One sentence to say at the end of Step 3

> *"The base design names three patterns out loud — Strategy on PaymentProcessor, State Machine on Payment, Facade on PaymentGateway. Plus Information Expert as the principle that keeps the state machine on Payment, not on the gateway. Observer (webhooks), Decorator (retries), and Chain of Responsibility (fraud pipeline) land in Step 5 if those follow-ups surface."*

---

## Interview deep-dives — the questions you'll definitely get asked

### 1. Complexity (Big-O)

Let `P` = total processors, `N` = number of payments in the lifetime, `K` = number of unique idempotency keys.

| Operation                                | Time                                              | Space               | Notes                                                                              |
| ---------------------------------------- | ------------------------------------------------- | ------------------- | ---------------------------------------------------------------------------------- |
| `pay(request)` — cache hit               | **`O(1)`**                                        | O(1) per call       | Map lookup; no processor call                                                       |
| `pay(request)` — cache miss              | **`O(P)`** + cost of `processor.process`           | O(1) per call       | Linear processor scan (P ≤ 3 in practice); processor I/O dominates                  |
| `refund(paymentId)`                      | **`O(1)`** + two `transitionTo` calls              | O(1)                | Map lookup; state-machine check; status update                                     |
| `getPayment(paymentId)`                  | **`O(1)`**                                        | O(1)                | Map lookup                                                                          |
| `processNew` (internal)                  | **`O(P)`** + processor cost                       | O(1)                | Same as cache-miss pay                                                              |
| Storage — idempotencyCache               | -                                                 | **`O(K)`**          | One entry per unique key; cleanup TTL recommended in production                    |
| Storage — payments                       | -                                                 | **`O(N)`**          | Full lifecycle store retained                                                       |

> **Senior callout:** *"`pay` is O(1) for retries and O(P) for new requests where P is tiny (≤3 in practice). The cost of `pay` is dominated by `processor.process` — a network call to the vendor. The map operations are noise compared to that. Storage grows with K (idempotency keys) and N (payments); in production both need TTL-based eviction or a database — out of scope for v1 but call out."*

### 2. Concurrency / thread-safety

| Approach                                | When to use                                  | Cost                                                              |
| --------------------------------------- | -------------------------------------------- | ----------------------------------------------------------------- |
| **`ConcurrentHashMap.computeIfAbsent`** ⭐ | **Default for idempotency.** Atomic per key; no outer lock needed. | Java guarantees the lambda runs at most once per key |
| `synchronized(payment)` for refunds      | Per-payment lock for state transitions      | Different payments never block each other                         |
| Read-write lock on idempotencyCache      | Read-heavy + occasional bulk eviction       | Overkill for current scale                                        |
| Optimistic CAS on Payment.status         | Extreme throughput                          | Painful — state machine validation gets harder                    |

> **The key insight:** *"Idempotency uses `computeIfAbsent` precisely because it's per-key atomic — Java handles the per-bucket locking inside the ConcurrentHashMap. We don't need a `synchronized` on the gateway. For refunds we synchronize on the payment object itself (per-resource locking — same pattern as Movie Ticket's per-Showtime lock, Inventory's per-Warehouse lock)."*

### 3. Testing — what to write tests for

The injected `Clock` makes time deterministic. The CountingCardProcessor pattern makes "did the processor actually run?" measurable.

| Test category                | Cases to cover                                                                                              |
| ---------------------------- | ----------------------------------------------------------------------------------------------------------- |
| Happy path                   | Card payment under limit → SUCCESS                                                                          |
| Idempotency                  | Same key submitted 3 times → 1 processor call, 3 identical results                                          |
| **Concurrent idempotency**   | 50 threads same key → exactly 1 processor call, 50 SUCCESS results, 1 distinct paymentId                    |
| Failure                      | Card over limit → FAILED with errorCode="CARD_DECLINED"                                                     |
| Processor throws             | Mock processor throws → FAILED with errorCode="PROCESSOR_THREW" — never propagates                          |
| State machine — valid path   | PENDING → PROCESSING → SUCCESS → REFUND_PENDING → REFUNDED                                                  |
| State machine — illegal      | refund(FAILED) throws; double-refund throws; refund(PENDING) throws                                         |
| Method routing               | UPI request goes to UpiProcessor, not CardProcessor                                                         |
| Unknown method               | Request with unsupported method → FAILED with errorCode="NO_PROCESSOR"                                      |

```java
@Test
void fifty_threads_same_idempotency_key_exactly_one_processor_call() throws Exception {
    CountingCardProcessor counter = new CountingCardProcessor();
    PaymentGateway pg = new PaymentGateway(List.of(counter));
    PaymentRequest req = new PaymentRequest("idem-x", "cust", 1000L, "USD", PaymentMethod.CARD, "");

    int N = 50;
    ExecutorService pool = Executors.newFixedThreadPool(N);
    CountDownLatch fire = new CountDownLatch(1);
    AtomicInteger successes = new AtomicInteger();

    for (int i = 0; i < N; i++) pool.submit(() -> {
        try { fire.await(); } catch (InterruptedException e) { return; }
        if (pg.pay(req).status() == PaymentStatus.SUCCESS) successes.incrementAndGet();
    });
    fire.countDown();
    pool.shutdown();
    pool.awaitTermination(5, TimeUnit.SECONDS);

    assertEquals(1, counter.count.get());
    assertEquals(N, successes.get());
}
```

### 4. SOLID mapping

| Letter                       | Where it shows up                                                                                          |
| ---------------------------- | ---------------------------------------------------------------------------------------------------------- |
| **S** Single Responsibility  | Gateway = orchestration + idempotency. Payment = state machine. Processor = vendor integration. Request/Result = boundary DTOs. Five reasons to change, five types. |
| **O** Open/Closed            | New payment method = new Processor + one constructor argument. Gateway code unchanged. New state transitions = new edges in the EnumMap. No method body changes. |
| **L** Liskov Substitution    | Any PaymentProcessor substitutable behind the interface — same `supports`/`process` contract. Cannot throw on normal failures (`ProcessorResponse` carries them instead). |
| **I** Interface Segregation  | PaymentProcessor has TWO methods, both narrow. Payment doesn't expose `setStatus(...)` — only `transitionTo` (validated). Gateway exposes 3 narrow methods. |
| **D** Dependency Inversion   | Gateway depends on the `PaymentProcessor` interface, not on `CardProcessor`. `Clock` injected. Processors injected at construction — composition root wires the concretes. |

### 5. "Summarize your design in 30 seconds"

> *"PaymentGateway is the orchestrator + facade — one public method `pay`, one public `refund`. Strategy in the base for processors — one impl per payment method, picked via `supports(method)`. State machine on `Payment` — `EnumMap<Status, EnumSet<Status>>` of allowed transitions; `transitionTo` throws on illegal moves so refunds can't move FAILED → REFUNDED or skip stages. Idempotency via `ConcurrentHashMap.computeIfAbsent` on the idempotencyKey — atomic per-key, no outer lock; 50 concurrent retries cause EXACTLY ONE processor invocation, the other 49 get the cached result. `Clock` injected for deterministic timestamps. Processor exceptions caught and converted to FAILED — never propagate to the caller. Money is `long cents`. Extensions: Observer for webhook delivery, Decorator for retries with exponential backoff, Chain of Responsibility for the fraud pipeline, Strategy variant for multi-processor fallback."*

That's ~50 seconds. Hits: Strategy + state machine + idempotency + the 50-thread proof + extension framing.

---

## Closing soundbites (memorize these)

- **Opening:** *"Two correctness traps — double-charging on retries and refunds corrupting status. Idempotency + state machine solves both."*
- **Why `computeIfAbsent`:** *"It's atomic per key — Java guarantees the lambda runs at most once per key under contention. No outer lock on the gateway. The cleanest possible idempotency primitive."*
- **Why state machine on Payment:** *"Only Payment knows its status — Information Expert. The gateway never asks `if status == SUCCESS`; it just calls `transitionTo` and lets the machine reject."*
- **Why `EnumMap<Status, EnumSet<Status>>`:** *"Right Map and Set for enum keys. Compile-time exhaustiveness; cache-friendly; the most idiomatic Java for state machines."*
- **Processor exceptions never propagate:** *"A vendor SDK throwing must not crash the gateway thread. Catch, convert to FAILED with errorCode='PROCESSOR_THREW'."*
- **On extensibility:** *"Webhooks → Observer. Retries → Decorator. Fraud pipeline → Chain of Responsibility. Multi-processor fallback → Strategy on selection. Four extensions, four different patterns — each justified by its own follow-up."*

---

## Top mistakes that lose points

- **Idempotency via `synchronized` on the gateway** — works but serializes all payments. `computeIfAbsent` is per-key atomic.
- **`if (cache.contains(key)) return cache.get(key); else { process; cache.put; }`** — classic check-then-act race; two threads both pass the check, both process, both cache. The whole point of `computeIfAbsent` is to make this atomic.
- **No state machine — `payment.setStatus(REFUNDED)`** — allows FAILED → REFUNDED, SUCCESS → PENDING, all silently. The bug surface is enormous.
- **Throwing processor exceptions through to the caller** — caller can't tell if it's idempotent to retry. Convert to FAILED with errorCode.
- **`if (payment.getStatus() == SUCCESS)` in the gateway** — gateway shouldn't know the state machine rules. They live on Payment.
- **Money as `double`** — see Splitwise / Parking Lot. Use `long` cents.
- **Storing the result BEFORE the processor call** — if the processor throws and you've cached the result, retries see stale state. Cache only after the processor's response is final.
- **One Payment instance shared across multiple idempotent calls** — each cache lookup must return the cached RESULT (immutable record), not the Payment object (mutable, status evolving).
- **Builder for the 2-arg ctor** — academic noise.
- **Skipping the 50-thread same-key test** — the empirical idempotency proof is exactly what the interviewer wants to see.

---

## Files in this folder (your reference implementation)

| File                                                  | What it shows                                                                            |
| ----------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| `model/PaymentStatus.java`                            | Enum + `isTerminal()` helper                                                             |
| `model/PaymentMethod.java`                            | Enum — CARD / UPI / NETBANKING                                                           |
| `model/PaymentRequest.java`                           | Immutable record + null/positive validation in compact constructor                       |
| `model/PaymentResult.java`                            | Immutable record + `.success` / `.failed` factory methods                                |
| `model/Payment.java`                                  | **The state machine class** — EnumMap of allowed transitions; `transitionTo` throws on invalid moves |
| `processor/PaymentProcessor.java`                     | Strategy interface + `ProcessorResponse` nested record                                   |
| `processor/CardProcessor.java`                        | Card impl with simulated issuer limit                                                    |
| `processor/UpiProcessor.java`                         | UPI impl (always succeeds in demo)                                                       |
| `processor/NetBankingProcessor.java`                  | Net-banking impl                                                                          |
| `PaymentGateway.java`                                 | **The hot class** — `computeIfAbsent` idempotency + state-machine-driven refund         |
| `PaymentGatewayDriver.java`                           | 6 scenarios incl. **50-thread same-key burst** proving exactly 1 processor call         |

Run from the project root:

```bash
mvn -q compile exec:java \
  -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.paymentgateway.PaymentGatewayDriver
```
