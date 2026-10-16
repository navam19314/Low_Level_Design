package com.conceptcoding.interviewquestions.hello_all_questions.policies.ratelimiter;

// Strategy: every rate-limiting algorithm answers the same question.
// true = let the request through, false = reject it (the API returns HTTP 429).
public interface RateLimiter {
    boolean allow(String clientId);
}
