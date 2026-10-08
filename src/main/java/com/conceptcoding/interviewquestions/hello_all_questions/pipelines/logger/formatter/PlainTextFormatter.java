package com.conceptcoding.interviewquestions.hello_all_questions.pipelines.logger.formatter;

import com.conceptcoding.interviewquestions.hello_all_questions.pipelines.logger.model.LogRecord;

// Default human-readable format: 2026-05-06T10:00:00Z [INFO] [main] message text
public class PlainTextFormatter implements Formatter {

    @Override
    public String format(LogRecord record) {
        return record.getTimestamp()
                + " [" + record.getLevel() + "]"
                + " [" + record.getThreadName() + "] "
                + record.getMessage();
    }
}
