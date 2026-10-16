# Amazon SDE II: Leadership Principles Story Bank & Rehearsal Guide

> **Loop:** Fri 16 Oct 2026 (Paragon team). **Every round has 1–2 LP questions**, so expect **4–8 in total**. At Amazon, LPs often decide the result as much as the code does.
> **How to use this file:** learn the **10 stories** in Part 2. The map in Part 3 tells you which story answers which question. Rehearse **out loud** with the plan in Part 6.
> All stories come from your **real work** at Jio (resume + code history). Anything marked ✍️ is a blank only you can fill in. **Never invent numbers.** Bar raisers dig 3–4 levels deep, and made-up details fall apart.

---

## Part 0: Quick-start (read this on the morning of the 16th)
1. **STAR**: Situation 15% · Task 10% · **Action 55%** · Result 20%. About **2 minutes**, then stop and let them ask.
2. Say **"I"** for what you did and **"we"** for the team. Interviewers check which one you mean.
3. Every Result needs **a number** and **a learning**.
4. Every story needs some **difficulty**: a wrong guess, a trade-off, pushback, or a risk.
5. If you don't remember a detail, say: *"I don't remember the exact number, but the trend was…"* That's honest, and it's fine.
6. Don't reuse a story in the same round. Across rounds, reuse is OK if you use a **different angle**.

---

## Part 1: How Amazon LP interviews work

**The 16 Leadership Principles.** ⭐ marks the ones asked most for SDE II.
| LP | What they're really checking | Your best story |
|---|---|---|
| ⭐ **Customer Obsession** | You start from the user's pain, not the tech | S5 Asia Cup · S4 Preview tool |
| ⭐ **Ownership** | You act beyond your scope and think long-term | S2 Migration · S6 Segment rule |
| ⭐ **Dive Deep** | You go to logs, data and code; you know the details | S1 ML spike · S8 OOMKill |
| ⭐ **Deliver Results** | You ship despite obstacles | S2 Migration · S5 Asia Cup |
| ⭐ **Bias for Action** | You take calculated risks fast and keep things reversible | S7 Kafka pause · S5 |
| ⭐ **Insist on the Highest Standards** | You don't ship "good enough"; you raise the bar | S2 Parity harness · S9 CPU regression |
| ⭐ **Earn Trust** | You admit mistakes and are transparent | S1 wrong guess · S6 flagged eval risk |
| ⭐ **Have Backbone; Disagree & Commit** | You push back with data, then commit | ✍️ S11 (write it) · S3 design choice |
| **Invent & Simplify** | Simpler, novel solutions | S4 Preview · S2 strangler fig · S3 |
| **Are Right, A Lot** | Good judgment; you seek other views; you update your beliefs | S3 L1 vs Redis · S1 |
| **Learn & Be Curious** | You learn new things on your own | S8 page cache · S10 Threadpool/MCP |
| **Think Big** | A bold direction, not just a patch | S3 Redis L2 · S2 |
| **Frugality** | More with less (cost, people, time) | S3 ML cost · S7 sampling |
| **Hire & Develop the Best** | Mentoring, raising others | ✍️ S12 (write it) |
| **Strive to be Earth's Best Employer** | Team well-being, inclusion | ✍️ (rare for SDE II) |
| **Success & Scale Bring Broad Responsibility** | Thinking about wider impact: security, safety | S6 eval risk · Security hotspots |

**What happens in the room**
- They ask: *"Tell me about a time when…"*. You get about 2 minutes, then **follow-ups**: *"What exactly did YOU do?" · "What data did you have?" · "What would you do differently?" · "Who disagreed?" · "What was the impact?"*
- They write down your words. Vague answers ("we improved things") score low. Specific answers ("I found that `timeValidate` was passed the key instead of the cache time") score high.
- **Strong-hire signals:** clear ownership, data, trade-offs, learning from failure, and customer impact.

---

## Part 2: The Story Bank (10 core stories + 2 to write)

> Format for each story: **hook** (1 line) → **LPs** → **🗣️ spoken script** (~2 min) → **numbers** → **follow-ups with answers** → **⚠️ honesty guard**.

---

### S1 ⭐ ML traffic spike: my wrong first guess, then the real bug (Aug 2025)
**Hook:** "One wrong argument made us call an expensive ML API on almost every request."
**LPs:** **Dive Deep** · **Earn Trust** (admitted the wrong guess) · **Are Right A Lot** · Bias for Action · *"a time you made a mistake"*

🗣️ **Script**
> **S:** "Our personalization service decides what millions of Jio TV users see on each home-screen rail. For recommendation rails it calls an external ML API and caches the results, because that API is slow and costly.
> **T:** In August 2025 we saw a sharp spike in calls from our fleet to the ML API. I owned finding and fixing it.
> **A:** My first hypothesis was HTTP connection handling, since we had been changing that area. I turned keep-alive off, and then tried a tuned keep-alive config with socket limits. Neither brought the call rate down, so I stopped tuning infrastructure and went back to the code path itself. I traced one request: cache lookup, freshness check, refresh. The refresh was firing far too often, which pointed at the freshness check. There I found that `timeValidate` was being passed the **cache key** instead of the algorithm's **cache time**. So every valid cached result looked expired, and almost every request went back to the ML API. I fixed the argument, and I **reverted my connection experiments**, because they weren't the cause and I didn't want unexplained changes in production.
> **R:** The ML call rate went back to normal, and cached results were reused for their full cache time. ✍️ *(how fast / what you watched)* What I took from it: confirm the hypothesis with data before changing infrastructure, and undo changes that didn't help. I also proposed a unit test for cache expiry and an alert on ML calls per minute."

**Numbers:** 3 commits on 2025-08-18 (`95cdae8`, `34a08f5` = connection experiments; `5c14b40` = real fix). ✍️ Before/after ML call rate if you remember it.

**Follow-ups**
| Q | Answer |
|---|---|
| Why keep-alive first? | It was a recent area of change, and the symptom (bursts of calls from many pods) looked like a connection issue. When the data didn't move, I dropped the idea. |
| How long did you lose on the wrong guess? | ✍️ honest answer, e.g. "a few hours, the same day". |
| How did you prove the fix? | ✍️ e.g. local run with logs showing cache hits, then the ML call rate after deploy. |
| Why wasn't it caught before? | No unit test on the expiry function, and the arguments are untyped JavaScript, so a wrong argument doesn't fail. |
| What would you do differently? | Add a metric for cache hit ratio first. It would have pointed at the cache in minutes. |
| What did you change for the team? | Proposed a test for `timeValidate`, typed parameters (JSDoc/TS), and an ML-rate alert. ✍️ say which ones actually happened. |

⚠️ **Guard:** keep-alive was the **wrong** guess. Don't call it the fix.

---

### S2 ⭐ Legacy Java 8 → Spring Boot 3 migration, with zero behaviour change
**Hook:** "I moved a fragile legacy backend to Spring Boot one endpoint at a time, and proved each move with a parity harness."
**LPs:** **Ownership** · **Insist on the Highest Standards** · **Deliver Results** · **Invent & Simplify** · Think Big · *"most proud of" · "long project" · "handled ambiguity"*

🗣️ **Script**
> **S:** "sigly_server is the backend for our configuration portal: apps, segments, activities, users, SSO. It was Java 8 on embedded Jetty, with one giant generic servlet and a 1,900-line helper. It had about 8 tests and a SonarQube rating of E. Every change was scary, and the portal is used every day, so we couldn't take downtime.
> **T:** I drove moving it to Spring Boot 3 and Java 21 without breaking the portal, which depends on exact JSON shapes.
> **A:** A big-bang rewrite was too risky, so I used a **strangler-fig** approach. Spring Boot is the main app, and the old servlets are registered inside it with the **lowest priority**. When I move an endpoint into a Spring controller, Spring wins that path, and everything else still falls back to the old code. So each step is small and can be rolled back.
> I moved the endpoints group by group into controller → service → repository → DTO layers. To be **sure** nothing changed, I built a **parity harness** that sends the same request to the old and new server and diffs the JSON, ignoring fields like timestamps. It has 67 cases. While I was in the code I also cleared **65 SonarQube bugs and 51 security hotspots**: resource leaks, null checks, ReDoS-prone regexes, unsafe file permissions. And I grew tests from about 8 to about 1,200.
> **R:** All the old servlet endpoints now run on Spring MVC, read parity passes, and the code is testable and much safer to change. ✍️ *(any incident/bug count after cutover, or release speed)* My learning: for a migration, **proof of equivalence** is the product. Without the parity harness, nobody would have trusted the cutover."

**Numbers:** Java 8 → 21 · Spring Boot 3.3 · 67 parity cases · 65 bugs · 51 hotspots · tests 8 → ~1,200 · 146 commits.

**Follow-ups**
| Q | Answer |
|---|---|
| Why not a rewrite? | The portal depends on exact responses, and there was no downtime window. Small steps can be tested and rolled back. |
| Hardest part? | Matching responses byte-for-byte, including error responses; static legacy code that Mockito couldn't mock (I wrapped it); startup-order issues. |
| Why only GET in parity? | Writes change data, so write parity was off by default and blocked on prod hosts. Next step: write parity in a test environment. |
| How did you prioritise which endpoints first? | ✍️ e.g. read-heavy / low-risk groups first, then auth and SSO. |
| Did anyone push back? | ✍️ If true: someone wanted to rewrite or to skip parity. Explain how data convinced them. |
| What's left? | Tests still skipped in the GCP pipeline, write parity, finishing the JPA migration. Saying what's unfinished shows ownership. |

⚠️ **Guard:** teammates did the first bootstrap and the JPA pilot. Say *"I drove most of the endpoint migration."*

---

### S3 ⭐ Designing the L1 + Redis L2 cache (the project I'm proudest of)
**Hook:** "Hundreds of separate in-memory caches meant the same expensive ML call happened again and again. I designed a shared Redis tier on top of a bounded L1."
**LPs:** **Think Big** · **Are Right A Lot** (L1-only vs Redis-only vs two-tier) · **Frugality** (ML cost) · **Invent & Simplify** · Customer Obsession (latency at match time)

🗣️ **Script**
> **S:** "Our recommendation API runs on about 80 Kubernetes pods, each with several Node workers, so there are **hundreds of separate in-memory caches**. A user's request can land on any of them. The service handles 27M+ requests a day and 30K+ TPS bursts during live cricket.
> **T:** Two problems: the same ML result was computed again on many pods, which costs money and adds latency, and our unbounded caches had pushed pods to 8 GB and OOMKills. I owned designing the caching strategy.
> **A:** First I made L1 safe. I replaced unbounded maps, like the IP cache, with **size-limited LRU caches with TTLs**. Then I compared three options. **Only L1** isn't shared, so per-user and ML data rarely hits. **Only Redis** means a network trip on every lookup, around 120K Redis calls a second at peak, plus a hot-key risk, and a Redis outage would take everything down. So I chose **two tiers**: a small L1 per worker for microsecond reads of hot data, and **Redis as L2** for things that are expensive and shared: ML results and user profiles. Data from local files, like IP-to-city, stays L1-only, because Redis would be slower than the file. The ML cache key is a hash of the ML request, so all CSK fans share one entry and we make one ML call instead of millions. On a miss, a **Redis `SET NX` lock** lets only one request call ML, and others get the old value. If Redis fails, we **fail open** and go to the source, because a cache must never cause an outage. Consistency is TTL-based, at most about a minute stale, which is fine for recommendations.
> **R:** ✍️ *(pick what's TRUE: 'In testing we saw an 85–90% hit rate, P95 from ~150 ms to 30–60 ms, and ML calls 30–40% lower'; the L1 work took memory from 8 GB with OOMKills to 2–3 GB.)* The learning: put data where it's cheapest to serve. **Shared and expensive → Redis; local and cheap → L1.**"

**Numbers:** ~80 pods (110 earlier) · L1 ~1 µs, Redis ~0.5 ms, ML ~150 ms+ · recsCache 5,000 · userCache 10,000/5 min · ipCache 200,000/7 days · L1 TTL 60 s.

**Follow-ups**
| Q | Answer |
|---|---|
| Why not just Redis? | Network on every lookup, ~120K calls/s, hot keys during toss/match moments, and a single point of failure. |
| Consistency across 80 pods? | TTLs: L1 60 s, Redis 5 min; optional delete on update. No Pub/Sub; we kept it simple. |
| Thundering herd? | Singleflight within a worker, a Redis `SET NX` lock across pods, stale-while-revalidate. |
| Redis down? | A circuit breaker skips Redis; L1 still serves hot data, and the source serves the rest. |
| How do you measure success? | Hit ratio per tier, P95/P99, ML calls per minute, pod memory. |
| Is it in production? | ✍️ honest status: "We're rolling it out; today the shared tier is an Elasticsearch index." |
| Who disagreed? | ✍️ If true, someone wanted Redis-only or L1-only. This can become your **Have Backbone** story. |

⚠️ **Guard:** Redis is being **introduced**; don't say it has been in prod for a year. The singleflight code is mainly a teammate's, so say "we". Your own parts: the IP cache fix, the cache-mutation fix, the ML cache fixes, and the design reasoning.

---

### S4 Preview-before-publish tool for ops teams (Aug 2024)
**Hook:** "To test an activity, teams had to make it **live** and check it on a test STB box. I let them validate changes in real time **without saving or making anything live**."
**LPs:** **Customer Obsession** (internal customers) · **Invent & Simplify** · Ownership · *"anticipated a customer need" · "innovative idea"*

**Background (say it simply):** In the portal, a team creates an **activity** (what to show on a rail/screen), and inside it one or more **experiences** (variations: default, a segment like "CSK fans", A/B variants).

| Before | After |
|---|---|
| Create/edit the activity → **save** → **make it live** → open a **test STB device** → check → find a mistake → edit → save → make live → check again | Edit the activity → click **Preview** → see the real result **instantly in the portal** → tweak and preview again → **Publish** only when it's right |
| Needed a physical test STB and several minutes per round | No device, no save, no going live, seconds per round |
| Every small fix meant another save + make-live + device check | Fix and re-check instantly, as many times as needed |

🗣️ **Script**
> **S:** "In our Sigly portal, teams create an **activity** that controls what appears on a Jio TV rail, and inside it one or more **experiences**: the default, a segment like CSK fans, or A/B variants. To test any change, they had to **save it, make the activity live, and then check it on a test STB device**. If something was wrong, they had to edit, save, make it live and check on the box again. Testing happened only on the test device, so real users weren't affected, but every small change meant another full save, go-live and device check. It was slow and depended on having a test STB.
> **T:** I wanted them to validate changes **in real time, before saving or going live**.
> **A:** The key decision was to **not redraw the result in the portal**. Segment logic, ML calls and enrichment all live in the customer API, and copying them would drift. So when you click Preview, the portal sends the **unsaved activity JSON** and the selected **experience** to the runtime's `/getrecwithlogs` endpoint, authenticated with the user's key. The runtime evaluates it exactly like a live request from an STB. I built a preview modal with four tabs: **Errors**, the raw **Response**, a **Preview** of the actual poster tiles using our existing card component (STB and web layouts), and **Performance**, showing response time and payload size. The user can change something and preview again within seconds, then **Publish**, or **Reject**, which keeps it as a draft.
> **R:** Teams no longer needed to make an activity live or use a test STB to validate it. Based on team feedback, validation time dropped roughly **40–50%**, and teams could fix mistakes in seconds instead of repeating the whole cycle. My learning: the best internal tools reuse the real system instead of imitating it, and the biggest win came from removing the device-and-go-live step entirely."

**Follow-ups**
| Q | Answer |
|---|---|
| How did you know they needed it? | The test loop itself: every change meant save → make live → check on a test STB. ✍️ add if true: complaints from teams, or waiting for a test device. |
| Did real users ever see a half-tested change? | No. Testing was done only on a test device. The problem was speed and effort: a full save, go-live and device check for every small change. |
| What exactly is an "experience"? | A variation inside an activity: the default, a segment (e.g. CSK fans), or an A/B variant. Preview lets you pick which one to test. |
| How did you measure 40–50%? | An estimate: steps and time in the validate loop before vs after, from team feedback. Say that it's an estimate. |
| Security? | The endpoint needs the logged-in user's key; the public endpoint doesn't accept custom JSON. |
| Hardest part? | Screen activities return many activities (needed a dropdown), STB vs web cards look different, and wiring Reject into the draft flow. |
| What next? | A live-vs-draft side-by-side view, showing which segment matched, shareable preview links. |

⚠️ **Guard:** "I built the React side and integrated it with the customer API endpoint." Don't claim you built a new Node server.

---

### S5 Empty rails during the Asia Cup (Sep 2025)
**Hook:** "In the middle of a live tournament, users saw empty rails. I found that a cache entry being 'present' didn't mean it was valid."
**LPs:** **Customer Obsession** · **Bias for Action** · **Deliver Results** · *"worked under pressure" · "tight deadline"*

🗣️ **Script**
> **S:** "During the Asia Cup, live cricket is our peak traffic, and the home screen is the first thing millions of users see. Some users started getting **empty rails**.
> **T:** It had to be fixed during the tournament, with no time for a big change.
> **A:** ✍️ *(how you detected it: ops report / Kibana)* I traced the read path. Our in-memory cache check counted an entry as a hit if it **existed and wasn't expired**, but some entries had **no value inside**: the wrapper was there, but `cacheValue` was empty. So the code took the hit path and returned nothing instead of fetching from Elasticsearch. I made the smallest safe fix: an entry is a hit only if it **actually has data and is fresh**; otherwise fall through to Elasticsearch. I also simplified the hit/miss branching so it's easier to read. ✍️ *(how you tested and deployed safely during the event)*
> **R:** Rails filled correctly again for the rest of the tournament. ✍️ *(any numbers)* Learning: **'present' isn't the same as 'valid'.** Always validate what you read from a cache. Later the team moved to a proper LRU cache with built-in TTL, which removed this hand-written wrapper logic."

**Follow-ups:** *Why not a bigger refactor?* Peak event, so the smallest change that's easy to verify; the refactor came later. · *How did you avoid making it worse?* ✍️ (tested in SIT, watched error rate, ready to roll back). · *Root cause of the empty values?* ✍️ if known (e.g. a failed fetch stored an empty wrapper). Otherwise: "That's what drove the later move to a proper LRU with TTL."

---

### S6 One bad segment rule could break requests, and I flagged a security risk (Aug 2025)
**Hook:** "Ops-written rules ran as JavaScript `eval`. One broken rule could break responses. I isolated failures and raised the injection risk."
**LPs:** **Ownership** · **Insist on Highest Standards** · **Success & Scale Bring Broad Responsibility** · Earn Trust · *"prevented a bigger problem"*

🗣️ **Script**
> **S:** "Ops teams define audience segments as small rules like `usr.fav_team == 'CSK'`, and the service evaluates them for every request.
> **T:** When a rule was badly written or referenced missing data, the evaluation threw an error, and it propagated up instead of just skipping that segment.
> **A:** I wrapped the rule evaluation in try/catch, so a broken rule counts as 'doesn't match' and the request continues with the next segment or default content. I also added input validation to reject empty rules or rules with unexpected characters before evaluating. And since the rules run through `eval`, I **flagged to the team** that this is also a **code-injection risk**, not just a stability one, and suggested a safe expression evaluator as the long-term fix.
> **R:** One misconfigured segment no longer breaks responses for all users. ✍️ *(what happened to the long-term proposal)* Learning: config written by people should never be able to crash the system, and the safest default is 'fail to the default content'."

**Follow-ups:** *Why not remove eval right away?* It's a bigger change touching every existing rule; isolation first, a safer evaluator as the planned follow-up. · *Did anyone resist?* ✍️ · *How would you design the safe evaluator?* A whitelisted expression grammar (field, operator, value) parsed into an AST, with no access to globals.

---

### S7 Kafka logging was overloading our pods (Jan 2026), a team incident
**Hook:** "Observability was taking down the service it observed. We paused it, then rebuilt it with batching, sampling and a circuit breaker."
**LPs:** **Bias for Action** (pause first) · **Frugality** (89% less broker I/O) · Deliver Results · *failure: the LZ4 rollback* · *"calculated risk"*

🗣️ **Script**
> **S:** "We had ~110 pods at 20–30K TPS, and every request published a log/stats event to Kafka. Pods hit about **100% CPU and 8 GB memory**, and some were OOMKilled.
> **T:** Protect users first, then fix logging without losing the error data we need.
> **A:** We isolated it by turning log publishing **off**: pods dropped to about **1 CPU and 5 GB**, so the Kafka path was the cause. We kept publishing paused, which was a calculated trade-off: availability for users over logs for a short time. The root cause was one Kafka message per log line, about 30K sends a second, and the first batching attempt had batches that were too small and aggressive retries. The fix was fire-and-forget batching of 500 messages every 100 ms with GZIP, a bounded 50K queue that drops the oldest, a **circuit breaker** (20 failures → off for 15 minutes), and **sampling**: all errors and slow requests, 1 in 10 normal logs. We also tried LZ4 compression; it failed in production because the client needed an extra codec, so we **rolled back to GZIP the next day**.
> **My part:** ✍️ *(e.g. 'I was on call, compared pod metrics with and without publishing, tested the batch settings in SIT, handled the rollout and rollback')*
> **R:** About **89% less broker I/O**, while still capturing **100% of errors and slow requests**, and pods stable again. Learning: non-critical pipelines need a kill switch, bounded queues and a breaker. Test new dependencies, like a compression codec, in a production-like environment."

⚠️ **Guard:** "we" for the design; "I" only for your real part. Fill in ✍️ before Friday, or don't use this story.

---

### S8 Pods OOMKilled, but the heap was only 39 MB (Jan 2026)
**Hook:** "Memory said 5.8 GB, the heap said 39 MB. Most of it turned out to be Linux page cache."
**LPs:** **Dive Deep** · **Learn & Be Curious** · *"most complex problem" · "a problem without a clear answer"*

🗣️ **Script**
> **S:** "`kubectl top` showed pods at about 5.8 GB and growing to 8 GB, then OOMKilled.
> **T:** Find out where the memory was really going, because restarting pods only hid it.
> **A:** The obvious guess was a heap leak, but `process.memoryUsage()` showed the Node heap at only **~39 MB**. So the memory was outside the heap. We found most of it was **Linux page cache**: the service reads a **171 MB IP-location database file**, and page cache counts against the container's memory. Without a proper limit, the kernel never had to reclaim it. We also found two real leaks: an **unbounded `Map`** for SDK scripts, and **EventEmitter listeners** not removed on timeout. Fixes: a size-limited LRU (100 entries) for scripts, cleaning up listeners in `finally`, and a memory limit of 6 Gi so the kernel reclaims page cache before the pod is killed. My part: ✍️ *(e.g. the IP cache work: removing the in-memory IP map and moving to a bounded LRU over the local IP2Location file)*
> **R:** No more OOMKills; memory steady at **2–3 GB**. Learning: container memory isn't just your heap. Check heap vs RSS vs page cache before blaming the code."

**Follow-ups:** *What is page cache?* The kernel keeps file data in RAM to speed up reads, and inside a container cgroup it's charged to the pod. · *Why does a lower limit help?* Under pressure the kernel reclaims clean page cache first. · *How did you find the listener leak?* ✍️

---

### S9 Our "optimization" release used MORE CPU (R140 vs R136), a team incident
**Hook:** "A release meant to optimise doubled CPU. We compared branches and found 'safe' habits that were expensive at 30K TPS."
**LPs:** **Insist on the Highest Standards** · Dive Deep · Earn Trust (owning a regression) · *"a time something you shipped failed"*

🗣️ **Short script:** "Release R140, our caching optimisation, ran at 95–100% CPU, while the previous R136 ran at 50–60%. We compared the branches and found several costs that are tiny per call but huge at 30K TPS: **deep-copying every cache read** (~15–25%), background expiry timers on 7 caches (~21%), JavaScript Proxy wrappers (~10–15%), and eviction callbacks. We returned direct references, switched to lazy expiry on read, and removed the Proxies and callbacks, and CPU went back to about 50%. Removing the copies meant callers must not modify cached objects, and **I fixed a cache-mutation bug** that came from exactly that (Feb 2026). Learning: **benchmark before release**, and compare against the previous version with the same traffic."

---

### S10 Learning beyond the job: work-stealing threadpool (C++) / MCP server
**LPs:** **Learn & Be Curious** · Invent & Simplify
- **Threadpool:** "I wanted to understand how Java's ForkJoinPool works underneath, so I built a work-stealing threadpool in C++ with POSIX threads. Each thread has its own deque: the owner pushes and pops at the bottom, and idle threads **steal from the top**, so they rarely contend. I padded per-thread data to avoid **false sharing**. On divide-and-conquer workloads I got about a **3× speedup**." *(Follow-up: why steal from the top? The oldest tasks are usually the biggest, so one steal gives a lot of work, and it avoids clashing with the owner.)*
- **MCP (backup only):** "I taught myself the Model Context Protocol and built an internal server exposing our personalization platform to Claude/Cursor as 50+ tools, so engineers can query and operate it from their AI workflow."

---

### S11 ✍️ Have Backbone; Disagree & Commit (WRITE THIS BEFORE THURSDAY)
Amazon almost always asks it. Pick a **true** moment. Possible seeds from your own work (**use one only if it really happened**):
- You argued for **strangler-fig + parity** over a rewrite, or over "just test manually".
- You argued against **Redis-only** (or against L1-only) for the cache.
- You pushed back on **shipping during a peak event** (Asia Cup / IPL) without a test or rollback plan.
- You said the **15 s singleflight wait** was too long for a TV screen.
- You **disagreed but committed** to the team's choice (e.g. GZIP vs LZ4, a deadline), and how you made it succeed.

**Template:**
> "**S:** ___ **T:** ___ was proposed. I disagreed because ___ (**data**: ___). **A:** I raised it with ___ in ___ (1:1 / design review), showed ___, and listened to their reasons: ___. **Outcome:** we went with ___ . *(If they won:)* I committed fully and did ___ to make it work. **R:** ___. **Learning:** ___."
✅ Good signals: respectful, data-based, you listened, you committed after the decision. ❌ Avoid: "I was right and they were wrong", or a disagreement with no data.

### S12 ✍️ Helping / mentoring someone (Hire & Develop the Best, Earn Trust)
Seeds: onboarding a teammate to the customer API or the Spring Boot layering, review comments that taught a pattern, writing docs or runbooks (e.g. timeout analysis, incident notes), pairing during an incident.
> "**S:** A new / junior teammate ___ was struggling with ___. **A:** I ___ (pairing sessions, wrote a doc, reviewed their PRs with explanations). **R:** They ___ (shipped ___ on their own / handled the next on-call). **Learning:** ___."

### (Optional) ✍️ Missed deadline / cut scope
Seed: the migration (what you **deferred**: write parity, JPA, GCP tests) is honest scope-cutting. *"What did you sacrifice and why?"* → "Write-parity and the JPA migration, so read endpoints could ship safely first. I made the gap visible to my lead."

---

## Part 3: Question → Story map (60+ common Amazon LP questions)

| Question | Use | Angle to stress |
|---|---|---|
| Tell me about a time you went above and beyond for a customer | S4 / S5 | user pain → fix |
| A time you anticipated a customer need | **S4** | they didn't ask; you saw the loop |
| A time you had to balance customer needs with business constraints | S7 | paused logs (business data) to protect users |
| A decision that was hard but right for the customer | S5 | smallest safe fix at peak |
| Took ownership of something outside your responsibility | **S6** / S2 | flagged the eval security risk |
| A long-term vs short-term trade-off | **S2** / S6 | strangler fig; isolate now, safe evaluator later |
| You saw a problem and fixed it without being asked | S6 / S8 (IP cache) | |
| Most complex problem / hardest bug | **S1** / S8 | |
| Dove deep to find the root cause | **S1** / S8 | data, wrong guess, the exact line |
| A time data contradicted your intuition | **S1** / S8 | keep-alive guess; the heap was tiny |
| Solved a problem with very little information | S8 / S5 | |
| You made a decision without all the data | S7 (pause) / S5 | reversible decision |
| Calculated risk where speed mattered | **S7** / S5 | kill switch = reversible |
| Had to act quickly | S5 / S7 | |
| Simplified something complex | **S2** / S4 | one endpoint at a time; reuse the real engine |
| Most innovative idea | **S4** / S3 | |
| Raised the bar / not satisfied with "good enough" | **S2** (parity) / S9 | |
| A time you improved code quality | S2 (Sonar 65 + 51, tests 8 → 1,200) | |
| Delivered a project under a tight deadline | S5 / S2 | |
| The project you're most proud of | **S3** or **S2** | |
| Biggest failure / mistake | **S1** / S9 | own it → learning → change |
| A time you shipped something that broke | **S9** / S7 (LZ4) | |
| Critical feedback you received | ✍️ (e.g. a review comment about a missing test → you now add tests first) | |
| Disagreed with your manager / team | **S11** ✍️ | |
| Convinced others to adopt your idea | S3 / S2 | data + prototype |
| Earned trust with a team or stakeholder | S2 (parity proof) / S1 (admitting the wrong guess) | |
| Had to learn something new quickly | S8 (page cache) / S10 | |
| Thought big / bold idea | **S3** | fleet-wide shared tier |
| Did more with less (Frugality) | **S3** (ML cost) / S7 (89% less I/O) | |
| Helped a teammate grow | **S12** ✍️ | |
| Handled ambiguity | S2 / S8 | |
| Considered security or wider impact | **S6** / S2 hotspots | |
| Missed a deadline / cut scope | Optional story above | |
| Worked with a difficult stakeholder | ✍️ (e.g. ops team asking for urgent changes during a match) | |

> **Rule:** prepare at least **2 stories per ⭐ LP**, so you never repeat in the same round.

---

## Part 4: Bar-raiser follow-up drill (practise answering these for EVERY story)
1. **"What exactly did *you* do?"** List 3 actions with "I".
2. **"What data did you have?"** A metric, a log line, a command (`kubectl top`, `process.memoryUsage()`, Kibana).
3. **"What alternatives did you consider?"** Always have 2 rejected options and why.
4. **"What would you do differently?"** A real answer, not "nothing".
5. **"What was the impact on the customer / business?"** Users, latency, cost, risk.
6. **"Who did you involve or disagree with?"**
7. **"How did you measure success?"**
8. **"What happened after? Is it still in use?"**
9. **"What was the hardest part?"**
10. **"If you had 2× the time / half the time?"**

If you don't know: *"I don't remember the exact figure, but it was roughly ___ / the direction was ___."* Never make it up.

---

## Part 5: The openers

### "Tell me about yourself" (60–90 s)
> "I'm Navam, an SDE II at Jio Platforms with 3+ years in backend engineering: Java, Spring Boot, Node.js, Kafka and Kubernetes.
> I own a personalization service that decides what Jio TV and app users see on every home-screen rail. It serves 5.4M+ users and 27M+ requests a day, with 30K+ TPS bursts during live cricket, and I'm primary on-call for it.
> The work I'm proudest of is the caching layer: making the pod-local caches bounded and safe, which took pods from OOMKills at 8 GB to a steady 2–3 GB, and designing a shared Redis tier so expensive ML results are computed once for the whole fleet.
> I also drove the migration of our legacy Java 8 backend to Spring Boot 3 / Java 21, using a strangler-fig approach with a parity harness to prove nothing changed.
> I enjoy problems at the intersection of scale and reliability, which is why the Paragon role interests me."

### "Why Amazon?"
> "Two reasons. **Scale and customer impact**: I like systems where a millisecond or a bad cache decision affects millions of people, and Amazon operates at a level beyond what I've seen. And the **LP culture**, especially ownership and dive deep, matches how I already like to work: being on call for what I build and going to the data to find root causes. I want to grow as an engineer where the bar is high."

### "Why this team (Paragon)?"
> "My understanding is that Paragon is the platform customer-service associates use to resolve customer contacts. *(Check with the recruiter.)* What draws me is that it's **customer obsession, one step removed**: if the tool is slow or unreliable, both the associate and the customer feel it. That's close to what I do now, building internal tooling like the preview tool plus a high-traffic runtime that has to stay up."

### "Why are you leaving Jio?"
> "I've learned a lot and I'm proud of what I built there. I'm looking for a bigger scope and a higher engineering bar, systems at larger scale and stronger mentors. It's about growth, not about running away from something." *(Never criticise your current employer.)*

### Questions to ask them at the end (pick 1–2)
- "What are the biggest operational challenges Paragon faces today?"
- "How does the team decide between building new features and paying down operational debt?"
- "What does a great SDE II on this team do in the first six months?"
- "Can you tell me about a recent time the team used an LP in a tough decision?"

---

## Part 6: Rehearsal plan (Mon 12 → Thu 15 Oct)

| Day | LP practice (≈60–75 min a day) |
|---|---|
| **Mon 12** | Fill in **every ✍️**, especially S11 (Have Backbone) and S12 (mentoring), and "my part" in S7/S8. Say S1, S2 and S3 aloud **twice** each. |
| **Tue 13** | **Record yourself** (phone) on S1, S3, S4 and S5. Listen back: too long? enough "I"? a number in the result? Fix and redo. |
| **Wed 14** | Pick **10 random questions** from Part 3. Answer each in under 2.5 min, then do the **Part 4 drill** on 3 of them. |
| **Thu 15** | **Full mock**: a friend (or ChatGPT/Claude voice) asks 6 LP questions with follow-ups. Do "Tell me about yourself" and "Why Amazon" 3× each. Then stop; light revision only. |
| **Fri 16** | Read Part 0 + Part 7 only. No new stories. |

### Self-score each answer (aim for ≥ 8/10)
| Check | ✓ |
|---|---|
| Situation under 30 s | |
| Clear *task / my responsibility* | |
| **"I"** used for actions (≥ 3 concrete actions) | |
| A trade-off, alternative or difficulty is mentioned | |
| **Specific technical detail** (a name, a command, a number) | |
| **Result with a number** | |
| **Learning / what I changed afterwards** | |
| Under ~2.5 min | |
| Answers the exact question asked (and names the LP behaviour) | |
| Honest: every claim can survive 3 follow-ups | |

### Delivery tips
- **Pause** after the question for 3–5 seconds and pick the story. That's normal.
- Start with a **one-line headline**: *"This is about a time I found a one-argument bug that was multiplying our ML calls."*
- In a virtual round, keep a **1-page keyword sheet** (Part 7) nearby: keywords only, not scripts. Don't read from it.
- End with: *"…and that's what I'd do the same / differently next time."* Then **stop talking**.

---

## Part 7: One-page cheat sheet (keywords only)

| # | Story | Keywords | Number | LPs |
|---|---|---|---|---|
| S1 | ML spike | keep-alive wrong guess → `timeValidate(key)` should be cacheTime → reverted experiments | 3 commits, 1 day | Dive Deep, Earn Trust, mistake |
| S2 | Spring Boot migration | strangler fig, lowest-priority servlets, parity 67 cases, Sonar | 65 bugs · 51 hotspots · 8 → 1,200 tests | Ownership, Standards, Deliver |
| S3 | L1 + Redis | 80 pods × workers, L1 vs Redis-only vs 2-tier, hash key, SET NX, fail open, TTL | 8 GB → 2–3 GB; ✍️ hit rate / P95 / ML cost | Think Big, Right A Lot, Frugality |
| S4 | Preview tool | before: save → make live → test STB; after: unsaved JSON + experience → real engine `/getrecwithlogs`, 4 tabs, Publish/Reject | ~40–50% less validation time | Customer Obsession, Invent |
| S5 | Asia Cup | present ≠ valid, `cacheValue` check, smallest safe fix at peak | ✍️ | Customer, Bias for Action |
| S6 | Segment eval | try/catch → no match, validation, flagged injection risk | ✍️ | Ownership, Broad Responsibility |
| S7 | Kafka overload | turn off → CPU drops, pause, batch 500/100 ms, breaker, sampling, LZ4 rollback | 89% less I/O, 100% errors kept | Bias for Action, Frugality |
| S8 | OOMKill | heap 39 MB vs 5.8 GB, page cache, 171 MB file, unbounded Map, listeners | 8 GB → 2–3 GB | Dive Deep, Curious |
| S9 | R140 CPU | deep copies, timers, Proxies, compare branches | 95–100% → 50% CPU | Standards, failure |
| S10 | Threadpool | per-thread deques, steal top, false sharing | 3× | Curious |
| S11 | ✍️ Disagree | | | Backbone |
| S12 | ✍️ Mentoring | | | Develop the Best |

**Mantra:** *Headline → STAR → "I" → number → learning → stop.*
