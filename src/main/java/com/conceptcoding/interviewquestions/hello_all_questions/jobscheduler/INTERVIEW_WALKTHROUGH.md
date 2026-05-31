# Job Scheduler — 45-min LLD Interview Walkthrough

**Target role:** SDE‑2 (Amazon, Adobe, Microsoft, Atlassian, etc.)

> Job Scheduler is the **canonical "PriorityBlockingQueue + Strategy for ordering + worker pool" archetype** at SDE‑2 level. Four signals separate senior from mid: (a) **Strategy** for scheduling policy as a `Comparator<Job>` — swappable policy = swappable comparator, (b) **`PriorityBlockingQueue<Job>` built with that comparator** for thread-safe priority dequeue, (c) **`ScheduledExecutorService` for delayed enqueue** so workers don't peek-and-sleep, (d) **retry with exponential backoff** that re-arms the job back onto the same queue.

---

## Time budget (45 min)

| Step | Activity                                                                                | Budget   | Cumulative |
| ---- | --------------------------------------------------------------------------------------- | -------- | ---------- |
| 1    | Requirements                                                                            | ~5 min   | 5          |
| 2    | Entities & Relationships                                                                | ~4 min   | 9          |
| 3    | Class Design (Strategy + state machine on Job)                                          | ~10 min  | 19         |
| 4    | Implementation (`submit` w/ delayed enqueue + worker loop + retry + dry-run)            | ~17 min  | 36         |
| 5    | Extensibility (cron triggers, distributed scheduling, priority aging, job dependencies) | ~8 min   | 44         |
| —    | Wrap & questions                                                                        | ~1 min   | 45         |

Step 4 is the longest — worker loop + retry-with-backoff + delayed enqueue all converge there.

Watch the clock at minute **5** (Step 1 done), minute **19** (start coding), minute **36** (extensibility).

---

## Mental models — internalize these BEFORE you walk in

### M1. The submit-to-run pipeline

```
   sched.submit(Job(priority=5, scheduledAt=now+200ms, ...))
        |
        v
   +--------------------------------------------+
   | if scheduledAt <= now → readyQueue.offer    |
   | else → delayPool.schedule(() -> queue.offer)|  ← ScheduledExecutorService
   +--------------------------------------------+
        |
        v (later, when delay elapses)
   +--------------------------------------------+
   | PriorityBlockingQueue<Job>                  |  ← ordered by policy.comparator()
   |   ┌──────────────────────────────────────┐  |
   |   │ [highest priority] ... [lowest]      │  |
   |   └──────────────────────────────────────┘  |
   +--------------------------------------------+
        |
        v
   +--------------------------------------------+
   | N worker threads loop on queue.take()      |
   |   take()   ← BLOCKS until job available    |
   |   runJob() ← inside try/catch              |
   |     status = RUNNING; fireStarted          |
   |     payload.run()                          |
   |     status = COMPLETED; fireCompleted      |
   |   catch: handleFailure(job, t)             |
   +--------------------------------------------+
        |
        v
   on failure → handleFailure:
     if canRetry → delayPool.schedule(() -> queue.offer) with exponential backoff
     else        → status = FAILED; fireFailed(willRetry=false)
```

**Senior soundbite (memorize):** *"Two executors: a fixed-size **worker pool** drains the ready queue, and a single-thread **ScheduledExecutorService** handles deferred enqueue. Workers never sleep, never peek-and-poll — they just `queue.take()` and block. The schedule pool puts the job into the queue at the right moment. Clean separation of 'when' and 'who'."*

### M2. Strategy as a Comparator — swappable in one line

```
   SchedulingPolicy.comparator() — the ENTIRE Strategy interface

   PriorityBlockingQueue<Job>(initialCapacity, policy.comparator())
                                                ↑
                                       passed at construction

   PriorityFirstPolicy:                  EarliestDeadlineFirstPolicy:
     Comparator<Job> c =                   Comparator<Job> c =
       comparingInt(Job::getPriority)         comparing(Job::getScheduledAt)
         .reversed()                          .thenComparing(Job::getCreatedAt);
         .thenComparing(Job::getCreatedAt);

   FifoPolicy:
     Comparator<Job> c = comparing(Job::getCreatedAt);


   Why TIE-BREAK by createdAt:
     PriorityQueue ordering with non-total Comparator → non-deterministic order
     for "equal" elements. Threads race the head. Adding createdAt as a tiebreak
     makes the order strictly defined; the same submission sequence always
     produces the same execution sequence.
```

### M3. Retry as re-enqueue, not as a loop inside the worker

```
   Wrong:                                  Right:
   -------                                 -------
   while (attempts < max) {                catch (Throwable t):
     try { payload.run(); break; }           if (canRetry):
     catch (Throwable t) {                     delayPool.schedule(
       sleep(backoff);                           () -> readyQueue.offer(job),
       attempts++;                               backoff.toMillis(), MILLISECONDS);
     }                                         status = RETRYING;
   }                                         else:
                                               status = FAILED;
                                                  
   Problem with the loop-in-worker:        Win for re-enqueue:
   - worker stuck in sleep — can't run     - worker is free to run other jobs
     other jobs while one is in backoff      during the backoff window
   - hard to cancel mid-retry              - cancellation just clears job from queue
   - difficult to add max-time policy      - backoff stays consistent with other
                                             scheduled work
```

> **The interview rule:** *retries are reschedules, not in-place loops.* Same principle as background timer queues in production systems (Kubernetes back-offs, AWS Step Functions retries, etc.).

---

## STEP 1 — Requirements (~5 min)

### What to say out loud (opener)
> "Job scheduler at SDE-2 level usually means: priority queue + worker pool + delayed execution + retry. Let me clarify whether we're building an in-process scheduler (like a thread pool with extras) or a distributed one (like Sidekiq / Celery)."

### Probe the 4 themes

| Theme               | Question to ask                                                                                              |
| ------------------- | ------------------------------------------------------------------------------------------------------------ |
| Primary capabilities| "Submit a job with priority + scheduledAt. Multiple scheduling policies (FIFO, priority, EDF)? Cancellation?" |
| Rules / completion  | "Jobs that fail can retry with exponential backoff up to maxAttempts? After exhausted → permanent FAILED?" |
| Error handling      | "Job exception caught at the worker boundary, never crashes the worker? Listener exception isolation?" |
| Concurrency         | "N worker threads sharing a single queue? Submit must be safe from many threads concurrently?" |
| Scope boundaries    | "Out: distributed across hosts, cron triggers, job dependencies, durable persistence. Confirm?" |

### What to write on the board

```
Functional Requirements
1. submit(Job) — returns jobId. Job has: name, payload (Runnable), priority, scheduledAt, RetryPolicy.
2. Scheduling policy is pluggable — at minimum: FIFO, PriorityFirst, EarliestDeadlineFirst.
3. Jobs with scheduledAt in the future are DEFERRED — only enqueued when their time comes.
4. Worker pool drains the queue concurrently — N workers configurable at construction.
5. On failure, job retries up to RetryPolicy.maxAttempts with exponential backoff.
6. Lifecycle observable via pluggable JobListener (Observer): onStarted / onCompleted / onFailed / onCancelled.
7. cancel(jobId) — removes from queue if still SCHEDULED. Running jobs not interruptible in v1.
8. Graceful shutdown — stop accepting work, drain or interrupt workers, wait.

Out of Scope
- Distributed scheduling across multiple processes (Redis-backed queue would be Step 5)
- Cron / recurring triggers (Step 5)
- Job dependencies / DAGs (Step 5)
- Durable persistence (in-memory only)
- Priority aging (preventing low-priority starvation)
- Resource-aware scheduling (memory / CPU quotas)
```

### Close the step
> "Two load-bearing requirements: the worker-pool + priority queue shape, and the retry-as-reschedule pattern. Those are where the senior signal lives."

---

## STEP 2 — Entities & Relationships (~4 min)

### What to say out loud
> "Five types: **JobScheduler** (orchestrator + facade — owns the queue, workers, and listeners), **Job** (mutable; owns state machine + attempt count + retry policy), **RetryPolicy** (immutable value object), **SchedulingPolicy** (Strategy interface; provides a Comparator), **JobListener** (Observer interface). Plus one enum **JobStatus** for the lifecycle."

### Why no `JobQueue` class
> "PriorityBlockingQueue<Job> from the standard library IS the queue. Wrapping it in our own class would just forward methods. The whole point is leveraging the JVM's concurrent collections."

### Why Job is mutable but RetryPolicy is a record
> "Job IS the state machine — status, attempt count, last error all evolve. Mutation is the point. RetryPolicy is a configuration snapshot: read by the scheduler on every failure to compute the next backoff. No reason to mutate it; making it a record + final fields closes off accidental changes."

### What to write on the board

```
Entities
- JobScheduler        (orchestrator + facade: submit, cancel, shutdown, addListener)
- Job                 (MUTABLE — status, attempts, lastError; everything else final)
- RetryPolicy         (immutable record — maxAttempts, initialBackoff, maxBackoff)
- SchedulingPolicy    (interface — Strategy; one method returning Comparator<Job>)
                      (impls: PriorityFirstPolicy, FifoPolicy, EarliestDeadlineFirstPolicy)
- JobListener         (interface — Observer; default methods for selective overrides)

Enums
- JobStatus           { SCHEDULED, RUNNING, RETRYING, COMPLETED, FAILED, CANCELLED }

NOT entities
- JobQueue            (PriorityBlockingQueue does it)
- Worker              (ExecutorService thread, no user-facing handle)
- Trigger             (Step 5 — cron / event-driven)

Relationships
- JobScheduler owns:
    PriorityBlockingQueue<Job>    readyQueue              (built with policy.comparator())
    ExecutorService                workers                 (fixed-size; drain loop)
    ScheduledExecutorService       delayPool               (deferred enqueue for future jobs)
    ConcurrentHashMap<String, Job> jobs                    (lifecycle store, lookup by id)
    CopyOnWriteArrayList<JobListener> listeners            (lock-free fan-out)
- Each Job carries its own RetryPolicy (snapshot at submit time)
- Listeners observe state-transition events
```

### Diagram — boxes and arrows

```
                  +-----------------------------------------+
                  |             JobScheduler                |   <- orchestrator + facade
                  | submit / cancel / addListener / shutdown|
                  +-----------------------------------------+
                       |        |          |          |
                  owns | (4 stores + workers)         |
                       v        v          v          v
       PriorityBlockingQueue<Job>     ExecutorService    ScheduledExecutorService
       readyQueue                     workers            delayPool
       (built with                    (N drain threads)  (1 thread, defers enqueue)
        policy.comparator())
                       |                  |
              ConcurrentHashMap<id, Job>  CopyOnWriteArrayList<JobListener>

  Strategy:                                  Observer:
  +--------------------+                     +--------------------+
  | SchedulingPolicy   |                     | JobListener        |
  +--------------------+                     +--------------------+
  | + comparator()     |                     | onStarted / onCompl|
  +--------------------+                     | onFailed / onCancel|
       ^         ^                           +--------------------+
       |         |
   PriorityFirst  EDF, FIFO, ...

  Job (mutable):
    SCHEDULED → RUNNING → COMPLETED
                      ╰→ RETRYING → SCHEDULED (loop until maxAttempts)
                      ╰→ FAILED
    SCHEDULED → CANCELLED
```

---

## STEP 3 — Class Design (~10 min)

### JobScheduler — state ↔ requirement table

| Requirement                              | State JobScheduler must own                              |
| ---------------------------------------- | -------------------------------------------------------- |
| Priority-ordered ready queue             | `PriorityBlockingQueue<Job> readyQueue`                  |
| Worker pool                              | `ExecutorService workers`                                |
| Deferred enqueue for future jobs         | `ScheduledExecutorService delayPool`                     |
| Lookup by id                             | `ConcurrentHashMap<String, Job> jobs`                    |
| Observer fan-out                         | `CopyOnWriteArrayList<JobListener> listeners`            |
| Time injection                           | `Clock clock`                                            |

### JobScheduler — class outline (write on the board)

```java
public class JobScheduler {
    private final PriorityBlockingQueue<Job> readyQueue;
    private final ExecutorService            workers;
    private final ScheduledExecutorService   delayPool;
    private final ConcurrentHashMap<String, Job> jobs       = new ConcurrentHashMap<>();
    private final List<JobListener>          listeners      = new CopyOnWriteArrayList<>();
    private final Clock                      clock;
    private volatile boolean                  running       = true;

    public JobScheduler(SchedulingPolicy policy, int workerCount, Clock clock) {
        this.clock      = clock;
        this.readyQueue = new PriorityBlockingQueue<>(11, policy.comparator());
        this.workers    = Executors.newFixedThreadPool(workerCount);
        this.delayPool  = Executors.newScheduledThreadPool(1);
        for (int i = 0; i < workerCount; i++) workers.submit(this::workerLoop);
    }

    public String  submit(Job job);
    public boolean cancel(String jobId);
    public Job     getJob(String jobId);
    public void    addListener(JobListener l);
    public void    shutdown(Duration timeout) throws InterruptedException;
}
```

### Strategy — `SchedulingPolicy`

```java
public interface SchedulingPolicy {
    Comparator<Job> comparator();
}

public class PriorityFirstPolicy implements SchedulingPolicy {
    public Comparator<Job> comparator() {
        return Comparator.comparingInt(Job::getPriority).reversed()
               .thenComparing(Job::getCreatedAt);
    }
}

public class EarliestDeadlineFirstPolicy implements SchedulingPolicy { ... }
public class FifoPolicy implements SchedulingPolicy {
    public Comparator<Job> comparator() {
        return Comparator.comparing(Job::getCreatedAt);
    }
}
```

> **Why always `thenComparing(createdAt)`?** Without a deterministic tiebreak, PriorityQueue's head among equal elements is unspecified — different threads can see different "next" jobs. The createdAt tiebreak makes ordering reproducible.

### Job — mutable state machine

```java
public class Job {
    private final String id, name;
    private final Runnable payload;
    private final int priority;
    private final Instant scheduledAt, createdAt;
    private final RetryPolicy retryPolicy;

    private volatile JobStatus status = JobStatus.SCHEDULED;
    private final AtomicInteger attempts = new AtomicInteger(0);
    private volatile String lastError;

    // ctor + getters + setStatus + incrementAttempts + setLastError + canRetry()
    public boolean canRetry() { return attempts.get() < retryPolicy.maxAttempts(); }
}
```

### RetryPolicy — immutable + helper

```java
public record RetryPolicy(int maxAttempts, Duration initialBackoff, Duration maxBackoff) {
    public Duration backoffFor(int retryNumber) {
        long ms = initialBackoff.toMillis() * (1L << (retryNumber - 1));
        return Duration.ofMillis(Math.min(ms, maxBackoff.toMillis()));
    }
    public static RetryPolicy noRetries()        { return new RetryPolicy(1, ZERO, ZERO); }
    public static RetryPolicy defaultPolicy()    { return new RetryPolicy(3, ms(100), s(10)); }
}
```

### JobListener — Observer with default methods

```java
public interface JobListener {
    default void onStarted(Job job)                                { }
    default void onCompleted(Job job)                              { }
    default void onFailed(Job job, Throwable error, boolean willRetry) { }
    default void onCancelled(Job job)                              { }
}
```

> **Why `default` methods?** Subscribers usually care about ONE or TWO events. Default no-op implementations let them implement only what matters — e.g., a `MetricsListener` overrides only `onCompleted` and `onFailed`.

### The principle to verbalize — Strategy + Observer + Information Expert
> "Strategy lets scheduling policy vary at construction — three impls today, more if needed. Observer lets multiple subscribers (metrics, audit, alerting) observe lifecycle events without the scheduler knowing what they do. Job owns its own state machine — only Job knows its current status, attempt count, last error. The scheduler just calls `job.setStatus(next)` and lets the job handle it."

---

## STEP 4 — Implementation (~17 min)

### Open by asking
> "Real Java or pseudo-code? I'll do `submit` first — it shows the deferred-enqueue trick — then the `workerLoop` + `runJob` + `handleFailure` triad, then dry-run the priority-ordering scenario."

### 4.1 `submit` — immediate vs deferred enqueue

```java
public String submit(Job job) {
    jobs.put(job.getId(), job);
    long delayMs = Duration.between(clock.instant(), job.getScheduledAt()).toMillis();
    if (delayMs <= 0) {
        readyQueue.offer(job);              // ready now
    } else {
        delayPool.schedule(() -> {
            if (job.getStatus() == JobStatus.SCHEDULED) {
                readyQueue.offer(job);
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }
    return job.getId();
}
```

**Three callouts:**

1. *"Two paths: immediate (offer to queue now) and deferred (schedule offer for later). The `delayPool` is a SINGLE-thread ScheduledExecutorService — its job is just to call `queue.offer` at the right time. Workers don't peek-and-sleep; they `queue.take()` and block."*

2. *"Re-check status inside the deferred lambda — could've been cancelled between submit and the scheduled enqueue. Without this guard, a cancelled job lands in the queue and runs."*

3. *"`jobs.put` happens FIRST so cancel() can find the job by id even while it's still in delayPool. ConcurrentHashMap means the put is safe under concurrent submits."*

### 4.2 `workerLoop` + `runJob` — the worker contract

```java
private void workerLoop() {
    while (running) {
        try {
            Job job = readyQueue.take();    // BLOCKS until something is in the queue
            runJob(job);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } catch (Throwable t) {
            // Defense in depth — the worker loop must NEVER die.
            System.err.println("worker loop swallowed: " + t.getMessage());
        }
    }
}

private void runJob(Job job) {
    synchronized (job) {
        if (job.getStatus() == JobStatus.CANCELLED) return;
        job.setStatus(JobStatus.RUNNING);
    }
    job.incrementAttempts();
    fireStarted(job);
    try {
        job.getPayload().run();
        job.setStatus(JobStatus.COMPLETED);
        fireCompleted(job);
    } catch (Throwable t) {
        handleFailure(job, t);
    }
}
```

**Three callouts:**

1. *"`queue.take()` blocks — that's how workers stay idle without burning CPU. When a job arrives, exactly one worker wakes (the queue's per-thread signal is one-to-one)."*

2. *"`synchronized(job)` for the cancellation check. Without it, cancel() and runJob could race — a job marked CANCELLED a millisecond after status check would still run. The lock makes the check-and-transition atomic."*

3. *"The worker loop has TWO levels of defense: the InterruptedException-handling for graceful shutdown, and a Throwable catch as a last-resort 'never die' guard. Workers staying alive is the prerequisite for any other correctness property."*

### 4.3 `handleFailure` — retry as re-enqueue

```java
private void handleFailure(Job job, Throwable t) {
    job.setLastError(t.getMessage());
    boolean willRetry = job.canRetry();
    if (willRetry) {
        job.setStatus(JobStatus.RETRYING);
        fireFailed(job, t, true);
        Duration backoff = job.getRetryPolicy().backoffFor(job.getAttempts());
        delayPool.schedule(() -> {
            synchronized (job) {
                if (job.getStatus() == JobStatus.CANCELLED) return;
                job.setStatus(JobStatus.SCHEDULED);
            }
            readyQueue.offer(job);
        }, backoff.toMillis(), TimeUnit.MILLISECONDS);
    } else {
        job.setStatus(JobStatus.FAILED);
        fireFailed(job, t, false);
    }
}
```

> **Senior callout:** *"Retry is a RE-ENQUEUE, not a sleep-in-place. The worker that hit the failure is immediately freed to pick up another job. The delayPool will put this job back on the ready queue when the backoff elapses. This is how production schedulers work — Kubernetes back-offs, AWS Step Functions retries — they ALL use re-enqueue."*

### 4.4 `cancel` — pre-run cancellation

```java
public boolean cancel(String jobId) {
    Job job = jobs.get(jobId);
    if (job == null) return false;
    synchronized (job) {
        if (job.getStatus().isTerminal())       return false;
        if (job.getStatus() == JobStatus.RUNNING) return false;     // already running
        job.setStatus(JobStatus.CANCELLED);
        readyQueue.remove(job);     // O(n) but cancellation is rare
        fireCancelled(job);
        return true;
    }
}
```

> *"Cancellation only works pre-run. Mid-run cancellation requires Thread.interrupt() and the payload to be interrupt-aware — a separate design decision. For v1, 'cancel only if still SCHEDULED' is the honest behavior."*

### 4.5 `shutdown` — graceful drain

```java
public void shutdown(Duration timeout) throws InterruptedException {
    running = false;
    workers.shutdownNow();      // interrupts queue.take() blocks
    delayPool.shutdownNow();
    workers.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
    delayPool.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
}
```

### 4.6 Verification — dry-run priority ordering

```
Setup: 1 worker, PriorityFirstPolicy. We want to verify execution order is by descending priority
       — but we have to ensure all 5 jobs are queued BEFORE the worker picks the first one.

Step 1: Submit a "primer" job with priority MAX_VALUE that awaits a CountDownLatch.
   The single worker dequeues the primer, calls payload.run() → block on latch.

Step 2: Submit 5 priority jobs in non-priority order: [3, 1, 5, 2, 4].
   Worker is still busy with primer; jobs pile up in the priority queue.

Step 3: Brief sleep (50ms) to ensure all 5 are in the queue.

Step 4: latch.countDown() — primer's payload returns.
   Worker loops back to queue.take(). Queue contains 5 jobs ordered by priority:
     head: 5, then 4, 3, 2, 1.

Step 5: Worker drains in head-order. Execution order recorded into a list:
   [5, 4, 3, 2, 1]   ✓


Why the primer pattern matters:
   Without it, the first arriving job (3) is picked immediately by the idle worker.
   By the time job 5 arrives, the worker is already busy with 3. PriorityQueue ordering
   ONLY applies to jobs sitting in the queue at the same time — not across time.
   This is a real-world scheduling property, not a bug in the queue.
```

> **This dry-run insight is the senior signal.** *"Priority ordering applies among queued jobs, not across time. To test ordering deterministically, you need a primer that holds the worker while jobs queue up. In production, this same property means high-priority jobs only beat low-priority jobs that are still in the queue when they arrive."*

### 4.7 Verification — retry exhaustion

```
Setup: 1 worker. Job that always throws. RetryPolicy(maxAttempts=3, initialBackoff=50ms).

t=0    Worker takes job → runJob → attempts=1 → payload throws → handleFailure
       canRetry? attempts(1) < maxAttempts(3) → YES
       delayPool.schedule(re-offer, 50ms × 2^0 = 50ms)
       status = RETRYING; fireFailed(willRetry=true)

t=50ms delayPool fires → status = SCHEDULED; queue.offer
       Worker takes → attempts=2 → throws → handleFailure
       canRetry? attempts(2) < 3 → YES
       delayPool.schedule(re-offer, 50ms × 2^1 = 100ms)

t=150ms delayPool fires → queue.offer → Worker takes → attempts=3 → throws → handleFailure
        canRetry? attempts(3) < 3 → NO (3 < 3 is false)
        status = FAILED; fireFailed(willRetry=false)
        TERMINAL — no more re-schedules

Final: attempts=3, status=FAILED, lastError="boom"
```

---

## STEP 5 — Extensibility (~8 min)

### 5.1 "Cron / recurring triggers"

> **Problem in current design:** *"Jobs run once. Real schedulers support recurring patterns — every minute, every Sunday at 3am."*
>
> **Pattern as the fix:** *"Promote 'trigger' to an entity. `Trigger` interface with `Instant nextRunTime(Instant after)`. Implementations: `OneTimeTrigger` (today's behavior), `CronTrigger`, `IntervalTrigger`. When a recurring job completes, the scheduler asks `trigger.nextRunTime(now)` and re-schedules. Quartz / Sidekiq use this pattern."*

### 5.2 "Distributed scheduling (multiple processes)"

> **Problem in current design:** *"Single-process queue means scheduler restart loses pending jobs, and one process can't share work with another."*
>
> **Pattern as the fix:** *"Replace the in-memory PriorityBlockingQueue with a Redis sorted set (ZADD score=priority/scheduledAt). Workers across processes do ZPOPMIN. Idempotency: use a Redis lock to claim a job before running. Same approach as Sidekiq's `enterprise` SLA features."*

### 5.3 "Priority aging — low-priority jobs starve under load"

> **Problem in current design:** *"With constant high-priority traffic, low-priority jobs never run."*
>
> **Pattern as the fix:** *"Periodically bump every queued job's priority by 1 if it's been waiting more than N seconds. Implement as a separate periodic task on the delayPool that scans the queue. Or: build aging into the comparator — `priority + (now - createdAt) / agingFactor`. Tradeoff: queue ordering invariant changes over time (in-place mutation breaks PriorityQueue contract); the aging-by-comparator approach requires removing-and-reinserting."*

### 5.4 "Job dependencies / DAGs"

> **Problem in current design:** *"Job A depends on Job B's output — no way to express 'don't run A until B completes'."*
>
> **Pattern as the fix:** *"Add `dependsOn: Set<String>` field on Job. Don't put the job in the ready queue until all dependencies are COMPLETED. Listener triggers re-evaluation on each completion. For full DAG support, use Kahn's algorithm at submit time to detect cycles."*

### 5.5 Other "what-if" answers

| Follow-up                                  | Answer                                                                                              |
| ------------------------------------------ | --------------------------------------------------------------------------------------------------- |
| "Cancellation mid-run"                     | Pass a `volatile boolean cancelled` flag into the payload; payloads check it cooperatively. Or use `Thread.interrupt()` + interruptible payloads. |
| "Resource-aware scheduling (memory/CPU)"   | Add `requirements` (memory, CPU) to Job. Worker has `capacity`. Only dequeue if worker has capacity left. |
| "Pause / resume the scheduler"             | Add `paused` flag; workers check before `queue.take()`. Or temporarily redirect to a holding queue. |
| "Persistence — survive restart"            | Inject `JobRepository`; persist job state on every transition; replay SCHEDULED + RETRYING jobs on boot. |
| "Metrics — throughput, latency"            | Listener already exists — add `MetricsListener` that records timing per job; export to Prometheus. |
| "Priority levels with reserved workers"    | Two worker pools — high-priority pool + general pool — each draining its own queue.                 |

---

## Design Patterns — Hello Interview's canonical 8

> **Two patterns earn rent in the base:**
> - **Strategy (#1)** for SchedulingPolicy — justified by R2's policy enumeration.
> - **Observer (#2)** for JobListener — justified by R6's pluggable lifecycle observers.

### How this maps to JobScheduler specifically

**Already in the BASE design — call out by name:**

- **Strategy (#1)** ⭐ — `SchedulingPolicy.comparator()` provides the PriorityBlockingQueue's ordering. Name it in Step 2.
- **Observer (#2)** — `JobListener` with default methods; lock-free CopyOnWriteArrayList. Name it in Step 2.
- **State Machine (#3) — lite** — Job's state transitions are governed by setStatus calls. **Draw the state diagram in Step 3.**
- **Facade (#8)** — JobScheduler hides the queue, workers, delayPool, and listeners behind 5 public methods.
- **Information Expert** (GRASP) — Job owns status/attempts/lastError; scheduler doesn't reach into them.

**Reach for these on Step-5 follow-ups:**

| Follow-up                                  | Pattern (HI's 8)             | Your line                                                                                            |
| ------------------------------------------ | ---------------------------- | ---------------------------------------------------------------------------------------------------- |
| "Cron / recurring triggers"                | **Strategy (#1)** ⭐         | *"`Trigger` interface — `OneTimeTrigger`, `CronTrigger`, `IntervalTrigger`. Same axis as SchedulingPolicy but for time."* |
| "Different retry strategies"               | **Strategy (#1)** ⭐         | *"`RetryStrategy` interface — exponential, linear, fixed-delay. Wrap or replace RetryPolicy."*       |
| "Metrics / audit"                          | **Observer (#2)**            | *"Add MetricsListener / AuditListener — Observer is already in place."*                              |
| "Job dependencies / DAGs"                  | (Topological sort)           | *"Add `dependsOn` to Job; only enqueue when all deps COMPLETED. Listener triggers re-eval."*        |
| "Distributed across hosts"                 | (Redis-backed queue)         | *"Replace PriorityBlockingQueue with a Redis sorted set; workers ZPOPMIN. Same Strategy/Observer."* |

**Patterns to actively refuse:**

- **Singleton on JobScheduler** — kills tests; DI a single instance.
- **Builder for the 3-arg ctor** — academic noise.
- **Full GoF State pattern** (one class per JobStatus) — overkill for 6 states with no per-state methods. Enum + `setStatus` is correct.
- **Visitor over Job** — wrong shape; the scheduler doesn't dispatch on job subtypes.

### One sentence to say at the end of Step 3

> *"The base design names two patterns out loud — Strategy on SchedulingPolicy, Observer on JobListener — plus Facade on JobScheduler and State Machine on Job as the principles. Cron triggers, retry-strategy variants, distributed queues, and job dependencies all land in Step 5 as natural extensions."*

---

## Interview deep-dives — the questions you'll definitely get asked

### 1. Complexity (Big-O)

Let `Q` = jobs currently in the ready queue, `W` = worker count, `L` = listener count.

| Operation                                | Time                                              | Space               | Notes                                                                              |
| ---------------------------------------- | ------------------------------------------------- | ------------------- | ---------------------------------------------------------------------------------- |
| `submit(job)` — immediate                | **`O(log Q)`**                                    | O(1) per call       | PriorityBlockingQueue offer is heap insert                                         |
| `submit(job)` — deferred                 | **`O(1)`** to schedule + later `O(log Q)` enqueue | O(1) per call       | delayPool.schedule is constant                                                     |
| `queue.take()` (worker hot path)         | **`O(log Q)`** for poll-and-rebalance              | -                    | Heap extract-min                                                                    |
| `runJob` — happy path                    | O(L) listeners + payload cost                     | O(1)                | Payload dominates                                                                  |
| `cancel(jobId)`                          | **`O(Q)`** (queue.remove is linear)               | O(1)                | Rare operation; lookup by id is O(1) but queue.remove is linear                   |
| Storage — jobs map                       | -                                                 | **`O(N)`**          | Lifetime job count (could TTL-evict for memory)                                   |
| Storage — readyQueue                     | -                                                 | **`O(Q)`**          | Heap                                                                               |

> **Senior callout:** *"The hot path is `queue.take()` which is `O(log Q)`. With Q in the thousands, that's microseconds — payload cost dominates. The `O(Q)` cancellation is acceptable because cancel is rare and we trade it for `O(log Q)` submits. If cancellation becomes hot, add a secondary index `Map<JobId, Job>` and lazy-delete (skip cancelled jobs at `runJob` start) — what we do already."*

### 2. Concurrency / thread-safety

| Approach                                | When to use                                  | Cost                                                              |
| --------------------------------------- | -------------------------------------------- | ----------------------------------------------------------------- |
| **`PriorityBlockingQueue<Job>`** ⭐      | **Default.** Thread-safe priority dequeue.   | Single global lock inside the queue, but operations are short    |
| Per-priority sub-queues                  | High contention at very high throughput      | Workers round-robin across sub-queues; ordering across priorities harder |
| Disruptor-style ring buffer              | Extreme throughput (millions/sec)            | Significant complexity; only for low-latency trading-style systems |
| Redis sorted set (distributed)           | Cross-process scheduling                     | Network latency per operation                                    |

> **The deadlock-free guarantee:** *"We never hold two locks at once. The `synchronized(job)` blocks inside cancel/runJob are short — set status, return. The queue's own locking is internal. No nested locking, no acquisition ordering needed."*

### 3. Testing — what to write tests for

| Test category                | Cases to cover                                                                                              |
| ---------------------------- | ----------------------------------------------------------------------------------------------------------- |
| Priority ordering            | Primer pattern (4.6); verify [5,4,3,2,1] when submitted [3,1,5,2,4]                                        |
| EDF ordering                 | scheduledAt past, varied — earliest runs first                                                            |
| FIFO ordering                | createdAt ordering preserved                                                                                |
| Delayed scheduling           | scheduledAt = now+200ms → measure executedAt — submittedAt ≥ 200ms                                          |
| **Retry exhaustion**         | maxAttempts=3, payload throws → exactly 3 attempts, final status FAILED                                    |
| Retry success on attempt N   | Payload throws first 2 times, succeeds on 3rd → status COMPLETED                                            |
| Cancellation                 | cancel a SCHEDULED job → it never runs; cancel a RUNNING job → returns false                                |
| Listener observation         | Counter listener — onStarted = N, onCompleted = N for happy path                                            |
| Listener failure isolation   | Throwing listener doesn't break others                                                                      |
| **Concurrent submission**    | 50 threads × 1 job each → all 50 COMPLETED, listener count = 50                                            |
| Worker crash resilience      | Payload throwing OOM doesn't kill the worker; next job still runs                                          |

```java
@Test
void retry_exhaustion_3_attempts_then_failed() throws Exception {
    JobScheduler s = new JobScheduler(new PriorityFirstPolicy(), 1);
    AtomicInteger attempts = new AtomicInteger();
    Job j = new Job("fail", () -> {
        attempts.incrementAndGet();
        throw new RuntimeException("boom");
    }, 0, Instant.now(),
       new RetryPolicy(3, Duration.ofMillis(50), Duration.ofSeconds(1)),
       Instant.now());
    s.submit(j);

    long deadline = System.currentTimeMillis() + 2000;
    while (!j.getStatus().isTerminal() && System.currentTimeMillis() < deadline) Thread.sleep(20);

    assertEquals(3, attempts.get());
    assertEquals(JobStatus.FAILED, j.getStatus());
    s.shutdown(Duration.ofSeconds(1));
}
```

### 4. SOLID mapping

| Letter                       | Where it shows up                                                                                          |
| ---------------------------- | ---------------------------------------------------------------------------------------------------------- |
| **S** Single Responsibility  | Scheduler = orchestration. Job = state machine. RetryPolicy = backoff math. SchedulingPolicy = ordering. JobListener = observation. Five reasons to change, five types. |
| **O** Open/Closed            | New policy = new SchedulingPolicy class + one constructor arg. New listener = new class + addListener. Scheduler unchanged. |
| **L** Liskov Substitution    | Any SchedulingPolicy substitutable behind the interface — same Comparator contract. New ones must produce a total ordering. |
| **I** Interface Segregation  | SchedulingPolicy has ONE method. JobListener has FOUR but all are `default` — implementers override what they need. |
| **D** Dependency Inversion   | Scheduler depends on SchedulingPolicy + JobListener interfaces. Concrete impls wired at composition time. Clock injected for deterministic time. |

### 5. "Summarize your design in 30 seconds"

> *"JobScheduler is the orchestrator + facade. It owns a `PriorityBlockingQueue<Job>` built with a Comparator provided by an injected SchedulingPolicy (Strategy) — PriorityFirst, FIFO, EarliestDeadlineFirst are the three I'd ship. A fixed-size worker pool drains the queue via `queue.take()` — blocks until work arrives, no peek-and-sleep. A separate single-thread ScheduledExecutorService handles DEFERRED ENQUEUE for jobs scheduled in the future — at the delay, it calls `queue.offer`. Retry is a RE-ENQUEUE, not a sleep-in-place — failed jobs go back into the same delayPool with exponential backoff, freeing the worker immediately. Job is the state machine — SCHEDULED → RUNNING → COMPLETED or RETRYING → SCHEDULED loop or FAILED. Failure isolation at TWO layers — payload exception is caught at the worker boundary (worker never dies), listener exceptions are caught individually (one bad listener doesn't break others). Listeners stored in a CopyOnWriteArrayList for lock-free fan-out. The empirical priority-ordering test uses a primer job that holds the single worker while 5 priority jobs queue up, then releases — execution order is [5,4,3,2,1] from a submission order of [3,1,5,2,4]."*

That's ~55 seconds. Hits: Strategy + worker pool + delayPool + retry-as-reschedule + state machine + the primer-pattern insight.

---

## Closing soundbites (memorize these)

- **Opening:** *"In-process scheduler or distributed? In-process for v1; distributed is a Step-5 swap of the queue."*
- **Why PriorityBlockingQueue:** *"Thread-safe priority dequeue out of the JDK. Built with the SchedulingPolicy's comparator at construction — one line of code, full thread safety."*
- **Why TWO executors:** *"Workers `queue.take()` — block until work arrives. The delayPool is for `queue.offer(...)` at a future time. Clean separation: workers don't peek-and-sleep, the delayPool doesn't run job code."*
- **Why retry is re-enqueue:** *"Sleeping in the worker means the worker is wasted during backoff. Re-enqueueing returns the worker to the pool immediately and re-arms the job for later. Same pattern as Kubernetes back-offs."*
- **Why state machine on Job:** *"Information Expert — only Job knows its current status, attempt count, last error. Scheduler tells Job 'set status to X'; the state machine guards the transition."*
- **Why createdAt tiebreak:** *"Without a total order in the comparator, PriorityQueue can return any of the 'equal' jobs — non-deterministic. createdAt tiebreak makes ordering reproducible."*
- **On the primer-pattern test insight:** *"Priority ordering only applies among jobs already in the queue. To test deterministically, hold the worker with a primer while 5 jobs queue, then release."*

---

## Top mistakes that lose points

- **Worker `Thread.sleep(backoff)` for retry** — wastes the worker during backoff. Re-enqueue via delayPool.
- **Catching `Exception` not `Throwable` at the worker boundary** — OOM kills the worker; next job never runs.
- **No tie-break in the Comparator** — PriorityQueue returns equal elements non-deterministically; tests flake.
- **Single ExecutorService for both workers and deferred enqueue** — they have different lifecycles and different blocking patterns; keep them separate.
- **Calling `payload.run()` while holding the job's lock** — payload duration is unbounded; locks must be brief. Set status under the lock; release before running.
- **Forgetting to re-check status inside the delayed lambda** — cancellation between submit and enqueue is silently ignored.
- **Promoting `JobQueue` to a class** — PriorityBlockingQueue is the queue. Wrapping is ceremony.
- **State machine on the scheduler, not on Job** — violates Information Expert; scheduler ends up with `if (status == ...)` checks scattered everywhere.
- **No CountDownLatch in concurrent tests** — `Thread.sleep(500)` is a flake. Use latches for "done" detection.
- **Skipping the primer-pattern dry-run** — without it, ordering tests are non-deterministic; reviewer will catch this.

---

## Files in this folder (your reference implementation)

| File                                                      | What it shows                                                                            |
| --------------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| `model/JobStatus.java`                                    | Enum — 6 states + `isTerminal()`                                                         |
| `model/RetryPolicy.java`                                  | Immutable record — `backoffFor(retryNumber)` exponential with cap                        |
| `model/Job.java`                                          | Mutable state machine — volatile status, AtomicInteger attempts, lastError              |
| `policy/SchedulingPolicy.java`                            | Strategy interface — one method returning `Comparator<Job>`                             |
| `policy/PriorityFirstPolicy.java` / `FifoPolicy.java` / `EarliestDeadlineFirstPolicy.java` | Concrete strategies w/ createdAt tiebreak |
| `listener/JobListener.java`                               | Observer with default no-op methods                                                      |
| `JobScheduler.java`                                       | **The hot class** — PriorityBlockingQueue + worker pool + delayPool + retry-as-reschedule |
| `JobSchedulerDriver.java`                                 | 6 scenarios — priority ordering (primer pattern) + EDF + delay + retry exhaustion + listener + **50-thread concurrent burst** |

Run from the project root:

```bash
mvn -q compile exec:java \
  -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.jobscheduler.JobSchedulerDriver
```
