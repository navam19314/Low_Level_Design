package com.conceptcoding.interviewquestions.hello_all_questions.ratelimiter;

import com.conceptcoding.interviewquestions.hello_all_questions.ratelimiter.algorithm.Limiter;
import com.conceptcoding.interviewquestions.hello_all_questions.ratelimiter.algorithm.SlidingWindowLogLimiter;
import com.conceptcoding.interviewquestions.hello_all_questions.ratelimiter.algorithm.TokenBucketLimiter;
import com.conceptcoding.interviewquestions.hello_all_questions.ratelimiter.model.LimiterConfig;

import java.time.Clock;

/**
 * Factory pattern — turns a heterogeneous config blob into the right concrete
 * {@link Limiter}. Centralizes the "algorithm string → concrete class" dispatch
 * so it lives in ONE place; adding a new algorithm is one new switch case here
 * plus one new class. Nothing else in the system changes.
 *
 * <p>If algorithms ever become pluggable at runtime, this evolves to a Registry
 * pattern with a {@code Map<String, AlgorithmBuilder>}. For two-to-five algorithms
 * the switch is clearer and ships fewer abstractions.
 */
public class LimiterFactory {

    private final Clock clock;

    public LimiterFactory()              { this(Clock.systemUTC()); }
    public LimiterFactory(Clock clock)   { this.clock = clock; }

    public Limiter create(LimiterConfig config) {
        switch (config.algorithm()) {
            case "TokenBucket":
                return new TokenBucketLimiter(
                        config.getInt("capacity"),
                        config.getInt("refillRatePerSecond"),
                        clock);
            case "SlidingWindowLog":
                return new SlidingWindowLogLimiter(
                        config.getInt("maxRequests"),
                        config.getLong("windowMs"),
                        clock);
            default:
                // Fail fast — silently using a default would corrupt observability.
                throw new IllegalArgumentException("Unknown algorithm: " + config.algorithm());
        }
    }
}
