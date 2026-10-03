package com.conceptcoding.interviewquestions.hello_all_questions.logger_practice;

import com.conceptcoding.interviewquestions.hello_all_questions.logger_practice.appender.LogAppender;
import com.conceptcoding.interviewquestions.hello_all_questions.logger_practice.model.LogLevel;
import com.conceptcoding.interviewquestions.hello_all_questions.logger_practice.model.LogMessage;

import java.time.Instant;

public class Logger {
    private final LoggerConfig config;
    public Logger(LoggerConfig config) { this.config = config; }

    public void log(LogLevel level, String message) {
        if (!level.isAtLeast(config.getMinLevel())) return;

        LogMessage logMessage = new LogMessage(level, message, Thread.currentThread().getName(), Instant.now());

        for (LogAppender appender : config.getAppendersFor(level)) {
            try {
                appender.append(logMessage);
            } catch (Exception e) {
                // one bad appender can't take out the rest of the fan-out
                System.err.println("logger: appender failed - " + e.getMessage());
            }
        }
    }

    public void debug(String message) { log(LogLevel.DEBUG, message); }
    public void info (String message) { log(LogLevel.INFO,  message); }
    public void warn (String message) { log(LogLevel.WARN,  message); }
    public void error(String message) { log(LogLevel.ERROR, message); }
}
