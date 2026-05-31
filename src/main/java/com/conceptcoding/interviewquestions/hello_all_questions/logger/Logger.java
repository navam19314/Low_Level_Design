package com.conceptcoding.interviewquestions.hello_all_questions.logger;

import com.conceptcoding.interviewquestions.hello_all_questions.logger.model.LogLevel;
import com.conceptcoding.interviewquestions.hello_all_questions.logger.model.LogRecord;

import java.time.Clock;
import java.util.List;

/**
 * The public face of the library — the only class application code interacts with.
 *
 * <p>Config is set ONCE at construction; the destinations list is then immutable.
 * That's what lets us iterate without locking: no thread can race a structural
 * change because no API exposes one. Adding {@code addDestination} would force
 * locking around every iteration with zero requirement-driven benefit.
 *
 * <p>Timestamp and thread name are captured at the top of {@code log()} (NOT
 * inside each destination), so every destination sees the same moment for the
 * same record. Capturing per-destination would produce per-line clock skew.
 */
public class Logger {

    private final List<Destination> destinations;
    private final Clock clock;

    public Logger(List<Destination> destinations) {
        this(destinations, Clock.systemUTC());
    }

    public Logger(List<Destination> destinations, Clock clock) {
        this.destinations = List.copyOf(destinations);    // defensive immutable copy
        this.clock = clock;
    }

    public void log(LogLevel level, String message) {
        // Capture per-call data ONCE — every destination sees the same record.
        LogRecord record = new LogRecord(
                clock.instant(),
                level,
                message,
                Thread.currentThread().getName());

        // Sequential dispatch. No fan-out across threads — async dispatch is a
        // separate decision (see walkthrough §5.1). Destinations handle their own
        // locking, and Destination.write swallows its own failures, so a slow or
        // throwing destination can't block the others structurally.
        for (Destination destination : destinations) {
            destination.write(record);
        }
    }

    // Convenience methods — match the call shape developers actually use.
    public void debug(String message) { log(LogLevel.DEBUG, message); }
    public void info (String message) { log(LogLevel.INFO,  message); }
    public void warn (String message) { log(LogLevel.WARN,  message); }
    public void error(String message) { log(LogLevel.ERROR, message); }
    public void fatal(String message) { log(LogLevel.FATAL, message); }
}
