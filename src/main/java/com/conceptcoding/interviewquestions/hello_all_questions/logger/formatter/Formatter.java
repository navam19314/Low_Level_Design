package com.conceptcoding.interviewquestions.hello_all_questions.logger.formatter;

import com.conceptcoding.interviewquestions.hello_all_questions.logger.model.LogRecord;

/**
 * Strategy interface — serializes a LogRecord to a string. Two implementations
 * ship in v1 (plain text, JSON); a third (CSV, key-value, etc.) is a new class
 * with zero changes to Destination, Sink, or Logger.
 *
 * <p>Implementations MUST be pure functions — given the same record, return the
 * same string. Pure formatters are safe to share across threads and destinations
 * with no synchronization. Don't put mutable state on a Formatter.
 */
public interface Formatter {
    String format(LogRecord record);
}
