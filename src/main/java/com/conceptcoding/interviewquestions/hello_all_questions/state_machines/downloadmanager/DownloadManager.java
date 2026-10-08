package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.downloadmanager;

import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.downloadmanager.model.Download;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.downloadmanager.model.DownloadStatus;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.downloadmanager.source.Downloader;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.downloadmanager.source.TransientDownloadException;

import java.util.Comparator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

// Producer–consumer:
//   the user's thread = producer: add / resume put downloads on a priority queue
//   N worker threads  = consumers: take the most urgent download and fetch it chunk by chunk
// N = "max parallel downloads", so a 1 GB file can't hog every connection.
public class DownloadManager {

    private static final int CHUNK_BYTES = 64 * 1024;

    private final Map<String, Download> downloads = new ConcurrentHashMap<>();
    private final PriorityBlockingQueue<Download> queue = new PriorityBlockingQueue<>(16,
            Comparator.comparingInt(Download::getPriority).reversed()       // most urgent first
                      .thenComparingLong(Download::getSequence));          // then first come, first served
    private final ExecutorService workers;
    private final Downloader downloader;
    private final int maxRetries;
    private final long retryBackoffMs;
    private final AtomicLong seq = new AtomicLong();

    public DownloadManager(Downloader downloader, int maxParallel, int maxRetries, long retryBackoffMs) {
        this.downloader = downloader;
        this.maxRetries = maxRetries;
        this.retryBackoffMs = retryBackoffMs;
        this.workers = Executors.newFixedThreadPool(maxParallel);
        for (int i = 0; i < maxParallel; i++) workers.submit(this::workerLoop);
    }

    public String add(String url, long totalBytes, int priority) {
        if (totalBytes < 0) throw new IllegalArgumentException("Size must be >= 0");
        long n = seq.incrementAndGet();
        Download d = new Download("DL-" + n, url, totalBytes, priority, n);
        downloads.put(d.getId(), d);
        queue.offer(d);
        return d.getId();
    }

    public void pause(String id)  { get(id).pause(); }
    public void cancel(String id) { get(id).cancel(); }

    public void resume(String id) {
        Download d = get(id);
        if (d.resume()) queue.offer(d);          // continues from getDownloadedBytes(), not from 0
    }

    public Download get(String id) {
        Download d = downloads.get(id);
        if (d == null) throw new NoSuchElementException("No download " + id);
        return d;
    }

    public void shutdown() throws InterruptedException {
        workers.shutdownNow();                   // interrupts take() and sleeps
        workers.awaitTermination(5, TimeUnit.SECONDS);
    }

    // ---------- consumer ----------

    private void workerLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            Download d;
            try {
                d = queue.take();                // waits until there is work
            } catch (InterruptedException e) {
                return;
            }
            if (d.tryStart()) run(d);            // false = paused/cancelled while queued, or already running
        }
    }

    private void run(Download d) {
        while (d.continueRunning()) {            // stops promptly on pause / cancel
            long remaining = d.getTotalBytes() - d.getDownloadedBytes();
            if (remaining <= 0) {
                d.finish(DownloadStatus.COMPLETED, null);
                return;
            }
            try {
                int read = downloader.readChunk(d.getUrl(), d.getDownloadedBytes(), (int) Math.min(CHUNK_BYTES, remaining));
                d.addProgress(read);
                d.resetFailures();
            } catch (TransientDownloadException e) {
                if (d.recordFailure() > maxRetries) {
                    d.finish(DownloadStatus.FAILED, "gave up after " + maxRetries + " retries: " + e.getMessage());
                    return;
                }
                try {
                    Thread.sleep(retryBackoffMs);   // retry the SAME offset after a pause
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    d.finish(DownloadStatus.FAILED, "shut down");
                    return;
                }
            } catch (Exception e) {                // permanent: 404, disk full — don't retry
                d.finish(DownloadStatus.FAILED, e.getMessage());
                return;
            }
        }
    }
}
