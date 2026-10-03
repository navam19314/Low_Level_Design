# Session Handoff — LLD Interview Prep Deck

_Last updated: 2026-08-02. Scope: the `hello_all_questions` package — a set of Low-Level-Design
(LLD) interview practice problems, each with runnable Java + a companion `INTERVIEW_WALKTHROUGH.md`._

---

## 1. Project overview

This is a **study/reference deck for 45-minute SDE-2 LLD interview rounds** (target companies:
Amazon India, Adobe Noida, Microsoft, Atlassian). It is NOT production software — every problem is
a self-contained mini-app that:

- compiles under Maven,
- has a `*Driver.java` `main()` that prints scenario output (acts as an executable test), and
- ships an `INTERVIEW_WALKTHROUGH.md` — a 5-step interview walkthrough (Requirements → Entities →
  Class Design → Implementation + dry-run → Extensibility) plus mental models, patterns, "what's
  expected at each level," complexity/concurrency/testing deep-dives, top-mistakes, and a 30-sec summary.

The owner uses these to drill problems and to refresh intuition after long gaps. **Two goals drive every
edit:** (a) code should be what you'd realistically *finish writing* in 45 minutes, and (b) the MD should
teach the senior signal for that problem without bloat.

Root of the deck:
`src/main/java/com/conceptcoding/interviewquestions/hello_all_questions/`

Strategy doc for prioritization: `00_PREP_STRATEGY.md` (Tier A/B/C ranking of the 20 problems).

---

## 2. Current architecture / conventions (apply these to ANY future edit)

These conventions were established and applied consistently across the "improved" problems. **Match them.**

### Code conventions
- **Plain `//` comments, not `/** Javadoc */`.** Every problem was converted away from Javadoc blocks.
- **Normal classes, NOT records**, for data holders (`Split`, `RateLimitResult`, `Booking`, `Notification`,
  `DeliveryResult`, etc.). Rationale the owner chose: "interviewers can frown on records / harder to write
  fluently on a whiteboard." **Do not convert classes back to records** without asking.
- **Money = `long`, never `double`/`float`.** Splitwise/Vending Machine/Parking Lot use whole **rupees**
  (₹, India context), not cents. Rate Limiter/others that predate the rupee switch may still say cents —
  check before assuming.
- **`catch (Exception)`, NOT `catch (Throwable)`** in failure-isolation boundaries (Notification, Logger).
  Reason: don't swallow JVM `Error`s (OOM/StackOverflow) inside a business-logic catch. This was a real bug
  fixed in both Notification and Logger.
- **Worked examples in comments** where the owner asked for study aids (Splitwise `ExpenseManager`,
  Vending Machine states) — concrete numeric traces, not abstract prose.
- **Law of Demeter**: prefer one-hop delegation but DON'T over-abstract for base scope (see Movie Ticket:
  the 5-level search cascade was deliberately flattened to a direct loop, with the LoD version kept only as
  a Step-5 talking point).

### MD structure (the "standardized" shape — 8 problems now follow it)
Every improved MD has, in order:
1. Header + one-line senior-signal summary
2. `Part 0 — Understand it from scratch` (layman intuition) — **only Logger & Notification have this so far**
3. Time budget table
4. Mental models (M1/M2/M3…)
5. Step 1 Requirements — as a **clarifying-questions dialogue** (You/Interviewer exchanges), then a
   requirements block, with an explicit `ASSUMED` vs `OUT OF SCOPE` split
6. Steps 2–5 (Entities, Class Design, Implementation + dry-run, Extensibility)
7. `Design patterns in play` — 3 tables: base-design patterns / Step-5 extension patterns / patterns to
   refuse + a "rule to sound natural"
8. `What is expected at each level` — Junior / Mid (the target) / Senior
9. Interview deep-dives (complexity, concurrency, one test snippet)
10. 30-second summary, Top mistakes, Files-in-folder table, run command

**Removed from the old MDs during standardization:** the "Hello Interview canonical 8 patterns" preamble,
the "5-step timing rule" table, the full SOLID mapping table, and the duplicate "Closing soundbites" section.
If you touch an un-standardized MD, apply this same cleanup.

---

## 3. Files modified this session (and their current state)

All of the following were edited/improved this session and **compile + run clean**:

| Problem | Status | Key work done |
|---------|--------|---------------|
| **ratelimiter** | ✅ done | 2026-10-03: base is Strategy-only (`LimiterFactory` + config ctor removed; Factory now lives only in MD follow-up Q5); `RateLimiter` map is `ConcurrentHashMap`; SlidingWindowLog evicts with `<=` (exact retry); MD rewritten in the new Amazon format |
| **splitwise** | ✅ done | Simplified `PercentSplitStrategy` (loop not stream); added worked-example comments to `ExpenseManager`; **converted cents → rupees** across all files + MD; patterns + "what's expected" sections |
| **vendingmachine** | ✅ done | Simplified code, class-per-state kept; **coins → Indian ₹ denominations (1/2/5/10/20)**; `getCents`→`getValue`; MD trimmed 657→~442 lines; added comments/examples |
| **parkinglot** | ✅ done | MD aligned to actual code (was referencing a non-existent `Clock`); trimmed 737→~570; patterns + "what's expected" sections |
| **movieticket** | ✅ done | Big evolution: `Reservation`→`Booking` rename; added `City`+`Screen` (full BookMyShow hierarchy `City→Theater→Screen→Showtime`); flat numbered seats `"1".."100"`; removed custom `SeatUnavailableException` (uses `IllegalStateException`); removed `Clock` (uses `LocalDateTime`); **fixed real concurrency bug** (`isAvailable`/`getAvailableSeats` now `synchronized` like `book`); flattened LoD search to a direct loop; wired up `getAvailableSeats(showtimeId)`; cancellation deferred to Step-5 |
| **notification** | ✅ done | Records→classes; dropped `Clock`→`Instant.now()`; `catch(Throwable)`→`catch(Exception)`; MD standardized (725→~593) + added `Part 0` layman section |
| **lrucache** | ✅ done | Javadoc→`//`; MD standardized (canonical-8/SOLID/soundbites removed, clarifying dialogue + "what's expected" added) |
| **logger** | ✅ done | Javadoc→`//`; `catch(Throwable)`→`catch(Exception)`; removed redundant `Collections.synchronizedList(new CopyOnWriteArrayList<>())`; MD standardized + added `Part 0` layman section |
| **filesystem** | ✅ done (this was the last change) | **Trimmed to a true 45-min base** — see §4 |

**`git status` at handoff:** branch `main`. Only `filesystem/` source changes are uncommitted-and-tracked
(FileSystem.java, FileSystemDriver.java, INTERVIEW_WALKTHROUGH.md modified; 4 exception classes deleted).
`target/` compiled artifacts are dirty (ignore them — build output). **Nothing has been committed this
session** — the owner reviews diffs in Cursor and commits manually (see §9). Earlier problems' edits may
already be committed from prior sessions.

---

## 4. filesystem — the most recent change (full detail, since it's freshest / uncommitted)

**What changed:** the File System problem was over-scoped and over-engineered for 45 min. Trimmed to base:

- **Deleted 4 of 5 exception classes** (`AlreadyExistsException`, `InvalidPathException`,
  `NotADirectoryException`, `NotFoundException`). Kept only `FileSystemException` — all throws now use it
  with a descriptive message. (Rationale: a 5-class exception tree is boilerplate with no design payoff.)
- **Removed `move()` and `rename()` from the base `FileSystem.java`.** They now live ONLY in the MD's
  Step-5 section (5.1 rename, 5.2 move) as code sketches you'd "talk through, code if time allows." The
  move-into-descendant **cycle check** and rename **map-key dance** are the highest-value teaching content
  and were preserved — just relocated out of the base.
- **Base public API is now 5 methods:** `createFile`, `createFolder`, `delete`, `list`, `get` + 3 private
  path helpers (`resolvePath`, `resolveParent`, `extractName`).
- Driver rewritten to exercise only base ops; dry-run in MD swapped move/rename traces for list/delete.
- MD fully reconciled (requirements, entities, API table, class-card diagram, 30-sec summary, top-mistakes,
  and the file-listing table — which had a `sed` artifact of 5 identical rows, now fixed).

**Composite design itself was NOT changed** — `FileSystemEntry` (abstract) / `File` (leaf) / `Folder`
(composite) with parent-pointer-based `getPath()` is the senior signal and stayed intact.

---

## 5. APIs and data structures (quick reference for the improved problems)

- **ratelimiter**: `RateLimiter.allow(clientId, endpoint) → RateLimitResult`; `Limiter` strategy interface;
  `TokenBucketLimiter`/`SlidingWindowLogLimiter`; `Clock`-injected; per-key lock via
  `ConcurrentHashMap.computeIfAbsent` + `synchronized(bucket)`.
- **splitwise**: `ExpenseManager` facade — `addExpense`, `getBalance`, `getNetBalance`, `simplifyDebts`;
  `SplitStrategy` (Equal/Exact/Percent); balance graph `Map<String,Map<String,Long>>` (never both
  directions positive); greedy two-heap simplification. Amounts in **rupees (long)**; PERCENT uses basis
  points (10000 = 100%).
- **vendingmachine**: GoF **State** pattern — `VendingMachineState` + NoCoin/HasCoin/Dispensing; `Coin`
  enum in ₹; state objects are flyweights built once in the ctor.
- **parkinglot**: `ParkingLot.enter(type)→Ticket`, `exit(ticketId)→long`; occupancy is **relational**
  (`Set<String> occupiedSpotIds` on the lot, not on the spot); `LocalDateTime.now()` inline (no Clock).
- **movieticket**: `BookingSystem` facade — `searchMovies(cityId, title)`, `getShowtimesAtTheater`,
  `getAvailableSeats(showtimeId)`, `book(showtimeId, seatIds)→Booking`. Hierarchy `City→Theater→Screen→
  Showtime`. `Showtime.bookings` is the SINGLE source of truth; `book`/`isAvailable`/`getAvailableSeats`
  all `synchronized`. Seats are strings `"1".."100"`. Cancellation = Step-5.
- **notification**: `NotificationService.send(notification)→List<DeliveryResult>`; `NotificationSender`
  strategy per channel routed via `EnumMap`; failure isolation catches per-channel `Exception`.
- **lrucache**: `Cache<K,V>` interface; `LRUCache` = `HashMap<K,Node>` + doubly-linked list with sentinel
  head/tail (all ops O(1)); `LinkedHashMapLRUCache` as the "production" alt. `synchronized` methods.
- **logger**: `Logger.log(level,msg)` fans one `LogRecord` to N `Destination`s; each Destination =
  threshold + `Formatter` (Strategy) + `Sink` (Strategy) + per-destination lock; format outside the lock,
  write under it; failure isolated per destination.
- **filesystem**: see §4.

---

## 6. Important design decisions & why (don't relitigate these)

1. **Records → normal classes** everywhere — owner's call for whiteboard fluency. (Notification was
   converted; don't flip back.)
2. **Rupees, not cents** — India context. Splitwise/Vending Machine/Parking Lot done; some older files may
   still say cents.
3. **`catch (Exception)` not `Throwable`** in isolation boundaries — never swallow JVM Errors.
4. **Movie Ticket `Booking` (not `Reservation`)** — the verb is `book()`, so the noun should be `Booking`.
   Seat kept as a **string**, deliberately: if `Seat` owned `SeatStatus`, you'd have two sources of truth
   vs the `bookings` list. `bookedSeats` set optimization was discussed and REJECTED for the base (adding a
   second source of truth; O(R) scan is fine since R ≤ 100).
5. **Movie Ticket LoD search flattened** — base uses a direct nested loop; the 5-method LoD cascade is a
   Step-5 talking point only. (Owner chose the flatter base explicitly.)
6. **File System `move`/`rename` are Step-5, not base** — and one `FileSystemException`, not five. (§4)
7. **Deliberate scope discipline everywhere:** each problem's base = "what you finish in 45 min"; the
   impressive-but-time-expensive parts (async, cancellation, per-seat locking, move cycle-check, Factory,
   Observer) are Step-5 "talk through" material.

---

## 7. TODOs / remaining work

There is **no broken or half-finished work**. Everything compiles and runs. Optional future work the owner
may pick up:

- **`Part 0` layman intro** exists only for **Logger** and **Notification**. The owner liked it a lot.
  Consider adding the same "read this if you're rusty" from-scratch section to other problems (Movie Ticket,
  Splitwise, LRU Cache are good candidates) **only if asked**.
- **Un-standardized MDs**: problems NOT touched this session (amazonlocker, cabbooking, chess, connectfour,
  elevator, inventory, jobscheduler, meetingscheduler, paymentgateway, snakeladder, tictactoe, urlshortener)
  likely still use the OLD MD structure (canonical-8 preamble, SOLID tables, closing soundbites, no
  clarifying dialogue). Apply §2 standardization if the owner asks.
- **Elevator**: prior session did substantial work (SCAN two-TreeSet, `moveToNextStop`/`advance` renames,
  thread-safety section) — verify it's consistent if revisited.
- **`.gitignore` for `target/`**: compiled `.class` artifacts show up dirty in `git status`. Consider adding
  `target/` to `.gitignore` if not already (owner may already handle this).

---

## 8. Known bugs / issues

- **None known in the improved problems.** The two real bugs found this session were both fixed:
  - Movie Ticket: reader methods weren't `synchronized` against the `synchronized book()` writer
    (ConcurrentModificationException risk) — **fixed**.
  - Notification & Logger: `catch (Throwable)` swallowing JVM Errors — **fixed to `catch (Exception)`**.
- Not verified this session: the ~12 un-touched problems (see §7) may have stale MDs or minor issues.

---

## 9. Commands to run

**Build (whole project):**
```bash
cd "/Users/navam.chaurasia/Desktop/Navam/LLD/LLD_Interview_Questions/lld-lowleveldesign"
mvn -q clean compile        # confirmed EXIT 0 at handoff
```

**Run any problem's driver** (replace the class):
```bash
mvn -q exec:java -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.<problem>.<Driver>
```
Examples:
```bash
# filesystem
mvn -q exec:java -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.filesystem.FileSystemDriver
# movieticket (prints a 50-thread race: successes=1, conflicts=49)
mvn -q exec:java -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.movieticket.BookingSystemDriver
# logger (prints a 50-thread atomicity check: 1000/1000 well-formed)
mvn -q exec:java -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.logger.LoggerDriver
# splitwise, notification, lrucache, ratelimiter, vendingmachine, parkinglot follow the same pattern
```

**Interactive-flag git operations are unsupported in this env.** Use `gh` for GitHub. Do NOT commit unless
asked — the owner reviews diffs in Cursor and commits manually each time.

---

## 10. Build / test status

- **Build:** `mvn -q clean compile` → **EXIT 0** (whole project compiles).
- **Tests:** there is no JUnit suite; each `*Driver.java main()` is the executable test and prints
  expected-vs-actual lines. All improved-problem drivers were run this session and pass:
  - filesystem — all scenarios pass
  - movieticket — 50-thread race: `successes=1, conflicts=49, unexpected=0`
  - logger — 50-thread atomicity: `1000/1000 well-formed`
  - notification — 5 scenarios incl. 150/150 concurrent fan-out
  - splitwise — EQUAL/EXACT/PERCENT + chain-collapse + circular-cancels + validation all pass
  - lrucache, ratelimiter, vendingmachine, parkinglot — all drivers pass

---

## 11. Important context that should NOT be lost

- **This is interview-prep, not production.** Optimize every change for "what a strong SDE-2 writes/says in
  45 minutes," not for production completeness. The reflex "add more" is usually WRONG here — scope
  discipline is the point.
- **The owner is preparing for India SDE-2 rounds** (Amazon/Adobe/Microsoft). Money is in ₹. The "senior
  signal" framing (Junior/Mid/Senior sections) matters to them.
- **Memory files exist** at
  `~/.claude/projects/-Users-navam-chaurasia-Desktop-Sigly-server-compare-server-old-sigly-server/memory/`
  and are loaded each session. Notably: "no commits without explicit ask" (owner reviews in Cursor first),
  and project-location pointers. Respect these.
- **Two recurring owner requests** you'll likely see again: (a) "simplify for a 45-min round" = trim scope +
  reduce comment verbosity + move impressive-but-slow parts to Step-5; (b) "explain like a layman / add to
  MD for future revision" = write a from-scratch `Part 0` intuition section.
- **When simplifying, prefer surgical edits** and always re-run the driver + `mvn -q compile` after. The MDs
  are large (600–900 lines) and tightly cross-referenced — after removing a method/field/exception, grep the
  MD for every mention and reconcile the requirements block, API tables, diagrams, dry-run, 30-sec summary,
  top-mistakes, and the files-in-folder table. (The filesystem `sed` left a 5-identical-row artifact that had
  to be hand-fixed — watch for that.)
- **`00_PREP_STRATEGY.md`** is the source of truth for which problems matter most (Tier A: Parking Lot,
  Splitwise, Movie Ticket, LRU Cache, Vending Machine; Tier B: Cab Booking, Rate Limiter, Snake & Ladder).
```
