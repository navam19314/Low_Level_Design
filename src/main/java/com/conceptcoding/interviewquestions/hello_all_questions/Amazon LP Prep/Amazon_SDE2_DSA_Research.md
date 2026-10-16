# Amazon SDE-2 — What Actually Gets Asked (research, Oct 2026)

**Source:** ~60 Amazon SDE-2 / L5 interview experiences on LeetCode Discuss (mostly India, Jun 2025 – Aug 2026), incl. the "Amazon Interview Experiences Last 6 Months — Compiled List" and "Amazon SDE II Interview Experience 2026". Counts = how many experiences mentioned the problem.

## Key observations
1. **Graphs are the #1 topic** (BFS/DFS on grids, topological sort, shortest path, union-find).
2. **Design-a-data-structure** problems are very common: **LRU Cache** shows up again and again (even as a "DSA" round and inside LLD "cache" rounds), plus Median from Data Stream, LFU, All O(1), Max Stack.
3. Problems are often **disguised** ("same question, different story") — e.g. Koko Eating Bananas told as a different scenario, Rotting Oranges variant, Asteroid Collision "asked in a different way". Recognise the **pattern**.
4. Most rounds: **2 LP questions (15–25 min) + 1–2 DSA problems** (medium, sometimes medium-hard). Follow-ups on optimisation are common ("now optimise space").
5. **"Problem Solving" round** = a vague/ambiguous problem with no signature — you must **ask clarifying questions** and define the API yourself.
6. Working, clean code is expected (some rounds ran code). Think out loud.

---

## 🔴 Tier 1 — do ALL of these (highest frequency)
| # | Problem | Pattern | Seen |
|---|---|---|---|
| 146 | **LRU Cache** | design, hashmap + DLL | ×4+ |
| 200 | **Number of Islands** (+ variants) | grid DFS/BFS | ×3+ |
| 295 | **Find Median from Data Stream** | two heaps | ×3 |
| 402 | **Remove K Digits** | monotonic stack | ×3 |
| 994 | **Rotting Oranges** (+ "time to burn tree" variant) | multi-source BFS | ×3 |
| 875 | **Koko Eating Bananas** (disguised) | binary search on answer | ×3 |
| 139 | **Word Break** (+ 140 Word Break II) | DP / memo | ×3 |
| 417 | Pacific Atlantic Water Flow | grid DFS from borders | ×2 |
| 210 | Course Schedule II (+ 2115 Find All Possible Recipes) | topological sort | ×3 |
| 337 | House Robber III (+ space follow-up) | tree DP | ×2 |
| 1004 | Max Consecutive Ones III | sliding window | ×2 |
| 438 | Find All Anagrams in a String | sliding window | ×1 (phone screen) |
| 560 | **Subarray Sum Equals K** (2026 phone screen) | prefix sum + hashmap | ×2 |
| 49 | Group Anagrams | hashing | ×2 |
| 1235 | Maximum Profit in Job Scheduling | DP + binary search | ×2 |
| 1186 | Maximum Subarray Sum with One Deletion | DP (Kadane variant) | ×2 |
| 316 | Remove Duplicate Letters | monotonic stack + greedy | ×2 |
| 134 | Gas Station | greedy | ×2 |
| 1167 / ropes | Minimum Cost to Connect Sticks / Ropes | min-heap | ×2 |
| 41 | First Missing Positive | index marking | ×2 |

## 🟠 Tier 2 — strongly recommended
| Topic | Problems seen in experiences |
|---|---|
| **Graphs** | 127 Word Ladder · 743 Network Delay Time (Dijkstra) · 815 Bus Routes · 332 Reconstruct Itinerary · 399 Evaluate Division · 827 Making a Large Island · cycle detection · MST (Kruskal/Prim) · DSU-optimised connectivity |
| **Trees** | 297 Serialize/Deserialize · 199 Right Side View · Top View · 2385 Time for Tree to Be Infected · 543 Diameter (+ return the path) · 110 Balanced Tree · 272 Closest BST Values II · tree height from parent array |
| **Sliding window / two pointers** | 1423 Max Points from Cards · 1658 Min Operations to Reduce X to Zero · 904 Fruit Into Baskets · 209 Min Size Subarray Sum · 239 Sliding Window Maximum · 1297 Max Occurrences of a Substring |
| **Heap / top-K** | 23 Merge K Sorted Lists · 621 Task Scheduler · 767 Reorganize String · 692 Top K Frequent Words · 373 K Pairs with Smallest Sums · **Top K items in last hour / all time** · **Top K hashtags** |
| **Design data structures** | 460 LFU Cache · 432 All O(1) · 716 Max Stack · **smallest-available-ID allocator** (`getToken`/`returnToken` → min-heap/TreeSet) · 362 Design Hit Counter |
| **Binary search** | 1552 Magnetic Force Between Balls · 33 Search Rotated Sorted Array · 540 Single Element in Sorted Array · 162 Peak Element (with variations) |
| **Stack** | 735 Asteroid Collision · 224 Basic Calculator · 1209 Remove Adjacent Duplicates II · 716 Max/Min Stack |
| **DP** | 300 LIS (variant) · 518 Coin Change II · 312 Burst Balloons · 1277 Count Square Submatrices · 198 House Robber |
| **Strings** | 1392 Longest Happy Prefix (KMP) · 273 Integer to English Words · 79 Word Search |
| **Intervals** | Minimum Platforms / 253 Meeting Rooms II · 56 Merge Intervals |
| **Other** | 31 Next Permutation · Celebrity Problem · QuickSort (with follow-up) |

## 🟡 Tier 3 — if time permits
Segment tree (one candidate got it), 25 Reverse Nodes in k-Group, 42 Trapping Rain Water, 76 Minimum Window Substring, 72 Edit Distance.

---

## Topic weightage (from the experiences)
| Topic | Weight |
|---|---|
| Graphs (BFS/DFS/topo/Dijkstra/DSU) | ████████ highest |
| Design data structures (LRU etc.) | ███████ |
| Sliding window / two pointers / prefix sum | ██████ |
| Trees | ██████ |
| Heap / top-K / greedy | █████ |
| Monotonic stack | ████ |
| DP | ████ |
| Binary search (incl. on answer) | ████ |
| Strings / KMP | ██ |

---

## LLD questions seen (for the LLD round) — grouped
| Group | Questions asked | Prep priority |
|---|---|---|
| **Booking / reservation** (concurrency!) | Movie ticket booking · Bus ticket booking (APIs + DB schema + concurrent booking) · Restaurant reservation/booking (×2) | 🔴 do one fully |
| **Cache / data structure** | Network request cache (**implement LRU in code**) · DNS resolver with health check + TTL, fast reads · File storing system like S3 | 🔴 |
| **Logging / filtering** | Logger framework (×2, one "with filters — combine multiple filters") · File filter by size & extension | 🔴 (Strategy / Chain of Responsibility / Specification) |
| **Notification / events** | Notification service (×2) · Event forwarding framework (pub-sub) · CDN push to edge nodes on new S3 content | 🔴 Paragon-relevant |
| **Classic OOD** | Parking lot (×2) · Elevator · Chess · ATM · Stack Overflow (DB normalisation asked) | 🟠 |
| **Money / commerce** | Splitwise (+ currency conversion — candidate failed for skipping it) · Gift card · Stock broker (Zerodha) · Seller experience app · Product ID generator with formats | 🟠 |
| **Scheduling / assignment** | Scheduler "run task after X seconds" · Delivery partner assignment · Rate limiter (coded sliding window) | 🟠 |
| **Other** | File system (create/print/delete children) · Package manager (yarn) · Amazon Locker | 🟡 |
→ Expect **interface-driven code + design patterns**; sometimes they want **APIs + DB schema** instead of classes. Always **finish the core logic** (e.g. actually code the LRU / currency conversion).

## HLD questions seen — grouped
| Group | Questions asked | In your Hello Interview list? |
|---|---|---|
| **Inventory / high-load commerce** | Amazon shopping cart with inventory under concurrent buying · Amazon website for **Diwali load** · Payment service | ✅ Flash Sale (done) · Payment System |
| **Messaging** | WhatsApp / chat (×2) · chat with video replies · Slack | ✅ WhatsApp |
| **Social / feed** | Twitter (consistent writes, timeline, 5 MB video) · News feed · K-leaderboard | ✅ FB News Feed · YouTube Top K |
| **Notifications** | Notification service (×2) | ❗ **Notification System — you haven't done it** |
| **Observability** ⭐ ("operational performance") | Log ingestion for microservices · Metrics & distributed tracing · Real-time monitoring system · Anomaly detection (traffic spikes) | ✅ Metrics Monitoring · Ad Click Aggregator |
| **Support / ticketing** ⭐ (Paragon-like) | Error-report system: user flags a bad search result → ticket → support assigned → resolved | ❌ not in list — practise once |
| **Location / matching** | Uber rider–driver matching (×2) · flightradar24 · route map for pickups from many stores | ✅ Uber |
| **Storage / media** | Google Drive · Netflix · URL shortener | ✅ Dropbox · YouTube · Bitly |
| **Booking** | Adventure-park booking · Parking lot (HLD) | ✅ Ticketmaster |
| **Other** | Recommendation service | — |

## LP questions seen
Conflict with manager/peer · missed a deadline · tight deadline (what did you sacrifice?) · project you're proud of · innovative idea · unknowns in a new project / new tech you had to learn · took something outside your responsibility · dug deep into a complex problem (who did you talk to?) · calculated risk where speed mattered · tough/critical feedback · anticipated a customer need · above and beyond · Why Amazon? — **expect deep cross-questioning on every story**.

---

## How to use LeetCode Premium
1. Problems → **Company: Amazon** → sort by **Frequency** → filter **"Last 30 days" / "Last 3 months"**.
2. Do the top ~40 that overlap with Tier 1–2 above.
3. Use **Amazon-tagged mock assessments** (Premium → Interview → Assessment) for timed practice.

## 4-day DSA plan (fits your loop on Fri 16 Oct)
| Day | Do |
|---|---|
| Mon | LRU, Median Stream, Subarray Sum = K, Group Anagrams, Find All Anagrams, Max Consecutive Ones III |
| Tue | Number of Islands, Rotting Oranges, Pacific Atlantic, Course Schedule II, Word Ladder, Network Delay Time |
| Wed | Koko, Remove K Digits, Remove Duplicate Letters, Word Break, House Robber III, Job Scheduling, Min Cost Ropes |
| Thu | Serialize/Deserialize Tree, Burn Tree, Top K Frequent Words, Smallest-ID allocator, Gas Station + 1 timed mock |
