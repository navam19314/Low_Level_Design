package com.conceptcoding.interviewquestions.hello_all_questions.logger.model;

import java.time.Instant;

/**
 * Immutable value object representing one log call. Built once at the call site,
 * read by every destination, never mutated. Immutability is what makes it safe
 * to share across threads and across destinations with no synchronization.
 *
 * <p>This is a class (not 4 raw parameters) because adding a new field later —
 * loggerName, requestId, MDC map — is a one-line change here instead of a
 * signature change on every Destination, Sink, and Formatter method.
 */
public final class LogRecord {

    private final Instant timestamp;
    private final LogLevel level;
    private final String message;
    private final String threadName;

    public LogRecord(Instant timestamp, LogLevel level, String message, String threadName) {
        this.timestamp  = timestamp;
        this.level      = level;
        this.message    = message;
        this.threadName = threadName;
    }

    public Instant  getTimestamp()  { return timestamp; }
    public LogLevel getLevel()      { return level; }
    public String   getMessage()    { return message; }
    public String   getThreadName() { return threadName; }
}
