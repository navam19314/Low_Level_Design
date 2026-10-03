# Logging Framework — practice attempt, reviewed and fixed

This folder is a **separate practice attempt** at the logging problem — written from
scratch, then reviewed and corrected. It is intentionally kept apart from
`hello_all_questions/logger/`, which is this deck's existing, already-reviewed
canonical implementation of the same problem (Logger.java, Destination.java,
Sink/Formatter, LogRecord, plus its own driver with concurrency tests). Nothing in
that folder was touched.

The two solve the same problem with different class shapes: this one uses
`LoggerConfig`/`LogAppender`/`LogMessage`, the canonical one uses
`Destination`/`Sink`/`Formatter`/`LogRecord`. Both are valid answers; the canonical
one additionally has JSON formatting, a separate `Sink` abstraction, `Clock`
injection for deterministic tests, and its own driver scenarios.

Split into one class/interface per file, grouped by concern into `model/`,
`formatter/`, and `appender/` sub-packages — the way you'd actually lay it out
typing on a shared doc in a real interview, not one giant file.

## Bugs found in the original draft (would have failed to compile or run)

1. Stray unused import of an internal JDK class (`sun.rmi.runtime.Log`) — not
   referenced anywhere, and not a public API on modern JDKs.
2. `TextFormatter.format()` had an empty body but a non-`void` return type —
   compile error ("missing return statement").
3. `addLogAppender` did `appenderMap.get(level).add(...)` — `.get(level)` returns
   `null` for a level not yet in the map, so this NPE'd on the very first call.
4. `Logger.log()` built `new LogMessage()` but never set any of its fields — the
   `message` parameter passed to `log()`/`info()`/etc. was silently discarded.
5. `FileAppender.append()` just did `System.out.println(...)`, identical to
   `ConsoleAppender` — it never actually wrote to its own `path`.

## Design changes made on top of the bug fixes

- **Threshold cascading**: appenders are now registered by *threshold*
  (`LogLevel.isAtLeast(minimum)`), not by exact level with a single fallback. An
  appender registered at `INFO` now automatically also receives `WARN`/`ERROR`/
  `FATAL` — this is what real logging frameworks (log4j, slf4j) do, and it's what
  an interviewer expects by default. This made the separate `defaultAppender`
  concept from the original draft unnecessary.
- **Immutable `LogMessage`**: built once per `log()` call via constructor, with
  all fields populated, then shared read-only across every appender.
- **Failure isolation**: each `appender.append(...)` call in the fan-out loop is
  wrapped in `try { } catch (Exception e)` so one broken appender can't stop the
  rest from running. Catches `Exception`, not `Throwable` — JVM `Error`s
  (OutOfMemoryError, etc.) should still propagate, not get swallowed.
- **`synchronized` on each appender's `append`**: protects the shared
  console/file resource if `log()` is ever called from multiple threads.
- **Real file I/O in `FileAppender`**: uses `BufferedWriter` over
  `Files.newBufferedWriter(..., CREATE, APPEND)`, flushing after every write so
  recent lines survive a crash.
- **`LogLevel` cleanup**: `ALL_CAPS` naming (Java's enum-constant convention),
  severities spaced by 10s (10/20/30/40/50) so a new level can be inserted later
  without renumbering everything, and added `FATAL`.

## Files in this folder

| File                        | What it shows                                                              |
| ---------------------------- | --------------------------------------------------------------------------- |
| `model/LogLevel.java`        | Severity enum + `isAtLeast` threshold check                                |
| `model/LogMessage.java`      | Immutable log record — built once, fully populated, shared read-only       |
| `formatter/LogFormatter.java`| Strategy interface — "how should this look?"                              |
| `formatter/TextFormatter.java`| One implementation — plain-text line                                     |
| `appender/LogAppender.java`  | Strategy interface — "where does this go?"                                |
| `appender/ConsoleAppender.java`| Writes to stdout, `synchronized`                                        |
| `appender/FileAppender.java` | Real file I/O (`BufferedWriter`, flush per line), `synchronized`           |
| `LoggerConfig.java`          | Threshold → appenders map, with cascading lookup                          |
| `Logger.java`                | The facade — builds one `LogMessage`, fans it out with per-appender isolation |
| `LoggingFramework.java`      | Entry point / demo (`main`)                                               |

## Run it

```bash
mvn -q compile exec:java \
  -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.logger_practice.LoggingFramework
```

Expected output: `debug` is filtered out (below the `INFO` minimum); `info` and
`warn` print to the console (both cascade from the `INFO` registration); `error`
prints to the console (cascades from `INFO`) **and** appends a line to
`error.txt` in the project root (cascades from its own `ERROR` registration).
