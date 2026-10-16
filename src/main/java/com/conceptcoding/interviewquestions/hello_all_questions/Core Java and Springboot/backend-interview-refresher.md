# Backend Interview Refresher (Gaps Only)

> Each topic: **Simple** (1–2 lines) · **Example** · **Key points** · **Follow-ups** (Q → short answer)
> Topics already covered in your notes are listed below — not repeated here.

## Already covered — where to look

| Topic | File |
|---|---|
| REST principles, HTTP methods, status codes, idempotent methods, versioning | [spring-boot/03](spring-boot/03-rest-api-exceptions.md) |
| AuthN vs AuthZ, JWT, OAuth2, sessions | [spring-boot/06](spring-boot/06-security-jwt.md) |
| Transactions, ACID, isolation levels, dirty/phantom reads | [spring-boot/05](spring-boot/05-transactions.md) |
| Optimistic vs pessimistic locking, N+1, lazy vs eager | [spring-boot/04](spring-boot/04-jpa-hibernate.md) |
| Microservices, discovery, gateway, Saga, sync vs async, idempotency keys, outbox, circuit breaker / bulkhead / timeouts | [spring-boot/07](spring-boot/07-microservices.md) |
| Kafka, delivery semantics (at-most/at-least/exactly-once), DLT, @Cacheable | [spring-boot/08](spring-boot/08-aop-caching-async-kafka.md) |
| Spring Boot (IoC, beans, annotations, @Transactional, JPA, @ControllerAdvice, security, profiles, actuator) | [spring-boot/](spring-boot/) |
| Java Core, Collections, equals/hashCode, exceptions, Java 8+, JVM & GC | [core-java/](core-java/) |
| Multithreading, locks, CompletableFuture, producer–consumer | [core-java/07](core-java/07-multithreading-concurrency.md) |

---

# 1. API Design (gaps)

## Pagination — Offset vs Cursor
**Simple:** Return big lists in pages. Offset = "skip N rows". Cursor = "give me items after this one".
**Example:** Admin table with page numbers → offset. Instagram/Twitter infinite feed → cursor.

| Offset (`?page=3&size=20`) | Cursor (`?after=abc123&limit=20`) |
|---|---|
| Easy, can jump to any page | Only next/previous |
| Slow on deep pages (DB still scans skipped rows) | Fast at any depth (`WHERE id > last_id`, uses index) |
| Duplicates/missed items if data changes | Stable while data changes |

**Key points**
- Cursor = encoded last-seen sort key (e.g. `created_at + id`), usually Base64 so clients treat it as opaque.
- Filtering/sorting via query params: `GET /orders?status=PAID&sort=-createdAt` — whitelist allowed fields; index the filter/sort columns.

**Follow-ups**
- **Why is offset slow on page 10,000?** `OFFSET 200000` still reads and throws away 200k rows.
- **Cursor with non-unique sort column?** Add a tiebreaker: sort by `(created_at, id)`.

## REST vs GraphQL vs gRPC
**Simple:** Three ways for clients to call servers.
**Example:** Public API → REST. Mobile app needing flexible data in one call → GraphQL. Internal service-to-service, high throughput → gRPC.

| | REST | GraphQL | gRPC |
|---|---|---|---|
| Format | JSON over HTTP | JSON, single endpoint | Protobuf (binary) over HTTP/2 |
| Strength | Simple, cacheable, universal | Client asks for exactly the fields it needs | Fast, typed contract, streaming |
| Weakness | Over/under-fetching | Hard to cache, N+1 in resolvers, complex | Not browser-friendly, binary is hard to debug |

**Follow-ups**
- **Why gRPC is faster?** Binary Protobuf (smaller) + HTTP/2 multiplexing + generated clients.
- **GraphQL N+1?** Each field resolver hits the DB → fix with batching (DataLoader).

---

# 2. Databases

## SQL vs NoSQL
**Simple:** SQL = tables with fixed schema, joins and ACID. NoSQL = flexible models built to scale out.
**Example:** Bank ledger, orders → Postgres. User activity feed, IoT telemetry, sessions → Cassandra/DynamoDB/Redis.

| Choose SQL when | Choose NoSQL when |
|---|---|
| Relationships + joins | Simple access by key |
| Strong consistency / transactions | Massive scale, high write throughput |
| Schema is stable | Schema changes often / semi-structured data |

**NoSQL types:** Key-value (Redis, DynamoDB) · Document (MongoDB) · Wide-column (Cassandra) · Graph (Neo4j).

**Follow-ups**
- **Can SQL scale?** Yes — read replicas, partitioning, sharding (Vitess, Citus) — just more effort.
- **"NoSQL means no transactions"?** Not always — MongoDB and DynamoDB support transactions, with limits.

## Indexing
**Simple:** An index is a sorted lookup structure so the DB finds rows without scanning the whole table.
**Example:** Index at the back of a book — jump to the page instead of reading every page.

**Key points**
- **B-tree** (default): good for `=`, `<`, `>`, `BETWEEN`, `ORDER BY`. Lookup O(log n). (Hash index → only `=`.)
- **Composite index** `(a, b, c)`: works for `a`, `a,b`, `a,b,c` — **not** `b` alone (**leftmost prefix rule**). Put the equality-filter column first.
- **Covering index**: index contains all columns the query needs → answered from the index only (no table lookup).
- **When indexes hurt:**
  - Slower writes — every INSERT/UPDATE/DELETE updates each index.
  - Extra storage.
  - Low-cardinality columns (e.g. `gender`) — little benefit.
  - Not used when you wrap the column in a function (`WHERE LOWER(email)=...`) or use a leading wildcard (`LIKE '%abc'`).

**Follow-ups**
- **How to check if an index is used?** `EXPLAIN` / `EXPLAIN ANALYZE`.
- **Clustered vs non-clustered?** Clustered = table rows stored in index order (one per table, e.g. MySQL InnoDB primary key). Non-clustered = separate structure pointing to rows.

## Normalization vs Denormalization
**Simple:** Normalize = store each fact once (no duplication). Denormalize = copy data to avoid joins and make reads faster.
**Example:** Normalized: `orders.user_id → users.name`. Denormalized: store `user_name` inside `orders` for a fast order-list page.

| Normalized | Denormalized |
|---|---|
| No duplicate data, easy updates | Fast reads, fewer joins |
| More joins (slower reads) | Duplicate data must be kept in sync |
| OLTP / write-heavy | Read-heavy, analytics, NoSQL |

**Follow-ups**
- **How to keep denormalized copies in sync?** Same transaction, or events (CDC/outbox) for eventual sync.

## Replication
**Simple:** Keep copies of the same data on multiple servers — for availability and to spread reads.
**Example:** One primary Postgres takes writes; 3 read replicas serve product-page reads.

**Key points**
- **Leader–follower** (most common): writes go to the leader, reads can go to followers.
- **Sync** replication = no data loss but slower writes. **Async** = fast but **replication lag** (follower may be behind).
- Multi-leader / leaderless (Cassandra, DynamoDB) → higher write availability, but conflicts to resolve.

**Follow-ups**
- **User updates profile, refreshes, sees old data?** Replication lag → read-your-own-writes: read from the leader for a short time after a write.
- **Leader dies?** Failover — promote a follower (possible loss of un-replicated async writes).

## Sharding & Partitioning
**Simple:** Split data into pieces. **Partitioning** = split a table inside one DB. **Sharding** = spread pieces across different servers.
**Example:** Orders partitioned by month in one Postgres. Users sharded by `userId` across 10 DB servers.

**Key points**
- **Shard key** choice is everything — must spread load evenly and match the main query.
  - **Hash-based** → even spread, but range queries hit all shards.
  - **Range-based** → good range queries, but **hotspots** (e.g. all new data hits the latest shard).
- Downsides: cross-shard joins/transactions are hard, resharding is painful (consistent hashing helps).

**Follow-ups**
- **Celebrity / hot-key problem?** One key gets huge traffic → add a random suffix to split it, or cache it.
- **Shard first or replicate first?** Replicate + cache first; shard only when writes or data size exceed one machine.

## CAP Theorem & Eventual Consistency
**Simple:** During a **network partition**, a distributed system must choose **Consistency** (everyone sees the latest data or gets an error) or **Availability** (always respond, maybe with stale data).
**Example:** Bank balance → choose C. Social media like-count → choose A.

**Key points**
- **P is not optional** in distributed systems → the real choice is **CP vs AP**.
- **Eventual consistency:** replicas may differ briefly but converge if writes stop.
- **PACELC** (bonus): even with no partition, you trade **latency vs consistency**.

**Follow-ups**
- **Examples?** CP: ZooKeeper, HBase, banking systems. AP: Cassandra, DynamoDB (default), DNS.
- **Is your design CP or AP?** Usually mixed: payments CP, feeds/notifications AP.

---

# 3. Caching

## Caching Strategies
**Simple:** Rules for how the cache and DB are read and written together.
**Example:** Product page reads → cache-aside. View counters → write-back.

| Strategy | How | Good for | Risk |
|---|---|---|---|
| **Cache-aside** (most common) | App reads cache → on miss reads DB and fills cache. On write: update DB, **delete** cache key | Read-heavy | First read is a miss; brief staleness |
| **Write-through** | Write cache + DB together | Data must be fresh in cache | Slower writes; caches unused data |
| **Write-back** (write-behind) | Write cache now, flush to DB later | Very write-heavy (counters) | Data loss if cache dies before flush |
| Write-around | Write DB only; cache fills on read | Write-once, rarely-read data | Read after write is a miss |

**Follow-ups**
- **Why delete the cache on update instead of updating it?** Avoids race conditions where two writers leave the cache with the older value.

## Eviction Policies
**Simple:** What to remove when the cache is full.
- **LRU** — remove least recently used (most common default).
- **LFU** — remove least frequently used (keeps long-term popular items).
- **TTL** — expire after a fixed time (limits staleness; use with LRU/LFU).
- FIFO — oldest first (rarely ideal).

**Example:** News site — TTL 60s on headlines + LRU when memory is full.

**Follow-ups**
- **LRU vs LFU?** LRU adapts to recent trends; LFU protects steady favorites but reacts slowly to new popular items.
- **Redis eviction?** Configured by `maxmemory-policy` (e.g. `allkeys-lru`, `allkeys-lfu`, `volatile-ttl`).

## Cache Invalidation & Stampede
**Simple:** Invalidation = removing stale data. Stampede (thundering herd) = a hot key expires and thousands of requests hit the DB at once.
**Example:** Homepage cache expires at 9:00 → 10k requests hit Postgres together.

**Fixes for stampede**
- **Lock / single-flight:** only one request rebuilds the key; others wait or get the old value.
- **Jittered TTL:** random expiry so keys don't all expire together.
- **Early refresh:** refresh hot keys in the background before they expire.

**Follow-ups**
- **Cache penetration?** Requests for keys that don't exist always miss → cache "null" briefly or use a Bloom filter.

## Redis Use Cases
**Simple:** In-memory key-value store with rich data types; ~100k ops/sec per node.

| Use | How |
|---|---|
| Cache | `GET`/`SET` with TTL |
| Session store | Key = sessionId, with TTL |
| Rate limiter | `INCR` + `EXPIRE` per user per window |
| Distributed lock | `SET key value NX PX 30000` |
| Leaderboard | Sorted set (`ZADD`, `ZREVRANGE`) |
| Counters | `INCR` (atomic) |
| Queue / pub-sub | Lists, Streams, Pub/Sub |

**Follow-ups**
- **Is Redis durable?** Optional — RDB snapshots and/or AOF log. Treat it as a cache unless configured for persistence.
- **Single-threaded — how is it fast?** In-memory + event loop, no lock contention; commands are atomic.

---

# 4. Messaging & Async (gaps)

## Queue vs Pub-Sub
**Simple:** **Queue** = each message is processed by **one** consumer. **Pub-sub** = each message goes to **every** subscriber.
**Example:** Queue → "send this email" (one worker sends it). Pub-sub → "OrderPlaced" goes to billing, inventory and notifications.

| | SQS | RabbitMQ | Kafka |
|---|---|---|---|
| Model | Managed queue (SNS for pub-sub fan-out) | Broker with flexible routing (exchanges) | Distributed log (pub-sub via consumer groups) |
| After consume | Deleted | Deleted after ack | **Kept** (retention) → replay possible |
| Best for | Simple AWS task queues | Complex routing, task queues | High throughput, event streaming, replay |

**Key points (SQS)**
- **Standard** = at-least-once, best-effort order. **FIFO** = ordered within a message group, deduplication.
- **Visibility timeout:** a message is hidden while a consumer works on it; if not deleted in time, it reappears → make consumers idempotent.

**Follow-ups**
- **SNS + SQS fan-out?** Publish once to SNS → each subscribing SQS queue gets a copy.

## Retries, Backoff & DLQ
**Simple:** Retry failed work, wait longer each time, and park messages that keep failing.
**Example:** Payment webhook fails → retry after 1s, 2s, 4s, 8s → still failing → move to a dead-letter queue for investigation.

**Key points**
- **Exponential backoff + jitter** (random delay) → stops all clients retrying at the same moment.
- Retry only **transient** errors (timeouts, 503) — not 400s.
- Set a **max retry count** → then DLQ + alert.
- Retries require **idempotent** handlers.

**Follow-ups**
- **Retry storm?** Many services retrying amplify load → backoff, jitter, retry budgets, circuit breaker.

## Event-Driven Architecture
**Simple:** Services publish events ("OrderPlaced") and others react, instead of calling each other directly.
**Example:** Order service publishes `OrderPlaced` → inventory reserves stock, email service sends confirmation, analytics records it.

| Pros | Cons |
|---|---|
| Loose coupling, easy to add consumers | Harder to debug and trace |
| Absorbs traffic spikes | Eventual consistency |
| Natural audit trail | Duplicate / out-of-order events to handle |

**Follow-ups**
- **Event vs command?** Event = "this happened" (past, many listeners). Command = "do this" (one handler).
- **Reliably publish after a DB write?** Outbox pattern (see [spring-boot/07](spring-boot/07-microservices.md)).

---

# 5. Scalability & Reliability (gaps)

## Vertical vs Horizontal Scaling & Stateless Services
**Simple:** Vertical = a bigger machine. Horizontal = more machines.
**Example:** Upgrade 8 → 32 CPU (vertical) vs run 10 copies behind a load balancer (horizontal).

| Vertical | Horizontal |
|---|---|
| Simple, no code change | Near-unlimited scale, fault tolerant |
| Hardware limit, single point of failure | Needs a load balancer + stateless app |

**Key points**
- **Stateless service** = no user data kept in server memory between requests → any instance can serve any request.
- Move state out: sessions → Redis/JWT, files → S3, data → DB.

**Follow-ups**
- **Why are sticky sessions bad?** Uneven load, and the user loses their session if that server dies.

## Load Balancing
**Simple:** Spread incoming requests across many servers.
**Example:** An AWS ALB sends traffic across 5 app instances and skips unhealthy ones.

**Algorithms**
- **Round robin** — in turn. **Weighted round robin** — bigger servers get more.
- **Least connections** — to the least busy server (good for long requests).
- **Least response time** — to the fastest server.
- **IP hash / consistent hashing** — same client → same server.

**Key points**
- **L4** (TCP level, fast, no request inspection) vs **L7** (HTTP level — can route by path/header, terminate SSL).
- **Health checks** remove dead servers automatically.

**Follow-ups**
- **The load balancer is a single point of failure?** Run it in pairs (active-passive) or use a managed LB.

## Rate Limiting Algorithms
**Simple:** Limit how many requests a client can make in a time window.
**Example:** 100 requests per minute per API key → over limit → `429 Too Many Requests`.

| Algorithm | How | Note |
|---|---|---|
| **Token bucket** | Tokens refill at a fixed rate; each request uses one | **Allows short bursts** — most common |
| Leaky bucket | Requests leave at a fixed rate | Smooth output, no bursts |
| Fixed window | Count per minute, reset each minute | Simple, but 2x burst at window edges |
| **Sliding window log** | Store each request timestamp | Accurate, uses more memory |
| **Sliding window counter** | Weighted mix of current + previous window | Good accuracy, low memory |

**Key points**
- Distributed limiter → keep counters in **Redis** (`INCR` + `EXPIRE`, or a Lua script for atomicity).
- Return `429` + `Retry-After` header. Limit per user / API key / IP.

**Follow-ups**
- **Where to rate limit?** API gateway (global), plus inside services for protection.

## Consistent Hashing
**Simple:** Place servers and keys on a hash ring; each key goes to the next server clockwise. Adding or removing a server moves only a small share of keys.
**Example:** Distributed cache with 4 nodes → add a 5th → only ~1/5 of keys move (with `hash % N`, almost all keys would move).

**Key points**
- **Virtual nodes:** each server appears many times on the ring → even load.
- Used in: Cassandra, DynamoDB, Memcached clients, CDNs, load balancers.

**Follow-ups**
- **Why not `hash(key) % N`?** Changing N remaps nearly every key → mass cache misses.

## Observability — Logs, Metrics, Traces
**Simple:** The three ways to see what a running system is doing.
**Example:** Alert fires on high p99 latency (metric) → trace shows the slow DB call → logs show the exact query error.

| | What | Tools |
|---|---|---|
| **Logs** | Detailed event records | ELK, Loki, CloudWatch |
| **Metrics** | Numbers over time (QPS, latency, errors) | Prometheus + Grafana |
| **Traces** | One request's path across services (traceId/spanId) | Jaeger, Zipkin, OpenTelemetry |

**Key points**
- **Golden signals:** latency, traffic, errors, saturation.
- Use **p95/p99** latency, not averages. Alert on symptoms users feel.
- Structured (JSON) logs with a `traceId` on every line.

**Follow-ups**
- **SLI vs SLO vs SLA?** Measured value (99.95% success) vs internal target (99.9%) vs contractual promise with penalties.

---

# 10. Networking & Web Basics

## What Happens When You Type a URL
**Simple:** The browser finds the server, connects securely, asks for the page, and renders it.
1. **DNS lookup** → domain to IP (browser cache → OS → resolver → root → TLD → authoritative server).
2. **TCP connection** (3-way handshake: SYN → SYN-ACK → ACK).
3. **TLS handshake** (HTTPS) → verify the certificate, agree on an encryption key.
4. **HTTP request** → often via CDN / load balancer → app server → DB/cache.
5. **Response** → browser parses HTML, fetches CSS/JS/images, renders the page.

**Follow-ups**
- **Where can it be slow?** DNS, TLS handshake, server processing, large assets → fix with caching, CDN, HTTP/2, keep-alive.

## TCP vs UDP
**Simple:** TCP = reliable, ordered delivery. UDP = fast, no guarantees.
**Example:** TCP → web pages, payments, file transfer. UDP → video calls, gaming, DNS queries.

| TCP | UDP |
|---|---|
| Connection-based (handshake) | Connectionless |
| Guaranteed, ordered, retransmits | May lose or reorder packets |
| Slower, more overhead | Fast, low latency |

**Follow-ups**
- **Why does video calling use UDP?** A late packet is useless — better to skip it than wait for a retransmit.

## HTTP/1.1 vs HTTP/2 vs HTTP/3
| HTTP/1.1 | HTTP/2 | HTTP/3 |
|---|---|---|
| Text, one request at a time per connection | Binary, **multiplexing** (many requests on one connection) | Runs on **QUIC (UDP)** |
| Head-of-line blocking per connection | Header compression (HPACK) | No TCP head-of-line blocking, faster setup |

**Follow-ups**
- **Why still slow on bad networks with HTTP/2?** One lost TCP packet blocks all streams → HTTP/3 fixes this with QUIC.

## HTTPS / TLS
**Simple:** HTTP + encryption. TLS proves the server's identity (certificate) and encrypts the traffic.
**Example:** The padlock in the browser — your password can't be read on public Wi-Fi.

**Key points**
- Handshake uses **asymmetric** crypto (public/private key) to agree on a key → data uses fast **symmetric** encryption.
- Certificate is signed by a trusted **Certificate Authority (CA)**; the browser checks it.
- TLS 1.3 → 1 round-trip handshake.

**Follow-ups**
- **Why not asymmetric encryption for everything?** Too slow — it's used only to exchange the symmetric key.
- **TLS termination?** Decrypt at the load balancer; plain HTTP inside the private network (or re-encrypt for zero-trust).

## WebSockets vs Polling vs SSE
**Simple:** Ways for the server to send updates to the client.
**Example:** Chat → WebSocket. Live score / notifications → SSE. Rarely-changing status → polling.

| | How | Use |
|---|---|---|
| Short polling | Client asks every N seconds | Simple, wasteful |
| Long polling | Server holds the request until there's data | Fallback for real-time |
| **SSE** | One-way server → client stream over HTTP | Feeds, notifications |
| **WebSocket** | Two-way persistent connection | Chat, games, collaboration |

**Follow-ups**
- **Scaling WebSockets?** Connections are stateful → sticky routing + pub-sub (Redis/Kafka) to deliver messages across servers.

## DNS
**Simple:** The internet's phonebook — turns `google.com` into an IP address.
**Key points**
- Records: **A** (→ IPv4), **AAAA** (→ IPv6), **CNAME** (alias → another name).
- **TTL** controls caching; low TTL = faster failover, more lookups.
- DNS can do simple load balancing / geo-routing (e.g. Route 53).

**Follow-ups**
- **Why did a DNS change take hours?** Old records were cached until their TTL expired.

## CDN
**Simple:** Servers around the world that cache content close to users.
**Example:** A user in Mumbai gets Netflix images from a Mumbai edge server, not from the US.

**Key points**
- Best for static content (images, JS, CSS, video); can also cache API responses.
- **Pull CDN** (fetch from origin on a miss) vs **push CDN** (you upload content in advance).
- Control with `Cache-Control` headers; purge/invalidate on change; use versioned file names (`app.v42.js`).

**Follow-ups**
- **Private content on a CDN?** Signed URLs / signed cookies with short expiry.

---

# 11. DevOps Basics

## Docker — Containers vs VMs
**Simple:** A container packages an app with its dependencies so it runs the same everywhere.
**Example:** "Works on my machine" → ship the same Docker image to dev, test and prod.

| Container | VM |
|---|---|
| Shares the host OS kernel | Has its own full OS (hypervisor) |
| MBs, starts in seconds | GBs, starts in minutes |
| Weaker isolation | Stronger isolation |

**Key points**
- **Image** = read-only template (built from a Dockerfile, in layers). **Container** = running instance of an image.
- Keep images small (slim base image, multi-stage builds); don't run as root.

**Follow-ups**
- **Image vs container?** Class vs object.

## Kubernetes Basics
**Simple:** Runs and manages containers across many machines — scaling, restarting and networking them.

| Object | What |
|---|---|
| **Pod** | Smallest unit — one or more containers sharing network/storage |
| **Deployment** | Keeps N pod replicas running; rolling updates and rollback |
| **Service** | Stable IP/DNS name + load balancing to pods (ClusterIP, NodePort, LoadBalancer) |
| Ingress | HTTP routing from outside into Services |
| ConfigMap / Secret | Config and secrets injected into pods |
| HPA | Auto-scales pods on CPU/metrics |

**Key points**
- **Liveness probe** fails → restart the container. **Readiness probe** fails → stop sending traffic.
- Pods are temporary (IPs change) → always reach them through a Service.

**Follow-ups**
- **Zero-downtime deploy?** Rolling update + readiness probe + graceful shutdown.

## CI/CD
**Simple:** CI = automatically build and test every change. CD = automatically release it.
**Example:** Push to `main` → build → unit tests → Docker image → deploy to staging → integration tests → deploy to prod.

**Key points**
- Continuous **Delivery** = always ready to release (manual approval). Continuous **Deployment** = releases automatically.
- Safe releases: **blue-green** (switch traffic between two environments), **canary** (release to 5% first), feature flags.
- Tools: GitHub Actions, Jenkins, GitLab CI, ArgoCD.

**Follow-ups**
- **Blue-green vs canary?** Blue-green = instant switch and instant rollback. Canary = gradual rollout, limits blast radius.

## AWS Basics
| Service | What | Use |
|---|---|---|
| **EC2** | Virtual machines | Run apps with full control |
| **S3** | Object storage, practically unlimited, 11 nines durability | Files, backups, static sites |
| **RDS** | Managed SQL DB (Postgres, MySQL) with backups, replicas, failover | Relational data |
| **Lambda** | Serverless functions, pay per call, max 15 min | Event handlers, light APIs |
| **SQS** | Managed message queue | Decouple services |

**Follow-ups**
- **Lambda cold start?** First call after idle is slow while the runtime starts → provisioned concurrency, smaller packages.
- **EC2 vs Lambda?** Long-running / steady traffic → EC2/containers. Spiky, short tasks → Lambda.
- **Multi-AZ RDS?** Synchronous standby in another availability zone for automatic failover (not for read scaling — use read replicas for that).
