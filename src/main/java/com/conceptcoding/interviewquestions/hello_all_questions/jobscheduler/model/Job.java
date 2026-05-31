package com.conceptcoding.interviewquestions.hello_all_questions.jobscheduler.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One scheduled unit of work. Mutable on {@code status} and {@code attempts}
 * (the rest of the fields are final). The state machine lives on this class —
 * not on the scheduler — Information Expert.
 *
 * <p>The {@code payload} is a {@link Runnable} for simplicity. Real schedulers
 * use a typed {@code Callable<Result>} or a job-class registry; here Runnable
 * keeps the contract minimal and lambda-friendly.
 */
public class Job {

    private final String id;
    private final String name;
    private final Runnable payload;
    private final int priority;
    private final Instant scheduledAt;
    private final Instant createdAt;
    private final RetryPolicy retryPolicy;

    private volatile JobStatus status = JobStatus.SCHEDULED;
    private final AtomicInteger attempts = new AtomicInteger(0);
    private volatile String lastError;

    public Job(String name, Runnable payload, int priority,
               Instant scheduledAt, RetryPolicy retryPolicy, Instant now) {
        this.id          = UUID.randomUUID().toString();
        this.name        = Objects.requireNonNull(name);
        this.payload     = Objects.requireNonNull(payload);
        this.priority    = priority;
        this.scheduledAt = Objects.requireNonNull(scheduledAt);
        this.retryPolicy = retryPolicy == null ? RetryPolicy.noRetries() : retryPolicy;
        this.createdAt   = Objects.requireNonNull(now);
    }

    public String       getId()           { return id; }
    public String       getName()         { return name; }
    public Runnable     getPayload()      { return payload; }
    public int          getPriority()     { return priority; }
    public Instant      getScheduledAt()  { return scheduledAt; }
    public Instant      getCreatedAt()    { return createdAt; }
    public RetryPolicy  getRetryPolicy()  { return retryPolicy; }
    public JobStatus    getStatus()       { return status; }
    public int          getAttempts()     { return attempts.get(); }
    public String       getLastError()    { return lastError; }

    // ----- mutation helpers — called only by the scheduler -----

    public void setStatus(JobStatus next) {
        this.status = next;
    }

    public int incrementAttempts() {
        return attempts.incrementAndGet();
    }

    public void setLastError(String message) {
        this.lastError = message;
    }

    /** Whether the policy allows another retry given the current attempt count. */
    public boolean canRetry() {
        return attempts.get() < retryPolicy.maxAttempts();
    }
}
