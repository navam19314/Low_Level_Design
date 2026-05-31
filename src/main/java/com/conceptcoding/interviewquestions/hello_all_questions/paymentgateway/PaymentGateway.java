package com.conceptcoding.interviewquestions.hello_all_questions.paymentgateway;

import com.conceptcoding.interviewquestions.hello_all_questions.paymentgateway.model.Payment;
import com.conceptcoding.interviewquestions.hello_all_questions.paymentgateway.model.PaymentMethod;
import com.conceptcoding.interviewquestions.hello_all_questions.paymentgateway.model.PaymentRequest;
import com.conceptcoding.interviewquestions.hello_all_questions.paymentgateway.model.PaymentResult;
import com.conceptcoding.interviewquestions.hello_all_questions.paymentgateway.model.PaymentStatus;
import com.conceptcoding.interviewquestions.hello_all_questions.paymentgateway.processor.PaymentProcessor;
import com.conceptcoding.interviewquestions.hello_all_questions.paymentgateway.processor.PaymentProcessor.ProcessorResponse;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrator + facade. The three load-bearing concerns are:
 *
 * <ol>
 *   <li><b>Idempotency.</b> Same {@code idempotencyKey} submitted N times → at most
 *       ONE actual processor call. The other N-1 callers get the cached result.
 *       Implemented with {@code ConcurrentHashMap.computeIfAbsent} so the dedup is
 *       atomic — no need for an outer lock on the gateway.</li>
 *   <li><b>State machine.</b> Each {@link Payment} owns a guarded state machine.
 *       Refunds are only allowed from SUCCESS — attempts on FAILED / PENDING /
 *       already-REFUNDED throw {@code IllegalStateException}.</li>
 *   <li><b>Processor routing (Strategy).</b> Each {@link PaymentProcessor} answers
 *       {@code supports(method)}. The gateway picks the matching one. Adding a
 *       new method = one new processor class registered at construction.</li>
 * </ol>
 *
 * <p>{@code Clock} is injected so the timestamps on payments are deterministic
 * in tests (same trick as Locker / Rate Limiter / Splitwise).
 */
public class PaymentGateway {

    private final List<PaymentProcessor>            processors;
    private final ConcurrentHashMap<String, PaymentResult> idempotencyCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Payment>       payments         = new ConcurrentHashMap<>();
    private final Clock clock;

    public PaymentGateway(List<PaymentProcessor> processors) {
        this(processors, Clock.systemUTC());
    }

    public PaymentGateway(List<PaymentProcessor> processors, Clock clock) {
        this.processors = List.copyOf(processors);
        this.clock = clock;
    }

    /**
     * Idempotent payment entry point. Two concurrent calls with the same idempotency
     * key are guaranteed to result in EXACTLY ONE processor invocation — the second
     * caller gets the first one's PaymentResult straight from the cache.
     */
    public PaymentResult pay(PaymentRequest request) {
        // computeIfAbsent does the dedup atomically — no double-charge under contention.
        return idempotencyCache.computeIfAbsent(request.idempotencyKey(), key -> processNew(request));
    }

    /** Refund a previously-successful payment. State-machine-guarded — throws on bad transitions. */
    public PaymentResult refund(String paymentId) {
        Payment payment = payments.get(paymentId);
        if (payment == null) throw new NoSuchElementException("Unknown payment id: " + paymentId);

        synchronized (payment) {
            // SUCCESS → REFUND_PENDING (will throw if status is FAILED, PENDING, PROCESSING, REFUND_PENDING, REFUNDED)
            payment.transitionTo(PaymentStatus.REFUND_PENDING, clock.instant());
            // In a real system this calls back out to the processor's refund endpoint.
            // For this design we treat refunds as always succeeding on the gateway side.
            payment.transitionTo(PaymentStatus.REFUNDED, clock.instant());
        }

        // Refund result shares the original idempotency key so duplicate-refund-on-retry is naturally safe.
        return new PaymentResult(payment.getId(), payment.getIdempotencyKey(),
                payment.getStatus(), null, null, payment.getLastUpdatedAt());
    }

    public Payment getPayment(String paymentId) {
        Payment p = payments.get(paymentId);
        if (p == null) throw new NoSuchElementException("Unknown payment id: " + paymentId);
        return p;
    }

    // ----- internals -----

    /**
     * Called from {@code pay} ONLY when no cached result exists for the idempotency
     * key. Because computeIfAbsent runs this lambda atomically per key, two threads
     * with the same key can never both enter here.
     */
    private PaymentResult processNew(PaymentRequest request) {
        Instant now = clock.instant();
        Payment payment = new Payment(UUID.randomUUID().toString(), request, now);
        payments.put(payment.getId(), payment);

        PaymentProcessor processor = selectProcessor(request.method());
        if (processor == null) {
            payment.markFailure("NO_PROCESSOR", "No processor for method " + request.method(), clock.instant());
            return PaymentResult.failed(payment.getId(), request.idempotencyKey(),
                    payment.getErrorCode(), payment.getErrorMessage(), payment.getLastUpdatedAt());
        }

        // Drive the state machine: PENDING → PROCESSING → SUCCESS / FAILED.
        payment.transitionTo(PaymentStatus.PROCESSING, clock.instant());
        ProcessorResponse response;
        try {
            response = processor.process(request);
        } catch (Throwable t) {
            // Any processor throw is treated as a definitive failure — don't bubble it up.
            payment.markFailure("PROCESSOR_THREW", t.getMessage(), clock.instant());
            return PaymentResult.failed(payment.getId(), request.idempotencyKey(),
                    payment.getErrorCode(), payment.getErrorMessage(), payment.getLastUpdatedAt());
        }

        if (response.success()) {
            payment.transitionTo(PaymentStatus.SUCCESS, clock.instant());
            return PaymentResult.success(payment.getId(), request.idempotencyKey(), payment.getLastUpdatedAt());
        }
        payment.markFailure(response.errorCode(), response.errorMessage(), clock.instant());
        return PaymentResult.failed(payment.getId(), request.idempotencyKey(),
                payment.getErrorCode(), payment.getErrorMessage(), payment.getLastUpdatedAt());
    }

    private PaymentProcessor selectProcessor(PaymentMethod method) {
        for (PaymentProcessor p : processors) {
            if (p.supports(method)) return p;
        }
        return null;
    }
}
