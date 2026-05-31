package com.conceptcoding.interviewquestions.hello_all_questions.ratelimiter.algorithm;

import com.conceptcoding.interviewquestions.hello_all_questions.ratelimiter.model.RateLimitResult;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sliding window log — perfectly accurate rate limiting by storing the exact
 * timestamp of every request in the last {@code windowMs}.
 *
 * <p>Accuracy beats fixed-window counters (no boundary "double-burst" effect)
 * at the cost of memory: O(maxRequests) per active client.
 *
 * <p>Thread-safety: per-key locking on the request log, same shape as
 * {@link TokenBucketLimiter}. Different clients never block each other.
 */
public class SlidingWindowLogLimiter implements Limiter {

    private final int maxRequests;
    private final long windowMs;
    private final ConcurrentHashMap<String, RequestLog> logs = new ConcurrentHashMap<>();
    private final Clock clock;

    public SlidingWindowLogLimiter(int maxRequests, long windowMs) {
        this(maxRequests, windowMs, Clock.systemUTC());
    }

    public SlidingWindowLogLimiter(int maxRequests, long windowMs, Clock clock) {
        if (maxRequests <= 0) throw new IllegalArgumentException("maxRequests must be > 0");
        if (windowMs    <= 0) throw new IllegalArgumentException("windowMs must be > 0");
        this.maxRequests = maxRequests;
        this.windowMs    = windowMs;
        this.clock       = clock;
    }

    @Override
    public RateLimitResult allow(String key) {
        RequestLog log = logs.computeIfAbsent(key, k -> new RequestLog());

        synchronized (log) {
            long now = clock.millis();
            long cutoff = now - windowMs;

            // Evict stale timestamps from the front (oldest first).
            // ArrayDeque.peek() / poll() are both O(1) — the right structure for this.
            while (!log.timestamps.isEmpty() && log.timestamps.peekFirst() < cutoff) {
                log.timestamps.pollFirst();
            }

            if (log.timestamps.size() < maxRequests) {
                log.timestamps.addLast(now);
                return RateLimitResult.allow(maxRequests - log.timestamps.size());
            }

            // Deny: client can retry when the OLDEST in-window timestamp ages out.
            long oldest = log.timestamps.peekFirst();
            long retryAfterMs = (oldest + windowMs) - now;
            return RateLimitResult.deny(retryAfterMs);
        }
    }

    static final class RequestLog {
        final Deque<Long> timestamps = new ArrayDeque<>();
    }
}
