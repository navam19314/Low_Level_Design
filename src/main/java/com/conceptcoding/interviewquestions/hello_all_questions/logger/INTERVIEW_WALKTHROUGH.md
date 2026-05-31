# Logging Service — 45-min LLD Interview Walkthrough

**Target role:** SDE‑2 (Amazon, Adobe, Microsoft, Atlassian, etc.)
**Source method:** Hello Interview *Delivery Framework* applied to the *Logging Service* problem breakdown.

> Logging Service is the **canonical "composition over inheritance" LLD problem**. The headline isn't class design or concurrency in isolation — it's the **two-dimensional variation** (any format with any sink) that forces you to *compose* rather than subclass. Get that right, plus the per-resource lock placement, and you're senior. The base design genuinely uses two Strategy interfaces (Formatter, Sink) — this is the only one of our 7 problems where pre-baked Strategy passes the one-sentence test in TWO places.

---

## Time budget (45 min)

| Step | Activity                                                                       | Budget   | Cumulative |
| ---- | ------------------------------------------------------------------------------ | -------- | ---------- |
| 1    | Requirements                                                                   | ~5 min   | 5          |
| 2    | Entities & Relationships                                                       | ~5 min   | 10         |
| 3    | Class Design (the inheritance-vs-composition fork)                             | ~12 min  | 22         |
| 4    | Implementation (`log` + `Destination.write` + format/sink classes + dry-run)   | ~14 min  | 36         |
| 5    | Extensibility (async writes, hierarchical loggers)                             | ~8 min   | 44         |
| —    | Wrap & questions                                                               | ~1 min   | 45         |

Step 3 gets an extra minute because the inheritance-vs-composition discussion is THE central senior-signal moment. Don't shortchange it.

Watch the clock at minute **5** (Step 1 done), minute **22** (start coding), minute **36** (extensibility).

---

## Mental models — internalize these BEFORE you walk in

### M1. The dispatch flow (single log call → fan out)

```
   logger.info("user signed in")
                |
                v
   +-------------------------------------------+
   | 1. now()                                  |  <-- one timestamp for all destinations
   | 2. Thread.currentThread().name            |
   | 3. record = LogRecord(ts, INFO, msg, tn)  |
   +-------------------------------------------+
                |
                v
       for destination in destinations:        <-- list is IMMUTABLE post-ctor
                |                                   so no lock around iteration
                v
   +-------------------------------------------+
   | Destination.write(record):                |
   |   if level < minLevel  -> drop            |
   |   formatted = formatter.format(record)    |  <-- OUTSIDE the lock (pure fn)
   |   synchronized(this.lock):                |
   |       try { sink.write(formatted); }      |
   |       catch { stderr diagnostic; }        |  <-- failure isolation
   +-------------------------------------------+
                                                each destination is independent
```

### M2. The 2D variation that DEMANDS composition (not inheritance)

```
   The requirement: format and sink-type vary INDEPENDENTLY.

   With inheritance — N×M classes:                With composition — N+M classes:
   ----------------------------------             -------------------------------
                                                
   abstract Destination                           interface Formatter             (N=2)
       PlainConsoleDestination                        PlainTextFormatter
       JsonConsoleDestination                         JsonFormatter
       PlainFileDestination                       
       JsonFileDestination                        interface Sink                  (M=2)
       PlainRemoteDestination       [v2]              ConsoleSink
       JsonRemoteDestination        [v2]              FileSink
       CsvConsoleDestination        [v3]              RemoteSink                  [v2]
       CsvFileDestination           [v3]              CsvFormatter                [v3]
       CsvRemoteDestination         [v3]
                                                  Destination(formatter, sink)    (1 concrete class)
   3 formats × 3 sinks = 9 classes                3 + 3 = 6 classes
   Each new axis multiplies                       Each new axis adds linearly
   Combinations are CONSTRUCTOR ARGS, not class declarations.
```

**Senior soundbite (memorize):** *"When a requirement gives you two dimensions that vary independently — any format with any sink — that's almost always the signal for composition over inheritance. Inheritance gives you N×M subclasses; composition gives you N+M and a single concrete class that takes both as constructor args."*

### M3. Lock placement — per-destination, around the sink only

```
   Option A — global lock on Logger.log         |   Option B — per-destination, around sink
   --------------------------------------       |   ---------------------------------------
   synchronized(this) {                         |   for d in destinations:
     for d in destinations: d.write(record)     |     d.write(record)                     <-- no global lock
   }                                            |                                          
                                                |   Destination.write(record):
   PROBLEM:                                     |       if filtered out return
   - one slow file write blocks ALL             |       formatted = format(record)        <-- pure, no lock
     destinations, including console            |       synchronized(this.lock):
   - format() (pure!) also runs serialized      |           sink.write(formatted)
   - blocks UNRELATED loggers' threads          |
                                                |   WIN:
   When the shared state lives PER-DESTINATION  |   - lock is next to the resource it protects
   (the file handle, the stdout buffer), the    |   - format() runs in parallel for all destinations
   lock belongs per-destination — not on the    |   - one slow destination doesn't block the others
   orchestrator above it.                       |   - record is immutable; safe to share across destinations
```

> **The interview rule:** *"The lock lives with the shared resource it's protecting. Logger doesn't own the file handle; the Destination's Sink does. So the lock belongs on Destination."* Same per-resource principle as Showtime's seat-reservation lock in Movie Ticket Booking, Compartment's status in Amazon Locker, and per-folder in File System.

---

## STEP 1 — Requirements (~5 min)

### What to say out loud (opener)
> "'Logger' can mean wildly different things — in-process library vs. distributed log aggregator. Let me clarify scope before designing."

### Probe the 4 themes

| Theme               | Question to ask                                                                                              |
| ------------------- | ------------------------------------------------------------------------------------------------------------ |
| Primary capabilities| "In-process library (Log4j-style) or network aggregator? Five levels DEBUG/INFO/WARN/ERROR/FATAL?"           |
| Variation           | "Multiple destinations from one logger? Per-destination threshold + format? Format and destination type vary independently?" *(THIS is the load-bearing question — it dictates composition.)* |
| Concurrency         | "Multi-threaded callers — what's the bar? Per-record atomicity vs strict global order?"                      |
| Scope boundaries    | "Static config at startup? Async writes, hot reload, log rotation, remote sinks — all out-of-scope but pluggable later?" |

### What to write on the board

```
Functional Requirements
1. Five severity levels: DEBUG < INFO < WARN < ERROR < FATAL.
2. Each record carries: timestamp, level, message, emitting thread name.
3. Logger writes each record to one or more destinations (set at startup).
4. Per-destination: own min-level threshold AND own format.
   FORMAT and DESTINATION TYPE vary INDEPENDENTLY.    <-- THE key requirement
5. Concurrent calls are safe; bytes for one record never interleave with another's on the same destination.

Out of Scope (mentioned by name to signal awareness)
- Hot-reload of config at runtime
- Async / buffered writes
- Remote / network destinations (v1, but the design must accommodate)
- Log rotation
- Hierarchical / named loggers (com.app.service inheriting from com.app)
```

### Close the step
> "Does this match what you had in mind? Requirement 4 (format and sink type vary independently) is the load-bearing one — it dictates composition over inheritance, which I'll explain in Step 3."

---

## STEP 2 — Entities & Relationships (~5 min)

### What to say out loud
> "Four entities: **Logger** (orchestrator — the only class the app touches), **LogRecord** (immutable value object — what every log call produces), **Destination** (one configured output target — owns threshold, formatter, sink, lock), **Formatter** (Strategy interface for serialization). Plus one enum **LogLevel** with 5 ordered values. And — because format and sink type vary independently — a second Strategy interface **Sink** for the byte-write itself. The 2D variation is what justifies pre-baking *two* Strategy interfaces in the base."

### Why no `Level` class hierarchy
> "Five fixed values with an ordering and no per-level behavior — textbook enum. A `DebugLevel`/`InfoLevel`/etc. class hierarchy is the classic over-modeling trap; nothing varies per level."

### Why LogRecord IS a class (not 4 parameters)
> "Today the record has 4 fields. Tomorrow there's a logger name, a request id, an MDC context map. With a class, that's a one-line change in `LogRecord`. With raw parameters, every `Destination.write`, every `Sink.write`, every `Formatter.format` signature changes — and so does every implementation. Group the per-call data into one immutable value object."

### What to write on the board

```
Entities
- Logger        (orchestrator + facade: log / debug / info / warn / error / fatal)
- LogRecord     (immutable value object: timestamp + level + message + threadName)
- Destination   (one configured target: minLevel + formatter + sink + per-destination lock)
- Formatter     (interface — Strategy; implementations: PlainText, Json)         <-- 1st axis
- Sink          (interface — Strategy; implementations: Console, File, [Remote])  <-- 2nd axis

Enums
- LogLevel      { DEBUG < INFO < WARN < ERROR < FATAL }  with isAtLeast(threshold)

NOT entities
- Thread / Application      (external — we read currentThread().name at the call site)
- "Level" class             (5 fixed values + ordering + no behavior → enum)
- A unified "Destination class per (format, target)"  (would be N×M; we use composition)

Relationships
- Logger        owns  List<Destination>        (immutable post-ctor)
- Destination   owns  Formatter + Sink + lock + minLevel
- Logger        creates LogRecord per log() call; hands SAME record to each destination
```

### Diagram — boxes and arrows

```
   (application code)
        |
        | logger.info("...")
        v
   +----------------------------+
   |          Logger            |   <- orchestrator + facade
   |  log / debug / info / ...  |
   +----------------------------+
        |
        | iterates List<Destination>   (immutable)
        v
   +-----------------------------------------+
   |             Destination                 |   <-- the per-target object
   |  minLevel + formatter + sink + lock     |
   |  filter -> format (no lock) -> sink (locked)
   +-----------------------------------------+
            |                       |
       Formatter (Strategy)    Sink (Strategy)
            |                       |
   +---+----+----+----+    +---+----+----+----+
   |              |        |              |
   v              v        v              v
PlainText      Json     Console        File   [Remote in v2]
Formatter   Formatter   Sink           Sink
```

---

## STEP 3 — Class Design (~12 min)

### Work top-down: Logger → LogRecord → LogLevel → Formatter → Sink → Destination.

### Logger — state ↔ requirement table

| Requirement                              | State Logger must own                              |
| ---------------------------------------- | -------------------------------------------------- |
| Write to one or more destinations         | `List<Destination> destinations` (immutable post-ctor) |
| Capture wall-clock time                  | `Clock clock` (injected — makes time testable)    |

> **Why immutable destinations list?** Config is set ONCE at startup. The list never changes. Iteration is safe under concurrent calls with no locking. A mutable list with `addDestination()` would force locking around every iteration and buy nothing the requirements asked for.

### Logger — behavior table

| Need from requirements              | Method                                                |
| ----------------------------------- | ----------------------------------------------------- |
| Emit a record                       | `void log(LogLevel level, String message)`            |
| Convenience helpers (ergonomics)    | `debug` / `info` / `warn` / `error` / `fatal`         |

### Logger — class outline (write this on the board)

```java
public class Logger {
    private final List<Destination> destinations;
    private final Clock clock;                          // injected — testability

    public Logger(List<Destination> destinations) { this(destinations, Clock.systemUTC()); }
    public Logger(List<Destination> destinations, Clock clock) {
        this.destinations = List.copyOf(destinations); // immutable defensive copy
        this.clock = clock;
    }

    public void log(LogLevel level, String message)   { /* Step 4 */ }

    public void debug(String m) { log(LogLevel.DEBUG, m); }
    public void info (String m) { log(LogLevel.INFO,  m); }
    public void warn (String m) { log(LogLevel.WARN,  m); }
    public void error(String m) { log(LogLevel.ERROR, m); }
    public void fatal(String m) { log(LogLevel.FATAL, m); }
}
```

### The fork — Destination as inheritance vs composition

This is the **moment of truth** in the interview. Three candidate designs:

| Option | Shape | Verdict |
| ------ | ----- | ------- |
| **A. Single class, switch inside `write`** | One `Destination` class with a `type` field; switch on `type` to dispatch to file/console/etc. | Works at v1 but every new sink is a new switch case AND every new format multiplies the cases. Open/Closed violation. |
| **B. Inheritance hierarchy** | `abstract Destination` + `ConsoleDestination`, `FileDestination`, `JsonConsoleDestination`, ... | Solves the dispatch problem but creates the **N×M class explosion** when format and sink-type are independent. Adding `RemoteSink` × 3 formats = 3 new classes. |
| **C. Composition with two Strategy interfaces** ⭐ | One concrete `Destination` composes a `Formatter` + a `Sink` + a `minLevel`. New format = new Formatter; new sink = new Sink. | **N+M classes, not N×M.** Combinations are constructor args, not class declarations. Open/Closed: extending neither requires touching existing code. |

> **Say this out loud:** *"With N formats × M sinks, inheritance gives N×M classes. The requirement explicitly says format and sink type vary independently — that's the textbook composition signal. I'll have one concrete `Destination` class that takes a `Formatter` and a `Sink` as constructor args. Adding CSV is a new `Formatter`; adding a remote endpoint is a new `Sink`. No combinatorial growth."*

### Strategy interfaces — Formatter + Sink

```java
public interface Formatter {
    String format(LogRecord record);                 // pure function — safe to share
}
public class PlainTextFormatter implements Formatter { ... }
public class JsonFormatter      implements Formatter { ... }

public interface Sink {
    void write(String formatted);                    // turn a string into bytes
}
public class ConsoleSink implements Sink { ... }
public class FileSink    implements Sink, AutoCloseable { ... }
// Future: RemoteSink — new class, zero changes elsewhere.
```

### Destination — the composed class

```java
public class Destination {
    private final Formatter formatter;
    private final LogLevel  minLevel;
    private final Sink      sink;
    private final Object    lock = new Object();   // protects the sink resource

    public Destination(Formatter formatter, LogLevel minLevel, Sink sink) { ... }

    public void write(LogRecord record) {           // filter → format → lock-and-write
        if (!record.getLevel().isAtLeast(minLevel)) return;
        String formatted = formatter.format(record);          // OUTSIDE the lock
        synchronized (lock) {
            try { sink.write(formatted); }
            catch (Throwable t) {                              // failure isolation
                System.err.println("logger: sink write failed: " + t.getMessage());
            }
        }
    }
}
```

### LogRecord — the immutable value object

```java
public final class LogRecord {
    private final Instant timestamp;
    private final LogLevel level;
    private final String message;
    private final String threadName;
    // ctor + getters only — all fields final
}
```

### LogLevel — enum with explicit severity

```java
public enum LogLevel {
    DEBUG(10), INFO(20), WARN(30), ERROR(40), FATAL(50);
    private final int severity;
    LogLevel(int severity) { this.severity = severity; }
    public boolean isAtLeast(LogLevel min) { return severity >= min.severity; }
}
```

> **Why `severity` as an explicit int and not `ordinal()`?** Severity-as-int survives reordering of declarations. If someone later adds `TRACE(5)` below DEBUG, comparisons stay correct. With `ordinal()`, all your comparison logic silently shifts.

### Diagram — class cards

```
+------------------------------+   +-------------------------+   +--------------------------+
|           Logger             |   |       Destination       |   |         LogRecord         |
+------------------------------+   +-------------------------+   +--------------------------+
| - destinations:               |   | - formatter: Formatter  |   | - timestamp: Instant     |
|   List<Destination>           |   | - minLevel: LogLevel    |   | - level: LogLevel        |
| - clock: Clock                |   | - sink: Sink            |   | - message: String        |
+------------------------------+   | - lock: Object          |   | - threadName: String     |
| + log(level, msg)             |   +-------------------------+   +--------------------------+
| + debug/info/warn/error/fatal |   | + write(record):        |   | + getters (immutable)    |
+------------------------------+   |    filter -> format     |   +--------------------------+
                                   |    -> lock+sink         |
                                   +-------------------------+

+--------------------------+      +--------------------------+
| <<interface>>            |      | <<interface>>            |
| Formatter                |      | Sink                     |
+--------------------------+      +--------------------------+
| + format(record): String |      | + write(formatted)       |
+--------------------------+      +--------------------------+
      ^         ^                       ^         ^
      |         |                       |         |
PlainText    Json                  Console     File   [Remote in v2]
Formatter    Formatter             Sink        Sink

Logger --owns--> List<Destination>
Destination --composes--> Formatter + Sink   (Strategy × 2 — the WHOLE POINT)
```

### The principle to verbalize — Dependency Inversion + SRP
> "Destination depends on the `Formatter` and `Sink` interfaces, not on `JsonFormatter` or `FileSink` concretes. That's Dependency Inversion — the high-level workflow (filter, format, lock, write) doesn't know which concrete formatter or sink it's holding. And each class owns exactly one reason to change: orchestration in Logger, data in LogRecord, classification in LogLevel, serialization in Formatter, byte-writing in Sink, per-destination invariants in Destination."

---

## STEP 4 — Implementation (~14 min)

### Open by asking
> "Real Java or pseudo-code? I'll do `Logger.log` first — short, but the ordering of the 4 lines matters — then `Destination.write` which has the lock, then concrete Formatter and Sink, then dry-run the 3 scenarios."

### 4.1 `Logger.log` — capture once, fan out

```java
public void log(LogLevel level, String message) {
    // Capture per-call data ONCE — every destination sees the same record.
    LogRecord record = new LogRecord(
            clock.instant(),                          // ts
            level,
            message,
            Thread.currentThread().getName());        // thread

    // Sequential dispatch. The list is immutable, so no lock here.
    for (Destination destination : destinations) {
        destination.write(record);
    }
}
```

**Three callouts to deliver while writing this:**

1. *"Timestamp and thread name are captured at the TOP of `log()`, not inside each destination. If I captured them inside `Destination.write`, every destination would record slightly different timestamps for the same log call — microseconds apart, but still wrong."*

2. *"No level filter on the Logger. The threshold is per-destination, so the filter has to live where the threshold lives — inside `Destination.write`. Filtering here would override per-destination thresholds."*

3. *"No lock around the for-loop. The destinations list is immutable after construction; concurrent threads walk it safely. The locking lives one layer down, next to the shared resource (the sink)."*

### 4.2 `Destination.write` — filter, format outside lock, write under lock

```java
public void write(LogRecord record) {
    // 1. Filter — cheap, no allocation.
    if (!record.getLevel().isAtLeast(minLevel)) return;

    // 2. Format OUTSIDE the lock. Records are immutable, formatters are pure;
    //    two threads can format the SAME record concurrently with no contention.
    String formatted = formatter.format(record);

    // 3. Lock only around the sink write (the shared resource).
    synchronized (lock) {
        try {
            sink.write(formatted);
        } catch (Throwable t) {
            // Failure isolation — caller never sees an exception from logging.
            System.err.println("logger: sink write failed: " + t.getMessage());
        }
    }
}
```

> **Senior callout — format outside the lock:** *"Records are immutable; Formatter implementations are pure functions. There's zero shared state between two threads formatting the same record, so formatting outside the critical section gives me real concurrency — only the actual sink write serializes. If I'd put `format()` inside the synchronized block, slow JSON serialization would block every other thread on the same destination for no correctness benefit."*

> **Senior callout — failure isolation:** *"A failing sink (disk full, broken pipe) MUST NOT propagate to `Logger.log` or to other destinations. Otherwise one flaky file destination could kill console output and crash the application's request thread. The stderr diagnostic lets you see the failure during debugging without breaking the caller."*

### 4.3 `PlainTextFormatter` + `JsonFormatter`

```java
public class PlainTextFormatter implements Formatter {
    @Override public String format(LogRecord r) {
        return r.getTimestamp() + " [" + r.getLevel() + "] [" + r.getThreadName() + "] " + r.getMessage();
    }
}

public class JsonFormatter implements Formatter {
    @Override public String format(LogRecord r) {
        return "{\"timestamp\":\"" + r.getTimestamp()
             + "\",\"level\":\""    + r.getLevel()
             + "\",\"thread\":\""   + escape(r.getThreadName())
             + "\",\"message\":\""  + escape(r.getMessage()) + "\"}";
    }
    // escape() handles \" \\ \n \r \t — basic JSON safety
}
```

> *"Both formatters are stateless and side-effect-free — that's what makes them safe to share across destinations and threads without synchronization."*

### 4.4 `ConsoleSink` + `FileSink`

```java
public class ConsoleSink implements Sink {
    @Override public void write(String formatted) { System.out.println(formatted); }
}

public class FileSink implements Sink, AutoCloseable {
    private final BufferedWriter writer;
    public FileSink(Path filePath) throws IOException {
        // Open ONCE in ctor — open syscalls are expensive; don't reopen per write.
        this.writer = Files.newBufferedWriter(filePath, UTF_8, CREATE, APPEND);
    }
    @Override public void write(String formatted) {
        try {
            writer.write(formatted);
            writer.newLine();
            writer.flush();       // visible before crash — default "flush on close" is the WRONG moment
        } catch (IOException e) { throw new UncheckedIOException("FileSink write failed", e); }
    }
    @Override public void close() throws IOException { writer.close(); }
}
```

> **Senior callout on flush:** *"Default behavior in most languages is to flush on close. That's exactly the wrong moment for a logger — you want recent logs visible BEFORE the crash, not after a clean shutdown. Flush per write trades some throughput for crash-proof visibility; for an interview that's the right default."*

### 4.5 Verification — dry-run three scenarios

#### Scenario A — different thresholds, different formats, single thread

```
Setup:
  console : PlainTextFormatter, minLevel=DEBUG
  json    : JsonFormatter,      minLevel=WARN

logger.debug("x")
  -> console: DEBUG >= DEBUG ✓ → format → write → "[DEBUG] x"
  -> json:    DEBUG >= WARN  ✗ → silent drop

logger.info("y")
  -> console: INFO >= DEBUG  ✓ → format → write → "[INFO] y"
  -> json:    INFO >= WARN   ✗ → silent drop

logger.warn("z")
  -> console: WARN >= DEBUG  ✓ → format → write → "[WARN] z"      (plain text)
  -> json:    WARN >= WARN   ✓ → format → write → {"level":"WARN",...}  (JSON)
                                                                   ^^^ same record,
                                                                       different format ✓
```

#### Scenario B — failing sink, others unaffected

```
Setup:
  console : working
  flaky   : sink throws RuntimeException("disk full") on every write

logger.error("disk space low")
  -> console.write(record):
       filter ✓, format ✓, lock, sink.write(...)  → "disk space low" appears
       unlock, exit normally                                                ✓
  -> flaky.write(record):
       filter ✓, format ✓, lock, sink.write(...)  → THROWS
       catch (Throwable) → stderr "logger: sink write failed: disk full"
       finally release lock, exit normally                                    ✓

Result: console line written; flaky failed silently with stderr diagnostic;
        Logger.log returned normally; caller saw nothing.                    ✓
```

#### Scenario C — 50 threads × 20 records = 1000 records, atomicity check

The included driver runs this for real. With `synchronized(lock)` around `sink.write`:

```
total records written:  1000   (expect 1000)
well-formed JSON lines: 1000   (expect 1000)
any malformed?          no  ✓
```

Without the lock, you'd see well-formed counts < 1000 — interleaved bytes from two threads concatenated into one entry, breaking the JSON shape. **This is the test that proves R5 empirically, not just on paper.**

---

## STEP 5 — Extensibility (~8 min)

Two follow-ups are practically guaranteed: **async writes** (we explicitly scoped them out — the interviewer will ask) and **hierarchical/named loggers** (everyone who's used Log4j/SLF4J/Python's logging will ask).

### 5.1 "How would you make `log()` non-blocking?"

> **Problem in current design:** *"The per-destination lock makes the calling thread wait for `sink.write` to finish — fine for stdout, ugly for a slow disk or a future remote endpoint. Tens of thousands of call sites × even 1ms of I/O = real application latency."*
>
> **Pattern as the fix:** *"Put a bounded blocking queue in front of each destination. `Destination.write` enqueues the record and returns immediately. A dedicated worker thread per destination drains the queue and does the actual sink write — single-consumer per sink means the lock isn't even needed anymore."*
>
> **Tradeoffs (the three things a senior interviewer pushes on):**
> 1. **Worker lifecycle.** Each destination owns a thread for the life of the app. Shutdown must signal the worker to stop, drain the queue, then exit — otherwise you lose every buffered record on JVM exit.
> 2. **Overflow policy.** Bounded queue + producer faster than consumer → what do you do? Block the producer (defeats the point), drop newest (silent loss right when you need logs), drop oldest, throw. Most production loggers default to drop-newest + stderr diagnostic.
> 3. **Debuggability.** The actual write happens on a different thread; stack traces no longer point to the call site. Mitigate by capturing call-site info into the record itself.

```java
class AsyncDestination {
    private final BlockingQueue<LogRecord> queue;   // bounded
    private final Thread worker;
    private final Formatter formatter;
    private final Sink sink;

    public AsyncDestination(Formatter f, LogLevel min, Sink s, int capacity) {
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.worker = new Thread(this::drain, "logger-" + System.identityHashCode(this));
        this.worker.setDaemon(true);
        this.worker.start();
    }
    public void write(LogRecord r) {
        if (!r.getLevel().isAtLeast(minLevel)) return;
        if (!queue.offer(r)) { System.err.println("logger: queue full, dropping record"); }
    }
    private void drain() {
        while (!Thread.currentThread().isInterrupted()) {
            try { sink.write(formatter.format(queue.take())); }     // single-consumer; no lock needed
            catch (Throwable t) { System.err.println("..." ); }
        }
    }
}
```

> **Key observation:** *"async writes and thread-safe writes solve different problems. The lock is correctness (no torn bytes). The queue is coordination (don't block the caller). With single-consumer-per-destination, the queue lets you drop the lock entirely — until two destinations share an underlying stream."*

### 5.2 "How would you support hierarchical named loggers?"

> **Problem in current design:** *"Production loggers expose `LoggerFactory.getLogger('com.app.service.payments')`, and the returned logger inherits config from its parent in the dotted-name tree. Our design has a single global Logger."*
>
> **Pattern as the fix:** *"Introduce a `LoggerFactory` (a deliberate Singleton — this is the rare valid case) that maintains a registry keyed by name. `Logger` gains a `name` field and a `parent` pointer. Effective level + effective destinations walk the parent chain when not set locally."*
>
> **Tradeoffs:** *"Walking the parent chain on every `log()` call would be hot-path expensive. Real frameworks cache the effective level on each logger and invalidate on config change. The LoggerFactory IS a global — that's the requirement, not an accident; two callers asking for `getLogger('X')` must get the same instance."*

### 5.3 Other "what-if" answers

| Follow-up                                  | Answer                                                                                              |
| ------------------------------------------ | --------------------------------------------------------------------------------------------------- |
| "Add a remote / network sink"              | New class `RemoteSink implements Sink`. Zero changes to anything else. (This was the WHOLE point of the Sink interface.) |
| "Add CSV format"                           | New class `CsvFormatter implements Formatter`. Zero changes to anything else.                       |
| "Add structured fields / MDC"              | Extend `LogRecord` with `Map<String, String> context`. Existing formatters keep working; new formatters opt-in to context. |
| "Log rotation"                             | Wrap `FileSink` in a `RotatingFileSink` — Decorator pattern. Rotates on size/time threshold.        |
| "Hot-reload config at runtime"             | Replace `final List<Destination>` with `volatile List<Destination>` (or `AtomicReference`). Config updates atomically swap the list. |
| "Per-message rate limiting / dedup window" | Wrap a Sink in a `RateLimitedSink` — Decorator. Drops or batches records over the limit.            |
| "Observability for the logger itself"      | Observer — Destination publishes `SinkWriteFailed` events; alerting subscribes. Don't log-from-logger. |

---

## Design Patterns — Hello Interview's canonical 8

> **HI's stance:** *"Patterns arise from good design decisions, not the other way around. Most interview designs use zero to two patterns maximum."*
>
> **Logging Service is the rare problem where the base design uses TWO Strategy interfaces** — both pass the one-sentence test (R4: "format and sink type vary independently"). This is not pattern-stuffing; this is the requirements explicitly demanding it.

### The 5-step timing rule

| Step                       | Use a pattern here?                                                                 |
| -------------------------- | ----------------------------------------------------------------------------------- |
| **1. Requirements**        | **Never.**                                                                          |
| **2. Entities**            | **Sometimes** — when a clear seam exists. *Both `Formatter` and `Sink` belong here.* |
| **3. Class Design**        | **YES, when you can state the design pressure in one sentence.** *Logging passes for two patterns.* |
| **4. Implementation**      | **No new patterns.**                                                                |
| **5. Extensibility**       | **YES — for additional patterns triggered by follow-ups (Decorator for rotation, Observer for alerting, etc.).** |

### Hello Interview's canonical 8 × interviewer trigger

| # | Pattern              | Category   | Trigger phrase                                                                | One-line response                                                                                       |
| - | -------------------- | ---------- | ------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------- |
| 1 | **Strategy** ⭐       | Behavioral | "different rules" · "variants" · "swap at runtime" · "vary independently"     | *"Promote X to an interface; inject the concrete implementation."*                                       |
| 2 | **Observer**         | Behavioral | "notify multiple" · "broadcast"                                                | *"X publishes events; subscribers register independently."*                                              |
| 3 | **State Machine**    | Behavioral | "behavior depends on state"                                                     | *"Each state owns its transitions."*                                                                    |
| 4 | **Factory** (Method) | Creational | "support different types"                                                       | *"Centralize creation behind a method."*                                                                  |
| 5 | **Builder**          | Creational | "many optional fields"                                                          | *"Builder collects fields incrementally."*                                                              |
| 6 | **Singleton**        | Creational | "exactly one" · "global registry"                                              | *"Resist textbook Singleton — but a LoggerFactory registry IS a defensible case."*                       |
| 7 | **Decorator**        | Structural | "wrap with additional behavior" · "stack capabilities"                          | *"Wrap X in decorators, each adding one concern."*                                                       |
| 8 | **Facade**           | Structural | "single entry point"                                                            | *"Orchestrators usually ARE facades."*                                                                    |

### Three rules to sound natural

1. **Cap at 2 patterns in the base.** Logging earns exactly 2 (Formatter + Sink), so that's the cap.
2. **Always name the concrete win in the same breath.**
3. **Never volunteer a pattern without a trigger.**

### How this maps to Logging Service specifically

**Already in the BASE design — call out by name:**

- **Strategy (#1)** ⭐ — `Formatter` interface + `PlainTextFormatter`, `JsonFormatter` implementations. **Justified by R4:** "format and destination type vary independently". Name it in Step 2.
- **Strategy (#1)** ⭐ — `Sink` interface + `ConsoleSink`, `FileSink` implementations. **Justified by R4** AND by the explicit "design should accommodate remote destinations later".
- **Facade (#8)** — `Logger` is the only class application code touches; it hides Destination, Formatter, Sink, locking, and dispatch.
- **Composition over inheritance** (principle, not GoF) — Destination COMPOSES Formatter + Sink. The N+M result vs the N×M inheritance result is the headline senior signal.
- **Information Expert** (GRASP) — lock lives on Destination because the resource it protects (the sink) lives there.
- **Dependency Inversion** (principle) — Destination depends on the Formatter and Sink interfaces, not on `JsonFormatter` or `FileSink` concretes.
- **Immutability** (principle) — `LogRecord` and the destinations list. No shared mutable state in the hot path.

**Reach for these on the matching Step-5 follow-up — 3-beat phrasing (problem → pattern → tradeoff):**

| Follow-up                                  | Pattern (HI's 8)             | Your line                                                                                            |
| ------------------------------------------ | ---------------------------- | ---------------------------------------------------------------------------------------------------- |
| "Log rotation by size/time"                | **Decorator (#7)**           | *"Currently FileSink writes forever. Wrap in `RotatingFileSink(FileSink)` that swaps the underlying file on threshold. Decorator over Sink, so any sink can be rotated."* |
| "Async / non-blocking log()"               | (Bounded queue + worker)     | *"Queue-per-destination + single-consumer worker. Lock vanishes because the consumer is single-threaded."* |
| "Hierarchical named loggers"               | **Singleton (#6)** (registry) | *"`LoggerFactory` is the rare defensible Singleton — two callers asking for `getLogger('X')` must get the same instance. Plus a `name` field and `parent` pointer on Logger."* |
| "Alerting on logger-internal failures"     | **Observer (#2)**            | *"Destination publishes `SinkWriteFailed` events; alerting subscribes. Don't log-from-logger — recursive logging is a footgun."* |
| "Per-application configuration"            | **Builder (#5)**             | *"`LoggerBuilder` collects destinations + clock + name registry, then `.build()`. Worth it once Logger gains optional config knobs."* |
| "Different log shipping protocols (HTTP/gRPC/Kafka)" | **Strategy (#1)** ⭐ | *"All are new `Sink` implementations. The Sink interface is already the seam — that's why it's in the base."* |

**Patterns to actively refuse if interviewer baits you:**

- **Singleton on Logger** (the top-level class) — kills tests; just DI a single instance. The Singleton case is the *factory/registry*, not the Logger itself.
- **`LogLevel` as a class hierarchy** with subclasses per level — five fixed values + ordering + no per-level behavior = enum. The hierarchy buys nothing.
- **Composite over Destinations** — they're a flat collection, not a tree. (Logger isn't a Destination.)
- **State pattern on Logger** — there are no states.
- **Visitor over LogRecord** — no heterogeneous traversal need.

### One sentence to say at the end of Step 3

> *"The base design names three patterns out loud: Strategy on Formatter, Strategy on Sink, and Facade on Logger — all justified by R4's 'format and destination type vary independently'. Plus composition-over-inheritance as the principle that prevents the N×M class explosion. No Observer or Decorator yet; those land in Step 5 if rotation, alerting, or hot-reload come up."*

---

## Interview deep-dives — the questions you'll definitely get asked

### 1. Complexity (Big-O)

Let `D` = number of destinations, `R` = bytes per record (string length), `K` = thread count.

| Operation                                | Time                                              | Space            | Notes                                                                              |
| ---------------------------------------- | ------------------------------------------------- | ---------------- | ---------------------------------------------------------------------------------- |
| `Logger.log(level, message)`             | **`O(D · R)`**                                    | `O(R)` per call  | One pass over destinations; each formats+writes ~R bytes                            |
| `Destination.write` filter (below threshold) | **`O(1)`**                                    | 0                | No allocation, no formatting; cheapest path                                        |
| `Destination.write` (above threshold)    | `O(R)` format + `O(R)` write under lock           | `O(R)` for string| Lock held only during the sink write, not during formatting                        |
| Throughput under contention              | `O(K)` callers serialize per destination          | -                | Two callers booking same destination: one waits for the sink write                |
| Async variant — `Destination.write`      | **`O(1)`** enqueue                                | `O(R)` per record in queue | The whole point of going async                                            |
| Storage — Logger                         | -                                                 | `O(D)`           | One immutable list                                                                  |

> **Senior callout:** *"The hot path is `Destination.write` for each enabled destination. Filtering is O(1) and happens first — most log calls from production code are DEBUG/INFO at a destination configured for WARN+, so the hot path is actually `if (level < minLevel) return`. Formatting only runs on the records that pass the filter. That's why per-destination thresholds matter beyond just 'cleaner logs' — they protect production throughput."*

### 2. Concurrency / thread-safety — the full menu

| Approach                                   | When to use                                  | Cost                                                              |
| ------------------------------------------ | -------------------------------------------- | ----------------------------------------------------------------- |
| **Per-destination `synchronized` lock** ⭐ | **Default.** Correct, simple, low contention | Two callers on same destination serialize; different destinations are independent |
| Global lock on `Logger.log`                | NEVER as default                              | A slow file write blocks the console; format runs serialized; failure cascades across destinations |
| Per-destination async queue + worker        | High-throughput apps; tens of thousands of call sites | Worker lifecycle, overflow policy, debuggability cost           |
| Lock-free MPSC queue per destination        | Extreme throughput, you control the lib       | Tricky to get right; only worth it after profiling                |

> **What HI's commenters argue about:** *"Even with per-destination locks, the for-loop in `Logger.log` is sequential — a slow first destination still delays the second. True parallelism would require fan-out across threads or async queues. The per-destination lock prevents byte interleaving (R5); it doesn't make `log()` itself non-blocking. The async-queue extension is the answer if blocking is the problem."*

### 3. Testing — what to write tests for

| Test category                | Cases to cover                                                                                              |
| ---------------------------- | ----------------------------------------------------------------------------------------------------------- |
| Filtering                    | DEBUG record at WARN destination → silent drop; WARN record at DEBUG destination → written                  |
| Format independence          | Same record at PlainText destination AND JSON destination → both succeed, different output                  |
| Per-call capture             | Two destinations log the same record → both see the SAME timestamp and thread name                          |
| Failure isolation            | Sink throws → other destinations still get the record; caller sees no exception                            |
| **Concurrent atomicity (R5)**| 50 threads × 20 records → 1000 well-formed entries, no interleaved bytes                                    |
| Immutability                 | After `log()`, the record's fields are unchanged (verify via reflection or by side-channel)                 |
| Timestamp injection          | With a fixed `Clock`, the record's timestamp is deterministic (the whole reason Clock is injected)         |

```java
@Test
void fiftyThreads_atomic_writes_no_torn_bytes() throws Exception {
    CapturingSink sink = new CapturingSink();
    Logger logger = new Logger(List.of(new Destination(new JsonFormatter(), DEBUG, sink)));

    int N = 50, perThread = 20;
    ExecutorService pool = Executors.newFixedThreadPool(N);
    CountDownLatch fire = new CountDownLatch(1);

    for (int t = 0; t < N; t++) {
        final int tid = t;
        pool.submit(() -> {
            Thread.currentThread().setName("w-" + tid);
            fire.await();
            for (int i = 0; i < perThread; i++) logger.info("msg " + tid + "." + i);
        });
    }
    fire.countDown();
    pool.shutdown();
    pool.awaitTermination(5, TimeUnit.SECONDS);

    assertEquals(N * perThread, sink.size());
    assertEquals(N * perThread, sink.wellFormedJsonCount());   // no torn entries
}
```

> **Senior callout:** *"This test is in the driver. With the `synchronized(lock)` removed, the well-formed count drops below 1000 — torn JSON because two threads' bytes ended up concatenated. The test is the empirical proof that R5 is satisfied, not just on paper."*

### 4. SOLID mapping

| Letter                       | Where it shows up                                                                                          |
| ---------------------------- | ---------------------------------------------------------------------------------------------------------- |
| **S** Single Responsibility  | Logger = orchestration. LogRecord = data. LogLevel = classification. Formatter = serialization. Sink = byte-writing. Destination = per-target invariants (filter + lock + compose). Six classes, six reasons to change. |
| **O** Open/Closed            | Adding a new format = new Formatter class. Adding a new target = new Sink class. **Zero changes to Logger, Destination, or LogRecord.** This is the whole reason for the Strategy interfaces. |
| **L** Liskov Substitution    | Any Formatter is substitutable in a Destination — same `format(record)` contract, same exception expectations. Same for Sink. New impls must honor the same purity / no-side-effect guarantees. |
| **I** Interface Segregation  | Formatter has ONE method; Sink has ONE method. No fat `Destination` interface mixing format + write. The narrowness is what lets RemoteSink land as a 3-line class. |
| **D** Dependency Inversion   | Destination depends on `Formatter` and `Sink` interfaces, not `JsonFormatter` and `FileSink` concretes. Logger depends on `List<Destination>`, not on any particular destination class. Construction wiring (which concrete impls) lives at the composition root, outside the core. |

### 5. "Summarize your design in 30 seconds"

> *"Six core types: Logger (orchestrator), LogRecord (immutable value object), LogLevel (5-value enum with explicit severity), Formatter and Sink (two Strategy interfaces — these are in the base because R4 explicitly says format and destination type vary independently), and Destination (the concrete class that composes them with a per-target threshold and a per-destination lock). The big architectural call is composition over inheritance — N+M classes vs N×M subclasses. `Logger.log` captures timestamp and thread name ONCE at the call site so every destination sees the same record. `Destination.write` filters first, formats outside the lock (records are immutable, formatters are pure), then synchronizes only around the sink write. Failure isolation: any sink exception is caught and routed to stderr — Logger.log never throws back to the caller. The 50-thread driver test verifies R5 empirically: 1000 records, 1000 well-formed entries, zero torn bytes. Extensions: queue-per-destination for async, Decorator for log rotation, LoggerFactory registry for named hierarchical loggers."*

That's ~50 seconds. Hits: structure, the composition rationale, the timestamp-once choice, the lock placement, format-outside-the-lock, failure isolation, and the empirical R5 verification.

---

## Closing soundbites (memorize these)

- **Opening:** *"'Logger' can mean wildly different things — in-process library or distributed aggregator? Let me clarify before designing."*
- **Why composition (the senior moment):** *"Format and destination type vary independently — that's the textbook signal for composition. Inheritance would give N×M subclasses; two Strategy interfaces give me N+M."*
- **The 2-axis Strategy is justified:** *"Both `Formatter` and `Sink` are in the base because R4 explicitly says they vary independently. This isn't pattern-stuffing — it's responding to a stated requirement."*
- **Why timestamp at the top:** *"Capture timestamp and thread name once in `Logger.log` so every destination sees the same moment. Capturing inside each destination would produce per-line clock skew."*
- **Lock placement:** *"The lock lives next to the resource it protects — per-destination, around the sink write. A global lock on Logger would let a slow file write block instant console output."*
- **Format outside the lock:** *"Records are immutable, formatters are pure — there's nothing to synchronize during format. Locking format would serialize work that doesn't need to be."*
- **Failure isolation:** *"A failing sink can't take out other destinations or crash the caller. Catch inside Destination.write, log to stderr, move on."*
- **On the async question:** *"The lock is correctness (no torn bytes). The queue is coordination (don't block the caller). Different problems."*

---

## Top mistakes that lose points

- **Coupling format to destination type** — `JsonFileDestination`, `PlainConsoleDestination`, etc. Watch for "format and destination vary independently" → composition.
- **A class hierarchy per level** (`DebugLevel`, `InfoLevel`...) — five fixed values + ordering + no behavior = enum.
- **Global lock on `Logger.log`** — slow file write blocks console; format runs serialized; failure propagates. The lock belongs per-destination.
- **Capturing timestamp inside each destination** — every destination records a slightly different time for the same call.
- **Letting a sink exception propagate** — one bad file destination kills the console line AND throws at the caller.
- **Building destinations with a mutable list + `addDestination`** — forces locking around every iteration with zero requirement-driven benefit.
- **Putting the level filter on Logger** — overrides per-destination thresholds; defeats requirement 4.
- **Reopening the file on every write** — open syscall dwarfs the actual write cost.
- **Formatting INSIDE the lock** — serializes work that has nothing shared to protect.
- **Not flushing the FileSink** — the most recent log lines die in the OS buffer when the process crashes.
- **Treating "async" as a thread-safety fix** — async writes solve blocking; per-destination locks solve correctness. They're different problems.
- **Skipping the empirical concurrency test** — the 50-thread driver scenario is exactly what the interviewer wants to see.

---

## Files in this folder (your reference implementation)

| File                                              | What it shows                                                                            |
| ------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| `model/LogLevel.java`                             | Enum with explicit severity int + `isAtLeast`                                            |
| `model/LogRecord.java`                            | Immutable 4-field value object                                                           |
| `formatter/Formatter.java`                        | Strategy interface — pure-function contract                                              |
| `formatter/PlainTextFormatter.java`               | Default human-readable format                                                            |
| `formatter/JsonFormatter.java`                    | Minimal JSON encoder with escaping                                                       |
| `sink/Sink.java`                                  | Strategy interface — byte-write only, no filter/format/lock                              |
| `sink/ConsoleSink.java`                           | Writes to stdout                                                                         |
| `sink/FileSink.java`                              | Append-mode file sink; AutoCloseable; flush-per-write                                     |
| `Destination.java`                                | Composes Formatter + Sink + minLevel + per-destination lock; failure isolation           |
| `Logger.java`                                     | Orchestrator + facade; injected Clock; immutable destinations list                       |
| `LoggerDriver.java`                               | 3 scenarios — filtering / failure isolation / **50-thread atomicity check (1000/1000)**  |

Run from the project root:

```bash
mvn -q compile exec:java \
  -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.logger.LoggerDriver
```
