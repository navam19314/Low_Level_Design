package com.conceptcoding.interviewquestions.hello_all_questions.ratelimiter;

import com.conceptcoding.interviewquestions.hello_all_questions.ratelimiter.algorithm.Limiter;
import com.conceptcoding.interviewquestions.hello_all_questions.ratelimiter.model.LimiterConfig;
import com.conceptcoding.interviewquestions.hello_all_questions.ratelimiter.model.RateLimitResult;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrator + facade. Application code calls {@link #allow(String, String)}
 * and the right algorithm runs behind the scenes.
 *
 * <p>Two key choices:
 * <ul>
 *   <li><b>Eager construction</b> — every endpoint's limiter is built at startup,
 *       not lazily on first request. Cleaner concurrency (no double-checked locking
 *       for first-request creation), trivial memory cost at typical scale (dozens
 *       of endpoints).
 *   <li><b>Default fallback</b> — requests to an unconfigured endpoint still get
 *       rate limited via the default limiter. Never reject for missing config.
 * </ul>
 *
 * <p>The internal map is read-only after construction, so iteration / lookup
 * needs no synchronization. Per-request mutation lives one layer down inside
 * the per-key locks in each {@link Limiter}.
 */
public class RateLimiter {

    private final Map<String, Limiter> limiters;
    private final Limiter defaultLimiter;

    public RateLimiter(List<LimiterConfig> configs, LimiterConfig defaultConfig) {
        this(configs, defaultConfig, Clock.systemUTC());
    }

    public RateLimiter(List<LimiterConfig> configs, LimiterConfig defaultConfig, Clock clock) {
        LimiterFactory factory = new LimiterFactory(clock);

        // Eagerly build the per-endpoint limiter map at startup.
        Map<String, Limiter> built = new HashMap<>();
        for (LimiterConfig cfg : configs) {
            if (cfg.endpoint() == null) continue;
            built.put(cfg.endpoint(), factory.create(cfg));
        }
        this.limiters = Map.copyOf(built);          // immutable post-ctor
        this.defaultLimiter = factory.create(defaultConfig);
    }

    /**
     * Look up the limiter for this endpoint (or fall back to default) and ask it
     * whether this client may proceed. Only public method on the facade.
     */
    public RateLimitResult allow(String clientId, String endpoint) {
        Limiter limiter = limiters.getOrDefault(endpoint, defaultLimiter);
        return limiter.allow(clientId);
    }
}
