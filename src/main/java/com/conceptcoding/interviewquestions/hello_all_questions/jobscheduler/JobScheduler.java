package com.conceptcoding.interviewquestions.hello_all_questions.jobscheduler;

import com.conceptcoding.interviewquestions.hello_all_questions.jobscheduler.listener.JobListener;
import com.conceptcoding.interviewquestions.hello_all_questions.jobscheduler.model.Job;
import com.conceptcoding.interviewquestions.hello_all_questions.jobscheduler.model.JobStatus;
import com.conceptcoding.interviewquestions.hello_all_questions.jobscheduler.policy.SchedulingPolicy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Orchestrator + facade. Four load-bearing concerns:
 *
 * <ol>
 *   <li><b>Strategy</b> for ordering — a {@link SchedulingPolicy} provides the
 *       Comparator; the ready queue is a {@code PriorityBlockingQueue<Job>} built
 *       with that comparator. Swap policy = swap the comparator at construction.</li>
 *   <li><b>Worker pool</b> drains the queue concurrently — N workers, each blocks
 *       on {@code queue.take()}, runs the job, fires listeners.</li>
 *   <li><b>Deferred enqueue</b> for jobs scheduled in the future — a separate
 *       {@link ScheduledExecutorService} schedules the {@code queue.offer} so
 *       workers don't busy-wait or peek-and-sleep.</li>
 *   <li><b>Retry with exponential backoff</b> — on failure, re-schedule via the
 *       same delayPool. Retries respect {@link com.conceptcoding.interviewquestions.hello_all_questions.jobscheduler.model.RetryPolicy#maxAttempts}.</li>
 * </ol>
 *
 * <p>Failure isolation: a job's exception is caught at the worker boundary — it
 * never crashes the worker thread. Listener exceptions are caught around each
 * invocation — one bad listener can't take down others.
 */
public class JobScheduler {

    private final PriorityBlockingQueue<Job> readyQueue;
    private final ExecutorService            workers;
    private final ScheduledExecutorService   delayPool;
    private final ConcurrentHashMap<String, Job> jobs       = new ConcurrentHashMap<>();
    private final List<JobListener>          listeners      = new CopyOnWriteArrayList<>();
    private final Clock                      clock;
    private volatile boolean                  running       = true;

    public JobScheduler(SchedulingPolicy policy, int workerCount) {
        this(policy, workerCount, Clock.systemUTC());
    }

    public JobScheduler(SchedulingPolicy policy, int workerCount, Clock clock) {
        if (workerCount <= 0) throw new IllegalArgumentException("workerCount must be > 0");
        this.clock      = clock;
        this.readyQueue = new PriorityBlockingQueue<>(11, policy.comparator());
        this.workers    = Executors.newFixedThreadPool(workerCount, daemonThreadFactory("job-worker"));
        this.delayPool  = Executors.newScheduledThreadPool(1, daemonThreadFactory("job-delay"));
        for (int i = 0; i < workerCount; i++) {
            workers.submit(this::workerLoop);
        }
    }

    /** Submit a job. If {@code scheduledAt} is in the future, deferred via delayPool. */
    public String submit(Job job) {
        jobs.put(job.getId(), job);
        long delayMs = Duration.between(clock.instant(), job.getScheduledAt()).toMillis();
        if (delayMs <= 0) {
            readyQueue.offer(job);
        } else {
            delayPool.schedule(() -> {
                // Re-check status — could've been cancelled between submit and enqueue.
                if (job.getStatus() == JobStatus.SCHEDULED) {
                    readyQueue.offer(job);
                }
            }, delayMs, TimeUnit.MILLISECONDS);
        }
        return job.getId();
    }

    /** Cancel a job. Removes from the ready queue if still there. */
    public boolean cancel(String jobId) {
        Job job = jobs.get(jobId);
        if (job == null) return false;
        if (job.getStatus().isTerminal()) return false;
        synchronized (job) {
            if (job.getStatus().isTerminal()) return false;
            if (job.getStatus() == JobStatus.RUNNING) {
                // Already running — can't interrupt mid-execution in this design.
                return false;
            }
            job.setStatus(JobStatus.CANCELLED);
            readyQueue.remove(job);                  // O(n) but rare
            fireCancelled(job);
            return true;
        }
    }

    public Job getJob(String jobId) {
        return jobs.get(jobId);
    }

    public void addListener(JobListener listener)    { listeners.add(listener); }
    public void removeListener(JobListener listener) { listeners.remove(listener); }

    /** Graceful shutdown — stop accepting work, drain in-flight jobs, wait. */
    public void shutdown(Duration timeout) throws InterruptedException {
        running = false;
        workers.shutdownNow();    // interrupt blocked queue.take()
        delayPool.shutdownNow();
        workers.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
        delayPool.awaitTermination(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    // ----- internals -----

    private void workerLoop() {
        while (running) {
            try {
                Job job = readyQueue.take();        // blocks until a job is available
                runJob(job);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Throwable t) {
                // Defense in depth — workerLoop itself must NEVER die.
                System.err.println("worker loop swallowed: " + t.getMessage());
            }
        }
    }

    private void runJob(Job job) {
        // Skip if cancelled between dequeue and now.
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

    private void handleFailure(Job job, Throwable t) {
        job.setLastError(t.getMessage());
        boolean willRetry = job.canRetry();
        if (willRetry) {
            job.setStatus(JobStatus.RETRYING);
            fireFailed(job, t, true);
            Duration backoff = job.getRetryPolicy().backoffFor(job.getAttempts());
            delayPool.schedule(() -> {
                // Re-arm: back to SCHEDULED so cancellation between attempts works.
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

    // ----- listener fan-out (failure-isolated per listener) -----

    private void fireStarted(Job job) {
        for (JobListener l : listeners) safe(() -> l.onStarted(job));
    }
    private void fireCompleted(Job job) {
        for (JobListener l : listeners) safe(() -> l.onCompleted(job));
    }
    private void fireFailed(Job job, Throwable error, boolean willRetry) {
        for (JobListener l : listeners) safe(() -> l.onFailed(job, error, willRetry));
    }
    private void fireCancelled(Job job) {
        for (JobListener l : listeners) safe(() -> l.onCancelled(job));
    }
    private void safe(Runnable r) {
        try { r.run(); } catch (Throwable t) {
            System.err.println("job listener threw — " + t.getMessage());
        }
    }

    private static java.util.concurrent.ThreadFactory daemonThreadFactory(String prefix) {
        return new java.util.concurrent.ThreadFactory() {
            private final java.util.concurrent.atomic.AtomicInteger n = new java.util.concurrent.atomic.AtomicInteger();
            @Override public Thread newThread(Runnable r) {
                Thread t = new Thread(r, prefix + "-" + n.incrementAndGet());
                t.setDaemon(true);
                return t;
            }
        };
    }
}
