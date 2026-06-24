package com.conceptcoding.interviewquestions.hello_all_questions.ratelimiter.model;

public class RateLimitResult {

    private final boolean allowed;
    private final int remaining;
    private final Long retryAfterMs; // null when allowed

    private RateLimitResult(boolean allowed, int remaining, Long retryAfterMs) {
        this.allowed = allowed;
        this.remaining = remaining;
        this.retryAfterMs = retryAfterMs;
    }

    public static RateLimitResult allow(int remaining) {
        return new RateLimitResult(true, remaining, null);
    }

    public static RateLimitResult deny(long retryAfterMs) {
        return new RateLimitResult(false, 0, retryAfterMs);
    }

    public boolean isAllowed()        { return allowed; }
    public int     getRemaining()     { return remaining; }
    public Long    getRetryAfterMs()  { return retryAfterMs; }

    @Override
    public String toString() {
        return allowed
            ? "ALLOW(remaining=" + remaining + ")"
            : "DENY(retryAfterMs=" + retryAfterMs + ")";
    }
}
