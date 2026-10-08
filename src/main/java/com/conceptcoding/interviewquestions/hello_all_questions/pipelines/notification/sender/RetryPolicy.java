package com.conceptcoding.interviewquestions.hello_all_questions.pipelines.notification.sender;

// maxAttempts 4, initialBackoff 100 ms, multiplier 2 → waits 100, 200, 400 ms between tries.
public class RetryPolicy {

    private final int maxAttempts;
    private final long initialBackoffMs;
    private final int multiplier;

    public RetryPolicy(int maxAttempts, long initialBackoffMs, int multiplier) {
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be >= 1");
        this.maxAttempts = maxAttempts;
        this.initialBackoffMs = initialBackoffMs;
        this.multiplier = multiplier;
    }

    public int  getMaxAttempts()      { return maxAttempts; }
    public long getInitialBackoffMs() { return initialBackoffMs; }
    public int  getMultiplier()       { return multiplier; }
}
