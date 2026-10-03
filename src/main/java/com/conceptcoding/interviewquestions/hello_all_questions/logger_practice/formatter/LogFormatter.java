package com.conceptcoding.interviewquestions.hello_all_questions.logger_practice.formatter;

import com.conceptcoding.interviewquestions.hello_all_questions.logger_practice.model.LogMessage;

public interface LogFormatter {
    String format(LogMessage logMessage);
}
