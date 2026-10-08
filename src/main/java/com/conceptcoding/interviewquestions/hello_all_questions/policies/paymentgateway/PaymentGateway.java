package com.conceptcoding.interviewquestions.hello_all_questions.policies.paymentgateway;

import com.conceptcoding.interviewquestions.hello_all_questions.policies.paymentgateway.model.Payment;
import com.conceptcoding.interviewquestions.hello_all_questions.policies.paymentgateway.model.PaymentMethod;
import com.conceptcoding.interviewquestions.hello_all_questions.policies.paymentgateway.model.PaymentRequest;
import com.conceptcoding.interviewquestions.hello_all_questions.policies.paymentgateway.model.PaymentResult;
import com.conceptcoding.interviewquestions.hello_all_questions.policies.paymentgateway.model.PaymentStatus;
import com.conceptcoding.interviewquestions.hello_all_questions.policies.paymentgateway.processor.PaymentProcessor;
import com.conceptcoding.interviewquestions.hello_all_questions.policies.paymentgateway.processor.PaymentProcessor.ProcessorResponse;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

// Orchestrator + facade. The three load-bearing concerns are:
//
//   Idempotency. Same idempotencyKey submitted N times → at most ONE processor call.
//       putIfAbsent claims the key atomically; duplicates wait on the first call's future.
//       The slow processor call runs outside any map lock (computeIfAbsent would hold one).
//       Reusing a key for a DIFFERENT charge is rejected as a client bug.
//   State machine. Each Payment owns a guarded state machine.
//       Refunds are only allowed from SUCCESS — attempts on FAILED / PENDING /
//       already-REFUNDED throw IllegalStateException.
//   Processor routing (Strategy). Each PaymentProcessor answers
//       supports(method). The gateway picks the matching one. Adding a
//       new method = one new processor class registered at construction.
//
//
// Clock is injected so the timestamps on payments are deterministic
// in tests (same trick as Locker / Rate Limiter / Splitwise).
public class PaymentGateway {

    private final List<PaymentProcessor>            processors;
    // key → the first request with that key + its (possibly still running) result
    private final ConcurrentHashMap<String, InFlight> idempotencyCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Payment>       payments         = new ConcurrentHashMap<>();
    private final Clock clock;

    public PaymentGateway(List<PaymentProcessor> processors) {
        this(processors, Clock.systemUTC());
    }

    public PaymentGateway(List<PaymentProcessor> processors, Clock clock) {
        this.processors = List.copyOf(processors);
        this.clock = clock;
    }

    // Idempotent payment entry point. Two concurrent calls with the same idempotency
    // key are guaranteed to result in EXACTLY ONE processor invocation — the second
    // caller gets the first one's PaymentResult straight from the cache.
    public PaymentResult pay(PaymentRequest request) {
        InFlight mine = new InFlight(request);
        InFlight existing = idempotencyCache.putIfAbsent(request.idempotencyKey(), mine);   // atomic claim of the key
        if (existing != null) {
            // a retry, or a concurrent duplicate: never charge again, return the first call's result
            if (!existing.request.sameChargeAs(request)) {
                throw new IllegalArgumentException("Idempotency key " + request.idempotencyKey()
                        + " was already used for a different payment");
            }
            return existing.result.join();                 // waits if the first call is still processing
        }
        // we own this key. The slow processor call runs OUTSIDE any map lock, so other keys never wait on it.
        try {
            PaymentResult result = processNew(request);
            mine.result.complete(result);
            return result;
        } catch (RuntimeException e) {
            mine.result.completeExceptionally(e);
            throw e;
        }
    }

    //  Refund a previously-successful payment. State-machine-guarded — throws on bad transitions.
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

    // Called from pay() only by the thread that claimed the idempotency key,
    // so two threads with the same key can never both get here.
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
        } catch (Exception e) {
            // A processor exception is treated as a definitive failure (Exception, not Throwable:
            // never swallow JVM errors). In real life a TIMEOUT is "unknown", not "failed": see Q3.
            payment.markFailure("PROCESSOR_THREW", e.getMessage(), clock.instant());
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

    // one entry per idempotency key: the original request (to detect key reuse) + its result
    private static final class InFlight {
        final PaymentRequest request;
        final CompletableFuture<PaymentResult> result = new CompletableFuture<>();
        InFlight(PaymentRequest request) { this.request = request; }
    }
}
