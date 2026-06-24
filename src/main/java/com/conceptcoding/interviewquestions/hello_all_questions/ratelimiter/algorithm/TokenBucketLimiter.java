package com.conceptcoding.interviewquestions.hello_all_questions.ratelimiter.algorithm;

import com.conceptcoding.interviewquestions.hello_all_questions.ratelimiter.model.RateLimitResult;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;

public class TokenBucketLimiter implements Limiter {

    private final int capacity;
    private final int refillRatePerSecond;
    private final Clock clock;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public TokenBucketLimiter(int capacity, int refillRatePerSecond) {
        this(capacity, refillRatePerSecond, Clock.systemUTC());
    }

    public TokenBucketLimiter(int capacity, int refillRatePerSecond, Clock clock) {
        this.capacity = capacity;
        this.refillRatePerSecond = refillRatePerSecond;
        this.clock = clock;
    }

    @Override
    public RateLimitResult allow(String clientId) {
        // first-time client starts with a full bucket
        Bucket bucket = buckets.computeIfAbsent(clientId,
                k -> new Bucket(capacity, clock.millis()));

        synchronized (bucket) {
            long now = clock.millis();
            long elapsedMs = now - bucket.lastRefillTime;

            // lazy refill: add however many tokens should have arrived since last touch
            double tokensToAdd = (elapsedMs * refillRatePerSecond) / 1000.0;
            bucket.tokens = Math.min(capacity, bucket.tokens + tokensToAdd); // cap at capacity
            bucket.lastRefillTime = now;

            if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0;
                return RateLimitResult.allow((int) Math.floor(bucket.tokens));
            }

            // ceil so we never tell the client to retry too soon
            long retryAfterMs = (long) Math.ceil((1.0 - bucket.tokens) * 1000.0 / refillRatePerSecond);
            return RateLimitResult.deny(retryAfterMs);
        }
    }

    // mutable per-client state
    static class Bucket {
        double tokens;
        long lastRefillTime;

        Bucket(double tokens, long lastRefillTime) {
            this.tokens = tokens;
            this.lastRefillTime = lastRefillTime;
        }
    }
}
