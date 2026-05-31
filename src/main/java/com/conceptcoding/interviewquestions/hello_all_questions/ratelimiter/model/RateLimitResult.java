package com.conceptcoding.interviewquestions.hello_all_questions.ratelimiter.model;

/**
 * Immutable value object returned from every rate-limit check.
 *
 * <p>{@code retryAfterMs} is deliberately nullable (not 0) when allowed —
 * a sentinel value would be ambiguous with "retry immediately, you're at the edge".
 * Use {@link #allow} / {@link #deny} factory methods instead of the raw constructor;
 * they make the intent obvious at the call site.
 */
public record RateLimitResult(boolean allowed, int remaining, Long retryAfterMs) {

    public static RateLimitResult allow(int remaining) {
        return new RateLimitResult(true, remaining, null);
    }

    public static RateLimitResult deny(long retryAfterMs) {
        return new RateLimitResult(false, 0, retryAfterMs);
    }
}
