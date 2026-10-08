# Download Manager

> **Amazon:** ★ reported in 2026 (offline downloads, Prime Video / Kindle style). Your guide: *"download states; pause/resume; thread pool; queue with priorities; retries."*
>
> **The crux (what's really being tested):**
> 1. **Producer–consumer:** a priority queue + a fixed pool of worker threads ("max 3 downloads at once").
> 2. **A state machine shared by two threads:** the user pauses or cancels while a worker is mid-download.
> 3. **Resume from where it stopped:** read from a byte offset; never restart from 0. Retry only errors that may go away.
>
> **Family:** F3 State machine + concurrency tool D (producer–consumer). See [foundations](../../00_AMAZON_LLD_FOUNDATIONS.md). Retries mirror [Notification](../../pipelines/notification/INTERVIEW_WALKTHROUGH.md).

---

## 1. Plain-language picture

### A post office with 2 counters
```
Queue (most urgent first):   [ movie (priority 10) ] [ podcast (5) ] [ ebook (0) ] ...
Counters (worker threads):   counter 1 → busy with movie       counter 2 → busy with podcast
                             ebook waits until a counter frees up.
```
- Users **add** downloads to the queue (producers).
- 2 workers **take** the most urgent one and fetch it chunk by chunk (consumers).
- "Max 2 at once" = the number of workers. A big file can't hog every connection.

### Each download moves through states
```
QUEUED ──worker picks it──→ DOWNLOADING ──all bytes──→ COMPLETED
   │                          │      │
   │ pause            pause   │      └──too many errors / 404──→ FAILED
   ↓                          ↓
 PAUSED ──resume──→ QUEUED (or straight back to DOWNLOADING)
any non-final state ──cancel──→ CANCELLED
```

### Pause is a polite request
The worker is in the middle of reading a 64 KB chunk; you can't yank it mid-read. So pause **sets a flag** (status = PAUSED) and the worker **checks it between chunks** and stops. The bytes already downloaded are kept. Resume puts the download back on the queue, and the next worker continues **from that byte offset**. That's why `readChunk(url, offset, size)` takes an offset.

### The nasty race: pause, then resume, very fast
You tap pause and then resume, all while the worker is still finishing its current chunk. Two bad outcomes are possible:
- **Two workers on one download:** resume re-queues it while the old worker hasn't stopped yet. The file gets corrupted.
- **A lost download:** the old worker stops after resume decided "the worker will carry on". Nobody downloads it.

The fix is a `running` flag ("a worker owns this download") checked **under the same lock** in both places:
```
worker, between chunks:  continueRunning() → status still DOWNLOADING? keep going : (running = false; stop)
user:                    resume()          → worker still running? status = DOWNLOADING (it carries on)
                                                                  : status = QUEUED (put back on the queue)
```
Because both take the download's lock, exactly one of them "wins" and the other adapts.

---

## 2. From story to design

| # | Decision | Rejected | Why |
|---|---|---|---|
| D1 | `PriorityBlockingQueue` (priority desc, then FIFO) + fixed thread pool | a thread per download | Caps parallel connections; `take()` blocks idle workers for free. |
| D2 | FIFO tie-break via a sequence number | priority only | Equal priorities would otherwise come out in an arbitrary order. |
| D3 | Pause = set status; the worker checks **between chunks** | `Thread.interrupt()` / killing the thread | Stops cleanly at a chunk boundary; the progress count stays exact. |
| D4 | `readChunk(url, offset, maxBytes)` | "download the whole file" | Offset = resume support (HTTP `Range: bytes=offset-`). |
| D5 | `running` flag + `continueRunning()` / `resume()` under one lock | status alone | Prevents two workers on one download *and* lost resumes (§1). |
| D6 | `tryStart()`: QUEUED and not running → DOWNLOADING | starting whatever comes off the queue | A paused/cancelled item may still sit in the queue; workers skip it. |
| D7 | Retry `TransientDownloadException` (same offset, with a back-off); fail fast on anything else | retry everything | A 404 won't fix itself; a timeout might. |
| D8 | `Downloader` interface + `FakeDownloader` | HTTP calls inside the manager | Deterministic tests (inject failures, count parallel reads); FTP/S3 later. |
| D9 | `downloadedBytes` is `volatile`, written only by the owning worker | `synchronized` around every increment | A single writer, many readers (the progress bar). |

### Class shape
```
DownloadManager                                  ← add · pause · resume · cancel · get · shutdown
  Map<id, Download> · PriorityBlockingQueue<Download> · fixed thread pool (N workers)
  workerLoop: take() → tryStart() → run(): while continueRunning(): readChunk → addProgress | retry | fail

Download                                          ← shared by the user thread and one worker
  id, url, totalBytes, priority, sequence · volatile downloadedBytes
  status, running, failedAttempts, error          (all guarded by the download's lock)
  worker side: tryStart · continueRunning · finish · addProgress
  user side:   pause · resume (→ re-queue?) · cancel
DownloadStatus enum { QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELLED }

«interface» Downloader  readChunk(url, offset, maxBytes)
  └── FakeDownloader (test double)            TransientDownloadException
```

---

## 3. Patterns that earn their place

| Pattern | In the base? | Problem it solves | Without it |
|---|---|---|---|
| **Producer–consumer** (queue + worker pool) | **Yes** | bounded parallelism, waiting work, priorities | a thread per download; no cap, no ordering |
| **Enum state machine** (`DownloadStatus`) | **Yes** | legal moves (can't resume a completed file) | flags like `isPaused`, `isDone` that contradict each other |
| **Strategy / test seam** (`Downloader`) | **Yes** | real HTTP vs fake; other protocols | untestable timing and failures |
| **Observer** (progress listeners) | No | progress bars, "download finished" notifications | Q3 |

**Why not the State pattern?** Statuses here mostly gate *which call is allowed*. The real work (fetching chunks) is the same in every state, so an enum + guarded methods is enough. Say it; it shows judgement (compare [Vending Machine](../vendingmachine/INTERVIEW_WALKTHROUGH.md), where behaviour truly differs).

---

## 4. The 45-minute run

### Clarify (min 0–5)
> **You:** Users add downloads; at most N run at once; others wait in a queue. Priorities?
> **Interviewer:** Yes, some downloads are more urgent.
> **You:** Pause/resume must keep progress and continue from the same byte?
> **Interviewer:** Yes.
> **You:** Network failures: retry some number of times? And not retry a 404?
> **Interviewer:** Yes.
> **You:** Cancel any time? Progress visible?
> **Interviewer:** Yes and yes.

```
In scope:  add(url, size, priority) · max N parallel · priority then FIFO
           pause (keeps bytes) · resume (from offset) · cancel · progress %
           transient retries with back-off · permanent errors fail fast · thread-safe
Out:       bandwidth limit, multi-part downloads, persistence across app restarts, checksums
```

### Timeline
| Min | Do |
|---|---|
| 5–10 | Draw the post office (queue + N workers) and the state diagram. Explain the pause/resume race. |
| 10–13 | Code step 1: `DownloadStatus`, `Downloader`, `TransientDownloadException`. |
| 13–25 | Code step 2: **`Download`** (worker-side and user-side methods, the `running` flag). |
| 25–35 | Code step 3: **`DownloadManager`**: queue, pool, `workerLoop`, `run`, `add`/`pause`/`resume`/`cancel`. |
| 35–40 | Dry run: pause mid-download → resume → finishes with exact bytes. |
| 40–45 | Follow-ups. |

### The code you write, in this order

**Step 1: status + the byte source** ([model/DownloadStatus.java](model/DownloadStatus.java), [source/](source/))
```java
public enum DownloadStatus {
    QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELLED;
    public boolean isFinal() { return this == COMPLETED || this == FAILED || this == CANCELLED; }
}

public interface Downloader {
    int readChunk(String url, long offset, int maxBytes);    // offset = resume support
}

public class TransientDownloadException extends RuntimeException {   // timeout, 503: worth retrying
    public TransientDownloadException(String message) { super(message); }
}
```

**Step 2: the download, shared by two threads** ([model/Download.java](model/Download.java))
```java
public class Download {
    private final String id;
    private final String url;
    private final long totalBytes;
    private final int priority;              // higher = sooner
    private final long sequence;             // FIFO among equal priorities
    private volatile long downloadedBytes;   // one writer (the worker), many readers (the UI)
    private DownloadStatus status = DownloadStatus.QUEUED;
    private boolean running;                 // a worker currently owns this download
    private int failedAttempts;
    private String error;

    // ---- worker side ----
    public synchronized boolean tryStart() {
        if (status != DownloadStatus.QUEUED || running) return false;
        status = DownloadStatus.DOWNLOADING;
        running = true;
        return true;
    }

    public synchronized boolean continueRunning() {          // asked between chunks
        if (status == DownloadStatus.DOWNLOADING) return true;
        running = false;                                     // stop and give up ownership, atomically
        return false;
    }

    public synchronized void finish(DownloadStatus finalStatus, String error) {
        if (status == DownloadStatus.DOWNLOADING) {          // pause/cancel may have won meanwhile
            status = finalStatus;
            this.error = error;
        }
        running = false;
    }

    public void addProgress(long bytes)      { downloadedBytes += bytes; }
    public synchronized int recordFailure()  { return ++failedAttempts; }
    public synchronized void resetFailures() { failedAttempts = 0; }

    // ---- user side ----
    public synchronized void pause() {
        if (status != DownloadStatus.QUEUED && status != DownloadStatus.DOWNLOADING) {
            throw new IllegalStateException("Cannot pause a " + status + " download");
        }
        status = DownloadStatus.PAUSED;                      // the worker stops at its next chunk
    }

    public synchronized boolean resume() {                   // true = caller must re-queue
        if (status != DownloadStatus.PAUSED) throw new IllegalStateException("Cannot resume a " + status + " download");
        if (running) {                                       // worker hasn't stopped yet: let it carry on
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
    // + constructor, synchronized getStatus/getError, getProgressPercent, getters
}
```

**Step 3: the manager** ([DownloadManager.java](DownloadManager.java))
```java
public class DownloadManager {
    private static final int CHUNK_BYTES = 64 * 1024;

    private final Map<String, Download> downloads = new ConcurrentHashMap<>();
    private final PriorityBlockingQueue<Download> queue = new PriorityBlockingQueue<>(16,
            Comparator.comparingInt(Download::getPriority).reversed()       // most urgent first
                      .thenComparingLong(Download::getSequence));          // then FIFO
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
        if (d.resume()) queue.offer(d);                  // continues from getDownloadedBytes()
    }

    private void workerLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            Download d;
            try { d = queue.take(); } catch (InterruptedException e) { return; }   // waits for work
            if (d.tryStart()) run(d);                    // skip items paused/cancelled while queued
        }
    }

    private void run(Download d) {
        while (d.continueRunning()) {                    // stops promptly on pause / cancel
            long remaining = d.getTotalBytes() - d.getDownloadedBytes();
            if (remaining <= 0) { d.finish(DownloadStatus.COMPLETED, null); return; }
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
                    Thread.sleep(retryBackoffMs);        // then retry the SAME offset
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    d.finish(DownloadStatus.FAILED, "shut down");
                    return;
                }
            } catch (Exception e) {                      // permanent: 404, disk full
                d.finish(DownloadStatus.FAILED, e.getMessage());
                return;
            }
        }
    }
    // + get(id), shutdown() (shutdownNow interrupts take() and sleeps)
}
```
**Shape to remember:** worker = `take()` → `tryStart()` → loop `continueRunning()` → `readChunk(offset)` → progress | retry | fail → `finish()`.

### Dry run
```
1 worker. add(movie, 10 MB).  take() → tryStart ✓ → DOWNLOADING
chunks: 0 → 64K → 128K → ... at 15%: user pause() → status PAUSED
next check: continueRunning() → not DOWNLOADING → running=false → worker returns to take()
user resume() → running is false → status QUEUED → offer(movie)
worker take() → tryStart ✓ → reads from offset 1.5 MB (not 0) → ... → 10 MB → COMPLETED (exact bytes)

Fast pause+resume while the worker is mid-chunk:
  pause() → PAUSED;  resume() → running is TRUE → status DOWNLOADING, no re-queue
  worker's next continueRunning() → DOWNLOADING → just carries on.  One worker, nothing lost.
```
The driver hammers 500 pause/resume pairs during a 50 MB download: it always finishes with exactly 52,428,800 bytes.

---

## 5. Follow-ups: answer out loud first, then open

<details>
<summary><b>Q1. Why not just interrupt the worker thread to pause?</b></summary>

Interrupting can land mid-read: the bytes from that read may be half-written, so your offset becomes uncertain. You also can't distinguish "pause" from "shutdown". A status flag checked at chunk boundaries stops at a **known, consistent offset**. Interrupts are still used for `shutdown()`, where losing the in-flight chunk is fine.
</details>

<details>
<summary><b>Q2. Limit total bandwidth to 2 MB/s across all downloads.</b></summary>

A shared **token bucket** (the deck's Rate Limiter): each chunk needs `chunkBytes` tokens; the bucket refills at 2 MB/s. Workers call `acquire(bytes)` before `readChunk` and wait if the bucket is empty. It's per manager, not per download, so the limit holds however many workers run. A *per-download* cap is just a bucket per download.
</details>

<details>
<summary><b>Q3. Show a progress bar, and notify when a download finishes.</b></summary>

**Observer**: `DownloadListener { onProgress(id, percent); onStatusChanged(id, status); }`. The worker calls `onProgress` after each chunk (throttled to, say, every 1%), and `finish()` triggers `onStatusChanged`. Listeners live in a `CopyOnWriteArrayList`, each call is wrapped in try/catch, and the UI thread does the rendering.
</details>

<details>
<summary><b>Q4. The app is killed; resume after restart.</b></summary>

Persist `{id, url, totalBytes, downloadedBytes, status, priority}` every few chunks, and write the bytes to a `.part` file. On start-up, reload: DOWNLOADING becomes QUEUED (re-offered), PAUSED stays PAUSED. Resume works because `readChunk` takes the offset; the server must support HTTP `Range` requests (check `Accept-Ranges`). Otherwise restart from 0.
</details>

<details>
<summary><b>Q5. Download one big file faster using several connections.</b></summary>

**Segmented download:** split the file into K ranges (`bytes=0-25MB`, `25MB-50MB`, ...), download each as a sub-task (each with its own offset and retries), write into the right position of the file (`RandomAccessFile.seek`), and mark the file COMPLETED when all segments are done. Pause/resume works per segment. Keep K small (3–5) to be polite to servers.
</details>

<details>
<summary><b>Q6. Verify the file isn't corrupted.</b></summary>

Store the expected checksum (SHA-256) from the server's metadata. After the last chunk, hash the file; on a mismatch → FAILED ("checksum mismatch") and delete the `.part`, or re-download only the bad segment if segments carry their own checksums.
</details>

<details>
<summary><b>Q7. Only download on Wi-Fi, or overnight.</b></summary>

A **policy check** before `tryStart`: `if (!policy.canRunNow(download)) { requeue later; }`, with `WifiOnlyPolicy` and `NightTimePolicy` as Strategies. When the network changes, pause the running mobile-data downloads and resume them on Wi-Fi. The status machine already supports this: it's just pause/resume driven by the system instead of the user.
</details>

<details>
<summary><b>Q8. Retry timing for thousands of clients after a CDN outage.</b></summary>

Exponential back-off **with jitter** (`backoff × 2^attempt + random`), capped, so a million phones don't retry in the same second and knock the CDN over again. The same idea is in the Notification follow-ups.
</details>

<details>
<summary><b>Q9. How would you know it's working in production?</b></summary>

- **Metrics:** downloads started/completed/failed per hour, retry rate, average throughput, time in queue, pause/resume counts.
- **Alarms:** failure-rate spike for one CDN host (an outage), throughput collapse, a queue that keeps growing.
- **Logs:** downloadId + url host + offset + error on every failure (never full signed URLs: they're credentials).
</details>

<details>
<summary><b>Q10. How do you test it?</b></summary>

The `FakeDownloader` makes it deterministic: the parallel limit (peak in-flight = N), pause keeps the bytes and resume ends with the exact total, priority **start order** (`[big, high, normal, low]`), transient failures then success, permanent failure not retried, cancel while queued vs mid-download, and 500 rapid pause/resume pairs → exact bytes. All are in the driver.
</details>

---

## 6. Traps
1. A thread per download (no cap).
2. Pause by interrupting or killing the thread.
3. Resume restarts from byte 0.
4. Two workers on one download after a fast pause/resume (no ownership flag).
5. Retrying 404s; no back-off between retries.
6. Workers starting items that were paused/cancelled while still in the queue.
7. `synchronized` around the network read: holding a lock during I/O blocks pause/cancel.

## 7. Recall check
1. Draw the queue + workers. What does "max N parallel" map to?
2. Draw the state diagram. Which calls are legal in each state?
3. Explain the fast pause/resume race and how `running` + one lock fixes it.
4. Why does `readChunk` take an offset?
5. Which errors are retried, and from where?
6. Why an enum here but the State pattern in Vending Machine?

**Rebuild in 12 minutes:** `DownloadStatus` · `Download` (tryStart, continueRunning, finish, pause, resume, cancel) · `DownloadManager` (priority queue, pool, workerLoop, run with retry).

---

**Files:** `DownloadManager` · `model/` (`Download`, `DownloadStatus`) · `source/` (`Downloader`, `FakeDownloader`, `TransientDownloadException`) · `DownloadManagerDriver` (parallel limit, pause/resume exact bytes, priority start order, retries/404, cancel, 500-pair pause/resume storm)
```bash
mvn -q compile exec:java -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.state_machines.downloadmanager.DownloadManagerDriver
```
