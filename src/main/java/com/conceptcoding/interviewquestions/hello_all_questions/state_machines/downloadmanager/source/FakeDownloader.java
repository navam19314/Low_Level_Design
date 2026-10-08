package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.downloadmanager.source;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

// Test double: every URL "exists"; reading a chunk takes chunkDelayMs.
// failTransiently(url, n): the next n reads of that URL time out.  failPermanently(url): 404.
// Also records the peak number of reads in flight, so tests can check the "max parallel" limit.
public class FakeDownloader implements Downloader {

    private final long chunkDelayMs;
    private final Map<String, AtomicInteger> transientFailures = new ConcurrentHashMap<>();
    private final Map<String, Boolean> notFound = new ConcurrentHashMap<>();
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger peakInFlight = new AtomicInteger();
    private final List<String> startOrder = Collections.synchronizedList(new ArrayList<>());   // URLs in the order they began

    public FakeDownloader(long chunkDelayMs) { this.chunkDelayMs = chunkDelayMs; }

    public void failTransiently(String url, int times) { transientFailures.put(url, new AtomicInteger(times)); }
    public void failPermanently(String url)            { notFound.put(url, true); }
    public int  getPeakInFlight()                      { return peakInFlight.get(); }
    public List<String> getStartOrder()                { synchronized (startOrder) { return new ArrayList<>(startOrder); } }

    @Override
    public int readChunk(String url, long offset, int maxBytes) {
        if (notFound.containsKey(url)) throw new IllegalArgumentException("404 Not Found: " + url);
        AtomicInteger failures = transientFailures.get(url);
        if (failures != null && failures.getAndDecrement() > 0) throw new TransientDownloadException("timeout at byte " + offset);

        if (offset == 0) startOrder.add(url);
        int now = inFlight.incrementAndGet();
        peakInFlight.accumulateAndGet(now, Math::max);
        try {
            Thread.sleep(chunkDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TransientDownloadException("interrupted");
        } finally {
            inFlight.decrementAndGet();
        }
        return maxBytes;
    }
}
