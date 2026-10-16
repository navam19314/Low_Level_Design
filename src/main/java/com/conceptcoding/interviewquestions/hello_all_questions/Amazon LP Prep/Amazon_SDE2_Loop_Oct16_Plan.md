# Amazon SDE II Loop — Fri 16 Oct 2026 (Paragon team) — 4-Day Plan

**Loop:** 2–3 rounds from: Coding (DSA) · LLD · System Design (Scalability & **Operational Performance**) · Coding (Problem Solving). **Every round has Leadership Principles (LP)** → expect **4–8 LP questions in total**. LPs often decide the result.

**About Paragon (verify with the recruiter):** Paragon is, as far as I know, Amazon's internal **customer-service platform** — the workbench associates use to handle customer contacts (cases, workflows, routing, knowledge, notifications). So design topics like **ticketing / case routing, notifications, workflow engines, search** are extra relevant. Ask the recruiter or interviewer: *"Could you tell me a bit about what the Paragon team works on?"*

---

## 0. 4-day schedule
| Day | Morning | Evening |
|---|---|---|
| **Mon 12** | ✅ confirm email by 6 PM · write **8 LP stories** (section 4) | Coding: 4 problems (arrays/hashing, sliding window) |
| **Tue 13** | Coding: 6 problems (trees, graphs, heap, binary search) | LLD: **Parking Lot** + **LRU Cache / Rate Limiter** in Java |
| **Wed 14** | System design: **Notification System** + **Flash Sale** (both unchecked in your list) | SD: **Rate Limiter / Job Scheduler** + a **Ticketing/Case-routing** design · LLD: 1 more |
| **Thu 15** | 1 full mock (coding 45 min + LP) · say all LP stories **out loud** | light revision only · **sleep early** |
| **Fri 16** | warm-up: 1 easy problem + read LP cheat sheet | interviews |

---

## 1. Coding (2 rounds possible) — patterns + must-do problems
**How to run a coding round (say it out loud):** clarify inputs and edge cases → give an example → brute force + complexity → optimise → code cleanly in Java → dry-run with an example → state time/space.

| Pattern | Must-do problems (LeetCode) |
|---|---|
| Hashing / arrays | Two Sum · Group Anagrams · Top K Frequent Elements · Product of Array Except Self · Subarray Sum Equals K · Longest Consecutive Sequence |
| Sliding window / two pointers | Longest Substring Without Repeating · Minimum Window Substring · Container With Most Water · 3Sum · Trapping Rain Water |
| Intervals | Merge Intervals · Meeting Rooms II · Insert Interval |
| Stack / monotonic | Valid Parentheses · Daily Temperatures · Min Stack · Largest Rectangle in Histogram |
| Heap | Kth Largest Element · K Closest Points · Merge K Sorted Lists · Find Median from Data Stream · Task Scheduler · Reorganize String |
| Binary search | Search in Rotated Sorted Array · Koko Eating Bananas (search on answer) · Time-Based Key-Value Store |
| Trees | Level Order / Zigzag · Right Side View · Validate BST · LCA of Binary Tree · Serialize/Deserialize · Diameter |
| Graphs | Number of Islands · Rotting Oranges · Course Schedule I/II (topo sort) · Clone Graph · Word Ladder · Accounts Merge / Number of Provinces (union-find) · Network Delay Time (Dijkstra) |
| DP | Coin Change · Word Break · Longest Increasing Subsequence · House Robber · Decode Ways · Longest Common Subsequence |
| Design-y coding | **LRU Cache** · Insert Delete GetRandom O(1) · Design Hit Counter · LFU Cache (stretch) |
| Linked list | Reverse List · Copy List with Random Pointer · Reorder List · Reverse Nodes in k-Group |

**Priority if short on time:** LRU Cache, Merge Intervals, Meeting Rooms II, Top K Frequent, Number of Islands, Rotting Oranges, Course Schedule, LCA, Kth Largest, Min Window Substring, Coin Change, Word Break, Time-Based KV Store.

---

## 2. Low-Level Design (LLD)
**What they check:** clean classes & interfaces, SOLID, right design patterns, extensibility, **working Java code** for the core flow, concurrency where needed.

**Approach:** requirements (ask!) → core entities → class diagram (fields + methods) → relationships → patterns → code the main flow → edge cases & concurrency → how to extend.

| Problem | Key ideas |
|---|---|
| **Parking Lot** ⭐ | Vehicle/Spot types, strategy for spot allocation, ticket + payment, floors |
| **LRU / LFU Cache** ⭐ | HashMap + doubly linked list; eviction strategy interface; thread-safety |
| **Rate Limiter** ⭐ | token bucket / sliding window behind a `RateLimiter` interface; per-user map; thread-safe |
| **Notification Service** ⭐ (Paragon-like) | channels (email/SMS/push) via Strategy, Observer for subscribers, retry, templates |
| **Ticket / Case Management** ⭐ (Paragon-like) | Case states via **State pattern** (OPEN → ASSIGNED → IN_PROGRESS → RESOLVED), assignment strategy (round-robin / skill-based), SLA timers, comments, audit history |
| Elevator | State pattern, scheduling strategy (nearest car / SCAN) |
| Movie / Hotel booking | seat locking & concurrency (no double booking) |
| Splitwise | balance simplification, expense split strategies |
| Logger framework | Chain of Responsibility for levels, Singleton, multiple sinks |
| Vending machine | State pattern |
| Pub-Sub / Message queue | topics, subscribers, offsets, thread pools |
| Task Scheduler | priority queue by run time, worker threads, retries |

**Patterns to name:** Strategy, Observer, Factory, Builder, Singleton, State, Decorator, Chain of Responsibility, Command. (Revise: `Core Java and Springboot/core-java/02-oop-solid.md`, `08-design-patterns.md`, `07-multithreading-concurrency.md`.)

---

## 3. System Design — "Scalability & Operational Performance"
### Priority from your list
| Priority | Design | Why |
|---|---|---|
| 🔴 1 | **Notification System** (unchecked!) | very common; Paragon sends customer/associate notifications |
| 🔴 2 | **Flash Sale** (unchecked!) | Amazon classic: inventory, high write contention, queues |
| 🟠 3 | **Rate Limiter**, **Distributed Cache** | building blocks; you have real experience |
| 🟠 4 | **Job Scheduler** | workflows / SLA timers in case management |
| 🟠 5 | **Ticketmaster** | reservations, locking, consistency |
| 🟠 6 | **WhatsApp** (chat) | customer-service chat with associates |
| 🟡 7 | Metrics Monitoring, Ad Click Aggregator / Top K | operational performance, streaming |
| 🟡 8 | Payment System, Bitly, News Feed | review your notes |
| ➕ | **Customer-support ticketing / case routing** | likely Paragon-flavoured question — practise once |

### Framework (45 min)
1. **Requirements** — functional (3–4) + non-functional with **numbers** (QPS, latency, availability, consistency). (5 min)
2. **Back-of-envelope** — QPS, storage, bandwidth (your notes: `Back of Envelope Calculations/`). (3 min)
3. **APIs** + **data model**. (5 min)
4. **High-level diagram.** (10 min)
5. **Deep dives** — bottlenecks, sharding, caching, queues, consistency, hot keys. (15 min)
6. **Operational performance** ⭐ (this team stresses it) — see below. (5 min)

### "Operational performance" — say these explicitly
- **Metrics:** p50/p99 latency, error rate, throughput, saturation (CPU, memory, queue depth).
- **Alarms** on SLO breaches; **dashboards**; **runbooks**; on-call.
- **Deployment safety:** canary / one-box → staged rollout → **automatic rollback** on alarms.
- **Resilience:** timeouts, retries with backoff + jitter, circuit breakers, bulkheads, idempotency keys, DLQs, graceful degradation.
- **Scaling:** autoscaling, pre-scaling for known peaks, load shedding, caching, multi-AZ.
- **Load testing:** ramp, spike, soak tests before launch.
- Tie to your experience: live-cricket spikes, OOMKill investigation, Kafka circuit breaker, stacked timeouts, cache stampede.

### Paragon-style design sketch — "Design a customer-support case routing system"
- **FR:** create case (chat/email/phone), route to the right associate (skill, language, load), associate workbench shows queue, update/resolve, SLAs + escalation, notifications.
- **NFR:** low latency routing (<1 s), high availability, no lost cases, audit trail.
- **Design:** Case Service (DB, state machine) → **event bus (Kafka/SQS)** → Routing Service (queues per skill; picks associate by availability/skill; uses Redis for live associate state) → Assignment → Notification Service → Workbench UI (WebSocket for live updates) · **SLA scheduler** (delayed jobs → escalate) · Search (Elasticsearch) · Analytics.
- **Deep dives:** exactly-one assignment (conditional update / lock), associate presence (heartbeats), hot queues, idempotency, retries/DLQ, metrics (time-to-assign, backlog per queue).

---

## 4. Leadership Principles — every round
Expect 1–2 LP questions per round → prepare **8 strong STAR stories**, each covering 2–3 LPs. Use **"I"**, give **data**, include **what went wrong / learned**. Bar raisers dig 3–4 levels deep — use only true stories.

| # | Story (real) | LPs it covers | Notes file |
|---|---|---|---|
| 1 | **Spring Boot migration** with parity tests (strangler fig, 67 cases, ~1,200 tests, Sonar fixes) | Ownership, Insist on Highest Standards, Deliver Results, Invent & Simplify | `ResumeDiscussion/02` |
| 2 | **ML traffic spike** — wrong first guess (keepalive) → real bug (cache key vs cache time) | Dive Deep, Are Right A Lot, Bias for Action | `Core Java and Springboot/CrowdStrike_Production_Incidents.md` Story 1 |
| 3 | **Asia Cup empty rails** — fixed under peak pressure | Customer Obsession, Deliver Results, Bias for Action | Incidents Story 2 |
| 4 | **Preview tool** (your PR) — ops validate before publishing | Customer Obsession, Invent & Simplify, Ownership | `ResumeDiscussion/08` |
| 5 | **Bad segment rule isolation** + flagged eval security risk | Ownership, Highest Standards, Earn Trust | Incidents Story 3 |
| 6 | **OOMKill / page-cache investigation** (team) — your part | Dive Deep, Learn & Be Curious | Incidents Story 7 |
| 7 | **Security hotspots** (ReDoS, 42 stack-trace leaks, file permissions) | Highest Standards, Ownership, Success & Scale Bring Broad Responsibility | Security guide §1 |
| 8 | ✍️ **A disagreement** with lead/teammate (design choice, deadline) | Have Backbone; Disagree & Commit, Earn Trust | write it |
| 9 | ✍️ **A failure / missed deadline / mistake** | Earn Trust, Learn & Be Curious | write it (Story 2's wrong guess works) |
| 10 | ✍️ **Helping / mentoring someone** | Hire & Develop the Best | write it |

### Most-asked LP questions
- Tell me about a time you **took ownership** beyond your scope.
- A time you **disagreed** with your manager / team — what happened?
- Your **biggest failure** / a mistake — what did you learn?
- A time you **dived deep** to find a root cause.
- A time you made a decision **without all the data**.
- A time you **simplified** something complex.
- A time you **missed a deadline** or had to cut scope.
- A time you **went above and beyond for a customer**.
- A time you **received critical feedback**.
- Your most **innovative** idea.

**STAR timing:** Situation 15% · Task 10% · **Action 55%** · Result 20% (with numbers + learning). ~2–3 min each.

---

## 5. Night-before & day-of
- Test camera, mic, internet; have a pen + paper or whiteboard tool ready.
- In each round: **think out loud**, ask clarifying questions, keep 2 questions ready for the interviewer.
- If stuck: say what you're thinking, try a simpler version, ask for a hint — that's fine.
- Between rounds: 2-minute reset, water, don't replay the last round.

## 6. Good questions to ask interviewers
- "What does the Paragon team own, and what are its biggest scaling or operational challenges?"
- "How does the team measure operational health — what metrics and alarms matter most?"
- "What would success look like for an SDE II in the first six months?"
