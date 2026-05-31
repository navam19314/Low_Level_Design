# LLD Interview Prep Strategy

> Ruthless prioritization of the 20 problems in this deck. Tells you exactly
> which to drill, which to skip, in what order, and how much time each is worth.
> Target companies: Adobe / Microsoft / Amazon India SDE-2.

---

## The Optimal 10 — covers ~95% of what gets asked

### Tier A — Top 5 (cannot skip — universal must-do)

| # | Problem | Why |
|---|---|---|
| 1 | **Parking Lot** | The universal warm-up. If you can't do this cold, nothing else matters. |
| 2 | **Splitwise** | Adobe + Amazon love it. Money atomicity + graph + ordered locks = senior signal in 45 min. |
| 3 | **Movie Ticket Booking** | Amazon India + Adobe (BookMyShow flavor). Two-phase booking + per-seat concurrency. |
| 4 | **LRU Cache** | Microsoft + universal. HashMap + DLL composition is interview-mandatory knowledge. |
| 5 | **Vending Machine** | Microsoft favorite. THE textbook class-per-state pattern. 30 min and you own State. |

**After these 5, you can claim "I can do LLD" in any India SDE-2 round.**

### Tier B — Next 3 (high yield)

| # | Problem | Why |
|---|---|---|
| 6 | **Cab Booking** | Amazon India + Adobe. Multi-pattern showcase. If you can do this, you can do anything. |
| 7 | **Rate Limiter** | Microsoft + Amazon. Strategy + per-key concurrency. Asked often as warm-up at MS. |
| 8 | **Snake & Ladder** | Adobe-frequent. 25-min drill. Cheap insurance against a curve-ball game question. |

### Tier C — Pattern coverage (pick 2 of 4)

Choose whichever patterns feel weakest:

| # | Problem | Pattern it teaches |
|---|---|---|
| 9 | **Logging Service** OR **Job Scheduler** | Async + Producer-Consumer (BlockingQueue + worker pool) |
| 10 | **File System** OR **Chess** | Composite / Polymorphism (no type-switching) |

---

## What to skip (and why)

| Problem | Reason to skip |
|---|---|
| **Connect Four** | Low frequency at India SDE-2; gravity logic doesn't transfer to other problems |
| **Amazon Locker** | Same patterns as Parking Lot — redundant |
| **Inventory Management** | Same patterns as Movie Ticket Booking — redundant |
| **Notification System** | Partial overlap with Logger; Observer-flavored — pick one |
| **Meeting Scheduler** | Niche interval algorithms; rarely asked at SDE-2 |
| **Payment Gateway** | Idempotency lesson is great but problem itself is rare in India |
| **URL Shortener** | More HLD-flavored; LLD round rarely asks the full thing |
| **Elevator** | Hard to time-fit in 45 min; mostly system-design territory |

**Note:** "Skip" means *don't drill these as full 45-min mocks*. Still **read the Quick Cards** for all 20 (in `LLD_Revision_Sheet.docx`) — it's 5 minutes per card, 100 minutes total, and it lets you adapt on the fly if a curve-ball lands.

---

## Optimal study order (3-week plan)

```
Week 1 — Foundation                Week 2 — Core                   Week 3 — Stretch
─────────────────────              ─────────────────────           ─────────────────────
Mon  Parking Lot                   Mon  Movie Ticket               Mon  Cab Booking
Tue  Snake & Ladder                Tue  Splitwise                  Tue  Cab Booking (re-drill)
Wed  Vending Machine               Wed  Splitwise (drill)          Wed  Rate Limiter
Thu  LRU Cache                     Thu  Logger / JobScheduler      Thu  File System / Chess
Fri  Parking Lot (re-drill)        Fri  Movie Ticket (drill)       Fri  Mock interview day
Sat  Catch-up                      Sat  Catch-up                   Sat  Catch-up
Sun  Rest                          Sun  Rest                       Sun  Rest
```

For each problem (across all three weeks):
1. **Day 1**: Read the Quick Card + full walkthrough (~30 min)
2. **Day 2**: Cold drill — close the doc, code in 45 min, then review (~60 min)
3. **Day 5+**: Re-drill in 35 min (~45 min)

By end of Week 3 each Tier-A/B problem has had 2 drills + 1 reading.

---

## Time budget per problem (realistic)

| Activity | Time |
|---|---|
| Read Quick Card + walkthrough | 30 min |
| First cold drill (45-min mock + 15-min review) | 60 min |
| Second drill | 45 min |
| **Mastered ≈ confident in interview** | **~2.5 hours per problem** |

**Total prep:**
- **5 problems (Tier A only)** ≈ 12 hours → "passable to strong"
- **8 problems (Tier A + B)** ≈ 20 hours → "solid"
- **10 problems (Tier A + B + 2C)** ≈ 25 hours → "complete"
- More than 12 problems → diminishing returns

---

## Realistic interview math

| What you'll see | Probability |
|---|---|
| One of Tier A's 5 (or a close variant) | ~70% |
| One of Tier B's 3 | ~20% |
| A curve-ball that needs adaptation | ~10% |

**The fastest path to a strong outcome is "drill 5 until they're reflexes," not "study all 20 superficially."** Depth beats breadth in LLD because the senior signal comes from *empirically* showing you understand the contention point, the lock granularity, the state machine guards — not from naming 20 different patterns.

---

## What "drilled until reflexive" actually looks like

For Parking Lot — your benchmark — you should be able to (without notes):
- List the 5 entities in 30 sec
- Sketch the class diagram in 5 min
- Code `Spot.tryReserve` + `Floor.findAndReserve` + `ParkingLotService.park` in 15 min
- Verbalize the per-spot lock granularity argument in 1 min
- Pivot to Step 5 questions (different vehicle sizes, multi-floor, payment integration) cold

If you can do that, you've drilled it enough. Apply the same bar to all Tier-A problems.

---

## Company-specific priorities (quick reference)

### Adobe Noida SDE-2 — top 5 to drill
1. Parking Lot · 2. Snake & Ladder · 3. Splitwise · 4. Movie Ticket (BookMyShow) · 5. Vending Machine

### Microsoft India SDE-2 — top 5 to drill
1. Vending Machine · 2. LRU Cache · 3. Logger · 4. Rate Limiter · 5. Job Scheduler

### Amazon India SDE-2 — top 5 to drill
1. Parking Lot · 2. Splitwise · 3. Movie Ticket Booking · 4. Cab Booking · 5. Rate Limiter

---

## Senior signals to verbalize (regardless of problem)

These cost you nothing to say and significantly raise your level:

1. *"Money is `long` cents — never doubles."*
2. *"Half-open intervals `[start, end)` to avoid boundary bugs."*
3. *"Per-resource lock granularity, not a global lock."*
4. *"Strategy is a one-sentence test: do I need 2+ implementations on day 1?"*
5. *"Class-per-state when behavior differs per state; enum-state-machine when transitions are just bookkeeping."*
6. *"Tell-Don't-Ask — the object decides, callers don't peek and branch."*
7. *"Clock injected for testability."*
8. *"Empirical concurrency proof — 50 threads + CountDownLatch, assert the invariant holds."*

---

## Anti-patterns to NEVER do in interview

| Don't say | Why it's a red flag |
|---|---|
| "I'll make it a Singleton" | Instantiation choice, not a pattern. Tell. |
| "I'll just use doubles for money for now" | Never. Long cents always. |
| "I'll add Observer in case we need it" | YAGNI. Defer to Step 5. |
| "I'll use HashMap for synchronization" | Use ConcurrentHashMap or explicit locks. |
| "I'll lock the whole \[lot/show/inventory\]" | Per-resource locks. Always. |

---

## When you only have 7 days

Crash plan — Tier A only, drilled hard:

| Day | Action |
|---|---|
| 1 | Read Parking Lot + Splitwise walkthroughs |
| 2 | Read Movie Ticket + LRU Cache + Vending Machine |
| 3 | Cold-drill Parking Lot (45 min) |
| 4 | Cold-drill Splitwise (45 min) |
| 5 | Cold-drill Movie Ticket (45 min) |
| 6 | Cold-drill LRU Cache + Vending Machine (90 min total) |
| 7 | Read all 20 Quick Cards (100 min) + skim reference pages |

That's 7 hours of focused work. Gets you to "passable to strong" — enough to survive an SDE-2 LLD round.

---

## When you only have 1 day

Read the **Quick Cards for the 8 Tier-A+B problems** (40 min) and the **5 reference pages** (30 min). Pick your weakest Tier-A problem and cold-drill it for 45 min. Total: 2 hours.

Not ideal but recoverable. The Quick Cards give you the cheat codes; the reference pages give you the universal playbook.

---

## File layout (for context)

```
hello_all_questions/
├── 00_PREP_STRATEGY.md             ← THIS FILE
├── parkinglot/                     ← Tier A
├── splitwise/                      ← Tier A
├── movieticket/                    ← Tier A
├── lrucache/                       ← Tier A
├── vendingmachine/                 ← Tier A
├── cabbooking/                     ← Tier B
├── ratelimiter/                    ← Tier B
├── snakeladder/                    ← Tier B
├── logger/                         ← Tier C (pick 1 of 2)
├── jobscheduler/                   ← Tier C (pick 1 of 2)
├── filesystem/                     ← Tier C (pick 1 of 2)
├── chess/                          ← Tier C (pick 1 of 2)
├── connectfour/                    ← Skip
├── amazonlocker/                   ← Skip
├── inventory/                      ← Skip
├── notification/                   ← Skip
├── meetingscheduler/               ← Skip
├── paymentgateway/                 ← Skip
├── urlshortener/                   ← Skip
└── elevator/                       ← Skip
```

Each problem folder contains:
- The Java source files (compiles + has a working driver)
- `INTERVIEW_WALKTHROUGH.md` — the full 45-min interview walkthrough

The consolidated `LLD_Revision_Sheet.docx` (in `~/Downloads/`) bundles all walkthroughs + Quick Cards + 5 reference pages with a static page-numbered index.
