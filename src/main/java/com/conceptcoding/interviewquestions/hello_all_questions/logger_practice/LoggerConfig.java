package com.conceptcoding.interviewquestions.hello_all_questions.logger_practice;

import com.conceptcoding.interviewquestions.hello_all_questions.logger_practice.appender.LogAppender;
import com.conceptcoding.interviewquestions.hello_all_questions.logger_practice.model.LogLevel;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class LoggerConfig {
    private LogLevel minLevel;
    // keyed by THRESHOLD, not exact level — an appender registered at WARN also
    // receives ERROR/FATAL. No separate "defaultAppender" needed: registering one
    // appender at the config's minLevel already acts as the catch-all.
    private final Map<LogLevel, List<LogAppender>> appendersByThreshold = new EnumMap<>(LogLevel.class);

    public LoggerConfig(LogLevel minLevel) { this.minLevel = minLevel; }

    public void setLogLevel(LogLevel minLevel) { this.minLevel = minLevel; }

    public void addLogAppender(LogLevel threshold, LogAppender appender) {
        appendersByThreshold.computeIfAbsent(threshold, k -> new ArrayList<>()).add(appender);
    }

    public List<LogAppender> getAppendersFor(LogLevel level) {
        List<LogAppender> result = new ArrayList<>();
        for (Map.Entry<LogLevel, List<LogAppender>> e : appendersByThreshold.entrySet()) {
            if (level.isAtLeast(e.getKey())) result.addAll(e.getValue());
        }
        return result;
    }

    public LogLevel getMinLevel() { return minLevel; }
}
