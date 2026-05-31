package com.conceptcoding.interviewquestions.hello_all_questions.logger.sink;

/**
 * Strategy interface — the byte-writing side. Implementations turn an already-
 * formatted string into bytes on a target (stdout, file, future remote endpoint).
 *
 * <p>Deliberately narrow: a Sink doesn't filter, doesn't format, doesn't lock.
 * Those concerns live one layer up in Destination. The whole point of this
 * interface is so a future {@code RemoteSink} drops in as a new implementation,
 * with zero changes elsewhere.
 */
public interface Sink {
    void write(String formatted);
}
