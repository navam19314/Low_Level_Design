package com.conceptcoding.interviewquestions.hello_all_questions.logger_practice.appender;

import com.conceptcoding.interviewquestions.hello_all_questions.logger_practice.model.LogMessage;

public interface LogAppender {
    void append(LogMessage logMessage);
}
