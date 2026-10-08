# Job Scheduler

> **Why it's in the deck:** the cleanest **producer–consumer** problem (queue + worker pool), with a swappable ordering policy, delayed jobs and retries. Microsoft asks it; it also comes up as "design a task queue / cron service". The same machinery powers [Download Manager](../../state_machines/downloadmanager/INTERVIEW_WALKTHROUGH.md) and async [Logger](../logger/INTERVIEW_WALKTHROUGH.md).
>
> **The crux (what's really being tested):**
> 1. **Producer–consumer:** `PriorityBlockingQueue` + N workers blocking on `take()`, with no busy-waiting.
> 2. **Ordering as a Strategy:** priority / FIFO / earliest-deadline = a different `Comparator`.
> 3. **Time and failure:** future jobs via a scheduled executor; retries with exponential back-off; cancel safely between attempts.
>
> **Family:** F4 Pipeline (producer–consumer) + F5 Strategy. See [foundations](../../00_AMAZON_LLD_FOUNDATIONS.md).

---

## 1. Plain-language picture

### A restaurant kitchen
```
Waiters (any thread)   → submit(job)      → order slips go on the rail, most urgent first
Cooks (N workers)      → take() a slip    → cook it → done (or burnt: try again later)
Timed orders ("serve at 8 PM")           → a timer holds them and puts them on the rail at 8
```
- Cooks with nothing to do **wait** (`take()` blocks); they don't keep asking "anything yet?".
- The rail's order is a **policy**: highest priority first, or oldest first, or earliest deadline first. Change the policy = change the sorting rule.

### Each job's life
```
SCHEDULED ──worker takes it──→ RUNNING ──ok──→ COMPLETED
    │                            │
    │ cancel                     └──throws──→ RETRYING ──(after back-off)──→ SCHEDULED → RUNNING ...
    ↓                                              └── out of attempts ──→ FAILED
CANCELLED  (also allowed while RETRYING, between attempts)
```

### Retries: back off
Attempt 1 fails → wait 100 ms → attempt 2 fails → wait 200 ms → attempt 3. The waits double (**exponential back-off**) so a struggling database isn't hammered. `maxAttempts = 3` means 3 runs total.

---

## 2. From story to design

| # | Decision | Rejected | Why |
|---|---|---|---|
| D1 | `PriorityBlockingQueue<Job>` + fixed worker pool, each worker loops on `take()` | polling with `sleep`; a thread per job | No busy-waiting, bounded parallelism, thread-safe ordering for free. |
| D2 | `SchedulingPolicy` gives the queue's `Comparator` (Strategy) | `if (policy == PRIORITY)` in the worker | A new policy = one class; the scheduler doesn't change. |
| D3 | Comparators always **break ties** (by `createdAt`) | priority only | A total order makes equal-priority jobs run FIFO, deterministically. |
| D4 | Future jobs: a `ScheduledExecutorService` puts them on the queue **at their time** | the worker peeks and sleeps until the head is due | A worker sleeping on the head job would block other jobs that are ready now. |
| D5 | Retry = re-schedule via the same delay pool, after `backoffFor(attempt)` | sleeping inside the worker | Never ties up a worker while waiting. |
| D6 | Status changes for cancel/run under `synchronized (job)`; re-check CANCELLED before running and before re-queueing | an unsynchronized status field only | Cancel between dequeue and start, or between retries, must win cleanly. |
| D7 | A running job can't be cancelled in the base (no interrupt) | `Thread.interrupt()` | Interrupting arbitrary user code is unsafe; a follow-up covers cooperative cancel. |
| D8 | `JobListener` (Observer) with default methods, each call isolated | callbacks in the job | Metrics/alerts plug in; a broken listener can't kill a worker. |
| D9 | `catch (Exception)` at the job boundary | `catch (Throwable)` | A job's failure is isolated; JVM errors aren't swallowed. |

### Class shape
```
JobScheduler                                   ← submit · cancel · getJob · addListener · shutdown
  PriorityBlockingQueue<Job> (policy's comparator) · worker pool (N) · ScheduledExecutorService (delays/retries)
  workerLoop: take() → runJob: lock → not cancelled? RUNNING → payload.run() → COMPLETED | handleFailure

Job { id, name, Runnable payload, priority, scheduledAt, createdAt, RetryPolicy, status, attempts, lastError }
JobStatus { SCHEDULED, RUNNING, RETRYING, COMPLETED, FAILED, CANCELLED } + isTerminal()
RetryPolicy { maxAttempts, initialBackoff, maxBackoff }  backoffFor(attempt)

«interface» SchedulingPolicy  comparator()
  ├── PriorityFirstPolicy (priority desc, then createdAt)
  ├── FifoPolicy
  └── EarliestDeadlineFirstPolicy
«interface» JobListener  onStarted · onCompleted · onFailed(willRetry) · onCancelled   (Observer)
```

---

## 3. Patterns that earn their place

| Pattern | In the base? | Problem it solves | Without it |
|---|---|---|---|
| **Producer–consumer** | **Yes** | decouple submitting from running; bounded workers | threads per job, or busy polling |
| **Strategy** (`SchedulingPolicy`) | **Yes** | interchangeable ordering rules | ordering `if`s in the worker loop |
| **Observer** (`JobListener`) | **Yes** | metrics, alerting, audit without touching the scheduler | callbacks hard-coded into the run loop |
| **Command** | Implicit | a job wraps an action (`Runnable`) that can be queued, retried, cancelled | — |

**Say:** *"Producer–consumer with a PriorityBlockingQueue whose comparator comes from a Strategy. Delayed jobs and retries go through a scheduled executor, so workers never sleep. Listeners are an Observer."*

**Tempting but wrong:** a Singleton scheduler (tests need fresh ones); the State pattern for job status (an enum + guarded transitions is enough); a `Thread.sleep` loop to wait for due jobs.

---

## 4. The 45-minute run

### Clarify (min 0–5)
> **You:** Callers submit jobs (a piece of code) to run now or at a future time, with a priority. N workers run them?
> **Interviewer:** Yes.
> **You:** Order by priority? Should that be swappable (FIFO, deadline)?
> **Interviewer:** Priority for now, swappable is good.
> **You:** If a job throws, retry a few times with back-off?
> **Interviewer:** Yes, configurable per job.
> **You:** Cancel: only before it starts?
> **Interviewer:** Yes. Running jobs are a follow-up.
> **You:** Notifications when jobs finish or fail?
> **Interviewer:** Nice to have.

```
In scope:  submit(job) now or later · N workers · ordering policy (priority / FIFO / EDF)
           retries with exponential back-off · cancel before running / between retries · listeners
Out:       recurring (cron) jobs, dependencies, timeouts, cancelling running jobs, persistence
```

### Timeline
| Min | Do |
|---|---|
| 5–9 | Draw the kitchen: queue, workers, timer. State diagram. |
| 9–14 | Code step 1: `JobStatus`, `RetryPolicy`, `Job`. |
| 14–18 | Code step 2: `SchedulingPolicy` + `PriorityFirstPolicy`. |
| 18–32 | Code step 3: **`JobScheduler`**: constructor (queue + workers), `submit`, `workerLoop`, `runJob`, `handleFailure`, `cancel`. |
| 32–36 | Code step 4: `JobListener` + safe fan-out. |
| 36–40 | Dry run: a failing job with 3 attempts; cancel between retries. |
| 40–45 | Follow-ups. |

### The code you write, in this order

**Step 1: model** ([model/](model/))
```java
public enum JobStatus {
    SCHEDULED, RUNNING, RETRYING, COMPLETED, FAILED, CANCELLED;
    public boolean isTerminal() { return this == COMPLETED || this == FAILED || this == CANCELLED; }
}

public class RetryPolicy {                        // maxAttempts includes the first run
    private final int maxAttempts;
    private final Duration initialBackoff;
    private final Duration maxBackoff;
    // constructor validates; noRetries(), defaultPolicy()

    public Duration backoffFor(int attempt) {      // 100, 200, 400 ... capped
        long ms = initialBackoff.toMillis() * (1L << (attempt - 1));
        return Duration.ofMillis(Math.min(ms, maxBackoff.toMillis()));
    }
}

public class Job {
    private final String id = UUID.randomUUID().toString();
    private final String name;
    private final Runnable payload;
    private final int priority;
    private final Instant scheduledAt;
    private final Instant createdAt;
    private final RetryPolicy retryPolicy;
    private volatile JobStatus status = JobStatus.SCHEDULED;
    private final AtomicInteger attempts = new AtomicInteger();
    private volatile String lastError;

    public boolean canRetry() { return attempts.get() < retryPolicy.getMaxAttempts(); }
    // + constructor, getters, setStatus, incrementAttempts, setLastError
}
```

**Step 2: the ordering Strategy** ([policy/](policy/))
```java
public interface SchedulingPolicy {
    Comparator<Job> comparator();                  // next-to-run job compares smallest
}

public class PriorityFirstPolicy implements SchedulingPolicy {
    public Comparator<Job> comparator() {
        return Comparator.comparingInt(Job::getPriority).reversed()   // highest priority first
                         .thenComparing(Job::getCreatedAt);           // ties: oldest first
    }
}
// FifoPolicy: comparing(createdAt) · EarliestDeadlineFirstPolicy: comparing(scheduledAt).thenComparing(createdAt)
```

**Step 3: the scheduler, the crux** ([JobScheduler.java](JobScheduler.java))
```java
public class JobScheduler {
    private final PriorityBlockingQueue<Job> readyQueue;
    private final ExecutorService workers;
    private final ScheduledExecutorService delayPool;           // future jobs + retries
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final List<JobListener> listeners = new CopyOnWriteArrayList<>();
    private final Clock clock;
    private volatile boolean running = true;

    public JobScheduler(SchedulingPolicy policy, int workerCount, Clock clock) {
        this.clock = clock;
        this.readyQueue = new PriorityBlockingQueue<>(11, policy.comparator());
        this.workers = Executors.newFixedThreadPool(workerCount);
        this.delayPool = Executors.newScheduledThreadPool(1);
        for (int i = 0; i < workerCount; i++) workers.submit(this::workerLoop);
    }

    public String submit(Job job) {
        jobs.put(job.getId(), job);
        long delayMs = Duration.between(clock.instant(), job.getScheduledAt()).toMillis();
        if (delayMs <= 0) {
            readyQueue.offer(job);
        } else {
            delayPool.schedule(() -> {
                if (job.getStatus() == JobStatus.SCHEDULED) readyQueue.offer(job);   // cancelled meanwhile? skip
            }, delayMs, TimeUnit.MILLISECONDS);
        }
        return job.getId();
    }

    public boolean cancel(String jobId) {
        Job job = jobs.get(jobId);
        if (job == null) return false;
        synchronized (job) {
            if (job.getStatus().isTerminal() || job.getStatus() == JobStatus.RUNNING) return false;
            job.setStatus(JobStatus.CANCELLED);
            readyQueue.remove(job);
        }
        fireCancelled(job);
        return true;
    }

    private void workerLoop() {
        while (running) {
            try {
                runJob(readyQueue.take());                // blocks until a job is ready
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {                       // the loop itself must not die
                System.err.println("worker loop swallowed: " + e.getMessage());
            }
        }
    }

    private void runJob(Job job) {
        synchronized (job) {
            if (job.getStatus() == JobStatus.CANCELLED) return;     // cancelled after dequeue
            job.setStatus(JobStatus.RUNNING);
        }
        job.incrementAttempts();
        fireStarted(job);
        try {
            job.getPayload().run();
            job.setStatus(JobStatus.COMPLETED);
            fireCompleted(job);
        } catch (Exception e) {                           // the job's failure: isolate it
            handleFailure(job, e);
        }
    }

    private void handleFailure(Job job, Exception e) {
        job.setLastError(e.getMessage());
        if (job.canRetry()) {
            job.setStatus(JobStatus.RETRYING);
            fireFailed(job, e, true);
            delayPool.schedule(() -> {
                synchronized (job) {
                    if (job.getStatus() == JobStatus.CANCELLED) return;      // cancelled between attempts
                    job.setStatus(JobStatus.SCHEDULED);
                }
                readyQueue.offer(job);
            }, job.getRetryPolicy().backoffFor(job.getAttempts()).toMillis(), TimeUnit.MILLISECONDS);
        } else {
            job.setStatus(JobStatus.FAILED);
            fireFailed(job, e, false);
        }
    }
    // + shutdown (shutdownNow both pools), getJob, addListener
}
```
**Shape to remember:** worker = `take()` → lock: cancelled? skip : RUNNING → run → COMPLETED | (canRetry? RETRYING + schedule re-offer : FAILED).

**Step 4: listeners (Observer)** ([listener/JobListener.java](listener/JobListener.java))
```java
public interface JobListener {                      // default methods: implement only what you need
    default void onStarted(Job job) { }
    default void onCompleted(Job job) { }
    default void onFailed(Job job, Throwable error, boolean willRetry) { }
    default void onCancelled(Job job) { }
}

private void fireCompleted(Job job) { for (JobListener l : listeners) safe(() -> l.onCompleted(job)); }
private void safe(Runnable r) {
    try { r.run(); } catch (Exception e) { System.err.println("job listener threw — " + e.getMessage()); }
}
```

### Dry run (maxAttempts 3, back-off 100 ms)
```
submit(job) → due now → offer
worker: take → RUNNING, attempts 1 → throws → canRetry (1 < 3) → RETRYING → schedule re-offer in 100 ms
100 ms later: still not cancelled → SCHEDULED → offer → worker: RUNNING, attempts 2 → throws → retry in 200 ms
... attempts 3 → throws → canRetry (3 < 3)? no → FAILED, listeners told willRetry=false
cancel(job) during a back-off → status CANCELLED → the scheduled re-offer sees CANCELLED → does nothing
```

---

## 5. Follow-ups: answer out loud first, then open

<details>
<summary><b>Q1. Recurring jobs: "run this report every day at 2 AM".</b></summary>

On completion, compute the next run time (from a cron expression or a fixed interval) and submit a **new** occurrence of the same job. Don't use `scheduleAtFixedRate` for business jobs: you lose retries, cancellation and history. Store `lastRunAt` and `nextRunAt` so a restart doesn't skip or double-run (run missed occurrences once, or skip them; it's a product choice).
</details>

<details>
<summary><b>Q2. Dependencies: "load" may only run after "clean" and "enrich" finish.</b></summary>

Count the unfinished dependencies per job. Start jobs with zero; when a job finishes, decrement its dependents and start any that reach zero (Kahn's topological sort, done live):
```java
private final Map<String, List<String>> dependents = new ConcurrentHashMap<>();   // A → jobs waiting on A
private final Map<String, AtomicInteger> pending = new ConcurrentHashMap<>();     // job → unfinished deps

void runAll() throws InterruptedException {
    for (String job : work.keySet()) if (pending.get(job).get() == 0) submit(job);   // no deps: start now
    allDone.await();
}

private void submit(String job) {
    pool.submit(() -> {
        work.get(job).run();
        allDone.countDown();
        for (String next : dependents.getOrDefault(job, List.of())) {
            if (pending.get(next).decrementAndGet() == 0) submit(next);           // last dependency finished
        }
    });
}
// extract → (clean ∥ enrich) → load → report
```
Reject cycles at submit time (a DFS for back edges). If a dependency **fails**, mark its dependents as SKIPPED rather than leaving them waiting forever.
</details>

<details>
<summary><b>Q3. Low-priority jobs never run because high-priority ones keep arriving (starvation).</b></summary>

**Aging:** the longer a job waits, the higher its effective priority. **Trap:** don't put a time-dependent value in the `PriorityBlockingQueue` comparator. A heap assumes an element's order never changes after insertion, so changing priorities silently corrupt it. Instead: a periodic task removes jobs waiting longer than T and re-inserts them with a bumped priority; or use **multi-level queues** (high / normal / low) where workers take from low every Nth time.
</details>

<details>
<summary><b>Q4. A job hangs forever. Add timeouts, and cancel a running job.</b></summary>

Run the payload on a separate executor and wait with a limit: `Future<?> f = exec.submit(payload); f.get(timeout, ...)`. On timeout, `f.cancel(true)` interrupts the thread, which **only** works if the job's code checks `Thread.interrupted()` or uses interruptible calls (sleep, blocking I/O). Say it plainly: in Java, cancellation is **cooperative**. Mark the job TIMED_OUT and decide whether it counts as a retryable failure.
</details>

<details>
<summary><b>Q5. Run it on many machines without running a job twice.</b></summary>

Jobs live in a DB table; workers **claim** one with a row lock that others skip:
```sql
BEGIN;
SELECT id FROM job
WHERE status = 'SCHEDULED' AND run_at <= now()
ORDER BY priority DESC, created_at
LIMIT 1
FOR UPDATE SKIP LOCKED;                 -- other workers skip this row instead of waiting
UPDATE job SET status = 'RUNNING', lease_until = now() + interval '5 minutes', worker = ? WHERE id = ?;
COMMIT;
```
If a worker dies, its lease expires and another worker re-claims the job. That means a job **can** run twice (at-least-once), so jobs must be **idempotent** (use an idempotency key for side effects like payments).
</details>

<details>
<summary><b>Q6. Shut down without losing jobs.</b></summary>

Stop accepting new submissions, stop the delay pool, and let workers **finish their current job** (`shutdown()` + `awaitTermination`, not `shutdownNow()`) up to a deadline. Jobs still queued are persisted (or left in the DB, Q5) and picked up on restart. The current `shutdown(timeout)` interrupts immediately; say that a graceful version would drain first.
</details>

<details>
<summary><b>Q7. How would you know it's working in production?</b></summary>

- **Metrics:** queue depth per priority, wait time (submit → start), run time, success/failure/retry counts, workers busy.
- **Alarms:** queue depth growing (not enough workers), jobs stuck in RUNNING past their lease, a failure-rate spike for one job type.
- **Logs:** jobId + attempt + outcome + error on every run. The listeners are the natural place to emit these.
</details>

<details>
<summary><b>Q8. How do you test it?</b></summary>

Priority order with one worker (`[5,4,3,2,1]`), EDF order, a delayed job runs **after** its time, a failing job makes exactly `maxAttempts` attempts then FAILED, listener counts, and 50 concurrent submissions all complete. All are in the driver. Use a latch or a recording listener rather than sleeps to wait for completion.
</details>

---

## 6. Traps
1. Workers polling with `sleep` instead of blocking on `take()`.
2. Sleeping inside a worker for back-off or for future jobs (it blocks other ready jobs).
3. Comparators without a tie-break, or with time-dependent values.
4. Not re-checking CANCELLED before running and before re-queueing a retry.
5. `catch (Throwable)`; or no catch at all, so one bad job kills a worker.
6. Assuming a running job can be killed safely.

## 7. Recall check
1. Draw the producer–consumer picture. Which line makes idle workers wait?
2. Why a scheduled executor for future jobs instead of the worker sleeping?
3. maxAttempts 4, initial 100 ms: list the waits.
4. Where are the two "is it cancelled?" re-checks, and why both?
5. Why must a priority-queue comparator not depend on the current time?
6. Distributed: what does `FOR UPDATE SKIP LOCKED` do, and why must jobs be idempotent?

**Rebuild in 12 minutes:** `JobStatus` · `RetryPolicy.backoffFor` · `Job.canRetry` · `PriorityFirstPolicy` · `JobScheduler` (queue + pool, `submit`, `workerLoop`, `runJob`, `handleFailure`, `cancel`).

---

**Files:** `JobScheduler` · `model/` (`Job`, `JobStatus`, `RetryPolicy`) · `policy/` (`SchedulingPolicy`, `PriorityFirstPolicy`, `FifoPolicy`, `EarliestDeadlineFirstPolicy`) · `listener/JobListener` · `JobSchedulerDriver` (priority order, EDF order, delayed job, retry exhaustion, listeners, 50-thread submissions)
```bash
mvn -q compile exec:java -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.pipelines.jobscheduler.JobSchedulerDriver
```
