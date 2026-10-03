package com.conceptcoding.interviewquestions.hello_all_questions.logger_practice.model;

public enum LogLevel {
    DEBUG(10), INFO(20), WARN(30), ERROR(40), FATAL(50);

    private final int severity;
    LogLevel(int severity) { this.severity = severity; }

    // true if this level is at-or-above the given threshold — this is what makes
    // registering at INFO also catch WARN/ERROR/FATAL automatically.
    public boolean isAtLeast(LogLevel minimum) { return severity >= minimum.severity; }
}
