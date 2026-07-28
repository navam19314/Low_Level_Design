# Logging Service — 45-min LLD Interview Walkthrough

**Target role:** SDE-2 (Amazon, Adobe, Microsoft, Atlassian)

> The canonical **"composition over inheritance" LLD problem**. The headline isn't class design or concurrency in isolation — it's the **two-dimensional variation** (any format with any sink) that forces you to *compose* rather than subclass. Get that right, plus the per-resource lock placement, and you're senior. This is the rare problem where TWO Strategy interfaces (Formatter, Sink) both pass the one-sentence test in the base design.

---

## Part 0 — Understand it from scratch (read this first if you're rusty)

*This section is plain-English intuition. Skip it if the design is already fresh; come back to it when it isn't.*

### What is "logging"?

When a program runs, you can't see what it's doing inside. So developers scatter little "print" statements to leave a trail of breadcrumbs:

```java
System.out.println("User logged in");
System.out.println("Payment failed!");
```

When something breaks at 3am, you read these breadcrumbs to figure out *what happened and when*. **Logging is a diary your program writes about itself.**

### Why not just use `println` everywhere?

Because real apps need more than "print to screen":

1. **Importance levels** — a routine "user logged in" is not as urgent as "database on fire!". You want to say "only show me the serious stuff."
2. **Multiple places to write** — print to the screen AND save to a file AND (later) send to a monitoring server.
3. **Different shapes** — a human reading the console wants `[WARN] disk low`; a monitoring tool wants machine-readable `{"level":"WARN"}`.

`println` can't do any of this. So we design a proper **Logger** — a small system that handles it all. That's this LLD exercise.

### What the developer actually types

As the person *using* the logger, you want dead-simple one-liners:

```java
logger.info("User logged in");
logger.warn("Disk almost full");
logger.error("Payment failed");
```

`info`/`warn`/`error` = the importance level. The string = the message. **Everything else — where it goes, how it's shaped, what gets filtered — you set up ONCE at startup and never think about again.**

### The 6 components, each solving ONE need

Introduced in the order the story needs them:

**1. `LogLevel` — "how serious is this?"**
A fixed ranking, cheapest to scariest. Lets you later say "ignore anything below WARN."
```
DEBUG  →  INFO  →  WARN  →  ERROR  →  FATAL
(chatty)                             (catastrophic)
```

**2. `LogRecord` — "one diary entry"**
When you call `logger.warn("disk low")`, the system bundles everything about that one call into a box:
```
LogRecord = { message: "disk low", level: WARN, time: 10:00:00, thread: "main" }
```
Instead of passing 4 loose values around, wrap them in one object. One log call = one LogRecord.

**3. `Formatter` — "HOW should the entry look?"**
A LogRecord is raw data; we turn it into a string. But two readers want two shapes:
- Human (console):  `2026-07-27 10:00 [WARN] [main] disk low`
- Machine (monitoring tool):  `{"time":"...","level":"WARN","message":"disk low"}`

A **Formatter**'s only job is "turn a LogRecord into a string." Versions: `PlainTextFormatter`, `JsonFormatter`.

**4. `Sink` — "WHERE do the characters physically go?"**
Now we have a finished string. Where do the actual bytes land — screen? file? remote server? A **Sink** (like a kitchen sink — stuff flows into it and away) has one job: "take this finished string and physically write it." Versions: `ConsoleSink` (screen), `FileSink` (a file).

> **Why keep Formatter and Sink separate?** "How it looks" and "where it goes" are *independent* choices. You might want JSON-to-file AND JSON-to-screen AND plaintext-to-file. If you glued format and destination together, you'd need a class for every combination (JsonFile, JsonConsole, PlainFile, PlainConsole… it explodes). Keeping them separate lets you mix-and-match freely. **This is the whole point of the problem** (see M2).

**5. `Destination` — "one complete output pipe"**
A single "output" is really THREE decisions bundled together:
- What's my minimum importance? (a threshold, e.g. WARN)
- How should it look? (a Formatter)
- Where does it go? (a Sink)

A **Destination** bundles those three. It's one fully-configured pipe:
```
Destination A = { threshold: DEBUG, format: PlainText, sink: Console }
   → "print EVERYTHING to the screen, human-readable"
Destination B = { threshold: WARN,  format: Json,      sink: File }
   → "save only WARN-and-above to a file, as JSON"
```
When a log entry arrives, a Destination does 3 steps: **filter** (important enough? if not, drop it) → **format** (shape it) → **write** (hand to its Sink).

**6. `Logger` — "the front door you call"**
The only class the developer touches. It holds a list of Destinations. When you call `logger.warn(...)`, the Logger builds one LogRecord and hands that same record to EVERY Destination — each one independently decides whether/how/where to write it.

### See it all work together

**Setup (once, at startup):**
```java
Destination screen = new Destination(PlainText, DEBUG, Console);   // everything → screen
Destination file   = new Destination(Json,      WARN,  File);      // warnings+ as JSON → file
Logger logger = new Logger(List.of(screen, file));
```

**Anywhere in your app:**
```java
logger.warn("disk space low");
```

**What happens inside:**
```
                logger.warn("disk space low")
                          │
                          v
          LogRecord{ WARN, "disk space low", 10:00, main }
                          │
        ┌─────────────────┴─────────────────┐
        v                                    v
   Destination "screen"                Destination "file"
   1. WARN >= DEBUG? yes ✓             1. WARN >= WARN? yes ✓
   2. PlainText -> "10:00 [WARN] ..."  2. Json -> {"level":"WARN",...}
   3. Console -> prints to screen      3. File -> appends to app.log
```
Result: **the screen shows readable text, the file gets JSON — from one single log call.**

And if you'd called `logger.debug("tiny detail")` instead:
- `screen`: DEBUG >= DEBUG? yes → prints it.
- `file`:   DEBUG >= WARN? **no** → silently dropped. Nothing written.

Same call, but each Destination's own threshold decides its fate.

### One-line summary of each piece

| Piece | Layman meaning | Answers |
|-------|----------------|---------|
| **LogLevel** | importance ranking | "how serious is this?" |
| **LogRecord** | one diary entry (message + when + level + who) | "what happened?" |
| **Formatter** | the styling | "how should it *look*?" |
| **Sink** | the physical writer | "*where* do the bytes go?" |
| **Destination** | one complete pipe (threshold + Formatter + Sink) | "one full output setup" |
| **Logger** | the front door you call | "the thing developers use" |

### The senior reason it's designed this way

The whole design answers one question elegantly: *"How do I support ANY format going to ANY place, without a class explosion?"* By splitting **Formatter** (how) from **Sink** (where), and letting **Destination** freely combine any Formatter with any Sink, you get infinite combinations from a handful of small single-purpose classes. New format = one new Formatter. New place = one new Sink. Everything else keeps working untouched.

---

## Time budget

| Step | Activity | Budget | Cumulative |
|------|----------|--------|------------|
| 1 | Requirements | ~5 min | 5 |
| 2 | Entities & relationships | ~5 min | 10 |
| 3 | Class design | ~12 min | 22 |
| 4 | Implementation + dry-run | ~14 min | 36 |
| 5 | Extensibility | ~8 min | 44 |
| — | Wrap | ~1 min | 45 |

Step 3 gets an extra minute — the inheritance-vs-composition fork is THE senior-signal moment.

---

## Mental models — memorize before you walk in

### M1. The dispatch flow (single log call → fan out)

```
   logger.info("user signed in")
                │
                v
   +-------------------------------------------+
   | 1. now()                                  |  ← one timestamp for all destinations
   | 2. Thread.currentThread().name            |
   | 3. record = LogRecord(ts, INFO, msg, tn)  |
   +-------------------------------------------+
                │
                v
       for destination in destinations:        ← list is IMMUTABLE post-ctor
                │                                  so no lock around iteration
                v
   +-------------------------------------------+
   | Destination.write(record):                |
   |   if level < minLevel  -> drop            |
   |   formatted = formatter.format(record)    |  ← OUTSIDE the lock (pure fn)
   |   synchronized(this.lock):                |
   |       try { sink.write(formatted); }      |
   |       catch { stderr diagnostic; }        |  ← failure isolation
   +-------------------------------------------+
                                                each destination is independent
```

### M2. The 2D variation that DEMANDS composition (not inheritance)

```
   The requirement: format and sink-type vary INDEPENDENTLY.

   With inheritance — N×M classes:                With composition — N+M classes:
   ----------------------------------             -------------------------------
   abstract Destination                           interface Formatter          (N=2)
       PlainConsoleDestination                        PlainTextFormatter
       JsonConsoleDestination                         JsonFormatter
       PlainFileDestination
       JsonFileDestination                        interface Sink               (M=2)
       PlainRemoteDestination       [v2]              ConsoleSink
       JsonRemoteDestination        [v2]              FileSink
       CsvConsoleDestination        [v3]              RemoteSink               [v2]
       CsvFileDestination           [v3]              CsvFormatter             [v3]
       CsvRemoteDestination         [v3]
                                                  Destination(formatter, sink)  (1 concrete class)
   3 formats × 3 sinks = 9 classes                3 + 3 = 6 classes
   Each new axis multiplies                       Each new axis adds linearly
   Combinations are CONSTRUCTOR ARGS, not class declarations.
```

**Senior soundbite:** *"When a requirement gives you two dimensions that vary independently — any format with any sink — that's almost always the signal for composition over inheritance. Inheritance gives you N×M subclasses; composition gives you N+M and a single concrete class that takes both as constructor args."*

### M3. Lock placement — per-destination, around the sink only

```
   Option A — global lock on Logger.log         |   Option B — per-destination, around sink
   --------------------------------------       |   ---------------------------------------
   synchronized(this) {                         |   for d in destinations:
     for d in destinations: d.write(record)     |     d.write(record)                  ← no global lock
   }                                            |
                                                 |   Destination.write(record):
   PROBLEM:                                     |       if filtered out return
   - one slow file write blocks ALL             |       formatted = format(record)     ← pure, no lock
     destinations, including console            |       synchronized(this.lock):
   - format() (pure!) also runs serialized      |           sink.write(formatted)
   - blocks UNRELATED loggers' threads          |
                                                 |   WIN: lock is next to the resource
   When shared state lives PER-DESTINATION      |   it protects; format() runs in
   (the file handle, the stdout buffer), the    |   parallel for all destinations; one
   lock belongs there — not on the orchestrator |   slow destination doesn't block others
   above it.
```

> **The interview rule:** *"The lock lives with the shared resource it's protecting. Logger doesn't own the file handle; the Destination's Sink does. So the lock belongs on Destination."* Same per-resource principle as Showtime's seat-booking lock in Movie Ticket Booking.

---

## Step 1 — Requirements (~5 min)

### Clarifying dialogue

**You:** *"'Logger' can mean an in-process library or a distributed log aggregator. I'll assume in-process, Log4j-style, with five severity levels — DEBUG through FATAL?"*
**Interviewer:** *"Yes, in-process, five levels."*
> Signals `LogLevel` as a simple ordered enum.

**You:** *"Multiple destinations from one logger call? And does each destination have its own threshold AND its own format — can format and destination type vary independently?"*
**Interviewer:** *"Yes — any format with any destination type."*
> This is THE load-bearing answer — it dictates composition over inheritance in Step 3.

**You:** *"Concurrency — what's the bar? Do bytes from one record need to stay intact, not interleaved with another thread's record on the same destination?"*
**Interviewer:** *"Yes, per-record atomicity on each destination."*
> Signals per-destination locking around the sink write.

**You:** *"Out of scope for v1 — async/buffered writes, remote sinks, hot-reload config, log rotation, hierarchical named loggers?"*
**Interviewer:** *"Correct — those are all follow-ups."*
> Name these explicitly even if unprompted — it buys you the time to finish the base build, and signals awareness of what a production logger eventually needs.

### Requirements to write down

```
IN SCOPE
1. Five severity levels: DEBUG < INFO < WARN < ERROR < FATAL.
2. Each record carries: timestamp, level, message, emitting thread name.
3. Logger writes each record to one or more destinations (set at startup).
4. Per-destination: own min-level threshold AND own format.
   FORMAT and DESTINATION TYPE vary INDEPENDENTLY. ← the key requirement
5. Concurrent calls are safe; bytes for one record never interleave with
   another's on the same destination.

OUT OF SCOPE (mentioned by name to signal awareness)
- Hot-reload of config at runtime
- Async / buffered writes
- Remote / network destinations (v1, but the design must accommodate)
- Log rotation
- Hierarchical / named loggers (com.app.service inheriting from com.app)
```

---

## Step 2 — Entities & relationships (~5 min)

```
Entities
- Logger        orchestrator + facade — log / debug / info / warn / error / fatal
- LogRecord     immutable value object — timestamp + level + message + threadName
- Destination   one configured target — minLevel + formatter + sink + per-destination lock
- Formatter     interface — Strategy; PlainTextFormatter, JsonFormatter        (1st axis)
- Sink          interface — Strategy; ConsoleSink, FileSink, [RemoteSink]      (2nd axis)

Enums
- LogLevel      { DEBUG < INFO < WARN < ERROR < FATAL } with isAtLeast(threshold)

NOT entities
- Thread / Application    external — we read currentThread().name at the call site
- A "Level" class hierarchy — 5 fixed values + ordering + no behavior = enum
- A unified "Destination per (format, target)" class — would be N×M; we use composition

Relationships
- Logger      owns  List<Destination>          (immutable post-ctor)
- Destination owns  Formatter + Sink + lock + minLevel
- Logger      creates LogRecord per log() call; hands the SAME record to each destination
```

### Why no `Level` class hierarchy?

> *"Five fixed values with an ordering and no per-level behavior — textbook enum. A `DebugLevel`/`InfoLevel` class hierarchy is the classic over-modeling trap; nothing varies per level."*

### Why is `LogRecord` a class, not 4 raw parameters?

> *"Today it's 4 fields. Tomorrow there's a logger name, a request id, an MDC context map. With a class, that's a one-line change here. With raw parameters, every `Destination.write`, `Sink.write`, and `Formatter.format` signature changes — and every implementation with it."*

### Class diagram

```
   (application code)
        │
        │ logger.info("...")
        v
   +----------------------------+
   |          Logger            |   ← orchestrator + facade
   |  log / debug / info / ...  |
   +----------------------------+
        │
        │ iterates List<Destination>   (immutable)
        v
   +-----------------------------------------+
   |             Destination                 |   ← the per-target object
   |  minLevel + formatter + sink + lock     |
   |  filter -> format (no lock) -> sink (locked)
   +-----------------------------------------+
            │                       │
       Formatter (Strategy)    Sink (Strategy)
            │                       │
   +---+----+----+----+    +---+----+----+----+
   │              │        │              │
   v              v        v              v
PlainText      Json     Console        File   [Remote in v2]
Formatter   Formatter   Sink           Sink
```

---

## Step 3 — Class design (~12 min)

### Logger — state derived from requirements

| Requirement | State |
|-------------|-------|
| Write to one or more destinations | `List<Destination> destinations` (immutable post-ctor) |
| Capture wall-clock time | `Clock clock` (injected — makes time testable) |

> **Why immutable destinations list?** *"Config is set ONCE at startup. Iteration is safe under concurrent calls with no locking. A mutable list with `addDestination()` would force locking around every iteration and buy nothing the requirements asked for."*

### Logger — class outline

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

This is the **moment of truth**. Three candidate designs:

| Option | Shape | Verdict |
|--------|-------|---------|
| **A. Single class, switch inside `write`** | One `Destination` with a `type` field; switch dispatches to file/console/etc. | Works at v1, but every new sink is a new case AND every new format multiplies the cases. Open/Closed violation. |
| **B. Inheritance hierarchy** | `abstract Destination` + `ConsoleDestination`, `FileDestination`, `JsonConsoleDestination`, ... | Solves dispatch but creates the **N×M class explosion** once format and sink-type are independent. |
| **C. Composition, two Strategy interfaces** ⭐ | One concrete `Destination` composes a `Formatter` + a `Sink` + a `minLevel`. | **N+M classes, not N×M.** Combinations are constructor args, not class declarations. |

> **Say this out loud:** *"With N formats × M sinks, inheritance gives N×M classes. The requirement explicitly says format and sink type vary independently — that's the textbook composition signal. One concrete `Destination` class takes a `Formatter` and a `Sink` as constructor args. Adding CSV is a new Formatter; adding a remote endpoint is a new Sink. No combinatorial growth."*

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

    public void write(LogRecord record) {           // filter -> format -> lock-and-write
        if (!record.getLevel().isAtLeast(minLevel)) return;
        String formatted = formatter.format(record);          // OUTSIDE the lock
        synchronized (lock) {
            try { sink.write(formatted); }
            catch (Exception e) {                              // failure isolation
                System.err.println("logger: sink write failed: " + e.getMessage());
            }
        }
    }
}
```

### LogRecord + LogLevel

```java
public final class LogRecord {
    private final Instant timestamp;
    private final LogLevel level;
    private final String message;
    private final String threadName;
    // ctor + getters only — all fields final
}

public enum LogLevel {
    DEBUG(10), INFO(20), WARN(30), ERROR(40), FATAL(50);
    private final int severity;
    LogLevel(int severity) { this.severity = severity; }
    public boolean isAtLeast(LogLevel min) { return severity >= min.severity; }
}
```

> **Why `severity` as an explicit int, not `ordinal()`?** *"Severity-as-int survives reordering of declarations. If someone later adds `TRACE(5)` below DEBUG, comparisons stay correct. With `ordinal()`, comparison logic silently shifts."*

### The principle to say aloud — Dependency Inversion + SRP

> *"Destination depends on the `Formatter` and `Sink` interfaces, not on `JsonFormatter` or `FileSink` concretes. Each class owns exactly one reason to change: orchestration in Logger, data in LogRecord, classification in LogLevel, serialization in Formatter, byte-writing in Sink, per-destination invariants in Destination."*

---

## Step 4 — Implementation + dry-run (~14 min)

### 4.1 `Logger.log` — capture once, fan out

```java
public void log(LogLevel level, String message) {
    // Capture per-call data ONCE — every destination sees the same record.
    LogRecord record = new LogRecord(
            clock.instant(),
            level,
            message,
            Thread.currentThread().getName());

    // Sequential dispatch. The list is immutable, so no lock here.
    for (Destination destination : destinations) {
        destination.write(record);
    }
}
```

**Three callouts:**

1. *"Timestamp and thread name are captured at the TOP of `log()`, not inside each destination. Capturing inside each destination would produce per-line clock skew — microseconds apart, but still wrong."*
2. *"No level filter on Logger — the threshold is per-destination, so the filter lives inside `Destination.write`. Filtering here would override per-destination thresholds."*
3. *"No lock around the for-loop. The destinations list is immutable after construction; concurrent threads walk it safely. Locking lives one layer down, next to the shared resource."*

### 4.2 `Destination.write` — filter, format outside lock, write under lock

```java
public void write(LogRecord record) {
    if (!record.getLevel().isAtLeast(minLevel)) return;

    // Format OUTSIDE the lock. Records are immutable, formatters are pure;
    // two threads can format the SAME record concurrently with no contention.
    String formatted = formatter.format(record);

    // Lock only around the sink write (the shared resource).
    synchronized (lock) {
        try {
            sink.write(formatted);
        } catch (Exception e) {
            // Failure isolation — caller never sees an exception from logging.
            // Catch Exception, not Throwable — don't swallow JVM Errors like OOM.
            System.err.println("logger: sink write failed: " + e.getMessage());
        }
    }
}
```

> **Senior callout — format outside the lock:** *"Records are immutable; Formatter implementations are pure functions. There's zero shared state between two threads formatting the same record, so formatting outside the critical section gives real concurrency — only the sink write serializes."*

> **Senior callout — catch Exception, not Throwable:** *"Catching Throwable would also swallow OutOfMemoryError and StackOverflowError — those should crash the process, not get silently absorbed by a logging call. Exception is the right boundary for failure isolation."*

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
            writer.flush();       // visible before crash — "flush on close" is the WRONG moment
        } catch (IOException e) { throw new UncheckedIOException("FileSink write failed", e); }
    }
    @Override public void close() throws IOException { writer.close(); }
}
```

> **Senior callout on flush:** *"Default behavior in most languages is to flush on close. That's exactly the wrong moment for a logger — you want recent logs visible BEFORE the crash, not after a clean shutdown. Flush per write trades some throughput for crash-proof visibility."*

### 4.5 Dry-run — three scenarios (say this at the board)

**A — different thresholds, different formats:**

```
console : PlainTextFormatter, minLevel=DEBUG
json    : JsonFormatter,      minLevel=WARN

logger.debug("x") -> console: writes "[DEBUG] x"   | json: DEBUG < WARN, dropped
logger.warn("z")  -> console: writes "[WARN] z"    | json: writes {"level":"WARN",...}
                                                       ^^^ same record, different format ✓
```

**B — failing sink, others unaffected:**

```
console : working          | flaky : throws RuntimeException("disk full") on every write

logger.error("disk space low")
  console.write: filter ✓, format ✓, lock, sink.write → "disk space low" appears     ✓
  flaky.write:   filter ✓, format ✓, lock, sink.write → THROWS
                 catch(Exception) → stderr diagnostic, lock released, exits normally  ✓

Result: console line written; flaky failed silently with stderr diagnostic;
        Logger.log returned normally; caller saw nothing.                            ✓
```

**C — 50 threads × 20 records = 1000 records, atomicity check:**

```
total records written:  1000   (expect 1000)
well-formed JSON lines: 1000   (expect 1000)
any malformed?          no  ✓
```

Without the lock, well-formed count would drop below 1000 — interleaved bytes from two threads concatenated into one entry, breaking the JSON shape. **This is the empirical proof that concurrent atomicity holds, not just on paper.**

---

## Step 5 — Extensibility (~8 min)

Two follow-ups are practically guaranteed: **async writes** (explicitly scoped out — the interviewer will ask) and **hierarchical named loggers** (everyone who's used Log4j/SLF4J will ask).

### E1. "How would you make `log()` non-blocking?"

**Problem:** The per-destination lock makes the calling thread wait for `sink.write` to finish — fine for stdout, ugly for a slow disk or a future remote endpoint.

**Fix:** A bounded blocking queue in front of each destination. `Destination.write` enqueues and returns immediately; a dedicated worker thread per destination drains the queue and does the actual sink write — single-consumer per sink means the lock isn't even needed anymore.

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
        if (!queue.offer(r)) System.err.println("logger: queue full, dropping record");
    }
    private void drain() {
        while (!Thread.currentThread().isInterrupted()) {
            try { sink.write(formatter.format(queue.take())); }     // single-consumer; no lock needed
            catch (Exception e) { System.err.println("logger: async write failed: " + e.getMessage()); }
        }
    }
}
```

**Tradeoffs to name:**
1. **Worker lifecycle** — shutdown must signal the worker to stop and drain the queue, or you lose buffered records on JVM exit.
2. **Overflow policy** — block the producer (defeats the point), drop newest (silent loss), drop oldest, or throw. Most production loggers default to drop-newest + stderr diagnostic.
3. **Debuggability** — the write happens on a different thread; stack traces no longer point to the call site.

> **Key observation:** *"Async writes and thread-safe writes solve different problems. The lock is correctness (no torn bytes). The queue is coordination (don't block the caller). With single-consumer-per-destination, the queue lets you drop the lock entirely — until two destinations share an underlying stream."*

### E2. "How would you support hierarchical named loggers?"

**Problem:** Production loggers expose `LoggerFactory.getLogger("com.app.service.payments")`, inheriting config from the parent in a dotted-name tree. Our design has a single global Logger.

**Fix:** A `LoggerFactory` (a deliberate Singleton — the rare valid case) maintains a registry keyed by name. `Logger` gains a `name` field and a `parent` pointer; effective level/destinations walk the parent chain when not set locally.

**Tradeoff to name:** *"Walking the parent chain on every `log()` call would be hot-path expensive. Real frameworks cache the effective level and invalidate on config change. The LoggerFactory IS a global — that's the requirement, not an accident; two callers asking for `getLogger('X')` must get the same instance."*

### E3. Other one-liners

| Follow-up | Answer |
|-----------|--------|
| "Add a remote / network sink" | New `RemoteSink implements Sink`. Zero changes elsewhere — the whole point of the Sink interface. |
| "Add CSV format" | New `CsvFormatter implements Formatter`. Zero changes elsewhere. |
| "Add structured fields / MDC" | Extend `LogRecord` with `Map<String, String> context`. Existing formatters keep working. |
| "Log rotation" | Wrap `FileSink` in a `RotatingFileSink` — Decorator. Rotates on size/time threshold. |
| "Hot-reload config" | Replace `final List<Destination>` with `AtomicReference<List<Destination>>`. Config updates atomically swap the list. |
| "Rate limiting / dedup" | Wrap a Sink in a `RateLimitedSink` — Decorator. Drops or batches records over the limit. |
| "Observability for the logger itself" | Observer — Destination publishes `SinkWriteFailed` events; alerting subscribes. Don't log-from-logger. |

---

## Design patterns in play (name these out loud in the interview)

### In the BASE design — mention in Step 2 or Step 3

This is the rare problem where **two** Strategy interfaces both pass the one-sentence test in the base — because the requirement explicitly says format and destination type vary independently.

| Pattern / Principle | Where it lives | One-line justification |
|---------------------|----------------|------------------------|
| **Strategy** ⭐ | `Formatter` interface — `PlainTextFormatter`, `JsonFormatter` | *"Format and destination type vary independently — that's the explicit requirement, not a guess."* |
| **Strategy** ⭐ | `Sink` interface — `ConsoleSink`, `FileSink` | *"Same justification, second axis. A future RemoteSink is a new class, zero other changes."* |
| **Facade** | `Logger` | *"The only class application code touches — hides Destination, Formatter, Sink, locking, and dispatch."* |
| **Composition over inheritance** | `Destination` composes Formatter + Sink | *"N+M classes vs N×M subclasses — the headline senior signal for this problem."* |
| **Information Expert** (GRASP) | Lock lives on `Destination` | *"The resource it protects — the sink — lives there too."* |
| **Immutability** | `LogRecord`, the destinations list | *"No shared mutable state in the hot path."* |

### Patterns for Step 5 extensibility

| Follow-up trigger | Pattern | The one-line move |
|--------------------|---------|--------------------|
| "Log rotation by size/time" | **Decorator** | *"`RotatingFileSink(FileSink)` swaps the underlying file on threshold. Decorator over Sink — any sink can be rotated."* |
| "Async / non-blocking log()" | Queue + worker | *"Queue-per-destination + single-consumer worker. Lock vanishes because the consumer is single-threaded."* |
| "Hierarchical named loggers" | **Singleton** (registry) | *"`LoggerFactory` is the rare defensible Singleton — two callers asking for `getLogger('X')` must get the same instance."* |
| "Alerting on logger-internal failures" | **Observer** | *"Destination publishes `SinkWriteFailed` events; alerting subscribes. Don't log-from-logger — recursive logging is a footgun."* |
| "Different log shipping protocols (HTTP/gRPC/Kafka)" | **Strategy** ⭐ | *"All are new `Sink` implementations. The interface is already the seam — that's why it's in the base."* |

### Patterns to actively refuse

- **Singleton on Logger** (the top-level class) — kills tests; DI a single instance. The defensible Singleton is the *factory/registry*, not Logger itself.
- **`LogLevel` as a class hierarchy** — five fixed values + ordering + no per-level behavior = enum.
- **Composite over Destinations** — flat collection, not a tree.
- **State pattern on Logger** — no states to speak of.

### The rule to sound natural

1. **Two Strategy interfaces in the base is correct here, not pattern-stuffing** — the requirement explicitly demands independent variation on two axes.
2. **Cap Step-5 patterns at 2** — usually Decorator (rotation/rate-limiting) + one of Observer/Singleton depending on the follow-up.
3. **Pair each pattern with the concrete requirement that justifies it.**

---

## What is expected at each level

### Junior (SDE-1)
- Recognizes multiple destinations and formats are needed but may reach for an inheritance hierarchy (`JsonFileDestination`, `PlainConsoleDestination`) before being nudged toward composition.
- Gets basic `log`/`write` working; may put the level filter on `Logger` instead of `Destination`.
- Global lock on `Logger.log` instead of per-destination — needs prompting to see why that's wrong.
- Doesn't test concurrent atomicity unprompted.

### Mid-level (SDE-2) — the target
- Reaches composition over inheritance unprompted, citing the N×M vs N+M argument.
- Two Strategy interfaces (Formatter, Sink) named and justified from the requirement, not pattern-stuffed.
- Timestamp/thread name captured once at the top of `log()`, not per-destination.
- Lock placed correctly — per-destination, around the sink write only, format outside the lock.
- Catches `Exception` (not `Throwable`) for failure isolation.
- Runs the 3-scenario dry-run out loud, including the 50-thread atomicity case.

### Senior (SDE-3 / SDE-II)
- Everything mid-level does, faster, with proactive tradeoffs.
- Explains why format runs OUTSIDE the lock (pure function, no shared state) as a deliberate concurrency optimization, not an accident.
- Distinguishes "lock = correctness" from "queue = coordination" unprompted when async comes up.
- Catches subtle issues: reopening the file per write (syscall cost), flush-on-close being the wrong default, an unbounded destinations list needing config-hardening.
- Names the LoggerFactory-as-Singleton exception explicitly and defends why it's the rare correct case.
- Finishes early; uses buffer to sketch the async-queue extension in code.

---

## Interview deep-dives

### Complexity

Let `D` = destinations, `R` = bytes per record.

| Operation | Time | Notes |
|-----------|------|-------|
| `Logger.log` | **O(D·R)** | One pass over destinations; each formats+writes ~R bytes |
| `Destination.write` (below threshold) | **O(1)** | No allocation, no formatting — cheapest path |
| `Destination.write` (above threshold) | O(R) format + O(R) write under lock | Lock held only during the sink write |
| Async variant | **O(1)** enqueue | The whole point of going async |

> **Senior callout:** *"Most production log calls are DEBUG/INFO against a destination configured for WARN+, so the hot path is `if (level < minLevel) return` — O(1). Formatting only runs on records that pass the filter. Per-destination thresholds protect production throughput, not just log cleanliness."*

### Concurrency

| Approach | When | Cost |
|----------|------|------|
| Per-destination `synchronized` lock ⭐ | **Default.** Correct, simple, low contention. | Same-destination callers serialize; different destinations are independent. |
| Global lock on `Logger.log` | **Never as default.** | Slow file write blocks console; format runs serialized; failure cascades. |
| Per-destination async queue + worker | High-throughput, many call sites | Worker lifecycle, overflow policy, debuggability cost. |

> *"Even with per-destination locks, the for-loop in `Logger.log` is sequential — a slow first destination still delays the second. The per-destination lock prevents byte interleaving; it doesn't make `log()` non-blocking. The async-queue extension is the answer if blocking is the actual problem."*

### The concurrent-atomicity test (mention this)

```java
@Test
void fiftyThreads_atomic_writes_no_torn_bytes() throws Exception {
    CapturingSink sink = new CapturingSink();
    Logger logger = new Logger(List.of(new Destination(new JsonFormatter(), DEBUG, sink)));

    int N = 50, perThread = 20;
    // ... 50 threads each log 20 records, released via CountDownLatch ...

    assertEquals(N * perThread, sink.size());
    assertEquals(N * perThread, sink.wellFormedJsonCount());   // no torn entries
}
```

*"With the `synchronized(lock)` removed, the well-formed count drops below 1000 — torn JSON from concatenated bytes. This test is the empirical proof, not just a paper argument."*

---

## 30-second summary (memorize for closing)

> *"Six core types: Logger (orchestrator), LogRecord (immutable value object), LogLevel (5-value enum with explicit severity), Formatter and Sink (two Strategy interfaces — justified because format and destination type vary independently), and Destination (composes them with a per-target threshold and lock). The architectural call is composition over inheritance — N+M classes vs N×M subclasses. `Logger.log` captures timestamp and thread name ONCE so every destination sees the same record. `Destination.write` filters first, formats outside the lock (pure function, no shared state), then synchronizes only around the sink write. Failure isolation: any sink exception is caught (Exception, not Throwable — never swallow JVM Errors) and routed to stderr. The 50-thread driver test verifies atomicity empirically: 1000 records, 1000 well-formed entries, zero torn bytes. Extensions: queue-per-destination for async, Decorator for log rotation, LoggerFactory registry for named hierarchical loggers."*

---

## Top mistakes that lose points

- **Coupling format to destination type** (`JsonFileDestination`, `PlainConsoleDestination`) — watch for "vary independently" → composition.
- **A class hierarchy per level** (`DebugLevel`, `InfoLevel`) — five fixed values + no behavior = enum.
- **Global lock on `Logger.log`** — slow file write blocks console; the lock belongs per-destination.
- **Capturing timestamp inside each destination** — produces per-line clock skew for the same call.
- **Letting a sink exception propagate** — one bad file destination kills the console line AND throws at the caller.
- **`catch (Throwable)` instead of `catch (Exception)`** — swallows JVM Errors like OutOfMemoryError.
- **Putting the level filter on Logger** — overrides per-destination thresholds.
- **Reopening the file on every write** — the open syscall dwarfs the actual write cost.
- **Formatting INSIDE the lock** — serializes work that has nothing shared to protect.
- **Not flushing the FileSink** — recent log lines die in the OS buffer when the process crashes.
- **Skipping the empirical concurrency test** — the 50-thread scenario is exactly what the interviewer wants to see.

---

## Files in this folder

| File | Purpose |
|------|---------|
| `model/LogLevel.java` | Enum with explicit severity int + `isAtLeast` |
| `model/LogRecord.java` | Immutable 4-field value object |
| `formatter/Formatter.java` | Strategy interface — pure-function contract |
| `formatter/PlainTextFormatter.java` | Default human-readable format |
| `formatter/JsonFormatter.java` | Minimal JSON encoder with escaping |
| `sink/Sink.java` | Strategy interface — byte-write only, no filter/format/lock |
| `sink/ConsoleSink.java` | Writes to stdout |
| `sink/FileSink.java` | Append-mode file sink; AutoCloseable; flush-per-write |
| `Destination.java` | Composes Formatter + Sink + minLevel + per-destination lock; failure isolation |
| `Logger.java` | Orchestrator + facade; injected Clock; immutable destinations list |
| `LoggerDriver.java` | 3 scenarios — filtering / failure isolation / 50-thread atomicity check (1000/1000) |

Run:
```bash
mvn -q compile exec:java \
  -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.logger.LoggerDriver
```
