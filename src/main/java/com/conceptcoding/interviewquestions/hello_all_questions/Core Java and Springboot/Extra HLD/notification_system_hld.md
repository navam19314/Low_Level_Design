# Notification System — HLD (Hello Interview article, simplified)

> Follows the Hello Interview "Notification System" breakdown. Lines starting with **Say:** are what you speak in the interview.
> ⚠️ In your copy, the option lists inside Deep Dives 1–3 were empty. Where these notes fill them in, it's marked **(filled in)**: the standard answer that matches the article's final design (SQS, a high-priority queue, Redis token bucket + dedup cache).

---

## Part 1: Understand It First (2-minute read)

**What it is:** an **internal platform**. Other teams (the auth service, marketing, the order service) hand us *"send this message to this user"*, and we deliver it by **push, email or SMS**.
Our customers are **other engineering teams**, not end users.

**The key idea: two very different kinds of traffic**
| Type | Example | Volume | Can it wait? |
|---|---|---|---|
| **High priority** | OTP, security alert | low | ❌ must arrive in **< 5 s** |
| **Standard** | promo, digest | **huge bursts** (1M at 9 am) | ✅ 10 minutes late is fine |

Getting both through one system is **the heart of the problem**. That's why every send carries a **priority**.

🌍 **Hospital picture:** a hospital has an **emergency lane** and a **normal OPD queue**. If 1,000 people are waiting for a check-up, the heart-attack patient still doesn't stand behind them.

**Second key idea:** we are a **router and a bookkeeper**, not the delivery company. Twilio (SMS), the email provider and APNs/FCM (push) do the last mile. We decide **what to send where**, and **record what happened**.
**Say:** "We don't own the last mile. We route to providers and keep the record. I won't design socket connections to phones."

---

## Part 2: The Interview Answer

### 1. Requirements

**Functional (core)**
1. Upstream services can send a notification to a user via **push, email or SMS**, **now or scheduled**.
2. Upstream services can send **campaigns**: the same message to a **whole segment**, now or scheduled.
3. Users can set **preferences**: channel opt-outs and **quiet hours**.

**Out of scope:** template management · analytics dashboards (open/click rates) · in-app feed and badge counts · frequency capping.

**Numbers (say them, they drive the design)**
- 10M notifications/day ≈ **~100/sec** on average, which is nothing.
- But campaigns come in bursts: **1M in 5 minutes** = **3,000+/sec** for a single campaign.
- So design for surges of **~5,000/sec**, about **50× the average**.

**Non-functional (core)**
1. **At-least-once delivery** with **best-effort deduplication**: better to send twice by accident than never.
2. Handle **surges of ~5,000/sec**.
3. **High-priority** (OTP, security) delivered **within 5 s**, **even during a surge**.

**Out of scope:** strict ordering across channels · consent/spam compliance · monitoring, alerting, CI/CD.

**Say:** "These requirements pull against each other. Deduplication makes surges harder, and surges threaten the 5-second OTP target."

**Whiteboard:**
```
Functional                                   Non-functional
1. Send to a user, now or scheduled          1. At-least-once + best-effort dedup
2. Campaigns to a segment, now or scheduled  2. Surges of ~5,000/sec
3. User preferences (opt-out, quiet hours)   3. High priority < 5 s, even in a surge
```

### 2. The Set Up
**Say:** "I'll build a working system one functional requirement at a time, then use the non-functional requirements for the deep dives."

---

### 3. Core Entities
| Entity | Simple meaning |
|---|---|
| **Notification** | **one** message, to **one** user, on **one** channel, plus its status (e.g. "promo email to John at 4 pm") |
| **Campaign** | a scheduled send to a **group**: time, channel, audience, template |
| **Segment** | a named audience ("all users in Canada"). Another tool maintains the members; we just read them. |
| **User** | the recipient: contact info + preferences |
| *(Template)* | the message content a campaign uses |

💡 **Campaign vs Notification = class vs instances.** One campaign turns into a million notifications. The same shape appears in a job scheduler (a job vs its runs) and in calendars (an event vs its occurrences).

---

### 4. API
| API | What it does | Maps to |
|---|---|---|
| `POST /notifications` | sends **one** notification to **one** user on a channel (push/email/SMS), now or at `scheduledAt` | **FR1** |
| `POST /campaigns` | sends the **same message to a whole segment**, now or scheduled | **FR2** |
| `PUT /users/{userId}/preferences` | saves the user's **opt-outs and quiet hours** | **FR3** |
| `PUT /users/{userId}/devices` | saves the phone's **push token**, so we can send push notifications | **FR1** (needed for the push channel) |

```
POST /notifications                -> 200 OK { id, status }
  { userId, channel (push|email|sms), priority (high|standard), content {title, body}, scheduledAt? }

POST /campaigns                    -> 202 Accepted { id }
  { segmentId, channel, priority, templateId, scheduledAt? }

PUT  /users/{userId}/preferences   -> Preferences
  { optOuts: ["sms"], quietHours: { start: "22:00", end: "08:00", tz } }

PUT  /users/{userId}/devices       -> Device          (for push)
  { platform (ios|android), pushToken }
```
- **Separate campaign endpoint.** Don't overload `/notifications` with a `segmentId`; one endpoint shouldn't sometimes create a notification and sometimes a campaign.
- **Why 202 for campaigns?** The sending happens later, so there's no result to report yet. Single sends start as `200`, because we call the provider directly for now. That changes in Deep Dive 1.
- **PUT for preferences:** it's a full replace, so it's naturally **idempotent**.
- **Why is `userId` in the path?** Normally that's a red flag, because identity should come from the JWT. Here our callers are **internal services with API keys** acting **for** users. The product backend has already authenticated the user.
- Both POST bodies get an **idempotency key** later (Deep Dive 3). **Say:** "I'll start simple and evolve the contract."

---

### 5. High-Level Design

#### FR1: Send to a user, now or scheduled
```
Upstream (Auth/Order) → API Gateway → Notification Service → Postgres (Notifications, User data)
                                              │
                                              ├─► SMTP / Email provider
                                              ├─► Twilio (SMS)
                                              └─► APNs / FCM (Push)
```
- **API Gateway:** authenticates each service's **API key** and applies **rate limits**, so one runaway service can't break critical flows like login.
- **Notification Service:** looks up the address → saves the notification → calls the provider.
- **Postgres:** Notification rows + **our copy of user data** (email, phone, synced from the user service). Any modern DB works here.

**Send-now flow:**
1. A service POSTs `/notifications`.
2. The gateway checks the API key → forwards.
3. The service looks up the user's email or phone.
4. Writes the row as **PENDING** → calls the provider.
5. Updates the row to **SENT / FAILED** → returns `200 { id, status }`.

**Push is different:** it needs a **device token** for APNs/FCM, and tokens **rotate**. So the **mobile app sends us its token** on login and whenever it changes (`PUT /users/{id}/devices`). After that, push works like email: look up the token instead of an address.

**Scheduled sends:** save the row as **SCHEDULED** and don't call the provider. A **scheduler cron job runs every minute**, finds rows whose time has come, and sends them through the same path. Minute-level timing is fine. OTPs are always "send now", so they never wait for the cron.

#### FR2: Campaigns
- `POST /campaigns` → save a Campaign row → return **202**. So far it's just CRUD.
- The **same cron job** also scans the Campaigns table:
  1. Finds a campaign that is due.
  2. Reads the **segment members**.
  3. Sends one notification **per user** through the normal path.
  4. Marks the campaign complete.
- **Segment = snapshot at start.** Someone who joins mid-send misses this campaign and gets the next one. Fine for marketing. **Say it out loud**; noticing this is what separates senior candidates from juniors.

**Say:** "This works, but it has three problems I'll fix in the deep dives."
1. **The provider call is in the request path.** If Twilio is slow, callers wait. If we crash after writing the row but before calling the provider, the notification is **lost**, which breaks at-least-once.
2. **Campaigns starve OTPs.** A 1M campaign can sit ahead of an OTP.
3. **One machine does a million provider calls.** If it dies halfway, we don't know where it stopped.

#### FR3: Preferences
- No new components. Preferences are fields on **User**: `optOutChannels`, `quietHoursStart/End`, `timezone`.
- Check preferences **right before the provider call**.
- **Quiet hours apply only to standard notifications**, never to OTPs or fraud alerts.
- When we skip a notification, save it as **SUPPRESSED + the reason**, never drop it silently. When on-call asks "why didn't this user get it?", the answer is in the DB.

✅ All functional requirements are done. **None** of the non-functional ones are met yet; that's what the deep dives are for.

---

### 6. Deep Dives

#### Deep dive 1: never drop an accepted notification ⭐
**Problem:** the provider call happens inside the request, so a crash or a slow provider means a lost or delayed notification.
**Fix: take delivery off the request path. Put it on a durable queue (SQS).**

**(filled in) The flow:**
1. The Notification Service validates the request → **publishes to the queue** → returns **202 Accepted** with an `id`. "Accepted" no longer means "delivered".
2. **Workers** pull from the queue → check preferences → call the provider → write **SENT / FAILED / SUPPRESSED** to the DB.
3. A worker deletes the queue message **only after** the provider call finishes. If the worker crashes, the message reappears after the **visibility timeout** and another worker retries it.
4. Retry with **backoff**. After N failures → **dead-letter queue (DLQ)** for investigation.
5. The scheduler cron also just **puts due items on the queue**. A campaign is split into **batches/chunks** that many workers expand, so no single machine does the whole million.

**Watch out for the dual write:**
- ❌ "Write to the DB first, then publish to the queue": a crash between the two leaves a row with no message, which is lost.
- The **outbox pattern** fixes that, but it's overkill here. Outbox is for when you must commit DB state **and** a message together. Accepting a notification is **just one publish**.
**Say:** "Accepting a notification is a single durable publish. Workers write the status afterwards, so there's no dual write."

**SQS or Kafka?** Both work. Kafka's advantage is **replay**: if a bad worker build ships, reset the offset and resend this morning's notifications. The requirements don't ask for that, so we use **SQS**.
*(Patterns: multi-step processes · scaling writes with a durable queue as a buffer.)*

#### Deep dive 2: OTPs in < 5 s, even during a campaign blast ⭐⭐ (the crux)
**Problem in numbers:** a 1M campaign at 9 am. Providers allow **5,000/sec** in total, so the queue takes **~200 s** to drain. Fine for a promo. But an **OTP that arrives just after** sits behind 1M messages and waits **200 s**. ❌

**(filled in) Fix: isolate by priority**
- **Two queues + two worker pools:** a **High Priority Queue** (OTP, security) and a **Standard Queue** (promos, digests). The OTP never waits behind promos.
- **Shared provider limit, using a token bucket in Redis:** the providers' 5,000/sec is shared by both pools. **Reserve capacity for high priority**; standard workers take only what's left. When an OTP arrives, standard traffic slows down, not the OTP.
- This is why **every send declares a priority** in the API.

**Say:** "Separate queues and workers per priority, with a shared Redis token bucket for provider limits that reserves capacity for high priority. Campaigns can wait 3 minutes; OTPs can't wait at all."

**Surviving a provider brownout** (e.g. Twilio's p99 goes from < 1 s to 30 s, so every call ties up a worker for 30 s):
| Technique | What it does |
|---|---|
| **Circuit breaker** | track the failure/timeout rate per provider. Over a threshold, the breaker **opens**: calls fail fast instead of waiting 30 s, and we shed non-essential load to keep room for high priority |
| **Bulkhead** | cap **in-flight calls per provider** (like a semaphore), so a mostly-down provider can't tie up all our workers |
| **Provider router** | a **primary + backup** provider per channel. The UI can help too: if SMS is down, offer email for login codes |

We can't beat physics: while a provider is degraded, not everything arrives on time. The goal is to **keep high priority moving** and let the rest wait.

#### Deep dive 3: no duplicates on top of at-least-once
**Where duplicates come from:**
1. **A worker dies after calling Twilio but before deleting the queue message** → the visibility timeout expires → another worker sends it **again**.
2. **A caller retries:** it POSTs, times out, and POSTs again.
3. **The cron crashes mid-fan-out** → on re-run, the already-queued users get queued **again**.

**(filled in) Fix: deduplicate at each entry point**
| Where duplicates enter | Fix |
|---|---|
| Caller retry | **Idempotency key** in the POST body. Store it (Redis **dedup cache** with a TTL, or a unique key in the DB). Same key → return the original `id` and don't enqueue again. |
| Cron re-fan-out | **Deterministic notification id**, e.g. `hash(campaignId + userId)`, so the re-queued copy has the same id and is caught by dedup |
| Worker retry | before calling the provider, the worker checks the dedup cache / status for this notification id: if it's already **SENT**, skip it. Mark it sent right after the provider call succeeds. |
| Provider | pass our notification id as the provider's **idempotency key** where the provider supports it |

There's still a tiny window: the provider sent it, but we crashed before recording it. So it's **best-effort**.
**Say:** "Exactly-once stops at the provider boundary. The honest design is **at-least-once with deduplication at each point where duplicates can enter**: idempotency keys from callers, deterministic ids for campaign fan-out, and a dedup check in the workers." Point at each spot on the diagram.

---

### 7. Final Design
```
Upstream (Auth/Order/Marketing)
        │
        ▼
   API Gateway  (API key auth, rate limits)
        │
        ▼
 Notification Service ──writes campaigns & preferences──► Postgres
        │                                                  (notifications, campaigns,
        │ publish by priority                               segments, users + prefs)
        ├──────────────► High Priority Queue ──► HP Workers ──┐
        └──────────────► Standard Queue ───────► Std Workers ─┤
                                ▲                              │ read prefs,
 Scheduler Cron ─ due sends & ──┘                              │ write SENT/FAILED/SUPPRESSED
   campaign fan-out                                            ▼
                                       Redis (token bucket, dedup cache)
                                                               │
                                     ┌─────────────┬───────────┴──────┐
                                     ▼             ▼                  ▼
                                Twilio (SMS)   SMTP (Email)     APNs / FCM (Push)
```

---

### 8. Extra deep dives the interviewer might pick (just know the idea)
| Topic | One-line idea |
|---|---|
| **Send-time optimisation** | an ML model picks each user's best time inside marketing's window; the pipeline already handles millions of small scheduled sends |
| **Frequency capping** | a per-user daily counter (e.g. in Redis), checked next to the preference check at send time |
| **Campaign cancellation** | rare but serious (legal, a typo); workers must check a cheap "cancelled" flag (cached) before sending |
| **Timezone-aware delivery** | "9 am for each user" = about 24 due times instead of one, and the same scheduler handles it |

### 9. What's expected at each level
**Mid-level (E4):** a clear API and data model (send + campaign endpoints with **priority**, a Campaign that fans out into Notifications); a working design for send-now **and** scheduled campaigns; noticing that **the synchronous provider call can drop notifications**, and reaching the durable-queue fix (with some hints). Priority isolation and dedup ordering aren't expected without help.
*(Your copy cut off after mid-level. As SDE II, aim to raise **priority isolation**, **brownout handling** and **dedup points** yourself.)*

---

## Part 3: Revision Cheat Sheet (1 minute)
1. **Platform for internal teams.** We're a **router + bookkeeper**; providers do the last mile.
2. **Numbers:** 10M/day ≈ 100/s, but **surges of 5,000/s** (50×).
3. **Entities:** Notification (instance) vs Campaign (definition), Segment, User.
4. **APIs:** `POST /notifications` (priority!), `POST /campaigns` → 202, `PUT preferences`, `PUT devices` (push token).
5. **Scheduled:** status SCHEDULED + a cron every minute; segment snapshot at fan-out.
6. **Preferences:** checked right before sending; quiet hours skip OTPs; save **SUPPRESSED + reason**.
7. **DD1, never drop:** accept = **one publish to SQS** → 202 → workers send + write status; visibility timeout, retries, DLQ; no DB-then-queue dual write.
8. **DD2, OTP < 5 s:** **separate high-priority queue + workers**, a Redis **token bucket** reserving provider capacity; brownouts → **circuit breaker, bulkhead, backup provider**.
9. **DD3, duplicates:** at-least-once + dedup at every entry point: **idempotency key**, **deterministic campaign ids**, **worker dedup check**, provider idempotency.
