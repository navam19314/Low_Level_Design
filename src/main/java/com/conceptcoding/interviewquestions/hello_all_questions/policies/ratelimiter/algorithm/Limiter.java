package com.conceptcoding.interviewquestions.hello_all_questions.policies.ratelimiter.algorithm;

import com.conceptcoding.interviewquestions.hello_all_questions.policies.ratelimiter.model.RateLimitResult;

// Strategy interface — every rate-limiting algorithm implements this
public interface Limiter {
    RateLimitResult allow(String clientId);
}
