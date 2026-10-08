package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.downloadmanager.model;

// One file being downloaded. Two threads touch it: the user's (pause / resume / cancel)
// and a worker's (start / progress / finish). Every status change is synchronized on the download.
//
// The tricky race is pause-then-resume while a worker is still mid-chunk. The 'running' flag
// says "a worker currently owns this download". Because the worker's between-chunk check
// (continueRunning) and resume() take the same lock, there are only two outcomes:
//   - the worker checks first → sees PAUSED, gives up ownership → resume() re-queues it
//   - resume() comes first   → worker still owns it → status goes back to DOWNLOADING, it keeps going
// Never two workers on one download, never a resumed download lost.
public class Download {

    private final String id;
    private final String url;
    private final long totalBytes;
    private final int priority;              // higher = sooner
    private final long sequence;             // FIFO among equal priorities

    private volatile long downloadedBytes;   // written by one worker, read by the UI
    private DownloadStatus status = DownloadStatus.QUEUED;
    private boolean running;                 // a worker currently owns this download
    private int failedAttempts;
    private String error;

    public Download(String id, String url, long totalBytes, int priority, long sequence) {
        this.id = id;
        this.url = url;
        this.totalBytes = totalBytes;
        this.priority = priority;
        this.sequence = sequence;
    }

    // ---------- worker side ----------

    // claim: only one worker, and only if it's still waiting
    public synchronized boolean tryStart() {
        if (status != DownloadStatus.QUEUED || running) return false;
        status = DownloadStatus.DOWNLOADING;
        running = true;
        return true;
    }

    // asked between chunks: keep going?  If not, the worker gives up ownership right here.
    public synchronized boolean continueRunning() {
        if (status == DownloadStatus.DOWNLOADING) return true;
        running = false;
        return false;
    }

    public synchronized void finish(DownloadStatus finalStatus, String error) {
        if (status == DownloadStatus.DOWNLOADING) {          // cancel/pause may have won meanwhile
            status = finalStatus;
            this.error = error;
        }
        running = false;
    }

    public void addProgress(long bytes)          { downloadedBytes += bytes; }   // single writer: the owning worker
    public synchronized int recordFailure()      { return ++failedAttempts; }
    public synchronized void resetFailures()     { failedAttempts = 0; }

    // ---------- user side ----------

    public synchronized void pause() {
        if (status != DownloadStatus.QUEUED && status != DownloadStatus.DOWNLOADING) {
            throw new IllegalStateException("Cannot pause a " + status + " download");
        }
        status = DownloadStatus.PAUSED;                      // a running worker stops at its next chunk
    }

    // returns true if the caller must put it back on the queue
    public synchronized boolean resume() {
        if (status != DownloadStatus.PAUSED) throw new IllegalStateException("Cannot resume a " + status + " download");
        if (running) {                                       // the worker hasn't stopped yet: just let it carry on
            status = DownloadStatus.DOWNLOADING;
            return false;
        }
        status = DownloadStatus.QUEUED;
        return true;
    }

    public synchronized void cancel() {
        if (status.isFinal()) throw new IllegalStateException("Already " + status);
        status = DownloadStatus.CANCELLED;
    }

    // ---------- reads ----------
    public synchronized DownloadStatus getStatus() { return status; }
    public synchronized String getError()          { return error; }
    public long getDownloadedBytes()               { return downloadedBytes; }
    public int  getProgressPercent()               { return totalBytes == 0 ? 100 : (int) (downloadedBytes * 100 / totalBytes); }
    public String getId()        { return id; }
    public String getUrl()       { return url; }
    public long   getTotalBytes(){ return totalBytes; }
    public int    getPriority()  { return priority; }
    public long   getSequence()  { return sequence; }

    @Override
    public synchronized String toString() { return id + "[" + status + " " + getProgressPercent() + "%]"; }
}
