# Kafka & Redis — Resume Deep Dive + FAQs 🔥

> Your resume says **two-tier cache, request coalescing, Kafka with backpressure** — interviewers WILL drill into these.
> **Rule:** Use your real numbers and details. Where it says **[fill in]**, put what you actually did — never guess in an interview.

**How to answer any resume question (60 sec):**
**Problem** (what hurt, with a number) → **What I did** (design + why) → **Trade-off** (what I gave up) → **Result** (number) → **What I'd do next**.

---

# Part 1: Your Resume Stories

## 1. Two-Tier Cache (L1 pod-local LRU + L2 Redis) 🔥🔥

**Say this:** "Our content service called an expensive external ML API on the read path, so P95 was ~150 ms and the API cost was high. I added a two-tier cache: L1 is an in-memory LRU inside each pod, L2 is a shared Redis. A request checks L1 (sub-ms), then Redis (~1 ms), and only on a miss calls the ML API and fills both. Hit rate reached 85–90%, P95 dropped to 30–60 ms, throughput doubled and ML API cost fell 50–60%."

```
Request → L1 (pod memory, LRU) ──miss──► L2 (Redis, shared) ──miss──► ML API / ES
             ▲ fill                        ▲ fill
```

**Likely drill-down questions**
| Question | How to answer |
|---|---|
| **Why two tiers, not just Redis?** | L1 = no network hop, absorbs hot keys, protects Redis at 30K TPS. L2 = shared across 110 pods, so one pod's miss warms the cache for all. |
| **L1 is per pod — how do 110 pods stay consistent?** | They don't, fully — L1 has a **short TTL**, so staleness is bounded. Content data tolerates a few seconds of staleness. (Option if asked: Redis Pub/Sub to broadcast invalidations.) **[fill in: your L1 TTL]** |
| **What did you use for L1?** | **[fill in: e.g. Caffeine / Guava / custom LinkedHashMap LRU]**. Bounded by max entries per domain. |
| **How did you measure hit rate?** | Counters for L1 hit / L2 hit / miss exported to Prometheus, viewed in Grafana. **[fill in]** |
| **What's the cache key?** | **[fill in: e.g. contentId + user segment + version]** — must include everything that changes the response. |
| **Cache-aside or write-through?** | Cache-aside (read path fills the cache on miss). |
| **What if Redis goes down?** | L1 still serves hot keys; misses fall back to the source with a timeout/circuit breaker; a Redis failure is never allowed to fail the request (fail-open). |

**Trap:** "Why not cache forever?" → Data changes; TTL bounds staleness and memory. Too long = stale, too short = low hit rate.

---

## 2. Request Coalescing (Stampede Fix) 🔥🔥

**Say this:** "When a hot key expired, hundreds of concurrent requests missed the cache together and all called the ML/Elasticsearch backend — a cache stampede. I added request coalescing: the first request for a key does the fetch, and every other request for the same key waits on that same in-flight result. Duplicate calls dropped 99%, and with parallel fetches tail latency went from 20–50 s down to 30–50 ms."

```java
ConcurrentHashMap<String, CompletableFuture<Result>> inFlight = new ConcurrentHashMap<>();

CompletableFuture<Result> get(String key) {
    return inFlight.computeIfAbsent(key, k ->
        CompletableFuture.supplyAsync(() -> fetchFromBackend(k), pool)
            .whenComplete((r, ex) -> inFlight.remove(k)));   // clean up when done
}
```

**Likely drill-down questions**
| Question | How to answer |
|---|---|
| **Why is `computeIfAbsent` safe here?** | It's atomic on ConcurrentHashMap — only one future is created per key; others get the same future. |
| **Coalescing is per pod — 110 pods still call 110 times?** | Yes, at most once per pod per key; that's already a 99% cut. For cross-pod: a short Redis lock (`SET key NX PX`) so one pod refreshes, others serve stale. |
| **What if the fetch fails?** | The future completes exceptionally → all waiters get the error/fallback, and the entry is removed so the next request retries. |
| **What if the fetch hangs?** | Add a timeout (`orTimeout`) so waiters don't pile up. |
| **Other stampede fixes?** | Jittered TTL (keys don't expire together), early background refresh of hot keys, serve stale while refreshing. |
| **20–50 s tail — why so bad before?** | **[fill in: e.g. backend overloaded by duplicate calls + sequential fetches]** |

---

## 3. TTL, Fallbacks & Failure Isolation 🔥

**Say this:** "At peak (World Cup, IPL) we saw spikes of stale data and cache errors. I used TTL-based invalidation so data expires on time, fallbacks — serve last-known value or a default when the backend fails — and failure isolation so a slow dependency can't take the whole service down. Cache-related errors fell 50–60%."

**Likely drill-down questions**
| Question | How to answer |
|---|---|
| **What is failure isolation?** | Separate timeouts / thread pools / circuit breakers per dependency (bulkhead), so one slow backend doesn't exhaust all threads. **[fill in what you used]** |
| **Stale vs error — which do you prefer?** | For content: serving slightly stale data beats returning an error. For payments: never. |
| **TTL vs event-based invalidation?** | TTL = simple, bounded staleness. Event-based (invalidate on update) = fresher, but more moving parts. |

---

## 4. OOMKills 8 GB → 2–3 GB 🔥

**Say this:** "Pods were getting OOMKilled at 8 GB because the in-memory caches were unbounded and grew with traffic. I bounded each domain's cache size with LRU + TTL eviction and tuned the Kubernetes memory limits. Memory settled at 2–3 GB and CPU dropped about 50% — mostly less GC work."

**Likely drill-down questions**
| Question | How to answer |
|---|---|
| **OOMKilled vs Java OutOfMemoryError?** | OOMKilled = **Kubernetes** kills the container for exceeding its memory limit. `OutOfMemoryError` = **JVM** heap full. |
| **How did you find the cause?** | **[fill in: e.g. heap dump / memory metrics showing cache growth]** |
| **Why did CPU drop?** | Smaller heap with fewer live objects → less GC work, shorter pauses. |
| **How to size heap in a container?** | Heap ≈ 70–75% of the container limit (`-XX:MaxRAMPercentage`), leaving room for metaspace, thread stacks, off-heap. |

---

## 5. Kafka for High-Volume Logging 🔥🔥

**Say this:** "Request logging was happening synchronously on the request path, adding latency and load. I moved it to Kafka: the service publishes log events asynchronously and consumers process them separately, so logging never blocks a request. I used partition-aware consumers and backpressure so consumers don't get overwhelmed. Broker I/O dropped 89% while we still captured 100% of errors and slow requests."

**Likely drill-down questions**
| Question | How to answer |
|---|---|
| **How did you cut broker I/O by 89%?** | **[fill in exactly — likely levers: sampling normal requests while keeping every error/slow request, producer batching (`linger.ms`, `batch.size`), compression]** |
| **What does "partition-aware consumers" mean?** | **[fill in — e.g. key by request/service so related events land in one partition; consumers scaled to partition count]** |
| **How did backpressure work?** | **[fill in — e.g. bounded in-memory buffer; `max.poll.records`; `pause()`/`resume()` when downstream is slow]** |
| **What if Kafka is down — does the request fail?** | No — logging is **fail-open**: async send with a bounded buffer; if full, drop low-value logs but never block the request. |
| **Why Kafka, not direct to ELK?** | Kafka decouples and buffers spikes; ELK can be slow or down without affecting the service; events can be replayed. |
| **Delivery guarantee for logs?** | At-least-once is fine (rare duplicates OK); errors must not be lost → `acks=all` for that stream. |
| **How do you monitor consumers?** | **Consumer lag** (latest offset − committed offset). Growing lag = consumers too slow. |

---

## 6. Linkly — Redis + Postgres + Bloom Filter 🔥

**Say this:** "Linkly is a URL shortener in Java 21 / Spring Boot 3. Reads use cache-aside over Redis with Postgres as the source of truth. A Bloom filter does negative caching — if it says a short code doesn't exist, we return 404 without touching Redis or the DB, which blocks junk-traffic floods. Alias creation is race-safe so two users can't claim the same alias. P95 is ~20 ms under k6 load tests."

| Question | How to answer |
|---|---|
| **What is a Bloom filter?** | A small bit array + several hash functions. Answers "**definitely not present**" or "**maybe present**". No false negatives, some false positives. |
| **Why use it here?** | Requests for non-existent codes would always miss cache and hit the DB (cache penetration). Bloom filter rejects them in memory. |
| **False positive → what happens?** | Falls through to Redis/DB, which return not-found. Only cost: one extra lookup. |
| **Deletes?** | A plain Bloom filter can't delete → rebuild periodically or use a counting Bloom filter. |
| **Race-safe alias creation?** | **[fill in — e.g. DB UNIQUE constraint + insert, catch duplicate-key; or Redis `SETNX`]**. Never check-then-insert (race). |

---

# Part 2: Redis FAQs

| Question | Short answer |
|---|---|
| **Why is Redis fast?** | In-memory, simple data structures, single-threaded command execution (no lock contention), efficient event loop. |
| **Single-threaded — isn't that slow?** | Commands are tiny and in memory, so one thread does ~100k ops/sec. Redis 6+ adds I/O threads for networking. Bonus: every command is atomic. |
| **Data types?** | String, Hash, List, Set, **Sorted Set** (leaderboards), Stream, Bitmap, HyperLogLog (approx. unique counts). |
| **Persistence?** | **RDB** = periodic snapshots (fast restart, may lose last minutes). **AOF** = log every write (safer, bigger). Can use both. |
| **Eviction policies?** | `noeviction` (default — errors when full), `allkeys-lru`, `allkeys-lfu`, `volatile-lru` / `volatile-ttl` (only keys with TTL). For a cache use `allkeys-lru`/`lfu`. |
| **How do keys expire?** | Lazily (checked when accessed) + actively (random sampling in the background). |
| **High availability?** | Replication (async) + **Sentinel** for automatic failover, or **Redis Cluster**. |
| **Redis Cluster?** | Data split into **16,384 hash slots** across masters, each with replicas. Multi-key ops need the same slot → use hash tags `{user:1}`. |
| **Distributed lock?** | `SET lock:key <uniqueToken> NX PX 30000`; release only if the token matches (Lua script). Always set an expiry. |
| **Pipelining vs transactions vs Lua?** | Pipeline = send many commands in one round trip. `MULTI/EXEC` = run as a group (no rollback). Lua = atomic custom logic. |
| **Hot key problem?** | One key gets huge traffic → L1 local cache (your design!), or split into `key#1..N` copies. |
| **Big key problem?** | Huge values/collections block the single thread and slow the network → split them, use `SCAN` not `KEYS`. |
| **Cache-aside: update DB then delete cache — why delete, not update?** | Avoids two writers leaving the cache with the older value. Delete → next read loads fresh data. |
| **Cache penetration / breakdown / avalanche?** | Penetration = keys that don't exist (→ Bloom filter / cache null). Breakdown = one hot key expires (→ coalescing / lock). Avalanche = many keys expire together (→ jittered TTL). |
| **Redis vs Memcached?** | Redis: rich types, persistence, replication. Memcached: simple multi-threaded key-value cache. |

---

# Part 3: Kafka FAQs

| Question | Short answer |
|---|---|
| **What is Kafka?** | A distributed, durable, append-only **log** for event streaming. Producers write to topics; consumers read at their own pace. |
| **Topic / partition / offset?** | Topic = category. Partition = ordered log (unit of parallelism). Offset = position of a message in a partition. |
| **Why is Kafka fast?** | Sequential disk writes, OS page cache, **zero-copy** to the network, batching and compression. |
| **Ordering?** | Guaranteed **only within a partition**. Same key → same partition → ordered. |
| **Consumer group?** | Consumers sharing a topic's work; each partition goes to **one** consumer in the group. More consumers than partitions → extra ones are idle. |
| **How to scale consumers?** | Add partitions + consumers (max parallelism = partition count). |
| **Rebalancing?** | Partitions reassigned when consumers join/leave/crash. Causes a pause → keep processing within `max.poll.interval.ms`; use cooperative-sticky assignor. |
| **Offsets & commits?** | Stored in `__consumer_offsets`. **Auto-commit** can lose or duplicate on crash → commit **manually after processing** for at-least-once. |
| **Producer `acks`?** | `0` = don't wait (fast, may lose) · `1` = leader wrote it · `all` = all in-sync replicas wrote it (safest, with `min.insync.replicas=2`). |
| **Replication / ISR?** | Each partition has a leader + followers (replication factor 3 is common). ISR = replicas fully caught up; only they can become leader. |
| **Delivery semantics?** | At-most-once (commit before processing), **at-least-once** (commit after — common), exactly-once (idempotent producer + transactions + `read_committed`). |
| **Duplicates with at-least-once?** | Make consumers **idempotent** — dedupe by event ID, or upsert. |
| **Consumer lag?** | Latest offset − committed offset. Rising lag = consumers can't keep up → scale out, speed up processing, batch. |
| **Backpressure in Kafka?** | Consumers *pull*, so they set the pace. Tune `max.poll.records`, `pause()`/`resume()` partitions, bounded internal queues. |
| **Poison message?** | Retry a few times with backoff → send to a **dead-letter topic** → alert. Don't block the partition forever. |
| **Retention vs compaction?** | Retention deletes by time/size. **Log compaction** keeps only the latest value per key (good for state/changelogs). |
| **Producer batching?** | `linger.ms` (wait a bit to fill a batch) + `batch.size` + `compression.type` (lz4/zstd) → fewer, bigger requests → much less broker I/O. |
| **ZooKeeper?** | Older Kafka used it for metadata; newer Kafka uses **KRaft** (built-in) — ZooKeeper is removed in Kafka 4.0. |
| **Kafka vs RabbitMQ?** | Kafka: log with retention + replay, very high throughput. RabbitMQ: traditional queue with flexible routing, messages deleted after ack. |

---

## 📌 Before the Interview
- Fill every **[fill in]** above with your real details — practise each resume story out loud in 60 seconds.
- Know your numbers by heart: **5.4M users · 27M req/day · 30K TPS · 110 pods · 85–90% hit rate · P95 150 → 30–60 ms · 99% fewer duplicate calls · 8 GB → 2–3 GB · 89% less broker I/O**.
- For every number, be ready for: **"How did you measure it?"** and **"What was the trade-off?"**
