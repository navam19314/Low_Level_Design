package com.conceptcoding.interviewquestions.hello_all_questions.logger;

import com.conceptcoding.interviewquestions.hello_all_questions.logger.formatter.Formatter;
import com.conceptcoding.interviewquestions.hello_all_questions.logger.model.LogLevel;
import com.conceptcoding.interviewquestions.hello_all_questions.logger.model.LogRecord;
import com.conceptcoding.interviewquestions.hello_all_questions.logger.sink.Sink;

/**
 * One configured output target. Owns:
 *   - a level threshold (filter records below it)
 *   - a formatter (serialize to string — Strategy)
 *   - a sink (write bytes — Strategy)
 *   - a per-destination lock (protect the shared sink resource)
 *
 * <p>Why this class is concrete (not abstract): there's exactly one valid
 * filter-format-lock-write shape. All variation lives behind Formatter and Sink.
 *
 * <p>Why composition (not subclasses like {@code JsonFileDestination}):
 * format and sink-type vary INDEPENDENTLY per the requirements. With N formats
 * × M sink types, inheritance would be N×M classes; composition is N+M.
 *
 * <p>Failure isolation: any exception from {@code sink.write} is caught and
 * routed to stderr — never propagates back to Logger.log, so one bad destination
 * can't take out the others or crash the caller.
 */
public class Destination {

    private final Formatter formatter;
    private final LogLevel minLevel;
    private final Sink sink;
    private final Object lock = new Object();    // protects the sink resource

    public Destination(Formatter formatter, LogLevel minLevel, Sink sink) {
        this.formatter = formatter;
        this.minLevel  = minLevel;
        this.sink      = sink;
    }

    public void write(LogRecord record) {
        // 1. Filter — cheap, no allocation. Below-threshold records are silently dropped.
        if (!record.getLevel().isAtLeast(minLevel)) {
            return;
        }

        // 2. Format OUTSIDE the lock. Records are immutable and formatters are pure,
        //    so two threads can format the SAME record concurrently with no contention.
        String formatted = formatter.format(record);

        // 3. Lock only around the sink write (the shared resource).
        synchronized (lock) {
            try {
                sink.write(formatted);
            } catch (Throwable t) {
                // Failure isolation: one bad sink (disk full, broken pipe, etc.)
                // must not propagate back to Logger.log or take out other destinations.
                System.err.println("logger: sink write failed: " + t.getMessage());
            }
        }
    }
}
