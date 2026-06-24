package com.conceptcoding.interviewquestions.hello_all_questions.ratelimiter.algorithm;

import java.time.Clock;
import java.util.Map;

public class LimiterFactory {

    public Limiter create(Map<String, Object> externalConfig) {
        return create(externalConfig, Clock.systemUTC());
    }

    // package-private — used by tests and the driver to inject a controllable clock
    Limiter create(Map<String, Object> externalConfig, Clock clock) {
        String algorithm = (String) externalConfig.get("algorithm");

        @SuppressWarnings("unchecked")
        Map<String, Object> algoConfig = (Map<String, Object>) externalConfig.get("algoConfig");

        switch (algorithm) {
            case "TokenBucket":
                return new TokenBucketLimiter(
                        ((Number) algoConfig.get("capacity")).intValue(),
                        ((Number) algoConfig.get("refillRatePerSecond")).intValue(),
                        clock);
            case "SlidingWindowLog":
                return new SlidingWindowLogLimiter(
                        ((Number) algoConfig.get("maxRequests")).intValue(),
                        ((Number) algoConfig.get("windowMs")).longValue(),
                        clock);
            default:
                throw new IllegalArgumentException("Unknown algorithm: " + algorithm);
        }
    }
}
