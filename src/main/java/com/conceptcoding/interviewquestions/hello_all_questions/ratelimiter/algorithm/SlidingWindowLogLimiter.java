package com.conceptcoding.interviewquestions.hello_all_questions.ratelimiter.algorithm;

import com.conceptcoding.interviewquestions.hello_all_questions.ratelimiter.model.RateLimitResult;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

public class SlidingWindowLogLimiter implements Limiter {

    private final int maxRequests;
    private final long windowMs;
    private final Clock clock;
    // per-client log of request timestamps within the current window
    private final ConcurrentHashMap<String, Deque<Long>> logs = new ConcurrentHashMap<>();

    public SlidingWindowLogLimiter(int maxRequests, long windowMs) {
        this(maxRequests, windowMs, Clock.systemUTC());
    }

    public SlidingWindowLogLimiter(int maxRequests, long windowMs, Clock clock) {
        this.maxRequests = maxRequests;
        this.windowMs = windowMs;
        this.clock = clock;
    }

    @Override
    public RateLimitResult allow(String clientId) {
        Deque<Long> log = logs.computeIfAbsent(clientId, k -> new ArrayDeque<>());

        synchronized (log) {
            long now = clock.millis();
            long cutoff = now - windowMs;

            // drop timestamps that have fallen outside the window
            while (!log.isEmpty() && log.peekFirst() < cutoff) {
                log.pollFirst();
            }

            if (log.size() < maxRequests) {
                log.addLast(now);
                return RateLimitResult.allow(maxRequests - log.size());
            }

            // retry when the oldest in-window timestamp ages out
            long retryAfterMs = log.peekFirst() + windowMs - now;
            return RateLimitResult.deny(retryAfterMs);
        }
    }
}
