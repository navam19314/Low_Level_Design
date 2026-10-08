# Logger (Logging Library)

> **Amazon:** ☆ reported in 2025: *"Logger with multiple formats and levels."* Your guide also mentions *Chain of Responsibility for levels, Strategy for formatters/sinks, Singleton logger, thread safety.* Also a Microsoft favourite.
>
> **The crux (what's really being tested):**
> 1. **Composition over inheritance:** format (text/JSON) and destination (console/file) vary **independently**, so 2 + 2 classes, not 2 × 2.
> 2. **Thread safety without slowing everyone down:** format outside the lock, lock only the shared write.
> 3. **Failure isolation:** a full disk must never crash the app or stop console logging.
>
> **Family:** F4 Fan-out pipeline. See [foundations](../../00_AMAZON_LLD_FOUNDATIONS.md). Sister problem: [Notification](../notification/INTERVIEW_WALKTHROUGH.md) (same fan-out + isolation idea).

---

## 1. Plain-language picture

### One log call, several outputs
```
logger.info("user 42 logged in")
   │  build ONE record: {time, INFO, "user 42 logged in", thread "http-7"}
   ├──→ Console:  level ≥ DEBUG?  yes → plain text → "2026-10-07T10:00Z [INFO] [http-7] user 42 logged in"
   ├──→ File:     level ≥ INFO?   yes → JSON       → {"timestamp":"...","level":"INFO",...}
   └──→ Alerts:   level ≥ ERROR?  no  → skipped
```
Each output ("destination") has three settings: a **minimum level**, a **format**, and **where** to write.

### Why composition beats subclasses
Formats: plain, JSON. Targets: console, file. With subclasses you'd write `PlainConsoleLogger`, `JsonConsoleLogger`, `PlainFileLogger`, `JsonFileLogger` (2 × 2 = 4), and adding a CSV format plus a remote target gives 3 × 3 = 9. With composition, a destination **has a** formatter and **has a** sink: 2 + 2 = 4 small classes, then 3 + 3 = 6.

### Two threads writing to the same file
Thread A writes `"user logged in"` while thread B writes `"payment failed"`. Without a lock, the file can contain `"user logpayment faiged in"`. So writes to one file are locked. But **formatting** a record (building the string) touches nothing shared, so it happens **outside** the lock. The lock is held only for the actual write, which keeps it short.

### A broken output must not break the app
The disk is full and the file write throws. If that exception escaped `logger.info(...)`, a logging call would crash a payment request. So each destination catches its own failure and reports it to stderr, and the console line still prints.

---

## 2. From story to design

| # | Decision | Rejected | Why |
|---|---|---|---|
| D1 | `Destination` = level threshold + `Formatter` + `Sink` (composition) | `JsonFileLogger extends Logger` subclasses | N formats × M sinks → N + M classes instead of N × M. |
| D2 | `Formatter` and `Sink` are **Strategy** interfaces | `switch(format)` | A new format or target = a new class; nothing else changes. |
| D3 | `LogRecord` built **once** per call (timestamp, level, message, thread) and shared | each destination reading the clock | Every destination shows the same moment; adding a field (requestId) is one change. |
| D4 | `LogRecord` immutable; formatters pure (no state) | mutable records | Safe to share across threads with no locking. |
| D5 | Lock **per destination**, only around `sink.write` | one global lock, or locking the format step too | Different destinations never block each other; formatting runs in parallel. |
| D6 | `catch (Exception)` inside `Destination.write` | letting it propagate; `catch (Throwable)` | Failure isolation; but never swallow JVM errors like OutOfMemoryError. |
| D7 | `LogLevel` with explicit **severity numbers** | comparing `ordinal()` | Adding TRACE later doesn't silently shift comparisons. |
| D8 | Destinations list fixed at construction (`List.copyOf`) | `addDestination()` at runtime | No structural changes means iterating needs no lock. |
| D9 | `FileSink` opens the file once and flushes each line | open/close per line; flush only on close | Fast, and recent lines survive a crash (the moment you most need logs). |

### Class shape
```
Logger                              ← what application code calls: log(level, msg), info(), error()...
  List<Destination> (immutable) · Clock

Destination                         ← one output: filter → format (outside lock) → write (inside lock)
  LogLevel minLevel · Formatter · Sink · lock

«interface» Formatter  format(record) → String      «interface» Sink  write(String)
  ├── PlainTextFormatter                               ├── ConsoleSink
  └── JsonFormatter                                    └── FileSink (open once, flush per line)

LogRecord { timestamp, level, message, threadName }  (immutable)
LogLevel { DEBUG 10, INFO 20, WARN 30, ERROR 40, FATAL 50 } + isAtLeast()
```

---

## 3. Patterns that earn their place

| Pattern | In the base? | Problem it solves | Without it |
|---|---|---|---|
| **Strategy** (`Formatter`, `Sink`) | **Yes** | formats and targets vary independently | N × M subclasses or `switch`es |
| **Composition** (`Destination`) | **Yes** | combine any format with any sink and any level | inheritance explosion |
| **Chain of Responsibility** | Optional (Q2) | route levels through ordered handlers | per-destination thresholds already do this more simply |
| **Singleton** | Discuss (Q3) | "one logger everywhere" | — |
| **Producer–consumer** | No (Q1) | async logging so the app never waits on disk | — |

**Say:** *"Each destination composes a level, a Formatter and a Sink, two Strategies that vary independently, so it's N + M classes, not N × M. Format outside the lock; lock only the write; catch per destination."*

**Tempting but wrong:** a `Logger` subclass per output; Observer (destinations aren't independent subscribers coming and going: they're fixed config); Decorator for formats (formats replace each other, they don't stack).

---

## 4. The 45-minute run

### Clarify (min 0–5)
> **You:** Levels DEBUG < INFO < WARN < ERROR < FATAL. Several destinations, each with its own minimum level?
> **Interviewer:** Yes, e.g. console shows everything, the file only INFO and up.
> **You:** Formats: plain text and JSON? Targets: console and file?
> **Interviewer:** Yes, and more may be added.
> **You:** Called from many threads at once? Lines must not interleave?
> **Interviewer:** Yes.
> **You:** If a destination fails, should the app keep running?
> **Interviewer:** Absolutely.

```
In scope:  5 levels · N destinations, each = min level + formatter + sink
           plain + JSON formats · console + file sinks · thread-safe, no interleaving · failure isolation
Out:       async logging, file rotation, remote sinks, per-class loggers, config files
```

### Timeline
| Min | Do |
|---|---|
| 5–9 | Draw one call fanning out to 3 destinations. Say the N + M argument. |
| 9–13 | Code step 1: `LogLevel` (severity), `LogRecord`. |
| 13–19 | Code step 2: `Formatter` + `PlainTextFormatter` + `JsonFormatter`; `Sink` + `ConsoleSink` (+ `FileSink`). |
| 19–27 | Code step 3: **`Destination.write`** (filter → format outside the lock → write inside, catch). |
| 27–32 | Code step 4: `Logger.log` (build the record once, loop) + convenience methods. |
| 32–36 | Dry run; explain the lock placement. |
| 36–45 | Follow-ups: async, Singleton, Chain of Responsibility. |

### The code you write, in this order

**Step 1: level + record** ([model/](model/))
```java
public enum LogLevel {
    DEBUG(10), INFO(20), WARN(30), ERROR(40), FATAL(50);

    private final int severity;
    LogLevel(int severity) { this.severity = severity; }

    public boolean isAtLeast(LogLevel minimum) { return severity >= minimum.severity; }
}

public final class LogRecord {                       // immutable: safe to share across threads
    private final Instant timestamp;
    private final LogLevel level;
    private final String message;
    private final String threadName;
    // constructor + getters
}
```

**Step 2: the two strategies** ([formatter/](formatter/), [sink/](sink/))
```java
public interface Formatter { String format(LogRecord record); }     // must be pure: no state

public class PlainTextFormatter implements Formatter {
    public String format(LogRecord r) {
        return r.getTimestamp() + " [" + r.getLevel() + "] [" + r.getThreadName() + "] " + r.getMessage();
    }
}
// JsonFormatter: {"timestamp":"...","level":"...","thread":"...","message":"..."} with quotes/newlines escaped

public interface Sink { void write(String formatted); }

public class ConsoleSink implements Sink {
    public void write(String formatted) { System.out.println(formatted); }
}

public class FileSink implements Sink, AutoCloseable {
    private final BufferedWriter writer;
    public FileSink(Path path) throws IOException {
        writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
    public void write(String formatted) {
        try {
            writer.write(formatted);
            writer.newLine();
            writer.flush();                          // visible even if the process crashes next
        } catch (IOException e) {
            throw new UncheckedIOException(e);       // Destination isolates it
        }
    }
    public void close() throws IOException { writer.close(); }
}
```

**Step 3: the destination, the crux** ([Destination.java](Destination.java))
```java
public class Destination {
    private final Formatter formatter;
    private final LogLevel minLevel;
    private final Sink sink;
    private final Object lock = new Object();        // protects this sink only

    public Destination(Formatter formatter, LogLevel minLevel, Sink sink) {
        this.formatter = formatter;
        this.minLevel = minLevel;
        this.sink = sink;
    }

    public void write(LogRecord record) {
        if (!record.getLevel().isAtLeast(minLevel)) return;        // 1. filter: cheap
        String formatted = formatter.format(record);                // 2. format OUTSIDE the lock
        synchronized (lock) {                                       // 3. lock only the shared write
            try {
                sink.write(formatted);
            } catch (Exception e) {                                 // isolate: never crash the caller
                System.err.println("logger: sink write failed: " + e.getMessage());
            }
        }
    }
}
```
**Shape to remember: filter → format (no lock) → lock → write → catch.**

**Step 4: the logger** ([Logger.java](Logger.java))
```java
public class Logger {
    private final List<Destination> destinations;
    private final Clock clock;

    public Logger(List<Destination> destinations, Clock clock) {
        this.destinations = List.copyOf(destinations);   // fixed: iterate without locking
        this.clock = clock;
    }

    public void log(LogLevel level, String message) {
        LogRecord record = new LogRecord(clock.instant(), level, message, Thread.currentThread().getName());
        for (Destination d : destinations) d.write(record);   // same record, same timestamp everywhere
    }

    public void debug(String m) { log(LogLevel.DEBUG, m); }
    public void info(String m)  { log(LogLevel.INFO, m); }
    public void warn(String m)  { log(LogLevel.WARN, m); }
    public void error(String m) { log(LogLevel.ERROR, m); }
    public void fatal(String m) { log(LogLevel.FATAL, m); }
}
// wiring:
Logger log = new Logger(List.of(
        new Destination(new PlainTextFormatter(), LogLevel.DEBUG, new ConsoleSink()),
        new Destination(new JsonFormatter(), LogLevel.INFO, new FileSink(Path.of("app.log")))), Clock.systemUTC());
```

### Dry run
```
log.debug("cache miss")   record{DEBUG}  console: DEBUG ≥ DEBUG ✓ print   file: DEBUG ≥ INFO ✗ skip
log.error("db timeout")   record{ERROR}  console ✓   file ✓ (JSON)
disk full → FileSink throws → Destination catches → stderr "sink write failed" → console still printed, caller unaffected
50 threads × 20 lines → each line is written whole (the lock covers the write), and formatting runs in parallel
```
The driver checks: 1,000 lines from 50 threads, all well-formed.

---

## 5. Follow-ups: answer out loud first, then open

<details>
<summary><b>Q1. Logging a line costs 2 ms when the disk is slow. Make it async.</b></summary>

**Producer–consumer:** the app thread only **enqueues** the record (microseconds); a background thread writes to the destinations.
```java
public class AsyncLogger implements AutoCloseable {
    private final BlockingQueue<LogRecord> queue = new ArrayBlockingQueue<>(10_000);   // bounded = backpressure
    private final List<Destination> destinations;
    private final Thread writer;
    private final AtomicLong dropped = new AtomicLong();
    private volatile boolean running = true;

    public AsyncLogger(List<Destination> destinations) {
        this.destinations = List.copyOf(destinations);
        this.writer = new Thread(this::drain, "log-writer");
        writer.setDaemon(true);
        writer.start();
    }

    public void log(LogLevel level, String message) {
        LogRecord r = new LogRecord(Instant.now(), level, message, Thread.currentThread().getName());
        if (level.isAtLeast(LogLevel.ERROR)) {
            try { queue.put(r); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }  // never drop errors
        } else if (!queue.offer(r)) {
            dropped.incrementAndGet();               // queue full: drop debug/info rather than slow the app
        }
    }

    private void drain() {
        try {
            while (running || !queue.isEmpty()) {
                LogRecord r = queue.poll(50, TimeUnit.MILLISECONDS);
                if (r != null) for (Destination d : destinations) d.write(r);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() throws InterruptedException {   // flush what's queued, then stop
        running = false;
        writer.join(5000);
    }
}
```
**Trade-offs to say out loud:** the bounded queue protects memory; when it's full you either **drop** (low levels) or **block** (errors); on a crash, queued lines can be lost, so call `close()` from a shutdown hook. Log4j2's async logger works this way.
</details>

<details>
<summary><b>Q2. "Use Chain of Responsibility for levels" (as your Amazon guide suggests).</b></summary>

Each handler deals with the levels it cares about, then passes the record on:
```java
public abstract class LevelHandler {
    private LevelHandler next;
    public LevelHandler linkWith(LevelHandler next) { this.next = next; return next; }

    public void handle(LogRecord r) {
        if (accepts(r.getLevel())) write(r);
        if (next != null) next.handle(r);
    }
    protected abstract boolean accepts(LogLevel level);
    protected abstract void write(LogRecord r);
}

public class ConsoleHandler extends LevelHandler {      // everything INFO and up
    protected boolean accepts(LogLevel l) { return l.isAtLeast(LogLevel.INFO); }
    protected void write(LogRecord r) { System.out.println(r.getMessage()); }
}
public class PagerHandler extends LevelHandler {        // only FATAL wakes someone up
    protected boolean accepts(LogLevel l) { return l == LogLevel.FATAL; }
    protected void write(LogRecord r) { /* call PagerDuty */ }
}
// console.linkWith(file).linkWith(pager);  console.handle(record);
```
**The honest trade-off:** our design already gets the same routing with a **threshold per destination** and a simple loop, which is less code and easier to configure. Chain of Responsibility pays off when handlers need **ordering** or can **stop** the chain (e.g. a dedup handler that swallows repeats). Say this; it shows you choose patterns by need.
</details>

<details>
<summary><b>Q3. "Should the logger be a Singleton?"</b></summary>

Libraries usually expose a static access point (`LoggerFactory.getLogger(MyClass.class)`), and that's fine for **convenience**. If you implement one, use the holder idiom (thread-safe, lazy):
```java
public final class LogManager {
    private LogManager() {}
    private static class Holder { static final Logger INSTANCE = buildFromConfig(); }
    public static Logger get() { return Holder.INSTANCE; }
}
```
But say the trade-off: a hard Singleton makes tests share state and hides the dependency. Inside your own classes, prefer **injecting** a `Logger` (or a logger interface) so tests can pass a fake that records lines.
</details>

<details>
<summary><b>Q4. The log file grows forever. Add rotation.</b></summary>

`RollingFileSink implements Sink`: before each write, check the size (or the date). Past the limit: close, rename `app.log` → `app.log.1` (shifting older ones, keeping K), and open a new `app.log`. It runs inside the destination's lock, so no line is written mid-rotation. A new class; nothing else changes.
</details>

<details>
<summary><b>Q5. Ship logs to a central service (ELK, CloudWatch).</b></summary>

`RemoteSink implements Sink` that **batches** lines (every 1 s or 500 lines) and sends them over HTTP, with a retry + back-off and a local buffer if the service is down. Combine it with the async logger so network slowness never touches app threads. Same interface, so nothing else changes.
</details>

<details>
<summary><b>Q6. Add a request id to every line of a request.</b></summary>

Add `requestId` (or a `Map<String, String> context`) to `LogRecord`: one field, because the record is the single place call data lives. Fill it from a `ThreadLocal` set at the start of each request (that's what SLF4J's MDC is). Formatters print it. No sink changes.
</details>

<details>
<summary><b>Q7. Change levels at runtime without restarting (turn on DEBUG to investigate).</b></summary>

Make `minLevel` a `volatile` field with a setter on `Destination` (one writer, many readers). Expose it on an admin endpoint. Reads in `write()` see the new level immediately; no lock needed for a single reference.
</details>

<details>
<summary><b>Q8. How do you test it?</b></summary>

- **Filtering:** a recording sink; log at each level; check what each destination received.
- **Formatting:** a fixed `Clock` → exact expected strings (including JSON escaping of quotes and newlines).
- **Isolation:** a sink that throws → other destinations still receive the line; no exception reaches the caller.
- **Concurrency:** 50 threads × 20 lines → 1,000 lines, each well-formed (no interleaving). All are in the driver.
</details>

---

## 6. Traps
1. Subclass per format × target.
2. One global lock for every destination, or formatting inside the lock.
3. Exceptions from a sink escaping `logger.info()`.
4. `catch (Throwable)`.
5. Each destination reading the clock itself (lines from one call show different times).
6. Mutable records or stateful formatters shared across threads.
7. Opening and closing the file on every line; flushing only on close.

## 7. Recall check
1. Why composition? Do the N + M vs N × M maths for 3 formats and 4 sinks.
2. What happens inside `Destination.write`, in order, and where exactly is the lock?
3. Why build the `LogRecord` once in `Logger.log`?
4. Async logging: what's queued, who writes, what happens when the queue is full?
5. Chain of Responsibility vs per-destination thresholds: when is each better?
6. Singleton: how would you write it, and why might you not?

**Rebuild in 10 minutes:** `LogLevel` (severity) · `LogRecord` · `Formatter` + plain · `Sink` + console · `Destination.write` (filter → format → lock → write → catch) · `Logger.log`.

---

**Files:** `Logger` · `Destination` · `formatter/` (`Formatter`, `PlainTextFormatter`, `JsonFormatter`) · `sink/` (`Sink`, `ConsoleSink`, `FileSink`) · `model/` (`LogLevel`, `LogRecord`) · `LoggerDriver` (filtering across destinations, failure isolation, 50-thread no-interleave check)
```bash
mvn -q compile exec:java -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.pipelines.logger.LoggerDriver
```
