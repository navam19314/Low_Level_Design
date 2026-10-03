package com.conceptcoding.interviewquestions.hello_all_questions.logger_practice.model;

import java.time.Instant;

// Immutable — built once per log() call, shared by every appender, never mutated.
public final class LogMessage {
    private final LogLevel level;
    private final String message;
    private final String source;
    private final Instant timestamp;

    public LogMessage(LogLevel level, String message, String source, Instant timestamp) {
        this.level = level;
        this.message = message;
        this.source = source;
        this.timestamp = timestamp;
    }

    public LogLevel getLevel()     { return level; }
    public String   getMessage()   { return message; }
    public String   getSource()    { return source; }
    public Instant  getTimestamp() { return timestamp; }
}
