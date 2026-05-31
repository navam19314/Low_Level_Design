package com.conceptcoding.interviewquestions.hello_all_questions.logger;

import com.conceptcoding.interviewquestions.hello_all_questions.logger.formatter.JsonFormatter;
import com.conceptcoding.interviewquestions.hello_all_questions.logger.formatter.PlainTextFormatter;
import com.conceptcoding.interviewquestions.hello_all_questions.logger.model.LogLevel;
import com.conceptcoding.interviewquestions.hello_all_questions.logger.sink.Sink;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class LoggerDriver {

    public static void main(String[] args) throws Exception {
        scenarioFilteringAcrossDestinations();
        scenarioFailureIsolation();
        scenarioConcurrentBurst();
    }

    // -------- Scenario 1: independent per-destination thresholds + formats --------
    private static void scenarioFilteringAcrossDestinations() {
        System.out.println("=== Scenario 1: per-destination threshold + format ===");

        Destination consoleDebug = new Destination(
                new PlainTextFormatter(), LogLevel.DEBUG, /* sink */ s -> System.out.println("[console] " + s));
        Destination consoleWarnJson = new Destination(
                new JsonFormatter(), LogLevel.WARN,  /* sink */ s -> System.out.println("[json-warn]  " + s));

        Logger logger = new Logger(List.of(consoleDebug, consoleWarnJson));
        logger.debug("debug-only goes to console");
        logger.info ("info hits console, filtered by json-warn");
        logger.warn ("warn hits BOTH destinations, different format");
        System.out.println();
    }

    // -------- Scenario 2: one sink throws; others keep working, caller never sees it --------
    private static void scenarioFailureIsolation() {
        System.out.println("=== Scenario 2: failing sink doesn't take down the others ===");

        Sink workingSink = formatted -> System.out.println("[console] " + formatted);
        Sink flakySink   = formatted -> { throw new RuntimeException("disk full"); };

        Destination console = new Destination(new PlainTextFormatter(), LogLevel.DEBUG, workingSink);
        Destination flaky   = new Destination(new PlainTextFormatter(), LogLevel.DEBUG, flakySink);

        Logger logger = new Logger(List.of(console, flaky));
        logger.error("this must reach console even though flaky-sink throws");
        System.out.println("(notice the stderr 'logger: sink write failed' line above)");
        System.out.println();
    }

    // -------- Scenario 3: 50 threads burst-log; verify per-record atomicity --------
    private static void scenarioConcurrentBurst() throws Exception {
        System.out.println("=== Scenario 3: 50 threads x 20 records — atomicity check ===");

        CapturingSink capture = new CapturingSink();
        Destination dest = new Destination(new JsonFormatter(), LogLevel.DEBUG, capture);
        Logger logger = new Logger(List.of(dest));

        int threads = 50;
        int perThread = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch fire  = new CountDownLatch(1);

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            pool.submit(() -> {
                Thread.currentThread().setName("worker-" + threadId);
                ready.countDown();
                try {
                    fire.await();
                    for (int i = 0; i < perThread; i++) {
                        logger.info("hello from worker " + threadId + " msg " + i);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        ready.await();
        fire.countDown();
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        int expected = threads * perThread;
        int actual   = capture.size();
        int wellFormed = capture.wellFormedCount();

        System.out.println("  total records written : " + actual + " (expect " + expected + ")");
        System.out.println("  well-formed JSON lines: " + wellFormed + " (expect " + expected + ")");
        System.out.println("  any malformed?         " + ((actual == wellFormed) ? "no  ✓" : "YES — race!"));
    }

    /**
     * Captures every formatted string. wellFormedCount verifies each entry matches
     * the expected JSON shape exactly — interleaved writes would produce malformed
     * entries because two threads' bytes would be concatenated into one slot.
     */
    static final class CapturingSink implements Sink {
        private static final Pattern WELL_FORMED = Pattern.compile(
                "^\\{\"timestamp\":\"[^\"]+\",\"level\":\"[A-Z]+\",\"thread\":\"[^\"]+\",\"message\":\"[^\"]+\"\\}$");
        private final List<String> entries = Collections.synchronizedList(new CopyOnWriteArrayList<>());

        @Override public void write(String formatted) { entries.add(formatted); }

        int size() { return entries.size(); }

        int wellFormedCount() {
            int count = 0;
            for (String e : entries) {
                if (WELL_FORMED.matcher(e).matches()) count++;
            }
            return count;
        }
    }
}
