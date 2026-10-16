# Back-of-the-Envelope Calculations — System Design Interview Notes

> **Goal:** quick, defensible estimates (right order of magnitude) that drive design decisions.
> Being off by 2× is fine. Being off by 1000× is not.

---

## Table of Contents
1. [Units of Data](#1-units-of-data)
2. [Size of Common Data Types & Objects](#2-size-of-common-data-types--objects)
3. [Time Conversions](#3-time-conversions)
4. [Powers of 10 & Powers of 2 Cheat Sheet](#4-powers-of-10--powers-of-2-cheat-sheet)
5. [Latency Numbers Every Engineer Should Know](#5-latency-numbers-every-engineer-should-know)
6. [Single-Machine Capacity (Ballpark)](#6-single-machine-capacity-ballpark)
7. [Availability — The "Nines"](#7-availability--the-nines)
8. [The Estimation Framework (Step by Step)](#8-the-estimation-framework-step-by-step)
9. [Traffic Estimation (QPS)](#9-traffic-estimation-qps)
10. [Storage Estimation](#10-storage-estimation)
11. [Bandwidth Estimation](#11-bandwidth-estimation)
12. [Memory / Cache Estimation](#12-memory--cache-estimation)
13. [Server Count Estimation](#13-server-count-estimation)
14. [Worked Examples](#14-worked-examples)
15. [Mental Math Tricks](#15-mental-math-tricks)
16. [Common Mistakes](#16-common-mistakes)
17. [One-Page Cheat Sheet](#17-one-page-cheat-sheet)

---

## 1. Units of Data

### Bit vs Byte
| Unit | Meaning |
|---|---|
| **bit (b)** | a single 0 or 1 |
| **byte (B)** | 8 bits |

> ⚠️ **Lowercase b = bits, uppercase B = bytes.**
> Network speeds are in **bits** per second (Mbps, Gbps). Storage is in **bytes** (MB, GB).
> **1 Gbps = 125 MB/s** (divide by 8).

### The ladder
| Unit | Decimal (SI) — use in interviews | Binary (exact) | Approx |
|---|---|---|---|
| 1 KB (Kilobyte) | 1,000 bytes = 10³ | 1,024 bytes = 2¹⁰ | Thousand |
| 1 MB (Megabyte) | 1,000 KB = 10⁶ bytes | 1,024 KB = 2²⁰ | Million |
| 1 GB (Gigabyte) | 1,000 MB = 10⁹ bytes | 1,024 MB = 2³⁰ | Billion |
| 1 TB (Terabyte) | 1,000 GB = 10¹² bytes | 1,024 GB = 2⁴⁰ | Trillion |
| 1 PB (Petabyte) | 1,000 TB = 10¹⁵ bytes | 1,024 TB = 2⁵⁰ | Quadrillion |
| 1 EB (Exabyte) | 1,000 PB = 10¹⁸ bytes | 1,024 PB = 2⁶⁰ | Quintillion |

**So: 1 GB = 1,000,000,000 bytes (10⁹)** — or exactly 1,073,741,824 bytes (2³⁰) in binary.

### Why we use powers of 10 in interviews
- 1024 vs 1000 is only a ~2.4% difference per level (~7% at GB, ~10% at TB).
- Powers of 10 make mental math trivial. Nobody penalises you for it.
- (Technically the binary units are KiB, MiB, GiB — mention only if asked.)

### The golden link: counts ↔ bytes
| Count | × 1 byte | × 1 KB | × 1 MB |
|---|---|---|---|
| 1 Thousand (10³) | 1 KB | 1 MB | 1 GB |
| 1 Million (10⁶) | 1 MB | 1 GB | 1 TB |
| 1 Billion (10⁹) | 1 GB | 1 TB | 1 PB |

> **Memorise this table.** "1 billion users × 1 KB each = 1 TB" — instantly.

---

## 2. Size of Common Data Types & Objects

### Primitive types
| Type | Size |
|---|---|
| `boolean` | 1 byte (in storage; 1 bit if bit-packed) |
| `char` (ASCII / UTF-8 English) | 1 byte |
| `char` (UTF-8 non-English) | 2–4 bytes |
| `char` (Java, UTF-16) | 2 bytes |
| `short` | 2 bytes |
| `int` / `float` | 4 bytes |
| `long` / `double` | 8 bytes |
| Timestamp (epoch millis) | 8 bytes |
| IPv4 address | 4 bytes |
| IPv6 address | 16 bytes |

### Identifiers
| ID | Size |
|---|---|
| Auto-increment `int` | 4 bytes (max ~4.3 billion unsigned) |
| Auto-increment `long` / Snowflake ID | 8 bytes |
| UUID (binary) | 16 bytes |
| UUID (string, `xxxxxxxx-xxxx-...`) | 36 bytes |
| Short URL key (7 chars Base62) | 7 bytes |

### Typical application objects (use these as defaults)
| Object | Ballpark size |
|---|---|
| Tweet / short post text (280 chars) | ~300 bytes |
| Post / tweet **with metadata** (ids, timestamps, counters) | **~1 KB** |
| User profile row | ~1 KB |
| Chat message | ~100 bytes |
| URL mapping row (short → long URL + metadata) | ~500 bytes |
| Location update (lat, long, id, ts) | ~50–100 bytes |
| Log line / event | ~100 bytes – 1 KB |
| Thumbnail image | ~10–50 KB |
| Compressed photo (web) | **~200 KB – 2 MB** (use 500 KB or 1 MB) |
| Raw phone photo | ~3–5 MB |
| Audio (MP3, 128 kbps) | ~1 MB / minute |
| Video 720p (~2.5 Mbps) | ~20 MB / minute (~1 GB/hour) |
| Video 1080p (~5 Mbps) | ~40 MB / minute (~2–2.5 GB/hour) |
| Video 4K (~20 Mbps) | ~150 MB / minute (~9 GB/hour) |

> 💡 **Video size from bitrate:** bitrate (Mbps) ÷ 8 = MB per second.
> 5 Mbps ÷ 8 = 0.625 MB/s × 60 = ~37.5 MB/min ≈ 40 MB/min.

---

## 3. Time Conversions

| Period | Exact seconds | Use in interviews |
|---|---|---|
| 1 minute | 60 | 60 |
| 1 hour | 3,600 | ~3.6 × 10³ |
| **1 day** | **86,400** | **~10⁵ (100K)** |
| 1 month (30 days) | 2,592,000 | ~2.5 × 10⁶ |
| 1 year | 31,536,000 | ~3 × 10⁷ |

### The most useful conversion: per day → per second
> **Divide by 10⁵** (really 86,400 — rounding to 10⁵ undercounts by ~15%; say so, or round up the result.)

| Per day | Per second (approx) |
|---|---|
| 1 Million / day | ~12 / s |
| 10 Million / day | ~120 / s |
| 100 Million / day | ~1,200 / s |
| 1 Billion / day | ~12,000 / s |
| 10 Billion / day | ~120,000 / s |

> Quick memory: **1M/day ≈ 12/sec.** Scale from there.

### Per second → per day / year
- × 10⁵ → per day
- × 3 × 10⁷ → per year

---

## 4. Powers of 10 & Powers of 2 Cheat Sheet

### Powers of 10
| Power | Name | Short |
|---|---|---|
| 10³ | Thousand | K |
| 10⁶ | Million | M |
| 10⁹ | Billion | B (G) |
| 10¹² | Trillion | T |
| 10¹⁵ | Quadrillion | P |

### Powers of 2 (useful for IDs, keyspace, ranges)
| Power | Exact | Approx |
|---|---|---|
| 2⁸ | 256 | — (1 byte range) |
| 2¹⁰ | 1,024 | ~1 Thousand |
| 2¹⁶ | 65,536 | ~65K (ports, short range) |
| 2²⁰ | 1,048,576 | ~1 Million |
| 2³⁰ | 1,073,741,824 | ~1 Billion |
| 2³² | 4,294,967,296 | **~4.3 Billion** (unsigned int / IPv4 space) |
| 2³¹ | 2,147,483,648 | ~2.1 Billion (signed int max) |
| 2⁴⁰ | — | ~1 Trillion |
| 2⁶⁴ | — | ~1.8 × 10¹⁹ (long — practically unlimited) |

> **Interview use:** "Will an `int` ID run out?" → 4 billion max. Facebook has 3B users → use `long`.

### Keyspace for encoded IDs (URL shortener etc.)
| Encoding | Length 6 | Length 7 | Length 8 |
|---|---|---|---|
| Base62 (a-z, A-Z, 0-9) | ~56.8 Billion | **~3.5 Trillion** | ~218 Trillion |
| Base64 | ~68.7 Billion | ~4.4 Trillion | ~281 Trillion |

---

## 5. Latency Numbers Every Engineer Should Know

(Updated ballparks from Jeff Dean's famous list — learn the **order of magnitude**, not exact values.)

| Operation | Latency | Scale |
|---|---|---|
| L1 cache reference | ~1 ns | nanoseconds |
| L2 cache reference | ~4 ns | |
| Mutex lock/unlock | ~20 ns | |
| **Main memory (RAM) reference** | **~100 ns** | |
| Compress 1 KB (Snappy) | ~2 µs | microseconds |
| Send 1 KB over 1 Gbps network | ~10 µs | |
| **SSD random read (4 KB)** | **~20–150 µs** | |
| Read 1 MB sequentially from RAM | ~10–250 µs | |
| **Round trip within same datacenter** | **~0.5 ms** | milliseconds |
| Read 1 MB sequentially from SSD | ~1 ms | |
| **HDD disk seek** | **~5–10 ms** | |
| Read 1 MB sequentially from HDD | ~5–20 ms | |
| **Cross-continent round trip (e.g. US ↔ Europe)** | **~150 ms** | |

### Takeaways (these are what interviewers want)
1. **Memory is fast, disk is slow.** RAM ≈ 1000× faster than SSD random reads, ~100,000× faster than HDD seeks → **cache hot data**.
2. **Avoid disk seeks.** Sequential reads >> random reads → why Kafka / LSM-trees (Cassandra) are append-only.
3. **Compress before sending** over the network — CPU is cheap compared to network.
4. **Cross-region calls are expensive (~150 ms)** → serve users from a nearby region / CDN.
5. **Within a datacenter, a network call (~0.5 ms) is cheaper than an HDD seek.** Fetching from a remote Redis beats a local disk.

### Latency budget example
"Feed must load in < 500 ms":
- Client ↔ server (same region): ~50–100 ms
- Redis lookups: ~1 ms each
- DB query (indexed, SSD): ~5–10 ms
- → Leaves plenty of room, **but** 1,000 sequential DB calls × 5 ms = 5 s ❌ → that's *why* fan-out on read fails.

---

## 6. Single-Machine Capacity (Ballpark)

> Use these to answer "how many servers do we need?" Numbers vary hugely by workload — state your assumption.

### Hardware of a typical modern server
| Resource | Ballpark |
|---|---|
| CPU cores | 16–64 cores |
| RAM | 64 GB – 512 GB (up to a few TB on big boxes) |
| SSD | 1–10+ TB |
| Network | 10–25 Gbps (≈ 1.25–3 GB/s) |
| Concurrent connections (WebSockets) | ~100K – 1M per server (tuned) |

### Throughput per node
| Component | Ballpark QPS / throughput |
|---|---|
| Stateless web / app server | ~1K – 10K req/s (simple API) |
| **Redis / Memcached** (single node) | **~100K ops/s** (up to ~1M with pipelining) |
| **MySQL / Postgres** (single node, indexed) | **~1K–10K writes/s, ~10K–50K simple reads/s** |
| Cassandra / DynamoDB partition-node | ~10K writes/s per node (scales horizontally) |
| **Kafka** broker | **~100K – 1M messages/s** |
| SQS (standard) | nearly unlimited (scales automatically) |
| Elasticsearch node | ~1K–10K queries/s (depends heavily on query) |

### Rule of thumb
- If your QPS < **~1K** → a single DB is fine; no fancy design needed.
- **~10K QPS** → need caching / read replicas.
- **~100K+ QPS** → need sharding + heavy caching.
- If your data < **~1 TB** → fits on one DB machine (maybe with replicas).
- If your **hot data < ~100s of GB** → fits in memory on a handful of cache nodes.

---

## 7. Availability — The "Nines"

| Availability | Downtime / year | Downtime / month | Downtime / day |
|---|---|---|---|
| 99% (two nines) | ~3.65 days | ~7.3 hours | ~14.4 min |
| 99.9% (three nines) | ~8.76 hours | ~43.8 min | ~1.44 min |
| **99.99% (four nines)** | **~52.6 min** | **~4.4 min** | ~8.6 s |
| 99.999% (five nines) | ~5.26 min | ~26 s | ~0.86 s |

> Serial components multiply: two services at 99.9% each → 99.9% × 99.9% ≈ **99.8%**.
> Redundant (parallel) components improve it: 1 − (0.001 × 0.001) = 99.9999%.

---

## 8. The Estimation Framework (Step by Step)

Do this in **~5 minutes** right after requirements. Talk out loud.

```
1. State assumptions   → users, DAU, actions per user per day, object sizes
2. Traffic (QPS)       → read QPS, write QPS, peak QPS, read:write ratio
3. Storage             → per-day, per-year, N-year, × replication
4. Bandwidth           → ingress (writes) & egress (reads) per second
5. Memory / cache      → how much hot data to cache
6. Servers / shards    → QPS ÷ per-node capacity, storage ÷ per-node disk
7. "So what?"          → what design decisions do these numbers force?
```

> ⭐ **Step 7 is the most important.** Numbers that don't change the design are wasted time.
> E.g. "100:1 read:write → read-optimised design, aggressive caching."
> "Storage is 5 PB → must shard; can't fit on one box."

### Tips while doing it
- **Write assumptions on the whiteboard** — interviewer can correct them early.
- **Round aggressively**: 86,400 → 10⁵; 2.6 → 3; 365 → 400 (or 3.65 → ~4).
- **Use scientific notation**: 5 × 10⁸ × 2 × 10³ = 10 × 10¹¹ = 10¹² = 1 TB.
- **Ask** whether the interviewer even wants detailed numbers — some prefer you skip to design and estimate only when it matters.

---

## 9. Traffic Estimation (QPS)

### Formulas
```
Average QPS = (DAU × actions per user per day) / 86,400  ≈  / 10⁵
Peak QPS    = Average QPS × 2 to 3     (sometimes ×5–10 for spiky events)
Read:Write  = Read QPS / Write QPS
```

### DAU assumptions if not given
- DAU is commonly **~50%** of MAU/total users for very popular apps (Facebook ~65%), lower for less sticky apps (10–30%).

### Example
- 500M DAU, each makes 2 writes/day and 50 reads/day.
- Writes: 500M × 2 = 10⁹/day → **~10K writes/s** (peak ~25K)
- Reads: 500M × 50 = 2.5 × 10¹⁰/day → **~250K reads/s** (peak ~600K)
- Read:Write = **25:1** → read-heavy → cache + precompute.

---

## 10. Storage Estimation

### Formula
```
Storage/day  = writes per day × size per object
Storage/year = Storage/day × 365      (≈ × 400 for easy math)
Total        = Storage/year × retention years × replication factor (usually 3)
```

### Remember to account for
- **Replication** — typically ×3.
- **Metadata / indexes** — add ~20–30% (or just round up).
- **Media separately from text** — media dominates; text is usually negligible next to it.
- **Multiple encodings** for video (360p/720p/1080p…) → ~×2–3 the original.

### Example
- 10⁹ posts/day × 1 KB = 10¹² bytes = **1 TB/day**
- × 365 ≈ **365 TB/year**
- × 3 replication ≈ **~1 PB/year**
- 5 years ≈ **~5 PB** → definitely sharded storage.

---

## 11. Bandwidth Estimation

### Formula
```
Ingress (upload)   = write QPS × size per write
Egress  (download) = read QPS × size per read response
```
Convert to network units: **bytes/s × 8 = bits/s**.

### Example
- Read QPS = 100K, each response = 20 posts × 1 KB = 20 KB
- Egress = 100K × 20 KB = 2 × 10⁹ B/s = **2 GB/s ≈ 16 Gbps**
- One server's NIC = 10 Gbps → need multiple servers just for bandwidth; media must come from **CDN**.

---

## 12. Memory / Cache Estimation

### The 80/20 rule (Pareto)
> **20% of the data generates 80% of the traffic.** Cache that 20%.

### Formula
```
Cache size = daily read volume (or daily new data) × 20%
```

### Example
- New posts/day = 1 TB. Hot set = the last day's posts.
- Cache 20% → **~200 GB**
- One Redis node with ~64–128 GB usable → **~2–4 nodes** (+ replicas). Easily fits.

### Things to mention
- The cache doesn't need *everything*, just the hot working set.
- Add replicas for availability and for hot-key read spreading.
- If hot data is > several TB, reconsider: maybe a tiered cache, or cache only IDs.

---

## 13. Server Count Estimation

### Formula
```
App servers  = Peak QPS / QPS per server   (× 1.5–2 headroom)
DB shards    = max( Total storage / storage per node ,  Peak write QPS / writes per node )
Cache nodes  = Cache size / RAM per node
Connection servers (WebSocket) = Concurrent users / connections per server
```

### Example
- Peak 300K QPS, each app server handles ~10K → 30 → with headroom **~50 servers**.
- Storage 5 PB, each DB node 10 TB → **500 shards** (before replication) → **1,500 nodes with RF=3**.

> 💡 Interviewers rarely care about exact server counts. Use it to show *"this needs to be horizontally scaled"* vs *"this fits on one box"*.

---

## 14. Worked Examples

### 14.1 Facebook News Feed
**Assumptions:** 2B users, 1B DAU, 10 feed views/user/day, 1 post/user/day, post = 1 KB, avg 200 followers.

| What | Calculation | Result |
|---|---|---|
| Read QPS | 1B × 10 / 10⁵ | **~100K/s** (exact ≈ 116K; peak ~300K) |
| Write QPS | 1B × 1 / 10⁵ | **~10K/s** (peak ~30K) |
| Read:Write | 100K / 10K | **10:1** |
| Fan-out writes | 10K posts/s × 200 followers | **~2M feed inserts/s** 😱 |
| Post storage/day | 10⁹ × 1 KB | **1 TB/day** |
| Post storage/year (×3 replicas) | 1 TB × 365 × 3 | **~1 PB/year** |
| Precomputed feeds | 2B × 200 IDs × 8 B ≈ 2B × 2 KB | **~4 TB** total |
| Feed egress | 100K × (20 posts × 1 KB) | **~2 GB/s** (text only) |
| Cache (hot posts) | 20% of 1 TB/day | **~200 GB** |

**So what?**
- 10:1 reads → precompute feeds, cache heavily.
- 2M fan-out writes/s → async queue + workers; skip inactive users and celebrities (hybrid).
- 4 TB of feeds → fits in a Redis cluster comfortably.
- 1 PB/year → sharded KV store (DynamoDB/Cassandra).

---

### 14.2 URL Shortener (TinyURL / bit.ly)
**Assumptions:** 100M new URLs/day, read:write = 100:1, row = 500 B, keep for 5 years.

| What | Calculation | Result |
|---|---|---|
| Write QPS | 10⁸ / 10⁵ | **~1K/s** |
| Read QPS | 1K × 100 | **~100K/s** |
| Total URLs in 5 years | 10⁸ × 365 × 5 | **~1.8 × 10¹¹ (~180 Billion)** |
| Key length | Base62⁶ ≈ 57B ❌, Base62⁷ ≈ 3.5T ✅ | **7 characters** |
| Storage | 1.8 × 10¹¹ × 500 B | **~90 TB** (×3 ≈ 270 TB) |
| Cache | 20% of daily reads: 0.2 × 10¹⁰ × 500 B | **~1 TB upper bound** (less after dedup of popular URLs) |

**So what?**
- 1K writes/s is small; 100K reads/s → cache in front of DB (redirects are very cacheable).
- 7-char Base62 keys are enough for 5+ years.
- 90 TB → shard the KV store.

---

### 14.3 WhatsApp / Chat
**Assumptions:** 2B users, 100B messages/day, message = 100 B, 500M concurrently online.

| What | Calculation | Result |
|---|---|---|
| Message QPS | 10¹¹ / 10⁵ | **~1M msgs/s** |
| Storage/day | 10¹¹ × 100 B | **10 TB/day** |
| Storage/year | 10 TB × 365 | **~3.65 PB/year** (before replication) |
| Bandwidth | 1M × 100 B | **~100 MB/s** (small — text is cheap) |
| Connection servers | 500M / 500K per server | **~1,000 servers** |

**So what?**
- The hard problem is **persistent connections**, not bandwidth.
- 1M msgs/s → Kafka-style log / Cassandra-style write-optimised store.
- Many products delete messages after delivery → storage drops massively (ask!).

---

### 14.4 YouTube (Video Streaming)
**Assumptions:** 500 hours of video uploaded/minute, stored at ~5 GB/hour total (all resolutions), 1B hours watched/day, avg stream bitrate ~2.5 Mbps.

| What | Calculation | Result |
|---|---|---|
| Uploads/day | 500 × 1,440 min | **~720K hours/day** |
| Storage/day | 720K × 5 GB | **~3.6 PB/day** 😱 |
| Avg concurrent streams | 1B hr × 3,600 s / 86,400 s | **~42M streams** |
| Egress | 42M × 2.5 Mbps | **~100 Tbps** |

**So what?**
- 100 Tbps egress → **CDN is mandatory**; origin servers can't serve this.
- PBs/day → blob storage (S3/GCS), tiered (hot/cold) storage.
- Transcoding is a big async pipeline.

---

### 14.5 Uber (Location Tracking)
**Assumptions:** 5M active drivers, location update every 4 s, update = 100 B, 20M rides/day.

| What | Calculation | Result |
|---|---|---|
| Location writes | 5M / 4 | **~1.25M writes/s** |
| Ingress | 1.25M × 100 B | **~125 MB/s** |
| Latest-location memory | 5M × 100 B | **~500 MB** (fits on 1 machine!) |
| Redis nodes for writes | 1.25M / 100K | **~13 → ~25 with headroom** |
| Ride QPS | 2 × 10⁷ / 10⁵ | **~200/s** (trivial) |

**So what?**
- Data is tiny, but **write throughput** is huge → in-memory store (Redis), sharded by geo cell.
- Don't persist every update to a disk DB; only keep latest (maybe sample history to cold storage).
- Rides themselves are low QPS → a normal relational DB is fine.

---

## 15. Mental Math Tricks

1. **Scientific notation always.** Multiply the front numbers, add the exponents.
   `3 × 10⁸ × 4 × 10³ = 12 × 10¹¹ = 1.2 × 10¹² = 1.2 TB`
2. **Per day → per second: ÷ 10⁵.** Mention it's ~15% low, or round up.
3. **Counts × sizes table:** Million × KB = GB; Billion × KB = TB; Billion × MB = PB.
4. **Year ≈ 400 days** for easy multiplication (or 365 ≈ 3.65 × 10²).
5. **Round to friendly numbers:** 86,400 → 100K, 2.6M → 3M, 1,024 → 1,000.
6. **Replication = ×3, Peak = ×2–3, Headroom = ×1.5–2.**
7. **Bits ↔ bytes:** ÷ 8 to get bytes; 1 Gbps ≈ 125 MB/s; 10 Gbps ≈ 1.25 GB/s.
8. **Sanity-check** with what you know: "Is 4 TB for 2B users reasonable? That's 2 KB each — yes."
9. **Units in every line.** Write "1 TB/day", never just "1".

---

## 16. Common Mistakes

| ❌ Mistake | ✅ Fix |
|---|---|
| Mixing bits and bytes (network in Gbps, storage in GB) | Always ÷ or × 8 explicitly |
| Forgetting replication | Multiply storage by 3 |
| Using average only | Mention peak = 2–3× average |
| False precision ("116,898 QPS") | "~100K QPS" |
| Spending 15 minutes on math | Cap at ~5 min; estimate what matters |
| Numbers that don't affect the design | Always end with "**so what?**" |
| Forgetting media vs text | Estimate separately; media dominates |
| Ignoring metadata / indexes | Round up ~20–30% |
| Not stating assumptions | Write them down first; let interviewer correct |
| Confusing DAU with total users | Ask, or assume DAU ≈ 50% of users |
| Computing storage per second instead of per day/year | Storage is usually per day → per year → × retention |

---

## 17. One-Page Cheat Sheet

```
DATA UNITS (powers of 10)
  1 KB = 10³ B   1 MB = 10⁶ B   1 GB = 10⁹ B   1 TB = 10¹² B   1 PB = 10¹⁵ B
  Million × KB = GB     Billion × KB = TB     Billion × MB = PB
  1 byte = 8 bits       1 Gbps ≈ 125 MB/s

SIZES
  int 4B | long 8B | timestamp 8B | UUID 16B (36B as string)
  post ~1KB | user ~1KB | chat msg ~100B | photo ~500KB–1MB
  video 1080p ~2.5 GB/hr | 720p ~1 GB/hr | MP3 ~1 MB/min

TIME
  1 day = 86,400 s ≈ 10⁵ s     1 month ≈ 2.5×10⁶ s     1 year ≈ 3×10⁷ s
  1M/day ≈ 12/s     1B/day ≈ 12K/s

POWERS OF 2
  2¹⁰≈10³  2²⁰≈10⁶  2³⁰≈10⁹  2³²≈4.3B (int)  2⁶⁴≈1.8×10¹⁹ (long)
  Base62: 6 chars ≈ 57B, 7 chars ≈ 3.5T

LATENCY
  RAM 100ns | SSD read ~100µs | same-DC RTT 0.5ms | HDD seek ~10ms | cross-continent 150ms

SINGLE NODE
  App server ~1K–10K QPS | Redis ~100K ops/s | SQL ~1K–10K writes/s
  Kafka ~100K–1M msg/s | WebSocket ~100K–1M conns | Server RAM 64–512GB | NIC 10–25Gbps

AVAILABILITY
  99.9% ≈ 8.8 hr/yr | 99.99% ≈ 53 min/yr | 99.999% ≈ 5 min/yr

FORMULAS
  QPS        = DAU × actions/day ÷ 10⁵          Peak = ×2–3
  Storage    = writes/day × size × 365 × years × 3 (replication)
  Bandwidth  = QPS × payload size
  Cache      = 20% of daily data (80/20 rule)
  Servers    = Peak QPS ÷ per-server QPS × 1.5 headroom

FRAMEWORK
  Assumptions → QPS → Storage → Bandwidth → Cache → Servers → SO WHAT?
```
