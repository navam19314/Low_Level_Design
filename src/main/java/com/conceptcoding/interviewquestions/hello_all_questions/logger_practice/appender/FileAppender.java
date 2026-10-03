package com.conceptcoding.interviewquestions.hello_all_questions.logger_practice.appender;

import com.conceptcoding.interviewquestions.hello_all_questions.logger_practice.formatter.LogFormatter;
import com.conceptcoding.interviewquestions.hello_all_questions.logger_practice.model.LogMessage;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class FileAppender implements LogAppender {
    private final LogFormatter formatter;
    private final BufferedWriter writer;

    public FileAppender(LogFormatter formatter, String path) throws IOException {
        this.formatter = formatter;
        this.writer = Files.newBufferedWriter(Path.of(path), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    @Override
    public synchronized void append(LogMessage logMessage) {
        try {
            writer.write(formatter.format(logMessage));
            writer.newLine();
            writer.flush();                       // visible even if the process crashes
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
