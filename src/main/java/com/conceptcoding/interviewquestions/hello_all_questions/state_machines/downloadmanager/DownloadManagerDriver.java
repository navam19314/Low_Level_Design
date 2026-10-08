package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.downloadmanager;

import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.downloadmanager.model.Download;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.downloadmanager.model.DownloadStatus;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.downloadmanager.source.FakeDownloader;

import java.util.ArrayList;
import java.util.List;

public class DownloadManagerDriver {

    static final long MB = 1024 * 1024;

    public static void main(String[] args) throws Exception {
        happyPathAndParallelLimit();
        pauseAndResume();
        priorityOrder();
        retriesAndFailures();
        cancel();
        pauseResumeStorm();
    }

    private static void happyPathAndParallelLimit() throws Exception {
        System.out.println("=== 6 downloads, max 2 at a time ===");
        FakeDownloader net = new FakeDownloader(1);
        DownloadManager dm = new DownloadManager(net, 2, 3, 1);
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 6; i++) ids.add(dm.add("https://cdn/file" + i, 2 * MB, 0));
        for (String id : ids) waitFor(dm.get(id), DownloadStatus.COMPLETED);
        System.out.println("  all completed: " + ids.stream().allMatch(id -> dm.get(id).getStatus() == DownloadStatus.COMPLETED)
                + ", peak parallel reads = " + net.getPeakInFlight() + " (expect 2)");
        dm.shutdown();
    }

    private static void pauseAndResume() throws Exception {
        System.out.println("\n=== Pause keeps progress; resume continues from there ===");
        DownloadManager dm = new DownloadManager(new FakeDownloader(2), 1, 3, 1);
        Download d = dm.get(dm.add("https://cdn/movie.mp4", 10 * MB, 0));   // 160 chunks × 2 ms
        Thread.sleep(60);
        dm.pause(d.getId());
        Thread.sleep(20);                                                   // let the worker notice
        long atPause = d.getDownloadedBytes();
        Thread.sleep(40);
        System.out.println("  paused at " + d.getProgressPercent() + "%, still paused after 40 ms? "
                + (d.getDownloadedBytes() == atPause && d.getStatus() == DownloadStatus.PAUSED));
        dm.resume(d.getId());
        waitFor(d, DownloadStatus.COMPLETED);
        System.out.println("  resumed → " + d + ", bytes = " + d.getDownloadedBytes() + " (expect exactly " + 10 * MB + ")");
        tryIt("resume a COMPLETED download", () -> dm.resume(d.getId()));
        dm.shutdown();
    }

    private static void priorityOrder() throws Exception {
        System.out.println("\n=== Priority: one worker busy; then low, high, normal queued → high goes next ===");
        FakeDownloader net = new FakeDownloader(2);
        DownloadManager dm = new DownloadManager(net, 1, 3, 1);
        dm.add("https://cdn/big", 2 * MB, 0);
        Thread.sleep(10);                                   // the only worker is now busy with "big"
        Download low = dm.get(dm.add("https://cdn/low", 64 * 1024, 0));
        dm.add("https://cdn/high", 64 * 1024, 10);
        dm.add("https://cdn/normal", 64 * 1024, 5);
        waitFor(low, DownloadStatus.COMPLETED);
        System.out.println("  start order: " + net.getStartOrder().stream().map(u -> u.substring(u.lastIndexOf('/') + 1)).toList()
                + " (expect [big, high, normal, low])");
        dm.shutdown();
    }

    private static void retriesAndFailures() throws Exception {
        System.out.println("\n=== Retries ===");
        FakeDownloader net = new FakeDownloader(1);
        net.failTransiently("https://cdn/flaky", 2);
        net.failTransiently("https://cdn/dead", 100);
        net.failPermanently("https://cdn/missing");
        DownloadManager dm = new DownloadManager(net, 3, 3, 1);
        Download flaky = dm.get(dm.add("https://cdn/flaky", MB, 0));
        Download dead = dm.get(dm.add("https://cdn/dead", MB, 0));
        Download missing = dm.get(dm.add("https://cdn/missing", MB, 0));
        waitFor(flaky, DownloadStatus.COMPLETED);
        waitFor(dead, DownloadStatus.FAILED);
        waitFor(missing, DownloadStatus.FAILED);
        System.out.println("  2 timeouts then ok → " + flaky.getStatus());
        System.out.println("  always times out   → " + dead.getStatus() + " (" + dead.getError() + ")");
        System.out.println("  404                → " + missing.getStatus() + " (" + missing.getError() + ", not retried)");
        dm.shutdown();
    }

    private static void cancel() throws Exception {
        System.out.println("\n=== Cancel ===");
        DownloadManager dm = new DownloadManager(new FakeDownloader(2), 1, 3, 1);
        Download running = dm.get(dm.add("https://cdn/a", 10 * MB, 0));
        Download waiting = dm.get(dm.add("https://cdn/b", 10 * MB, 0));
        Thread.sleep(20);
        dm.cancel(waiting.getId());                  // cancelled while queued: never starts
        dm.cancel(running.getId());                  // cancelled mid-download: stops at the next chunk
        Thread.sleep(20);
        System.out.println("  running → " + running + ", waiting → " + waiting + " (0%: never started)");
        tryIt("cancel again", () -> dm.cancel(running.getId()));
        tryIt("pause a cancelled download", () -> dm.pause(running.getId()));
        dm.shutdown();
    }

    // pause + resume hammered 500 times while 2 workers run: the file must finish exactly once, no bytes lost or doubled.
    private static void pauseResumeStorm() throws Exception {
        System.out.println("\n=== Concurrency: 500 rapid pause/resume pairs during a download ===");
        DownloadManager dm = new DownloadManager(new FakeDownloader(0), 2, 3, 1);
        Download d = dm.get(dm.add("https://cdn/iso", 50 * MB, 0));
        for (int i = 0; i < 500 && d.getStatus() != DownloadStatus.COMPLETED; i++) {
            try {
                dm.pause(d.getId());
                dm.resume(d.getId());
            } catch (IllegalStateException e) {
                // it completed in between: fine
            }
        }
        waitFor(d, DownloadStatus.COMPLETED);
        System.out.println("  " + d + ", bytes = " + d.getDownloadedBytes() + " (expect exactly " + 50 * MB + ")");
        dm.shutdown();
    }

    private static void waitFor(Download d, DownloadStatus target) throws InterruptedException {
        for (int i = 0; i < 1000 && d.getStatus() != target; i++) Thread.sleep(5);
        if (d.getStatus() != target) System.out.println("  TIMEOUT waiting for " + d + " to be " + target + " ✗");
    }

    private static void tryIt(String label, Runnable action) {
        try {
            action.run();
            System.out.println("  " + label + ": ALLOWED ✗");
        } catch (RuntimeException e) {
            System.out.println("  " + label + ": rejected → " + e.getMessage());
        }
    }
}
