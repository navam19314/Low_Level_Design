# Amazon SDE-2 LLD — Foundations

Read this once properly, then re-read it the night before. Every problem MD in this deck builds on it.

**What this page gives you:**
1. A way of thinking that works on problems you have never seen
2. The 7 problem families (most LLD questions are one or two of these)
3. Design patterns: when one helps, and when it doesn't (code is in the [Java + Patterns refresher](00_JAVA_AND_PATTERNS_REFRESHER.md))
4. The Java concurrency toolkit (5 tools cover every problem here)
5. The 35-minute Amazon round, minute by minute
6. Follow-ups that come up in every problem, with answers

---

## 1. How to think about any LLD problem

Ask these 6 questions, in this order. They work for a problem you have never seen.

| # | Ask yourself | What it gives you |
|---|---|---|
| 1 | **What is the one operation everything revolves around?** | Your main method. `book()`, `allow()`, `park()`, `dispense()`. Design outward from it. |
| 2 | **What can go wrong with that operation?** | The *crux*. Two people grab the same seat? Wrong state? Wrong algorithm? One output fails? This is what the interviewer is really testing. |
| 3 | **Which nouns have state or behaviour?** | Those become classes. Nouns that are only labels (client id, endpoint, seat number) stay `String`/`enum`. |
| 4 | **Who owns each piece of data?** | One owner per fact (single source of truth). If two classes both store "seat 5 is booked", they will disagree one day. |
| 5 | **What will change or come in several kinds?** | Put an interface there. Leave everything else concrete. |
| 6 | **What is the smallest object that protects the rule?** | Put the lock there. Not on the whole system. |

> If you can answer Q2 (the crux) in one sentence early on, say it out loud.
> *"The core challenge here is two users booking the same seat at once."*
> That one sentence tells the interviewer you see what matters.

---

## 2. The 7 problem families

Learn the **skeleton** of each family. When you get a new question, first work out which family (or two) it belongs to, then start from that skeleton.

### F1. Allocation: "find a free slot that fits"
- **Problems:** Parking Lot, Amazon Locker
- **Skeleton:** `Slot(id, size)` · `allocate(item) → Ticket` · `release(ticketId)` · free/occupied tracked in one place
- **Crux:** choosing the slot (smallest that fits) and claiming it atomically
- **Signal:** sizes, capacity, "assign", "nearest free"

### F2. Booking under contention: "two people want the same thing"
- **Problems:** Movie Ticket, Meeting Room, Restaurant table, Inventory last unit
- **Skeleton:** `Resource` · `Booking` · check-availability + book in **one atomic step**, using a lock on that resource only
- **Crux:** no double-booking. Optionally a temporary hold that expires. Time ranges use half-open intervals `[start, end)`.
- **Signal:** seats, slots, time ranges, "at the same time"

### F3. State machine: "what you can do depends on where you are"
- **Problems:** Vending Machine, Elevator, Download Manager, Ride/Trip lifecycle
- **Skeleton:** `enum State` + allowed transitions, **or** one class per state when the behaviour differs a lot per state
- **Crux:** reject illegal actions (can't dispense before paying, can't resume a finished download)
- **Signal:** lifecycle words: idle, paused, in-progress, completed, cancelled

### F4. Fan-out pipeline: "one event, many outputs"
- **Problems:** Logger, Notification Service
- **Skeleton:** `send(event)` loops over N handlers (`Sink` / `Sender`), each wrapped in its own try/catch
- **Crux:** one broken output must not stop the others. Optional: async with a queue, retries.
- **Signal:** channels, destinations, "email + SMS + push", levels

### F5. Swappable policy: "same question, different algorithms"
- **Problems:** Rate Limiter, Rider Matching, Splitwise split types, Elevator dispatch, Job Scheduler
- **Skeleton:** an interface for the algorithm, 2+ implementations, a service that holds one
- **Crux:** the algorithm itself (refill math, nearest driver) plus a clean interface boundary
- **Signal:** "by distance or by rating", "equal or exact", "token bucket or sliding window"

### F6. Board / turn game
- **Problems:** Tic-Tac-Toe, Snake & Ladder, Connect Four, Chess
- **Skeleton:** `Board` · `Player` · `Game` (turn loop, validate move, check winner)
- **Crux:** O(1) win check (row/column counters), clean turn rotation
- **Signal:** board, players, turns, win condition

### F7. Add-on composition: "stack extras on a base"
- **Problems:** Coffee Machine, Pizza Ordering
- **Skeleton:** `Beverage` interface · base drinks · decorators that wrap another `Beverage`
- **Crux:** avoid class explosion (`LatteWithMilkAndSugar...`). Price and description build up through the wrappers.
- **Signal:** toppings, add-ons, "extra shot", "requirements added mid-interview"

> **Real problems often combine two families.**
> Cab Booking = F5 (matching strategy) + F2 (two orders want one driver) + F3 (trip states).
> Download Manager = F3 (download states) + a producer-consumer queue (§4).
> Name both families in your head, then deal with each crux.

---

## 3. Design patterns: use them when they solve a real problem

**The one test for any pattern:** *"What goes wrong if I don't use it?"* If you can't answer that in one sentence for this problem, don't use the pattern.

| When you see... | Use | What goes wrong without it | Problems in this deck |
|---|---|---|---|
| 2+ interchangeable algorithms/policies **on day 1** | **Strategy** | `if/else` on a type string grows in one class; adding one means editing tested code | Rate Limiter, Cab Booking, Splitwise, Elevator, Job Scheduler, Meeting Scheduler |
| Behaviour of **every** method changes with state | **State** | every method starts with `if (state == ...)`; one missed check = illegal action | Vending Machine, Download Manager |
| Optional extras stacked at runtime | **Decorator** | one subclass per combination (2ⁿ classes) | Coffee Machine, Pizza; metrics wrapper in Rate Limiter |
| One event, several independent reactions that may change over time | **Observer** | the source has to know and call every reaction directly | Inventory low-stock alerts, Job Scheduler listeners |
| Pick the right subtype from config/input | **Factory** | the `switch` is copied everywhere objects are created | Rate Limiter (config), Notification senders |
| Object with many optional parts | **Builder** | constructors with 8 params, half `null` | Pizza (size, crust, toppings...) |
| Request passes through ordered handlers, each may handle it or pass it on | **Chain of Responsibility** | one giant method with every rule | ATM note dispenser, validation steps |
| Callers should see one simple entry point | **Facade** | callers juggle 5 internal objects | the main service class in **every** problem. Don't make a big deal of it. |

**Patterns to be careful with:**
- **Singleton:** say *"one instance, created once and injected"*. A static `getInstance()` makes testing hard. Amazon interviewers do ask about it, so know the thread-safe version (below), and say why you'd rather inject.
- **Observer "just in case":** this is YAGNI. Only use it when there is a real second listener.
- **More than 3 patterns in a 35-minute base:** you are over-engineering. Mention the extras as follow-ups.

**Code for every pattern** (plain-language picture, when to use it, a runnable example, and the patterns people mix up) is in [00_JAVA_AND_PATTERNS_REFRESHER.md](00_JAVA_AND_PATTERNS_REFRESHER.md), Part B.

---

## 4. Java concurrency toolkit: 5 tools cover everything

Amazon interviewers **will** ask *"what if two requests hit this at the same time?"*. Pick the tool by the shape of the problem:

| Shape of the problem | Tool | Example |
|---|---|---|
| **A. Many independent keys, each with its own state** | `ConcurrentHashMap.computeIfAbsent` + `synchronized(perKeyObject)` | Rate limiter bucket per client, showtime in movie booking |
| **B. Claim one thing exactly once** | `putIfAbsent` / `AtomicReference.compareAndSet` | claim a parking spot, claim a seat |
| **C. Need two locks at once** | always lock in a **fixed order** (e.g. by id) | Splitwise transfer A→B, seat hold across 2 rows |
| **D. Work produced in one place, done in another** | `BlockingQueue` + `ExecutorService` workers | Download Manager, async Logger, Job Scheduler |
| **E. Rarely written, often read list** | `CopyOnWriteArrayList` | listeners, sinks, config |

```java
// A. per-key lock: different clients never block each other
Bucket b = buckets.computeIfAbsent(clientId, k -> new Bucket(capacity, now));
synchronized (b) { /* read-modify-write this client's state only */ }

// B. claim exactly once: only one thread gets null back
String winner = spotOwner.putIfAbsent(spotId, ticketId);
if (winner != null) { /* someone else got it, try next spot */ }

// C. two locks, fixed order → no deadlock
Account first  = a.getId().compareTo(b.getId()) < 0 ? a : b;
Account second = first == a ? b : a;
synchronized (first) { synchronized (second) { /* move money */ } }

// D. producer-consumer
BlockingQueue<Task> queue = new PriorityBlockingQueue<>();
ExecutorService workers = Executors.newFixedThreadPool(4);
for (int i = 0; i < 4; i++) workers.submit(() -> { while (true) process(queue.take()); });
```

**Things to say about locks:**
- *"I lock the smallest thing that protects the rule. A global lock is correct but serialises everyone."*
- *"Never `synchronized` on a `String` key: two equal strings can be different objects, so they're different monitors."*
- *"Check-then-act must be inside one lock. `if (free) book()` across two steps is the classic race."*
- **Proving it:** 50 threads + `CountDownLatch` start gate, then assert the invariant (exactly 1 winner, exactly N allowed). Every driver in this deck has one. Mention it.

---

## 5. The Amazon LLD round (~35 minutes of design)

The round is 55–60 min. The **first 15–25 min are Leadership Principle questions**. You then get about **35 minutes**, and you **must write real code**. Candidates have been rejected for drawing diagrams and never coding.

| Minute | Do | Say |
|---|---|---|
| **0–4** | Clarify. Write 4–6 requirements + an explicit **out of scope** list. | *"Let me confirm scope: single machine, in-memory, thread-safe? I'll leave payments and persistence out."* |
| **4–8** | Entities + relationships. Interfaces first. | *"The nouns with state are X, Y. Z is just an id, so it stays a string."* |
| **8–10** | Say the **crux** and the pattern you'll use, with the reason. Write the method signatures. | *"The hard part is A. Two algorithms on day 1 → Strategy."* |
| **10–28** | **Code.** Core service + the 2–3 methods that contain the crux, fully. Skip getters/setters/boilerplate. **Start coding by minute 12–15 at the latest.** | Talk while coding: *"I'm using `<=` here because..."* |
| **28–32** | Dry-run one scenario on your code. Point at the lock. | *"Two threads arrive: both hit `synchronized(bucket)`, the second sees 0 tokens."* |
| **32–35** | Extensibility, in words. | *"To add X, I add one class implementing Y. Nothing existing changes."* |

**Rules:**
- **LLD is not HLD.** No load balancers, Kafka or databases until the core classes are coded. If they ask about scale, answer in one sentence, then come back to the code.
- **Narrate your decisions as "X over Y because Z."** That is the senior signal: you considered an alternative.
- **When a requirement is added mid-interview** (Amazon does this on purpose), show that it fits in by *adding* a class, not editing five.
- **If you are running out of time:** finish the crux method fully rather than half-writing every class.

---

## 6. Follow-ups that come up in every problem

Prepare these once and reuse them. Each problem MD has problem-specific versions.

<details>
<summary><b>"What if two requests hit this at the same time?"</b></summary>

Name the exact race (*"both read 1 seat free, both book it"*). Point at your fix (§4 tool A or B). Say why you chose that granularity over a global lock. Mention the 50-thread latch test.
</details>

<details>
<summary><b>"What if we run this on 10 servers / 10x traffic?"</b></summary>

In-memory state is per server, so each server enforces its own limit (or two servers book the same seat). Move the **shared state** to a shared store and make the check-and-update **atomic there**:
- DB: conditional update, e.g. `UPDATE seat SET status='BOOKED' WHERE id=? AND status='FREE'`. 1 row updated = you won.
- Redis: atomic ops / Lua script.

Shard by the natural key (clientId, showId) so one key's traffic always goes to one place. Then return to the code; don't drift into HLD.
</details>

<details>
<summary><b>"What fails, and what happens then?"</b></summary>

List the dependencies (payment, Redis, downstream sender) and give each a policy:
- **Timeouts + retry with exponential backoff and jitter**, only for errors that might succeed on retry
- **Idempotency key**, so a retry doesn't charge or book twice
- **Fail-open vs fail-closed**: which is safer for *this* product? A rate limiter usually fails open; payments fail closed.
- **Release held resources**: a seat hold expires, so a crash never locks the seat forever
</details>

<details>
<summary><b>"How would you know it's broken? How would you debug it?"</b></summary>

- **Metrics:** success/failure rate per operation, p99 latency, queue depth, retry count
- **Alarms** on those
- **Logs** with a request/correlation id, so one user's failed request can be traced
- Amazon loves this question. Have 3 concrete metrics ready for each problem.
</details>

<details>
<summary><b>"Add feature X without breaking what exists."</b></summary>

Find the interface it plugs into: *"New `FooStrategy implements Strategy`. Register it. No existing class changes."* That is the Open/Closed principle; say the name once. If X doesn't fit any interface, say honestly which one class changes and why that's acceptable.
</details>

<details>
<summary><b>"How would you test this?"</b></summary>

- **Inject `Clock`** (anything time-based), so tests move time instead of sleeping
- **Fake strategies**, e.g. a fixed-sequence dice or a fixed-location matcher
- **Concurrency test:** N threads, `CountDownLatch` start gate, assert the invariant
- **Edge cases:** exactly at a boundary, empty, full, same user twice
</details>

<details>
<summary><b>"Memory keeps growing with millions of users."</b></summary>

Per-key maps never shrink. Evict idle entries: a background sweeper removes keys not touched for T minutes, or a bounded LRU map. An evicted user looks brand new on their next request; check that's acceptable for this problem.
</details>

<details>
<summary><b>"Persist it / survive a restart."</b></summary>

Put storage behind an interface (`BookingRepository`), with an in-memory version now and a DB version later. The service depends on the interface (Dependency Inversion). One sentence is enough unless they push.
</details>

---

## 7. How candidates lose this round

1. Spent the round on diagrams and **never coded**
2. **Drifted into HLD** (Kafka, LB) before the classes existed
3. **Ignored concurrency** in booking/inventory/allocation problems
4. **Over-engineered:** 6 patterns, 15 classes, nothing works end to end
5. Classes for things with no behaviour (`Client`, `Request`, `Seat` with only an id)
6. `double` for money (use `long` rupees/paise), boundary bugs at `start == end`
7. Couldn't adapt when a requirement was added mid-round

---

## 8. How to study each problem in this deck

1. **Read §1 "Plain-language picture"** of the problem MD. Close it. Explain the problem out loud in 1 minute.
2. **Try the design yourself** (10 min on paper): run §1 of this page (the 6 questions) on it. *Then* read the problem's "From story to design". Compare your choices.
3. **Read the code** for the crux methods. Run the driver.
4. **Follow-ups:** read each question, answer it out loud, *then* open the answer.
5. **Next day:** answer the "Recall check" without looking. Anything you miss, re-read only that part.
6. **Timed drill** (later): 35 minutes, blank file, code the core.
