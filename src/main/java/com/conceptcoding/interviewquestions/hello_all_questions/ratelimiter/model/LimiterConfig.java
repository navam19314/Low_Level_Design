package com.conceptcoding.interviewquestions.hello_all_questions.ratelimiter.model;

import java.util.Map;
import java.util.Objects;

/**
 * Raw config blob — exactly the shape the external config service hands us:
 *   { endpoint: "/search", algorithm: "TokenBucket", algoConfig: { ... } }
 *
 * <p>We keep {@code algoConfig} as {@code Map<String,Object>} (instead of polymorphic
 * typed configs) so adding a new algorithm doesn't require a new config subclass —
 * just a new factory case. This matches what real config services emit (JSON/YAML).
 */
public record LimiterConfig(String endpoint, String algorithm, Map<String, Object> algoConfig) {

    public LimiterConfig {
        Objects.requireNonNull(algorithm, "algorithm required");
        Objects.requireNonNull(algoConfig, "algoConfig required");
    }

    /** Convenience: read an int from algoConfig (tolerates Integer/Long/Double from JSON). */
    public int getInt(String key) {
        Object v = algoConfig.get(key);
        if (v == null) throw new IllegalArgumentException("Missing config key: " + key);
        return ((Number) v).intValue();
    }

    public long getLong(String key) {
        Object v = algoConfig.get(key);
        if (v == null) throw new IllegalArgumentException("Missing config key: " + key);
        return ((Number) v).longValue();
    }
}
