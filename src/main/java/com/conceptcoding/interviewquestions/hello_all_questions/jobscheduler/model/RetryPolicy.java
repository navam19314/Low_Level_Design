package com.conceptcoding.interviewquestions.hello_all_questions.jobscheduler.model;

import java.time.Duration;

/**
 * Retry policy for a job. {@code maxAttempts} INCLUDES the initial attempt —
 * maxAttempts=3 means at most 3 runs total (1 initial + 2 retries).
 *
 * <p>Backoff is exponential with jitter capped at {@code maxBackoff} to avoid
 * thundering-herd on a transient outage.
 */
public record RetryPolicy(int maxAttempts, Duration initialBackoff, Duration maxBackoff) {

    public RetryPolicy {
        if (maxAttempts <= 0) throw new IllegalArgumentException("maxAttempts must be > 0");
        if (initialBackoff.isNegative()) throw new IllegalArgumentException("backoff must be >= 0");
    }

    /** No retries — single attempt, fail-fast. */
    public static RetryPolicy noRetries() {
        return new RetryPolicy(1, Duration.ZERO, Duration.ZERO);
    }

    /** Default: 3 attempts, 100ms initial, capped at 10s. */
    public static RetryPolicy defaultPolicy() {
        return new RetryPolicy(3, Duration.ofMillis(100), Duration.ofSeconds(10));
    }

    /**
     * Backoff for the Nth retry (attempt 2 is the 1st retry).
     * Doubling: initial × 2^(retryNumber-1), capped at maxBackoff.
     */
    public Duration backoffFor(int retryNumber) {
        long ms = initialBackoff.toMillis() * (1L << (retryNumber - 1));
        long capped = Math.min(ms, maxBackoff.toMillis());
        return Duration.ofMillis(capped);
    }
}
