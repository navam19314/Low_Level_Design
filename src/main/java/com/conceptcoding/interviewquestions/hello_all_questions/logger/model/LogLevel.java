package com.conceptcoding.interviewquestions.hello_all_questions.logger.model;

/**
 * Five severity levels with explicit ordering. Severity-as-int beats relying on
 * enum {@code ordinal()} because it survives reordering of declarations — adding
 * a new level (e.g., TRACE below DEBUG) doesn't silently shift comparisons.
 */
public enum LogLevel {
    DEBUG(10),
    INFO (20),
    WARN (30),
    ERROR(40),
    FATAL(50);

    private final int severity;

    LogLevel(int severity) { this.severity = severity; }

    public int getSeverity() { return severity; }

    /** True if this level is at-or-above the given threshold. */
    public boolean isAtLeast(LogLevel minimum) {
        return severity >= minimum.severity;
    }
}
