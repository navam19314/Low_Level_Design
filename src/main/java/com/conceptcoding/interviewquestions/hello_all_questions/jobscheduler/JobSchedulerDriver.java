package com.conceptcoding.interviewquestions.hello_all_questions.jobscheduler;

import com.conceptcoding.interviewquestions.hello_all_questions.jobscheduler.listener.JobListener;
import com.conceptcoding.interviewquestions.hello_all_questions.jobscheduler.model.Job;
import com.conceptcoding.interviewquestions.hello_all_questions.jobscheduler.model.JobStatus;
import com.conceptcoding.interviewquestions.hello_all_questions.jobscheduler.model.RetryPolicy;
import com.conceptcoding.interviewquestions.hello_all_questions.jobscheduler.policy.EarliestDeadlineFirstPolicy;
import com.conceptcoding.interviewquestions.hello_all_questions.jobscheduler.policy.PriorityFirstPolicy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class JobSchedulerDriver {

    public static void main(String[] args) throws Exception {
        scenarioPriorityOrdering();
        scenarioEdfOrdering();
        scenarioDelayedScheduling();
        scenarioRetryExhaustion();
        scenarioListenerObservation();
        scenarioConcurrentSubmissions();
    }

    // ---- 1. PriorityFirst — primer holds worker until all 5 jobs queued, then expect [5,4,3,2,1] ----
    private static void scenarioPriorityOrdering() throws Exception {
        System.out.println("=== Scenario 1: PriorityFirst — 5 jobs, expect order [5,4,3,2,1] ===");
        // A real scheduler ONLY honors priority among jobs ALREADY in the queue when a worker
        // becomes free. With 1 worker that's idle when the first job arrives, that job runs
        // immediately — there's no "wait for all" phase. To deterministically test priority
        // ordering, we submit a "primer" job that holds the single worker, then queue 5 priority
        // jobs while the worker is busy, then release the primer.
        JobScheduler sched = new JobScheduler(new PriorityFirstPolicy(), /* workers */ 1);
        List<Integer> executionOrder = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(5);
        Instant now = Instant.now();

        CountDownLatch primerHold = new CountDownLatch(1);
        sched.submit(new Job("primer",
                () -> { try { primerHold.await(); } catch (InterruptedException e) { } },
                Integer.MAX_VALUE, now, RetryPolicy.noRetries(), now));

        // Submit in non-priority order: 3, 1, 5, 2, 4 (worker is busy holding the primer)
        for (int p : new int[]{3, 1, 5, 2, 4}) {
            final int pr = p;
            sched.submit(new Job("j-" + pr,
                    () -> { executionOrder.add(pr); latch.countDown(); },
                    pr, now, RetryPolicy.noRetries(), now));
        }
        Thread.sleep(100);              // ensure all 5 are in the priority queue
        primerHold.countDown();         // release worker — it now picks from a populated queue

        latch.await(2, TimeUnit.SECONDS);
        sched.shutdown(Duration.ofSeconds(1));
        System.out.println("  execution order: " + executionOrder + "  (expect [5, 4, 3, 2, 1])");
        System.out.println();
    }

    // ---- 2. EarliestDeadlineFirst — scheduledAt drives order, not createdAt ----
    private static void scenarioEdfOrdering() throws Exception {
        System.out.println("=== Scenario 2: EarliestDeadlineFirst — scheduledAt in [past,past,past] ===");
        JobScheduler sched = new JobScheduler(new EarliestDeadlineFirstPolicy(), 1);
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(3);
        Instant now = Instant.now();

        // All in the past so they're immediately enqueued. EDF picks the EARLIEST scheduledAt first.
        Job late   = new Job("late",   () -> { order.add("late");   latch.countDown(); },
                0, now.minusSeconds(10), RetryPolicy.noRetries(), now);
        Job middle = new Job("middle", () -> { order.add("middle"); latch.countDown(); },
                0, now.minusSeconds(30), RetryPolicy.noRetries(), now);
        Job early  = new Job("early",  () -> { order.add("early");  latch.countDown(); },
                0, now.minusSeconds(60), RetryPolicy.noRetries(), now);

        sched.submit(late);
        sched.submit(middle);
        sched.submit(early);

        latch.await(2, TimeUnit.SECONDS);
        sched.shutdown(Duration.ofSeconds(1));
        System.out.println("  execution order: " + order + "   (expect [early, middle, late])");
        System.out.println();
    }

    // ---- 3. Delayed scheduling — job submitted now, runs 200ms in the future ----
    private static void scenarioDelayedScheduling() throws Exception {
        System.out.println("=== Scenario 3: delayed scheduling (200ms future) ===");
        JobScheduler sched = new JobScheduler(new PriorityFirstPolicy(), 1);
        CountDownLatch latch = new CountDownLatch(1);
        Instant submittedAt = Instant.now();
        Instant runAt = submittedAt.plusMillis(200);
        long[] executedAtMs = new long[1];

        sched.submit(new Job("delayed",
                () -> { executedAtMs[0] = System.currentTimeMillis(); latch.countDown(); },
                0, runAt, RetryPolicy.noRetries(), submittedAt));

        latch.await(2, TimeUnit.SECONDS);
        long delay = executedAtMs[0] - submittedAt.toEpochMilli();
        System.out.println("  executed " + delay + "ms after submit  (expect ≥ 200ms)");
        sched.shutdown(Duration.ofSeconds(1));
        System.out.println();
    }

    // ---- 4. Retry exhaustion — job that always fails, maxAttempts=3 → exactly 3 attempts then FAILED ----
    private static void scenarioRetryExhaustion() throws Exception {
        System.out.println("=== Scenario 4: failing job with maxAttempts=3 → 3 attempts then FAILED ===");
        JobScheduler sched = new JobScheduler(new PriorityFirstPolicy(), 1);
        AtomicInteger attempts = new AtomicInteger();
        Job failing = new Job("failing",
                () -> { attempts.incrementAndGet(); throw new RuntimeException("boom"); },
                0, Instant.now(),
                new RetryPolicy(3, Duration.ofMillis(50), Duration.ofSeconds(1)),
                Instant.now());
        sched.submit(failing);

        // Wait until terminal.
        long deadline = System.currentTimeMillis() + 3000;
        while (!failing.getStatus().isTerminal() && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        System.out.println("  attempts made:  " + attempts.get()  + "  (expect 3)");
        System.out.println("  final status:   " + failing.getStatus() + "  (expect FAILED)");
        System.out.println("  lastError:      " + failing.getLastError());
        sched.shutdown(Duration.ofSeconds(1));
        System.out.println();
    }

    // ---- 5. Listener observation — counts started/completed/failed ----
    private static void scenarioListenerObservation() throws Exception {
        System.out.println("=== Scenario 5: listener observes lifecycle events ===");
        JobScheduler sched = new JobScheduler(new PriorityFirstPolicy(), 1);
        CountingListener counter = new CountingListener();
        sched.addListener(counter);

        Instant now = Instant.now();
        Job good = new Job("good", () -> {}, 0, now, RetryPolicy.noRetries(), now);
        Job bad  = new Job("bad",  () -> { throw new RuntimeException("nope"); },
                0, now, RetryPolicy.noRetries(), now);

        sched.submit(good);
        sched.submit(bad);
        Thread.sleep(300);   // give both time to finish
        System.out.println("  started:   " + counter.started.get()   + "  (expect 2)");
        System.out.println("  completed: " + counter.completed.get() + "  (expect 1)");
        System.out.println("  failed:    " + counter.failed.get()    + "  (expect 1)");
        sched.shutdown(Duration.ofSeconds(1));
        System.out.println();
    }

    // ---- 6. Concurrent submissions — 50 threads each submit 1 job, all complete ----
    private static void scenarioConcurrentSubmissions() throws Exception {
        System.out.println("=== Scenario 6: 50 threads × 1 job each — all complete, no losses ===");
        JobScheduler sched = new JobScheduler(new PriorityFirstPolicy(), /* workers */ 4);
        CountingListener counter = new CountingListener();
        sched.addListener(counter);

        int N = 50;
        CountDownLatch ready = new CountDownLatch(N);
        CountDownLatch fire  = new CountDownLatch(1);
        CountDownLatch done  = new CountDownLatch(N);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(N);

        for (int i = 0; i < N; i++) {
            final int n = i;
            pool.submit(() -> {
                ready.countDown();
                try { fire.await(); } catch (InterruptedException e) { return; }
                Instant now = Instant.now();
                sched.submit(new Job("burst-" + n,
                        done::countDown,
                        n % 10,    // varied priorities
                        now, RetryPolicy.noRetries(), now));
            });
        }
        ready.await();
        fire.countDown();
        done.await(3, TimeUnit.SECONDS);
        pool.shutdown();
        Thread.sleep(100);    // give listeners a moment to fire on the last few

        System.out.println("  completed jobs (counter):    " + counter.completed.get()
                + "  (expect 50)");
        System.out.println("  done.getCount() remaining:   " + done.getCount()
                + "  (expect 0)");
        sched.shutdown(Duration.ofSeconds(1));
    }

    // ----- helpers -----

    static class CountingListener implements JobListener {
        final AtomicInteger started   = new AtomicInteger();
        final AtomicInteger completed = new AtomicInteger();
        final AtomicInteger failed    = new AtomicInteger();
        final AtomicInteger cancelled = new AtomicInteger();
        @Override public void onStarted(Job job)                                { started.incrementAndGet(); }
        @Override public void onCompleted(Job job)                              { completed.incrementAndGet(); }
        @Override public void onFailed(Job job, Throwable e, boolean willRetry) { if (!willRetry) failed.incrementAndGet(); }
        @Override public void onCancelled(Job job)                              { cancelled.incrementAndGet(); }
    }
}
