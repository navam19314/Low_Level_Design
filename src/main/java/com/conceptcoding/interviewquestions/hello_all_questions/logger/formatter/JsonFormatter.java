package com.conceptcoding.interviewquestions.hello_all_questions.logger.formatter;

import com.conceptcoding.interviewquestions.hello_all_questions.logger.model.LogRecord;

/**
 * Minimal JSON encoder — enough for the interview without pulling Jackson/Gson.
 * Escapes the message; ts/level/thread are safe primitive shapes. In production
 * you'd delegate to a real JSON library to handle every edge case.
 */
public class JsonFormatter implements Formatter {

    @Override
    public String format(LogRecord record) {
        StringBuilder sb = new StringBuilder(128);
        sb.append('{')
          .append("\"timestamp\":\"").append(record.getTimestamp()).append("\",")
          .append("\"level\":\"").append(record.getLevel()).append("\",")
          .append("\"thread\":\"").append(escape(record.getThreadName())).append("\",")
          .append("\"message\":\"").append(escape(record.getMessage())).append("\"")
          .append('}');
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        StringBuilder out = new StringBuilder(s.length() + 2);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\n': out.append("\\n");  break;
                case '\r': out.append("\\r");  break;
                case '\t': out.append("\\t");  break;
                default:   out.append(c);
            }
        }
        return out.toString();
    }
}
