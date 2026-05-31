package com.conceptcoding.interviewquestions.hello_all_questions.ratelimiter.algorithm;

import com.conceptcoding.interviewquestions.hello_all_questions.ratelimiter.model.RateLimitResult;

/**
 * Strategy interface — the contract every rate-limiting algorithm implements.
 *
 * <p>Single method, single responsibility: take a key (typically client id) and
 * return a decision. The interface DELIBERATELY exposes no per-key state — each
 * algorithm tracks state in whichever shape fits it (TokenBucket has tokens+lastRefill;
 * SlidingWindowLog has a queue of timestamps; FixedWindow has count+windowStart).
 */
public interface Limiter {
    RateLimitResult allow(String key);
}
