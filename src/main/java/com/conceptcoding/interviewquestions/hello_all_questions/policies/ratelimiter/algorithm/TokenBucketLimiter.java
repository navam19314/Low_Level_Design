package com.conceptcoding.interviewquestions.hello_all_questions.policies.ratelimiter.algorithm;

import com.conceptcoding.interviewquestions.hello_all_questions.policies.ratelimiter.RateLimiter;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

// Each client has a jar of up to capacity tokens. A request takes one token; no token → rejected.
// Tokens drip back at refillPerSecond. A full jar allows a short burst, the refill sets the steady rate.
//
// Lazy refill: no timer thread. When a client calls, we add the tokens that WOULD have dripped in
// since their last call, capped at capacity.
//
// Thread safety: allow() is synchronized: the check and the "take a token" are one step.
public class TokenBucketLimiter implements RateLimiter {

    private final int capacity;
    private final double refillPerSecond;
    private final Clock clock;
    private final Map<String, Bucket> buckets = new HashMap<>();

    public TokenBucketLimiter(int capacity, double refillPerSecond, Clock clock) {
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
        this.clock = clock;
    }

    @Override
    public synchronized boolean allow(String clientId) {
        long now = clock.millis();
        Bucket bucket = buckets.get(clientId);
        if (bucket == null) {                                   // a new client starts with a full jar
            bucket = new Bucket(capacity, now);
            buckets.put(clientId, bucket);
        }

        double secondsPassed = (now - bucket.lastRefillMs) / 1000.0;
        bucket.tokens = Math.min(capacity, bucket.tokens + secondsPassed * refillPerSecond);
        bucket.lastRefillMs = now;

        if (bucket.tokens >= 1) {
            bucket.tokens -= 1;
            return true;
        }
        return false;
    }

    // one client's jar
    private static class Bucket {
        double tokens;
        long lastRefillMs;

        Bucket(double tokens, long lastRefillMs) {
            this.tokens = tokens;
            this.lastRefillMs = lastRefillMs;
        }
    }
}
