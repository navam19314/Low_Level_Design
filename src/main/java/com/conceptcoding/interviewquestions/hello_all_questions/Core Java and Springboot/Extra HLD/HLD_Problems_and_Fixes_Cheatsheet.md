# HLD Quick Guide: Common Problems → How to Fix Them

> Use this in any design: when you spot a problem on the whiteboard, **name it** ("this is a hot key problem") and **pick the fix** from this sheet.
> Format: **Problem → Fix → Where you've seen it** (Flash Sale, WhatsApp, Notification, Metrics, Ticketmaster, Uber…).

---

## 1. Too many writes ("scaling writes")
🌍 *Too many letters arriving at one post office.*
| Problem | Fix | Seen in |
|---|---|---|
| Sudden burst of writes (sale, campaign, 5M metrics/s) | **Queue as a buffer** (Kafka/SQS): accept fast, process at a safe speed | Notification, Metrics, Flash Sale |
| Too many tiny requests | **Batch** at the client/agent (send 500 at once) | Metrics agents, your Kafka logging |
| One DB can't take the load | **Shard** by a key (userId, chatId); pick a key that spreads evenly | WhatsApp (DynamoDB by userId) |
| Wrong DB type for the data | **Write-optimised DB**: time-series DB for metrics, DynamoDB/Cassandra for huge key-value | Metrics, WhatsApp |
| Queue falls behind after an outage | Keep spare capacity; for monitoring, **drop old backlog** rather than stay behind | Metrics |

## 2. Too many reads ("scaling reads")
🌍 *Everyone asking the librarian the same question.*
| Problem | Fix | Seen in |
|---|---|---|
| Same data read again and again | **Cache** (Redis / in-memory L1), cache-aside + TTL | everywhere, your L1 + Redis work |
| Static files (images, video) | **CDN** near the users | YouTube, Netflix |
| Expensive calculation on every read | **Pre-compute** (rollups, pre-built feeds, materialised views) | Metrics rollups, News Feed |
| DB overloaded by reads | **Read replicas** | most designs |
| Full-text / fuzzy search | **Elasticsearch** (inverted index), synced from the DB | Ticketmaster search, ticketing |
| "Near me" search | **Geo-index**: geohash / quadtree / Redis GEO | Uber, Yelp |

## 3. Many users fighting for the same thing ("contention")
🌍 *10,000 people, one shop counter.*
| Problem | Fix | Seen in |
|---|---|---|
| Two people buy/book the last item (oversell, double booking) | **Atomic conditional update**: `UPDATE ... WHERE count > 0` / `WHERE status='AVAILABLE'` | Flash Sale, Ticketmaster |
| Everyone updates ONE hot row | **Spread it out**: one row per unit + `SKIP LOCKED`, or split the counter into N shards | Flash Sale |
| Hold an item while the user pays | **Reservation with a timer** (Redis lock with TTL, or DB status + `expiresAt`) | Ticketmaster, Flash Sale |
| Assign a job/driver/agent to only ONE request | **Lock / compare-and-set** on the worker ("only if still AVAILABLE") | Uber, ticket routing |
| Low conflict, rare clashes | **Optimistic locking** (version number, retry on conflict) | edits, profiles |

## 4. Huge crowds at once ("thundering herd")
| Problem | Fix | Seen in |
|---|---|---|
| Millions arrive in seconds | **Waiting room / admission control**: let users in at a safe rate with a signed token | Flash Sale, Ticketmaster |
| A cache key expires and 1,000 requests hit the DB | **Singleflight / lock**: one request refreshes, the rest wait or get the old value (stale-while-revalidate) | your caching work |
| One very popular key (celebrity, live match) | **Hot key fix**: local L1 cache, replicate the key, add a random suffix | News Feed, your live-cricket traffic |
| Everyone retries at the same moment | **Exponential backoff + jitter** | everywhere |

## 5. Real-time updates
🌍 *Phone call (WebSocket) vs checking the letterbox (polling).*
| Need | Fix | Seen in |
|---|---|---|
| Few users, short wait (payment status) | **Polling** every 1–2 s, the simplest option | Flash Sale |
| Server → client only (live scores, notifications) | **SSE** | feeds, dashboards |
| Two-way, constant (chat, games) | **WebSocket** behind an **L4** load balancer | WhatsApp |
| User is connected to another server | **Pub/Sub** (Redis): a channel per user | WhatsApp |
| Silent dead connections | **Heartbeat** ping/pong every 10–30 s | WhatsApp |

## 6. Never lose data ("durability / reliability")
| Problem | Fix | Seen in |
|---|---|---|
| Crash before the work is done | Save to a **durable queue/DB first**, then process; **ACK** only after success | Notification, WhatsApp Inbox |
| Message keeps failing | **Retries with backoff → DLQ** (dead-letter queue) | Notification |
| Write to DB AND send an event (dual write) | **Outbox pattern** (event is derived from the committed row) or **CDC** | payments, orders |
| A server/zone dies | **Replication** across AZs (Kafka RF=3, DB replicas), stateless services + LB | all, Metrics HA |
| Long multi-step process (book → pay → confirm) | **Saga / workflow** (e.g. Temporal, Step Functions) with compensations | payments, booking |

## 7. Duplicates ("idempotency")
🌍 *Pressing the lift button 5 times still calls one lift.*
| Problem | Fix | Seen in |
|---|---|---|
| Client retries after a timeout and charges twice | **Idempotency key**: same key → return the first result | Payments, Notification |
| Queue delivers twice (at-least-once) | **Dedup check** (Redis set / unique DB key) before doing the work | Notification |
| Exactly-once? | Say: "**At-least-once + idempotent processing**". True exactly-once doesn't exist across systems. | everywhere |

## 8. Slow or failing dependencies
🌍 *Don't wait forever for a shop that's closed.*
| Problem | Fix | Seen in |
|---|---|---|
| A call hangs | **Timeout** on every network call; total timeouts must fit the user's budget | your 20–50 s stacked-timeouts story |
| A dependency is down and calls keep piling up | **Circuit breaker**: fail fast, retry later | Notification providers, your Kafka logging |
| One bad dependency eats all threads | **Bulkhead**: cap in-flight calls per dependency | Notification |
| Provider fully down | **Fallback**: backup provider, cached/default data (**fail open**) | Notification router, your cache |
| Urgent work stuck behind bulk work | **Separate queues by priority** | Notification (OTP vs promo) |
| Abusive / runaway client | **Rate limiter** (token bucket in Redis) | API Gateway, everywhere |

## 9. Big files
| Problem | Fix | Seen in |
|---|---|---|
| Uploading video/photos through your servers | **Pre-signed URL** → client uploads **directly to S3** | WhatsApp, Dropbox, YouTube |
| Huge files, flaky network | **Chunked / multipart upload**, resume from the last chunk | Dropbox, YouTube |
| Same file uploaded many times | **Dedup by hash** | Dropbox |

## 10. Fan-out (one event → many receivers)
| Problem | Fix | Seen in |
|---|---|---|
| Post must reach all followers' feeds | **Fan-out on write** (pre-build feeds) for normal users, **fan-out on read** for celebrities | News Feed, Twitter |
| A campaign goes to 1M users | Split into **batches** on a queue; many workers expand them | Notification |
| A group message goes to 100 members | Publish per user (small groups) or per chat (big groups) | WhatsApp |

## 11. Consistency choices
| Data | Choose | Why |
|---|---|---|
| Money, inventory, bookings | **Strong** (SQL transactions, conditional writes) | wrong = real loss |
| Feeds, likes, recommendations, dashboards | **Eventual** + cache with TTL | a few seconds stale is fine |
| Message order in chat | **Server timestamp** / sequence number per chat | fast beats perfect |
| Late / out-of-order events | Accept within a **time window** (watermark), drop very late ones | Metrics, Ad clicks |

## 12. Counting at huge scale
| Problem | Fix | Seen in |
|---|---|---|
| Count clicks/views in real time | **Stream processing** (Flink) with time windows + Kafka | Ad Click Aggregator, Top-K |
| Top-K / leaderboard | **Redis sorted set**, or count-min sketch + heap | YouTube Top-K, leaderboard |
| "Have we seen this before?" cheaply | **Bloom filter** | Web crawler, URL shortener |
| Too many unique label combos | **Cardinality limits** (allowlist + series cap) | Metrics |

## 13. Unique IDs
| Need | Fix |
|---|---|
| Short unique codes (URL shortener) | counter + **base62**, or a pre-generated key pool |
| Sortable IDs across many servers | **Snowflake** (time + machine + sequence) |
| Random, no coordination | **UUID** |

## 14. Security (say 1 line in every design)
| Problem | Fix |
|---|---|
| Data sniffed on the network | **TLS** everywhere |
| Who is the user? | **JWT / OAuth** at the API Gateway (or once at WebSocket connect) |
| Can they do this? | **Authorisation check on every request** ("is this user in this chat?") |
| Bots / abuse | **Rate limiting**, CAPTCHA |
| Private files | private bucket + **short-lived pre-signed URLs** |
| Service-to-service | **API keys / mTLS** |

## 15. Operations (the Amazon "Operational Performance" bit; say it at the end)
| Topic | Say |
|---|---|
| Metrics | p50/p99 latency, error rate, throughput, queue depth, CPU/memory |
| Alarms | alert on SLO breaches → on-call with runbooks |
| Safe deploys | canary → staged rollout → **auto-rollback** on alarms |
| Scaling | autoscaling, **pre-scale for known peaks** (sale, IPL), load shedding |
| Testing | load test (spike, soak) before big events |
| Degrade gracefully | turn off non-critical features (logging, recommendations) to protect the core |

---

## 🧠 The 10 most-used fixes (if you remember nothing else)
1. **Queue** to absorb bursts.
2. **Cache** (+ TTL) for repeated reads.
3. **Shard** by a good key.
4. **Atomic conditional update** to stop double-sell.
5. **Reservation + TTL** to hold while paying.
6. **Idempotency key** to stop duplicates.
7. **Retries + backoff + DLQ** so nothing is lost.
8. **Circuit breaker + timeouts** for slow dependencies.
9. **Pre-signed URL → S3** for files.
10. **WebSocket + Pub/Sub** for real-time.

**How to use it in the interview:** "I see a **[problem name]** here. The usual fixes are **A** and **B**. I'll pick **A** because **[trade-off]**."
