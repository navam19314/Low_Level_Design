package com.conceptcoding.interviewquestions.hello_all_questions.ratelimiter;

import com.conceptcoding.interviewquestions.hello_all_questions.ratelimiter.algorithm.SlidingWindowLogLimiter;
import com.conceptcoding.interviewquestions.hello_all_questions.ratelimiter.algorithm.TokenBucketLimiter;
import com.conceptcoding.interviewquestions.hello_all_questions.ratelimiter.model.RateLimitResult;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class RateLimiterDriver {

    public static void main(String[] args) throws Exception {
        scenarioTokenBucket();
        scenarioSlidingWindowLog();
        scenarioMultiEndpoint();
        scenarioConcurrentBurst();
    }

    // capacity=5, refill=1/s — burst, deplete, deny, refill
    private static void scenarioTokenBucket() {
        System.out.println("=== TokenBucket: capacity=5, refill=1/s ===");
        MutableClock clock = new MutableClock(0);
        TokenBucketLimiter tb = new TokenBucketLimiter(5, 1, clock);

        for (int i = 1; i <= 5; i++) {
            System.out.printf("  req %d → %s%n", i, tb.allow("alice"));
        }
        System.out.println("  req 6 → " + tb.allow("alice") + "  (expect DENY retryAfterMs=1000)");

        clock.advanceMs(1500);
        System.out.println("  +1500ms → " + tb.allow("alice") + "  (expect ALLOW remaining=0)");
        System.out.println();
    }

    // 3 reqs per 1000ms — fill, deny, advance past window
    private static void scenarioSlidingWindowLog() {
        System.out.println("=== SlidingWindowLog: 3 reqs / 1000ms ===");
        MutableClock clock = new MutableClock(0);
        SlidingWindowLogLimiter swl = new SlidingWindowLogLimiter(3, 1000L, clock);

        for (int i = 1; i <= 3; i++) {
            System.out.printf("  req %d → %s%n", i, swl.allow("bob"));
        }
        System.out.println("  req 4 → " + swl.allow("bob") + "  (expect DENY)");

        clock.advanceMs(1001);
        System.out.println("  +1001ms → " + swl.allow("bob") + "  (expect ALLOW remaining=2)");
        System.out.println();
    }

    // register per-endpoint limiters; unknown endpoint falls back to default
    private static void scenarioMultiEndpoint() {
        System.out.println("=== Multi-endpoint + default fallback ===");
        MutableClock clock = new MutableClock(0);

        RateLimiter rl = new RateLimiter(new TokenBucketLimiter(10, 1, clock));
        rl.register("/search", new TokenBucketLimiter(100, 10, clock));
        rl.register("/upload", new SlidingWindowLogLimiter(5, 60_000L, clock));

        System.out.println("  /search  → " + rl.allow("client-1", "/search"));
        System.out.println("  /upload  → " + rl.allow("client-1", "/upload"));
        System.out.println("  /unknown → " + rl.allow("client-1", "/unknown") + "  (default limiter)");
        System.out.println();
    }

    // 50 threads race for capacity=10 — per-key locking means exactly 10 win
    private static void scenarioConcurrentBurst() throws Exception {
        System.out.println("=== Concurrent burst: 50 threads, capacity=10 ===");
        MutableClock clock = new MutableClock(0); // frozen — no refill between threads
        TokenBucketLimiter limiter = new TokenBucketLimiter(10, 1, clock);

        int threads = 50;
        AtomicInteger allowed = new AtomicInteger();
        AtomicInteger denied  = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch fire = new CountDownLatch(1);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try { fire.await(); } catch (InterruptedException e) { return; }
                if (limiter.allow("shared").isAllowed()) allowed.incrementAndGet();
                else                                     denied.incrementAndGet();
            });
        }

        fire.countDown();
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        System.out.println("  allowed = " + allowed.get() + "  (expect 10)");
        System.out.println("  denied  = " + denied.get()  + "  (expect 40)");
        System.out.println(allowed.get() == 10 ? "  per-key locking ✓" : "  RACE CONDITION ✗");
    }

    // Controllable clock — lets us simulate time passing without Thread.sleep
    static final class MutableClock extends Clock {
        private long nowMs;
        MutableClock(long startMs)           { this.nowMs = startMs; }
        void advanceMs(long ms)              { nowMs += ms; }
        @Override public long millis()       { return nowMs; }
        @Override public Instant instant()   { return Instant.ofEpochMilli(nowMs); }
        @Override public ZoneId getZone()    { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId z) { return this; }
    }
}
