package com.conceptcoding.interviewquestions.hello_all_questions.ratelimiter.algorithm;

import com.conceptcoding.interviewquestions.hello_all_questions.ratelimiter.model.RateLimitResult;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Classic token-bucket rate limiter — allows bursts up to {@code capacity}
 * and refills at {@code refillRatePerSecond} tokens/second.
 *
 * <p>Thread-safety: per-key locking. {@link ConcurrentHashMap#computeIfAbsent}
 * atomically creates the bucket on first contact; the check-and-update is then
 * serialized by {@code synchronized(bucket)}. Two clients never block each other
 * — only requests for the SAME client serialize, which is exactly the bar.
 *
 * <p>Refill is ON-DEMAND, not background-timer-driven. We compute how many
 * tokens should have accumulated since the last touch — no thread runs while
 * the bucket is idle, no work wasted on inactive clients.
 */
public class TokenBucketLimiter implements Limiter {

    private final int capacity;
    private final int refillRatePerSecond;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final Clock clock;

    public TokenBucketLimiter(int capacity, int refillRatePerSecond) {
        this(capacity, refillRatePerSecond, Clock.systemUTC());
    }

    public TokenBucketLimiter(int capacity, int refillRatePerSecond, Clock clock) {
        if (capacity <= 0)              throw new IllegalArgumentException("capacity must be > 0");
        if (refillRatePerSecond <= 0)   throw new IllegalArgumentException("refillRatePerSecond must be > 0");
        this.capacity = capacity;
        this.refillRatePerSecond = refillRatePerSecond;
        this.clock = clock;
    }

    @Override
    public RateLimitResult allow(String key) {
        // First-time clients start with a FULL bucket — they get their burst immediately.
        TokenBucket bucket = buckets.computeIfAbsent(
                key, k -> new TokenBucket(capacity, clock.millis()));

        synchronized (bucket) {
            long now = clock.millis();
            long elapsedMs = now - bucket.lastRefillTime;
            // Lazy refill: how many tokens "should have" arrived since last touch.
            double tokensToAdd = (elapsedMs * refillRatePerSecond) / 1000.0;
            bucket.tokens = Math.min(capacity, bucket.tokens + tokensToAdd);
            bucket.lastRefillTime = now;

            if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0;
                return RateLimitResult.allow((int) Math.floor(bucket.tokens));
            }

            // Deny: tell the client exactly how long to wait for 1 full token.
            double tokensNeeded = 1.0 - bucket.tokens;
            long retryAfterMs = (long) Math.ceil((tokensNeeded * 1000.0) / refillRatePerSecond);
            return RateLimitResult.deny(retryAfterMs);
        }
    }

    /** Test-only window into bucket state — handy for verification. */
    public double currentTokens(String key) {
        TokenBucket b = buckets.get(key);
        return b == null ? capacity : b.tokens;
    }

    /** Per-key mutable state. Package-private so the limiter can touch fields directly
     *  inside the synchronized block — no getter/setter ceremony on the hot path. */
    static final class TokenBucket {
        double tokens;
        long   lastRefillTime;

        TokenBucket(double tokens, long lastRefillTime) {
            this.tokens = tokens;
            this.lastRefillTime = lastRefillTime;
        }
    }
}
