package com.conceptcoding.interviewquestions.jobscheduler;

import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

public class Scheduler {

    private PriorityBlockingQueue<Job> jobQueue;
    private SchedulingStrategy strategy;
    private final ExecutorService threadPool;

    public Scheduler(int nThreads, SchedulingStrategy strategy) {
        this.strategy = strategy;
        this.jobQueue = new PriorityBlockingQueue<>(100, strategy.getComparator());
        this.threadPool = Executors.newFixedThreadPool(nThreads);
        startWorkers(nThreads);
    }

    public void addJob(Job job) {
        jobQueue.offer(job);
    }

    public void setStrategy(SchedulingStrategy newStrategy) {
        this.strategy = newStrategy;
        PriorityBlockingQueue<Job> newQueue = new PriorityBlockingQueue<>(100, newStrategy.getComparator());
        newQueue.addAll(jobQueue);
        this.jobQueue = newQueue;
    }

    public void shutdown() throws InterruptedException {
        threadPool.shutdown();
        threadPool.awaitTermination(30, TimeUnit.SECONDS);
    }

    private void startWorkers(int nThreads) {
        for (int i = 0; i < nThreads; i++) {
            threadPool.submit(this::workerLoop);
        }
    }

    private void workerLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Job job = jobQueue.take();  // blocks until a job is available

                if (!strategy.isValid(job)) {
                    System.out.println(Thread.currentThread().getName()
                            + ": skipping expired -> " + job.getName());
                    continue;
                }

                execute(job);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void execute(Job job) throws InterruptedException {
        System.out.println(Thread.currentThread().getName()
                + ": executing -> " + job.getName() + " (duration=" + job.getDuration() + ")");

        Thread.sleep(job.getDuration() * 10L);  // simulate work

        System.out.println(Thread.currentThread().getName()
                + ": completed -> " + job.getName());
    }
}
