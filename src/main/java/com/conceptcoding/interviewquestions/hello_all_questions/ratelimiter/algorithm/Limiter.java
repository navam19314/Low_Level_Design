package com.conceptcoding.interviewquestions.hello_all_questions.ratelimiter.algorithm;

import com.conceptcoding.interviewquestions.hello_all_questions.ratelimiter.model.RateLimitResult;

// Strategy interface — every rate-limiting algorithm implements this
public interface Limiter {
    RateLimitResult allow(String clientId);
}
