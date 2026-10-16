# Production Incidents & On-Call — Interview Stories

> Questions you'll get: *"Tell me about a production issue you handled." · "You were on call — what broke and how did you fix it?" · "Hardest bug you debugged?" · "A time you made a mistake in production?"*
>
> Every story below is **real** — from the customer API's history and the team's incident write-ups (`docs/OPTIMIZATION_JOURNEY.md`, `docs/GETREC_TIMEOUT_ANALYSIS.md`).
> - **Stories 1–4** = fixes you wrote yourself.
> - **Stories 6–10** = **team incidents** you were involved in, even where the final commit isn't yours (triage, debugging, testing, deploying, monitoring all count).
>
> Rule for team stories: **"we" for the team's fix, "I" for your real part.** That's normal — interviewers expect incidents to be team efforts and want to know *your* role.
> Fill the ✍️ parts with what you remember. **Don't add numbers you can't back up.**

### How to describe your role when it wasn't your commit (pick what's TRUE)
| Role | How to say it |
|---|---|
| On call / first responder | "I was on call — I picked up the alert, checked the dashboards and pulled in the right people." |
| Triage / analysis | "I compared pod metrics and logs and narrowed it down to ___." |
| Reproduced it | "I reproduced it locally / in SIT by ___ so we could test fixes." |
| Tested the fix | "I tested the fix in SIT under load and verified ___." |
| Deployed / rolled back | "I handled the rollout / rollback and watched metrics after deploy." |
| Monitored | "I monitored CPU, memory and error rates through the next match." |
| Follow-up | "I wrote the tests / documentation / fixed related issues afterwards." |

---

## How to answer any incident question (the 6-step shape)
```
1. CONTEXT   – what the system does, when it happened            (1–2 lines)
2. DETECT    – how you noticed (alert, dashboard, user/ops report)
3. MITIGATE  – what you did first to stop the pain
4. ROOT CAUSE – how you found the real reason (incl. wrong guesses)
5. FIX       – the change, how you tested and shipped it
6. PREVENT   – what stops it happening again + what you learned
```
💡 Interviewers love: **a wrong first guess**, **calm mitigation before the perfect fix**, **"I" for your actions**, and **a lesson**.

---

## Story 1 ⭐ — ML traffic spike caused by a cache bug (Aug 2025) — *your best one*
**Your commits:** `95cdae8`, `34a08f5`, `5c14b40` (all 2025-08-18)

| Step | What happened |
|---|---|
| **Context** | Our recommendation service calls an external ML API to get content for TV rails, and caches the ML results so we don't call the API on every request. |
| **Detect** | We saw a **spike in calls / TPS to the ML API** from our fleet. ✍️ *(alert from the ML team? dashboard?)* |
| **First guess (wrong)** | I suspected **HTTP connection handling**. I turned keep-alive off, then tried a tuned keep-alive config with socket limits. It didn't solve it. |
| **Root cause** | Reading the cache code, I found the **freshness check was given the wrong value**: `timeValidate(resp, key)` was passing the **cache key** instead of the algorithm's **cache time**. So valid cached results were treated as **expired**, and every request went back to the ML API. |
| **Fix** | Pass the right value: `timeValidate(resp, global.mlalgo[key].cacheTime)`. I **reverted my connection experiments** since they weren't the cause. |
| **Result** | ML calls went back to normal; cached results were reused for their full cache time. ✍️ *(how quickly, what you checked)* |
| **Prevent / learn** | A one-argument bug can multiply downstream traffic. Lesson: **verify the hypothesis with data before tuning infrastructure**, and **roll back changes that didn't help**. Next step: a unit test for cache expiry. |

🗣️ **90-second version:**
> "We saw a spike in calls from our service to the ML recommendation API. My first guess was connection handling, so I tried turning HTTP keep-alive off and then a tuned config — neither fixed it. So I went back to the caching code and found that the function checking whether a cached ML result was still fresh was being passed the cache **key** instead of the **cache time**. Valid results were being treated as expired, so every request called the API again. I fixed the argument, reverted my connection changes, and traffic went back to normal. My takeaway was to confirm the root cause with data before changing infrastructure, and to undo changes that don't help."

**Likely follow-ups**
- *Why did you try keep-alive first?* The symptom was traffic bursts from many pods, and connection settings were a recent suspect — but the data didn't improve, so I dug deeper.
- *How did you find the bug?* Traced the cache read path: cache hit → freshness check → refresh. The refresh was firing far too often, which pointed to the freshness check.
- *How did you test it?* ✍️ (local run with logging / staging / watching ML call rate after deploy)
- *How would you prevent it?* Unit test for `timeValidate`, typed parameters (TypeScript or JSDoc), and an alert on ML call rate per minute.

---

## Story 2 — Empty rails during the Asia Cup (Sep 2025)
**Your commit:** `32dbb7b` "fix for empty response asia cup" (2025-09-09)

| Step | What happened |
|---|---|
| **Context** | During the **Asia Cup** (live cricket = peak traffic), the service returns activity/rail data from an in-memory cache before going to Elasticsearch. |
| **Detect** | Some users were getting **empty responses** for rails. ✍️ *(how reported — ops, error logs, Kibana?)* |
| **Root cause** | The cache check treated an entry as valid if it **existed and was recent** — but some entries **had no value inside** (the cache wrapper was there, `cacheValue` was missing). So the code took the "cache hit" path and returned **nothing** instead of fetching fresh data. |
| **Fix** | Treat a cache entry as a hit only if it **actually has a value**: `recInCache && recInCache.cacheValue && fresh` — otherwise fall through to Elasticsearch. Also simplified the hit/miss branching. |
| **Result** | Rails filled correctly again during the tournament. ✍️ |
| **Learn** | **"Present" isn't the same as "valid".** A cache should never return an empty value as a hit; validate the shape of what you read. Later the team moved to a proper LRU cache with built-in TTL, which removed this manual wrapper logic. |

🗣️ **60-second version:**
> "During the Asia Cup, some users saw empty rails on their home screen. I traced it to our in-memory cache check: it considered an entry valid if it existed and wasn't expired, but some entries had no actual value inside. So we returned an empty result instead of fetching fresh data. I changed the check so an entry only counts as a hit if it actually contains data, otherwise we go to Elasticsearch. That fixed it during the tournament, and the lesson was to validate what you read from a cache — present doesn't mean valid."

---

## Story 3 — One bad segment rule could break requests (Aug 2025)
**Your commits:** `0853a90` "try catch added for eval()", `54e96b2` "segment error fix"

| Step | What happened |
|---|---|
| **Context** | Ops teams define audience **segments** as small rules (e.g. `usr.fav_team == 'CSK'`). The service evaluates these rules at runtime for each user. |
| **Detect** | Errors when a segment rule was **badly written or referenced missing data** — the evaluation threw, and the error propagated up instead of the request simply skipping that segment. ✍️ |
| **Fix** | Wrapped rule evaluation in **try/catch** → a broken rule counts as "doesn't match" (`qual = false`) and the request continues with the next segment / default content. Added **input validation**: reject empty rules or rules with unexpected characters before evaluating. |
| **Result** | One misconfigured segment no longer breaks responses for everyone. |
| **Learn / security angle 🛡️** | Running config as code (`eval`) is risky — both for stability and **security** (code injection). Better long-term: a safe expression evaluator or a small rule language instead of `eval`. |

🗣️ **Good for CrowdStrike** because it shows **failure isolation + security awareness**:
> "…and I flagged that evaluating config as JavaScript is a code-injection risk, so the longer-term fix is a sandboxed expression evaluator."

---

## Story 4 — ML cache returned a placeholder instead of data (Apr 2025)
**Your commit:** `171c463` "Cache failure solution1" (2025-04-25)

| Step | What happened |
|---|---|
| **Context** | When the ML cache was empty, the internal cache server should call the ML API and fill it. |
| **Root cause** | On an empty cache, the code **returned a status string** (`"SETDATAWAIT<host>"`) instead of fetching — callers got a placeholder, not recommendations. |
| **Fix** | On a miss: check the ML config exists (log a clear error if not), mark the entry as updating, **call the ML API**, store the result, and return the real data. |
| **Learn** | Every code path should return either **real data or a clear error** — never a placeholder that looks like success. |

---

# TEAM INCIDENTS (you were involved — say "we", add your part)

## Story 6 ⭐ — Kafka logging was overloading the pods (Jan 2026)
| Step | What happened |
|---|---|
| **Context** | ~110 pods at 20–30K TPS. Every request published a log/stats event to Kafka. |
| **Detect** | Pods at **~100% CPU and 8 GB memory**, some OOMKilled. |
| **Isolate** | Turning log publishing **off** dropped pods to **~0.8–1.1 CPU and 5 GB** → the Kafka path was the cause. |
| **Mitigate** | **Paused publishing** with a code switch so users weren't affected (availability over logs). |
| **Root cause** | The old client sent **one Kafka message per log line** (~30K sends/sec). The first batch client had settings too small for this traffic (100 per batch, every 1 s, 10K queue) and aggressive retries. |
| **Fix** | Fire-and-forget queue → **500-message GZIP batches every 100 ms**, 50K queue dropping oldest, **circuit breaker** (20 failures → off 15 min), unique client id per pod; later **sampling** (all errors, 1 in 10 normal logs → ~89% fewer messages). |
| **Setback** | Switching compression to **LZ4** failed in prod ("LZ4 compression not implemented" — kafkajs needs an extra codec) → **rolled back to GZIP next day**. |
| **Learn** | Observability can take down what it observes; non-critical pipelines need a **kill switch**, bounded queues and a breaker. |
**Your part:** ✍️ ________

🗣️ "Our pods were at full CPU and 8 GB memory. When we turned off log publishing, they dropped to about one CPU — so logging was the culprit. The old client sent one Kafka message per log line, about 30,000 a second. We paused publishing to protect users, then moved to batched, fire-and-forget logging with a circuit breaker and sampling. We also tried LZ4 compression, which failed in production because it needed an extra package, so we rolled back the next day. **My part was ___.**"

---

## Story 7 ⭐ — Pods OOMKilled, but the heap was tiny (Jan 2026)
Great "Dive Deep" story — the surprise is that it **wasn't** a normal memory leak.
| Step | What happened |
|---|---|
| **Detect** | `kubectl top pod` showed **~5.8 GB** and growing to **8 GB → OOMKilled**. |
| **Investigate** | `process.memoryUsage()` showed the **Node heap was only ~39 MB**. So where was 5.8 GB? |
| **Root cause** | (1) **Linux page cache ~5 GB** — the kernel caching file data; the pod reads a **171 MB IP-location database file**, and page cache counts towards the container's memory. Not a leak, but without a proper limit the kernel never had to reclaim it. (2) Real smaller leaks: an **unbounded `Map`** for SDK scripts (`js-creator.js`) and **EventEmitter listeners** not removed on timeout. |
| **Fix** | Replaced the unbounded map with a **size-limited LRU** (100 entries); cleaned up listeners (request coalescing with proper cleanup); set **memory limits** (8 Gi → 6 Gi) so the kernel reclaims page cache before the pod is killed. |
| **Result** | No more OOMKills; steady **2–3 GB**. |
| **Learn** | "`kubectl top` memory isn't just your heap — it includes page cache. Check heap vs RSS vs page cache before blaming the code." |
**Your part:** ✍️ ________ *(your related commits: IP cache fix / removing the in-memory IP map, Jan 2026)*

🗣️ "Pods were climbing to 8 GB and getting OOMKilled, but the Node heap was only about 40 MB. We found most of it was Linux page cache from a 171 MB IP database file the service reads — not a leak, but it counted against the pod. There were also two real leaks: an unbounded map for SDK scripts and event listeners not cleaned up on timeout. We bounded the map with an LRU, fixed the listener cleanup, and set a proper memory limit so the kernel reclaims page cache. Memory settled at 2 to 3 GB. **I worked on ___.**"

---

## Story 8 — Our "optimization" release used MORE CPU (R140 vs R136, early 2026)
| Step | What happened |
|---|---|
| **Detect** | Release branch **R140 (optimization)** used **95–100% CPU**; the previous **R136** ran at **50–60%** — with Kafka disabled on both. A regression. |
| **Investigate** | Compared the two branches; the new LRU caching code was the difference. |
| **Root cause** | Several "safe" but expensive choices at 30K TPS: **deep-copying every cache read** (~15–25% CPU), **background expiry timers** on 7 caches (~21%), **JavaScript Proxy wrappers** around caches (~10–15%), eviction callbacks (~1–2%), duplicate lookups. |
| **Fix** | Return direct references (callers must not mutate), lazy expiry on read (`ttlAutopurge: false`), remove Proxies, remove callbacks. |
| **Result** | CPU back to **~50%**. |
| **Learn** | **Benchmark before release**; defensive copying and timers are cheap per call but huge at 30K TPS. Compare branches, don't guess. |
**Your part:** ✍️ ________ *(you also fixed a cache-mutation bug in Feb 2026 — related: once you stop copying, callers must not modify cached objects)*

---

## Story 9 — Elasticsearch timeouts on IP lookups at peak
| Step | What happened |
|---|---|
| **Detect** | Errors like `timeout of 10000ms exceeded, url: ip2lcache/_doc/49.37.x.x` during high traffic. |
| **Root cause** | Every request looked up the user's IP location in **Elasticsearch** (10–50 ms); under peak load ES timed out. |
| **Fix** | Read IP data from the **local IP2Location file** and cache results in a **bounded LRU** (200K entries, 7 days) — no network call; removed the old unbounded in-memory IP map. |
| **Result** | IP lookups went from 10–50 ms network calls to microseconds from memory; ES timeouts gone. |
| **Learn** | Don't make a network call for near-static data on the hot path. |
**Your part:** ✍️ — *your commits: "inmemory cache for ip removed", IP2Location cache fix (Jan 2026)* → you can say **"I"** for the cache part.

---

## Story 10 — Some requests took 20–50 seconds (timeout analysis)
| Step | What happened |
|---|---|
| **Detect** | `/getrec` normally ~30–50 ms, but some responses took **20–50 s**. |
| **Root cause** | Timeouts **stacked up** on a cache miss: ES query 10 s → wait for cache refresh 15 s → ML API 20 s **× 2 with retry** → and the internal cache-server call had **no timeout at all**. |
| **Fix / recommendation** | Add a timeout on the internal cache-server call, cut the cache wait (15 s → ~3 s), don't retry on top of long timeouts, and set an **overall request time budget** with a fallback (return default content). |
| **Learn** | Every network call needs a timeout, and the **sum** of timeouts must fit the user's budget. |
**Your part:** ✍️ ________ ⚠️ Check which of these fixes actually shipped before saying "we fixed it" — some may still be recommendations.

---

## Which story for which question
| Question | Best story |
|---|---|
| "Biggest production incident?" | **6 (Kafka)** or **7 (OOMKilled)** |
| "Hardest bug / Dive Deep?" | **7 (page cache surprise)** or **1 (cache key vs cache time)** |
| "A fix that made things worse?" | **8 (optimization release regressed CPU)** or the LZ4 rollback in 6 |
| "Issue during peak traffic?" | **2 (Asia Cup)** or **9 (ES timeouts)** |
| "How do you handle latency problems?" | **10 (stacked timeouts)** |

---

## "Walk me through your on-call process" (generic answer)
> "When an alert fires, I first check **impact** — which service, how many users, is it getting worse. I look at dashboards and logs in **Kibana** for error rates and latency by endpoint or activity, and recent **deploys or config changes**. My first priority is to **stop the damage**: roll back a deploy, pause a bad activity, or disable a non-critical feature like log publishing. Then I find the root cause, fix it with a test, and share a short **post-mortem**: what happened, why, and what we changed so it doesn't recur."

**Mitigation toolbox (say a few):** roll back the deploy · pause/disable the activity or feature flag · scale up pods · turn off non-critical work (logging) · serve cached/stale data · fail open on a broken dependency.

---

## Other questions you may get
| Question | Use |
|---|---|
| "Hardest bug you've debugged?" | **Story 1** (wrong first guess → real root cause) |
| "An issue during peak traffic?" | **Story 2** (Asia Cup) |
| "A time you prevented a bigger problem?" | **Story 3** (failure isolation + security risk flagged) |
| "A mistake you made?" | Story 1 — "I spent time on connection tuning before confirming the cause; now I verify with data first." |
| "How do you make sure a fix doesn't break something else?" | tests + staging check + watch metrics after deploy + rollback plan; in the migration project you used **parity tests** |
| "Post-mortem culture?" | blameless, focus on process: what signal was missing, what test would have caught it |
| "Tell me about a security-related incident" | Story 3 + your hardening findings (secrets in config, root containers) |

---

## ⚠️ Rules for these answers
1. Use **"I"** only for what you did; **"we"** for team actions.
2. Don't invent durations, user counts or money numbers — say "within the same day" only if true.
3. If asked something you don't remember: "I don't remember the exact number, but the trend was…" — that's fine and honest.
4. Always end with **what you learned / changed**.
