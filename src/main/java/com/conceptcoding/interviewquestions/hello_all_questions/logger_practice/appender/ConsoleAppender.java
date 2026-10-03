package com.conceptcoding.interviewquestions.hello_all_questions.logger_practice.appender;

import com.conceptcoding.interviewquestions.hello_all_questions.logger_practice.formatter.LogFormatter;
import com.conceptcoding.interviewquestions.hello_all_questions.logger_practice.model.LogMessage;

public class ConsoleAppender implements LogAppender {
    private final LogFormatter formatter;
    public ConsoleAppender(LogFormatter formatter) { this.formatter = formatter; }

    @Override
    public synchronized void append(LogMessage logMessage) {
        System.out.println(formatter.format(logMessage));
    }
}
