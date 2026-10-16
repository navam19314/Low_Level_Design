package com.conceptcoding.interviewquestions.hello_all_questions.policies.ratelimiter;

import com.conceptcoding.interviewquestions.hello_all_questions.policies.ratelimiter.algorithm.TokenBucketLimiter;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class RateLimiterDriver {

    public static void main(String[] args) throws Exception {
        MutableClock clock = new MutableClock();
        RateLimiter limiter = new TokenBucketLimiter(5, 1, clock);           // 5 burst, 1 token per second

        System.out.println("=== A burst of 7 at the same instant: 5 pass, 2 rejected ===");
        StringBuilder results = new StringBuilder();
        for (int i = 0; i < 7; i++) results.append(limiter.allow("alice") ? "✓ " : "✗ ");
        System.out.println("  " + results + "(expect ✓ ✓ ✓ ✓ ✓ ✗ ✗)");

        System.out.println("\n=== Each client has its own jar ===");
        System.out.println("  bob: " + limiter.allow("bob") + " (expect true: alice's burst doesn't affect bob)");

        System.out.println("\n=== Refill: 1 token per second ===");
        clock.advanceMs(500);
        System.out.println("  after 0.5 s: " + limiter.allow("alice") + " (expect false: only half a token)");
        clock.advanceMs(500);
        System.out.println("  after 1 s:   " + limiter.allow("alice") + " (expect true), then " + limiter.allow("alice") + " (expect false)");

        System.out.println("\n=== Never more than capacity, even after a long wait ===");
        clock.advanceMs(60_000);
        int allowed = 0;
        for (int i = 0; i < 10; i++) if (limiter.allow("alice")) allowed++;
        System.out.println("  after 60 s idle, 10 requests → " + allowed + " allowed (expect 5, not 60)");

        concurrentRequests();
    }

    // 100 threads hit the same client at the same instant, capacity 10: exactly 10 allowed.
    private static void concurrentRequests() throws Exception {
        System.out.println("\n=== Concurrency: 100 requests at once, capacity 10 ===");
        RateLimiter limiter = new TokenBucketLimiter(10, 1, new MutableClock());   // frozen time: no refill
        AtomicInteger allowed = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(50);
        CountDownLatch go = new CountDownLatch(1);
        for (int i = 0; i < 100; i++) {
            pool.submit(() -> {
                go.await();
                if (limiter.allow("alice")) allowed.incrementAndGet();
                return null;
            });
        }
        go.countDown();
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println("  allowed = " + allowed + " (expect 10)");
    }

    // a clock the test moves by hand
    static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");
        void advanceMs(long ms) { now = now.plusMillis(ms); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
