package com.conceptcoding.interviewquestions.hello_all_questions.urlshortener;

import java.security.SecureRandom;
import java.util.Random;
import java.util.function.Predicate;

/**
 * Random ids in a configurable code-length range. Collision-probability scales
 * with how full the keyspace is; we retry on collision up to {@code maxAttempts}.
 *
 * <p>Use this when you want UNPREDICTABLE short codes — the counter strategy
 * leaks "next code is N+1" which lets adversaries enumerate every short link.
 */
public class RandomIdStrategy implements IdGenerationStrategy {

    private static final int DEFAULT_MAX_ATTEMPTS = 10;
    private final long range;            // exclusive upper bound for the random long
    private final Random random;
    private final int maxAttempts;

    public RandomIdStrategy()                              { this(/* 7-char codes */ 62L * 62 * 62 * 62 * 62 * 62 * 62); }
    public RandomIdStrategy(long range)                    { this(range, new SecureRandom(), DEFAULT_MAX_ATTEMPTS); }
    public RandomIdStrategy(long range, Random random, int maxAttempts) {
        this.range = range;
        this.random = random;
        this.maxAttempts = maxAttempts;
    }

    @Override
    public long nextId(Predicate<String> isAvailable) {
        for (int i = 0; i < maxAttempts; i++) {
            long candidate = (long) (random.nextDouble() * range);
            String code = Base62Encoder.encode(candidate);
            if (isAvailable.test(code)) return candidate;
        }
        // At this load factor we should reshape (expand range or rotate keys).
        throw new IllegalStateException(
                "Could not find a non-colliding id after " + maxAttempts + " attempts");
    }
}
