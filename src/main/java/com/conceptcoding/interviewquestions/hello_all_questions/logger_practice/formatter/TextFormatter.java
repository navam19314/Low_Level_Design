package com.conceptcoding.interviewquestions.hello_all_questions.logger_practice.formatter;

import com.conceptcoding.interviewquestions.hello_all_questions.logger_practice.model.LogMessage;

public class TextFormatter implements LogFormatter {
    @Override
    public String format(LogMessage m) {
        return m.getTimestamp() + " [" + m.getLevel() + "] [" + m.getSource() + "] " + m.getMessage();
    }
}
