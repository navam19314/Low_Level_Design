package com.conceptcoding.interviewquestions.hello_all_questions.ratelimiter;

import com.conceptcoding.interviewquestions.hello_all_questions.ratelimiter.algorithm.Limiter;
import com.conceptcoding.interviewquestions.hello_all_questions.ratelimiter.algorithm.LimiterFactory;
import com.conceptcoding.interviewquestions.hello_all_questions.ratelimiter.model.RateLimitResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Facade — application code only ever calls allow(); dispatch is hidden inside
public class RateLimiter {

    private final Map<String, Limiter> limiters = new HashMap<>();
    private final Limiter defaultLimiter;

    // Config-driven constructor — matches what an API gateway receives at startup
    public RateLimiter(List<Map<String, Object>> configs, Map<String, Object> defaultConfig) {
        LimiterFactory factory = new LimiterFactory();
        for (Map<String, Object> config : configs) {
            String endpoint = (String) config.get("endpoint");
            if (endpoint == null) continue;
            limiters.put(endpoint, factory.create(config));
        }
        this.defaultLimiter = factory.create(defaultConfig);
    }

    // Direct constructor — useful in tests/driver when you want to inject a specific Limiter
    public RateLimiter(Limiter defaultLimiter) {
        this.defaultLimiter = defaultLimiter;
    }

    // Register per-endpoint limiters at startup, before serving traffic
    public void register(String endpoint, Limiter limiter) {
        limiters.put(endpoint, limiter);
    }

    public RateLimitResult allow(String clientId, String endpoint) {
        Limiter limiter = limiters.getOrDefault(endpoint, defaultLimiter);
        return limiter.allow(clientId);
    }
}
