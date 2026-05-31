package com.conceptcoding.interviewquestions.hello_all_questions.ratelimiter;

import com.conceptcoding.interviewquestions.hello_all_questions.ratelimiter.algorithm.TokenBucketLimiter;
import com.conceptcoding.interviewquestions.hello_all_questions.ratelimiter.model.LimiterConfig;
import com.conceptcoding.interviewquestions.hello_all_questions.ratelimiter.model.RateLimitResult;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class RateLimiterDriver {

    public static void main(String[] args) throws Exception {
        scenarioTokenBucketBasics();
        scenarioSlidingWindowLog();
        scenarioMultiEndpointAndFallback();
        scenarioConcurrentBurst();
    }

    // ---- Scenario 1: Token-bucket basics — burst, deplete, retry math --------
    private static void scenarioTokenBucketBasics() {
        System.out.println("=== Scenario 1: TokenBucket — capacity=5, refill=1/s ===");
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        TokenBucketLimiter tb = new TokenBucketLimiter(5, 1, clock);

        // Five rapid hits — all should succeed (initial bucket is full).
        for (int i = 1; i <= 5; i++) {
            RateLimitResult r = tb.allow("alice");
            System.out.printf("  req %d : allowed=%s remaining=%d retryAfterMs=%s%n",
                    i, r.allowed(), r.remaining(), r.retryAfterMs());
        }

        // 6th must fail; retryAfterMs should be ~1000 (1 token / 1 per-sec).
        RateLimitResult denied = tb.allow("alice");
        System.out.printf("  req 6 : allowed=%s remaining=%d retryAfterMs=%s  (expect ~1000)%n",
                denied.allowed(), denied.remaining(), denied.retryAfterMs());

        // Advance 1.5s → 1.5 tokens refilled → 1 more request succeeds.
        clock.advanceMillis(1500);
        RateLimitResult afterRefill = tb.allow("alice");
        System.out.printf("  +1.5s : allowed=%s remaining=%d (expect 0 — 0.5 tokens left)%n",
                afterRefill.allowed(), afterRefill.remaining());
        System.out.println();
    }

    // ---- Scenario 2: Sliding-window-log — exact accuracy --------
    private static void scenarioSlidingWindowLog() {
        System.out.println("=== Scenario 2: SlidingWindowLog — 3 reqs / 1000ms ===");
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        var swl = new com.conceptcoding.interviewquestions.hello_all_questions.ratelimiter.algorithm
                .SlidingWindowLogLimiter(3, 1000L, clock);

        // 3 requests fit; 4th must fail.
        for (int i = 1; i <= 3; i++) System.out.printf("  req %d allowed=%s%n", i, swl.allow("bob").allowed());
        RateLimitResult denied = swl.allow("bob");
        System.out.printf("  req 4 allowed=%s retryAfterMs=%s (~1000ms — oldest must age out)%n",
                denied.allowed(), denied.retryAfterMs());

        // Advance past the window → all old timestamps age out.
        clock.advanceMillis(1001);
        RateLimitResult fresh = swl.allow("bob");
        System.out.printf("  +1001ms allowed=%s remaining=%d (expect 2 — slate is clean)%n",
                fresh.allowed(), fresh.remaining());
        System.out.println();
    }

    // ---- Scenario 3: multi-endpoint dispatch + default fallback --------
    private static void scenarioMultiEndpointAndFallback() {
        System.out.println("=== Scenario 3: multi-endpoint dispatch + default fallback ===");
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));

        LimiterConfig searchCfg = new LimiterConfig(
                "/search", "TokenBucket",
                Map.of("capacity", 100, "refillRatePerSecond", 10));
        LimiterConfig uploadCfg = new LimiterConfig(
                "/upload", "SlidingWindowLog",
                Map.of("maxRequests", 5, "windowMs", 60_000L));
        LimiterConfig defaultCfg = new LimiterConfig(
                null, "TokenBucket",
                Map.of("capacity", 10, "refillRatePerSecond", 1));

        RateLimiter limiter = new RateLimiter(List.of(searchCfg, uploadCfg), defaultCfg, clock);

        System.out.println("  /search   → " + limiter.allow("client-1", "/search"));      // TokenBucket(100, 10)
        System.out.println("  /upload   → " + limiter.allow("client-1", "/upload"));      // SlidingWindowLog(5, 60s)
        System.out.println("  /unknown  → " + limiter.allow("client-1", "/unknown"));     // falls back to default
        System.out.println();
    }

    // ---- Scenario 4: concurrent burst — 50 threads, frozen clock, capacity 10 --------
    // With per-key locking → exactly 10 should win. Without it, race → wrong count.
    private static void scenarioConcurrentBurst() throws Exception {
        System.out.println("=== Scenario 4: 50 threads, frozen clock, capacity=10 ===");
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        TokenBucketLimiter limiter = new TokenBucketLimiter(10, 1, clock);

        int threads = 50;
        AtomicInteger allowed = new AtomicInteger();
        AtomicInteger denied  = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch fire  = new CountDownLatch(1);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    fire.await();
                    RateLimitResult r = limiter.allow("shared-client");
                    if (r.allowed()) allowed.incrementAndGet();
                    else             denied.incrementAndGet();
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        ready.await();
        fire.countDown();
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        System.out.println("  allowed = " + allowed.get() + "   (expect exactly 10)");
        System.out.println("  denied  = " + denied.get()  + "   (expect exactly 40)");
        System.out.println("  total   = " + (allowed.get() + denied.get()) + " (expect 50)");
        System.out.println(allowed.get() == 10 && denied.get() == 40
                ? "  per-key locking holds ✓"
                : "  ✗ RACE — counts don't match!");
    }

    /** Mutable clock with no auto-advance — drives deterministic tests. */
    static final class MutableClock extends Clock {
        private volatile Instant now;
        MutableClock(Instant start) { this.now = start; }
        @Override public Instant instant()      { return now; }
        @Override public long millis()          { return now.toEpochMilli(); }
        @Override public ZoneId getZone()       { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId z) { return this; }
        void advanceMillis(long ms) { now = now.plusMillis(ms); }
    }
}
