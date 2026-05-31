package com.conceptcoding.interviewquestions.hello_all_questions.logger.sink;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Append-mode file sink. Open the file once in the constructor (open syscall is
 * expensive) and keep the handle. Flush after every write so recent log lines
 * are visible if the process crashes — the default "flush on close" is exactly
 * the wrong moment for a logger.
 *
 * <p>Implements AutoCloseable so a {@code try-with-resources} or shutdown hook
 * closes the underlying writer cleanly.
 */
public class FileSink implements Sink, AutoCloseable {

    private final BufferedWriter writer;
    private final Path filePath;

    public FileSink(Path filePath) throws IOException {
        this.filePath = filePath;
        this.writer = Files.newBufferedWriter(
                filePath, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    @Override
    public void write(String formatted) {
        try {
            writer.write(formatted);
            writer.newLine();
            writer.flush();                // visible before any process crash
        } catch (IOException e) {
            // Wrap as unchecked so Sink interface stays simple. Destination
            // catches Throwable and isolates the failure.
            throw new UncheckedIOException("FileSink write failed: " + filePath, e);
        }
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
