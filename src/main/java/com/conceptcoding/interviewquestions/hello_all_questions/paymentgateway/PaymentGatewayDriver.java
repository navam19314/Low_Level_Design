package com.conceptcoding.interviewquestions.hello_all_questions.paymentgateway;

import com.conceptcoding.interviewquestions.hello_all_questions.paymentgateway.model.Payment;
import com.conceptcoding.interviewquestions.hello_all_questions.paymentgateway.model.PaymentMethod;
import com.conceptcoding.interviewquestions.hello_all_questions.paymentgateway.model.PaymentRequest;
import com.conceptcoding.interviewquestions.hello_all_questions.paymentgateway.model.PaymentResult;
import com.conceptcoding.interviewquestions.hello_all_questions.paymentgateway.model.PaymentStatus;
import com.conceptcoding.interviewquestions.hello_all_questions.paymentgateway.processor.CardProcessor;
import com.conceptcoding.interviewquestions.hello_all_questions.paymentgateway.processor.NetBankingProcessor;
import com.conceptcoding.interviewquestions.hello_all_questions.paymentgateway.processor.UpiProcessor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class PaymentGatewayDriver {

    public static void main(String[] args) throws Exception {
        scenarioHappyPath();
        scenarioIdempotency();
        scenarioFailureCardOverLimit();
        scenarioRefund();
        scenarioInvalidTransitions();
        scenarioConcurrentSameIdempotencyKey();
    }

    // ---- 1. Happy path — card payment succeeds, status SUCCESS ----
    private static void scenarioHappyPath() {
        System.out.println("=== Scenario 1: happy-path card payment ===");
        PaymentGateway pg = newGateway();
        PaymentRequest req = new PaymentRequest("idem-1", "cust-A", 5000L, "USD",
                PaymentMethod.CARD, "test");
        PaymentResult r = pg.pay(req);
        System.out.println("  status=" + r.status() + " (expect SUCCESS)");
        System.out.println("  payment.status=" + pg.getPayment(r.paymentId()).getStatus());
        System.out.println();
    }

    // ---- 2. Idempotency — same key returns same result, NO double-charge ----
    private static void scenarioIdempotency() {
        System.out.println("=== Scenario 2: same idempotency key → same result, single processor call ===");
        CountingCardProcessor counting = new CountingCardProcessor();
        PaymentGateway pg = new PaymentGateway(List.of(counting));
        PaymentRequest req = new PaymentRequest("idem-2", "cust-B", 1000L, "USD",
                PaymentMethod.CARD, "test");

        PaymentResult r1 = pg.pay(req);
        PaymentResult r2 = pg.pay(req);     // same idempotency key
        PaymentResult r3 = pg.pay(req);
        System.out.println("  processor invocations: " + counting.count.get() + " (expect 1)");
        System.out.println("  all three results have same paymentId? "
                + (r1.paymentId().equals(r2.paymentId()) && r2.paymentId().equals(r3.paymentId())));
        System.out.println();
    }

    // ---- 3. Failure path — card over issuer limit ----
    private static void scenarioFailureCardOverLimit() {
        System.out.println("=== Scenario 3: card over issuer limit → FAILED with reason ===");
        PaymentGateway pg = newGateway();
        PaymentRequest req = new PaymentRequest("idem-3", "cust-C", 200_000L, "USD",
                PaymentMethod.CARD, "big purchase");
        PaymentResult r = pg.pay(req);
        System.out.println("  status=" + r.status() + " (expect FAILED)");
        System.out.println("  errorCode=" + r.errorCode());
        System.out.println("  errorMessage=" + r.errorMessage());
        System.out.println();
    }

    // ---- 4. Refund — SUCCESS → REFUND_PENDING → REFUNDED ----
    private static void scenarioRefund() {
        System.out.println("=== Scenario 4: refund a successful payment ===");
        PaymentGateway pg = newGateway();
        PaymentRequest req = new PaymentRequest("idem-4", "cust-D", 3000L, "USD",
                PaymentMethod.UPI, "test");
        PaymentResult r = pg.pay(req);
        PaymentResult refund = pg.refund(r.paymentId());
        System.out.println("  after refund status=" + refund.status() + " (expect REFUNDED)");
        System.out.println("  payment.status=" + pg.getPayment(r.paymentId()).getStatus());
        System.out.println();
    }

    // ---- 5. Invalid state transition — cannot refund FAILED, cannot double-refund ----
    private static void scenarioInvalidTransitions() {
        System.out.println("=== Scenario 5: invalid state-machine transitions ===");
        PaymentGateway pg = newGateway();

        // Cannot refund a FAILED payment.
        PaymentRequest fail = new PaymentRequest("idem-5a", "cust-E", 200_000L, "USD",
                PaymentMethod.CARD, "over-limit");
        PaymentResult failed = pg.pay(fail);
        try {
            pg.refund(failed.paymentId());
        } catch (IllegalStateException e) {
            System.out.println("  refund of FAILED payment rejected: " + e.getMessage());
        }

        // Cannot double-refund a SUCCESSFUL payment.
        PaymentRequest ok = new PaymentRequest("idem-5b", "cust-E", 1000L, "USD",
                PaymentMethod.UPI, "ok");
        PaymentResult okRes = pg.pay(ok);
        pg.refund(okRes.paymentId());
        try {
            pg.refund(okRes.paymentId());
        } catch (IllegalStateException e) {
            System.out.println("  double-refund of REFUNDED payment rejected: " + e.getMessage());
        }
        System.out.println();
    }

    // ---- 6. Concurrent identical idempotency keys — EXACTLY ONE processor call ----
    private static void scenarioConcurrentSameIdempotencyKey() throws Exception {
        System.out.println("=== Scenario 6: 50 threads, same idempotency key — exactly 1 processor call ===");
        CountingCardProcessor counting = new CountingCardProcessor();
        PaymentGateway pg = new PaymentGateway(List.of(counting));
        PaymentRequest req = new PaymentRequest("idem-burst", "cust-Z", 2500L, "USD",
                PaymentMethod.CARD, "race");

        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch fire  = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    fire.await();
                    PaymentResult r = pg.pay(req);
                    if (r.status() == PaymentStatus.SUCCESS) successes.incrementAndGet();
                } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            });
        }
        ready.await();
        fire.countDown();
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        System.out.println("  processor invocations: " + counting.count.get() + " (expect 1)");
        System.out.println("  successful results returned: " + successes.get() + " (expect 50 — all see the same cached SUCCESS)");
        System.out.println(counting.count.get() == 1 && successes.get() == 50
                ? "  ✓ idempotency holds under contention"
                : "  ✗ RACE — counts wrong");
    }

    // ----- helpers -----

    private static PaymentGateway newGateway() {
        return new PaymentGateway(List.of(new CardProcessor(), new UpiProcessor(), new NetBankingProcessor()),
                Clock.fixed(Instant.parse("2026-06-01T10:00:00Z"), ZoneId.of("UTC")));
    }

    /** Card processor that counts how many times it was invoked. */
    static class CountingCardProcessor extends CardProcessor {
        final AtomicInteger count = new AtomicInteger();
        @Override public ProcessorResponse process(PaymentRequest request) {
            count.incrementAndGet();
            return super.process(request);
        }
    }
}
