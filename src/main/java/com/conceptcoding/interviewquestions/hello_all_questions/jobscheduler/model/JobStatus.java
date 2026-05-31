package com.conceptcoding.interviewquestions.hello_all_questions.jobscheduler.model;

/**
 * State machine for a job's lifecycle.
 *
 * <pre>
 *   SCHEDULED ──→ RUNNING ──→ COMPLETED  (terminal)
 *                       ╰──→ RETRYING ──→ RUNNING (loop until COMPLETED or maxAttempts)
 *                       ╰──→ FAILED     (terminal — retries exhausted)
 *   SCHEDULED ──→ CANCELLED              (terminal — cancellation before run)
 * </pre>
 */
public enum JobStatus {
    SCHEDULED,
    RUNNING,
    RETRYING,
    COMPLETED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
