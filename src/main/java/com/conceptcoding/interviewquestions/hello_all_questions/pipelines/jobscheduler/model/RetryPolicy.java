package com.conceptcoding.interviewquestions.hello_all_questions.pipelines.jobscheduler.model;

import java.time.Duration;

// maxAttempts INCLUDES the first run: maxAttempts = 3 → at most 3 runs (1 + 2 retries).
// Back-off doubles each retry, capped at maxBackoff: 100 ms, 200 ms, 400 ms ... 10 s.
// (In production add jitter, a random extra delay, so many failed jobs don't all retry at once.)
public class RetryPolicy {

    private final int maxAttempts;
    private final Duration initialBackoff;
    private final Duration maxBackoff;

    public RetryPolicy(int maxAttempts, Duration initialBackoff, Duration maxBackoff) {
        if (maxAttempts <= 0) throw new IllegalArgumentException("maxAttempts must be > 0");
        if (initialBackoff.isNegative()) throw new IllegalArgumentException("backoff must be >= 0");
        this.maxAttempts = maxAttempts;
        this.initialBackoff = initialBackoff;
        this.maxBackoff = maxBackoff;
    }

    public static RetryPolicy noRetries()     { return new RetryPolicy(1, Duration.ZERO, Duration.ZERO); }
    public static RetryPolicy defaultPolicy() { return new RetryPolicy(3, Duration.ofMillis(100), Duration.ofSeconds(10)); }

    // back-off before the Nth run (attempt 2 is the first retry): initial × 2^(attempt-1), capped
    public Duration backoffFor(int attempt) {
        long ms = initialBackoff.toMillis() * (1L << (attempt - 1));
        return Duration.ofMillis(Math.min(ms, maxBackoff.toMillis()));
    }

    public int      getMaxAttempts()    { return maxAttempts; }
    public Duration getInitialBackoff() { return initialBackoff; }
    public Duration getMaxBackoff()     { return maxBackoff; }
}
