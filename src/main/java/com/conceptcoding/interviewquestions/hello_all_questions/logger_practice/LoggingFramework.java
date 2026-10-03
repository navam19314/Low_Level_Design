package com.conceptcoding.interviewquestions.hello_all_questions.logger_practice;

import com.conceptcoding.interviewquestions.hello_all_questions.logger_practice.appender.ConsoleAppender;
import com.conceptcoding.interviewquestions.hello_all_questions.logger_practice.appender.FileAppender;
import com.conceptcoding.interviewquestions.hello_all_questions.logger_practice.formatter.TextFormatter;
import com.conceptcoding.interviewquestions.hello_all_questions.logger_practice.model.LogLevel;

import java.io.IOException;

public class LoggingFramework {
    public static void main(String[] args) throws IOException {
        LoggerConfig config = new LoggerConfig(LogLevel.INFO);
        config.addLogAppender(LogLevel.INFO,  new ConsoleAppender(new TextFormatter()));
        config.addLogAppender(LogLevel.ERROR, new FileAppender(new TextFormatter(), "error.txt"));

        Logger logger = new Logger(config);
        logger.debug("Debug");    // ignored (below minLevel)
        logger.info("Info");      // console
        logger.warn("Warning");   // console (WARN cascades from the INFO registration)
        logger.error("Error");    // console + file (ERROR cascades from both)
    }
}
