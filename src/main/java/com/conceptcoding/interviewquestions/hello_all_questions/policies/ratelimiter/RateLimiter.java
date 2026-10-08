package com.conceptcoding.interviewquestions.hello_all_questions.policies.ratelimiter;

import com.conceptcoding.interviewquestions.hello_all_questions.policies.ratelimiter.algorithm.Limiter;
import com.conceptcoding.interviewquestions.hello_all_questions.policies.ratelimiter.model.RateLimitResult;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// The service callers use: picks the limiter for the endpoint and delegates to it (Strategy)
public class RateLimiter {

    // ConcurrentHashMap: allow() reads it from many threads, and register() stays safe at runtime
    private final Map<String, Limiter> limiters = new ConcurrentHashMap<>();
    private final Limiter defaultLimiter;

    public RateLimiter(Limiter defaultLimiter) {
        this.defaultLimiter = defaultLimiter;
    }

    public void register(String endpoint, Limiter limiter) {
        limiters.put(endpoint, limiter);
    }

    public RateLimitResult allow(String clientId, String endpoint) {
        Limiter limiter = limiters.getOrDefault(endpoint, defaultLimiter);
        return limiter.allow(clientId);
    }
}
