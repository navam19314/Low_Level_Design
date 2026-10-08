# LLD Deck: Index

Every problem, grouped by **what kind of problem it is**. Problems in the same folder share a skeleton: learn one well, and the others are variations.

**Start here:** [Java + Patterns refresher](00_JAVA_AND_PATTERNS_REFRESHER.md) → [Foundations](00_AMAZON_LLD_FOUNDATIONS.md) (how to think about *any* LLD problem) → the problems below.

**Tags:** ★ reported at Amazon in 2026 · ☆ reported at Amazon in 2025 · **E** on your Ethos list

---

## allocation/: "find a free slot that fits, claim it atomically"
| Problem | The crux | Asked |
|---|---|---|
| [Parking Lot](allocation/parkinglot/INTERVIEW_WALKTHROUGH.md) | smallest-fit spot, concurrent claim, pricing strategy | **E**, Amazon classic |
| [Amazon Locker](allocation/amazonlocker/INTERVIEW_WALKTHROUGH.md) | the same allocation + a one-time, expiring, unguessable pickup code | ★ ☆ |

## booking/: "two people want the same thing at the same time"
| Problem | The crux | Asked |
|---|---|---|
| [Movie Ticket (BookMyShow)](booking/movieticket/INTERVIEW_WALKTHROUGH.md) | atomic check-and-book per show, seat holds while paying | ★ **E** |
| [Meeting Room Scheduler](booking/meetingscheduler/INTERVIEW_WALKTHROUGH.md) | half-open time ranges, O(log n) conflict check with TreeMap | ★ |
| [Inventory (multi-warehouse)](booking/inventory/INTERVIEW_WALKTHROUGH.md) | no negative stock, deadlock-free transfers, flash-sale reservations | ★ (HLD flash sale) |

## matching/: "rank candidates, claim one atomically, then run a lifecycle"
| Problem | The crux | Asked |
|---|---|---|
| [Cab Booking / Rider Matching](matching/cabbooking/INTERVIEW_WALKTHROUGH.md) | matching strategy, `tryReserve` claim, trip state machine | ★ |
| [Food Delivery](matching/fooddelivery/INTERVIEW_WALKTHROUGH.md) | order state machine, rider CAS claim, price snapshot | **E** (reported at Ethos) |

## state_machines/: "what you can do depends on the current state"
| Problem | The crux | Asked |
|---|---|---|
| [Vending Machine](state_machines/vendingmachine/INTERVIEW_WALKTHROUGH.md) | the textbook State pattern (class per state) | ★ |
| [Insurance Application](state_machines/insurance/INTERVIEW_WALKTHROUGH.md) | State pattern + underwriting rules as Strategy | **E** (Ethos's core product) |
| [Elevator](state_machines/elevator/INTERVIEW_WALKTHROUGH.md) | SCAN scheduling, direction-aware dispatch | **E** |
| [Download Manager](state_machines/downloadmanager/INTERVIEW_WALKTHROUGH.md) | producer–consumer + pause/resume race + resume from an offset | ★ |

## pipelines/: "one event, many outputs" and "queue + workers"
| Problem | The crux | Asked |
|---|---|---|
| [Notification Service](pipelines/notification/INTERVIEW_WALKTHROUGH.md) | Observer event bus, sender Factory, retry Decorator, isolation | ★ **E** |
| [Logger](pipelines/logger/INTERVIEW_WALKTHROUGH.md) | formatter + sink composition, lock only the write, async follow-up | ☆ |
| [Job Scheduler](pipelines/jobscheduler/INTERVIEW_WALKTHROUGH.md) | PriorityBlockingQueue + workers, delayed jobs, retries | pattern coverage |

## policies/: "same question, swappable algorithm"
| Problem | The crux | Asked |
|---|---|---|
| [Rate Limiter](policies/ratelimiter/INTERVIEW_WALKTHROUGH.md) | token bucket math, per-client lock, distributed with Redis | ★ **E** |
| [Splitwise](policies/splitwise/INTERVIEW_WALKTHROUGH.md) | split strategies, one balance per pair, greedy simplify | **E** |
| [Payment Gateway](policies/paymentgateway/INTERVIEW_WALKTHROUGH.md) | idempotency keys done right, payment state machine | ★ (HLD payments) |
| [URL Shortener](policies/urlshortener/INTERVIEW_WALKTHROUGH.md) | Base62, counter vs random ids, the code-claim race | ☆ (HLD) |

## composition/: "objects that contain or wrap other objects"
| Problem | The crux | Asked |
|---|---|---|
| [Coffee Machine / Pizza](composition/coffeemachine/INTERVIEW_WALKTHROUGH.md) | Decorator (stackable add-ons), all-or-nothing inventory | ★ |
| [File System](composition/filesystem/INTERVIEW_WALKTHROUGH.md) | Composite (folder size = sum of children), path parsing, move cycle check | pattern coverage |
| [Kanban / Trello](composition/kanban/INTERVIEW_WALKTHROUGH.md) | ordered lanes and cards, atomic moves, optimistic versioning | **E** |

## data_structures/
| Problem | The crux | Asked |
|---|---|---|
| [LRU Cache (+ LFU)](data_structures/lrucache/INTERVIEW_WALKTHROUGH.md) | HashMap + doubly linked list, O(1) everything | **E**, LeetCode 146 |

## games/: "board, turns, win check"
| Problem | The crux | Asked |
|---|---|---|
| [Tic-Tac-Toe](games/tictactoe/INTERVIEW_WALKTHROUGH.md) | O(1) win check with counters, N×N | ☆ **E** |
| [Snake & Ladder](games/snakeladder/INTERVIEW_WALKTHROUGH.md) | board = map, game = rules, dice Strategy for testing | ☆ **E** |
| [Connect Four](games/connectfour/INTERVIEW_WALKTHROUGH.md) | gravity + 4-direction check through the last disc | low frequency |
| [Chess](games/chess/INTERVIEW_WALKTHROUGH.md) | polymorphic pieces, king safety via try-and-undo | pattern coverage |

---

## Suggested order

**Ethos round:** Insurance → Food Delivery → Parking Lot → Splitwise → Rate Limiter → Kanban → Movie Ticket → Notification → LRU → Tic-Tac-Toe / Snake & Ladder / Elevator.

**Amazon loop:** Rate Limiter → Movie Ticket → Cab Booking → Vending Machine → Notification → Amazon Locker → Coffee Machine → Download Manager → Parking Lot → Logger → Meeting Room → Splitwise → LRU.

**Learn one per folder first**, then the rest of that folder go quickly, because they reuse the same skeleton: Parking Lot → Amazon Locker; Movie Ticket → Meeting Room → Inventory; Cab Booking ↔ Food Delivery; Vending Machine → Insurance.

---

## Running any problem

Every problem has a driver whose `main()` runs its scenarios (and a many-threads test where it matters):
```bash
mvn -q compile exec:java -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.<folder>.<problem>.<Driver>
```
For example:
```bash
mvn -q compile exec:java -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.InsuranceDriver
```
Each walkthrough ends with its exact command.
