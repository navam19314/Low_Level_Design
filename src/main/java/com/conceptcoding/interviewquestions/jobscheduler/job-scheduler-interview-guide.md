# Job Scheduler LLD — Interview Guide (HelloInterview Framework)

Total time: ~40 minutes across 5 phases.

---

## Phase 1 — Requirements (5 min)

Say this out loud before writing anything.

**Questions to ask:**
- How many jobs and threads?
- What attributes does a job have? (name, duration, priority, deadline, user type?)
- Which scheduling algorithms are needed?
- Can the algorithm be switched at runtime?
- What happens when deadline is already passed?
- Out of scope: preemption, job dependencies, retries, persistence?

**Agree on this scope:**
- Schedule M jobs across N threads on a single machine
- Job attributes: name, duration, priority (P0 > P1 > P2), deadline, userType (ROOT > ADMIN > USER), arrivalOrder
- 4 algorithms: FCFS, SJF, FPS, EDF
- Round-robin assignment across threads
- EDF filters jobs whose deadline has already passed
- Out of scope: preemption, job queuing at runtime, retries

---

## Phase 2 — Core Entities (5 min)

Draw these boxes before writing code.

```
                    ┌──────────────────────────────────────┐
                    │             Scheduler                 │
                    │                                       │
                    │  - jobs: List<Job>                    │
                    │  - strategy: SchedulingStrategy       │
                    │                                       │
                    │  + addJob(Job)                        │
                    │  + setStrategy(SchedulingStrategy)    │
                    │  + getSchedulingSequence(nThreads)    │
                    │      → List<List<Job>>                │
                    └──────────────┬────────────────────────┘
                                   │ delegates ordering to
                                   ▼
                    ┌──────────────────────────────────────┐
                    │      <<interface>>                    │
                    │      SchedulingStrategy               │
                    │                                       │
                    │  + schedule(List<Job>) → List<Job>    │
                    └──────┬───────────────────────────────┘
                           │ implemented by
          ┌────────────────┼────────────────┐──────────────┐
          ▼                ▼                ▼              ▼
  ┌────────────┐  ┌────────────┐  ┌──────────────┐  ┌───────────┐
  │    FCFS    │  │    SJF     │  │     FPS      │  │    EDF    │
  │ Scheduler  │  │ Scheduler  │  │  Scheduler   │  │ Scheduler │
  │            │  │            │  │              │  │           │
  │ sort by    │  │ sort by    │  │ sort by      │  │ filter    │
  │ arrival    │  │ duration,  │  │ priority,    │  │ expired,  │
  │ order      │  │ tie→pri    │  │ tie→userType │  │ sort by   │
  │            │  │            │  │ tie→duration │  │ deadline  │
  └────────────┘  └────────────┘  └──────────────┘  └───────────┘


                    ┌──────────────────────────────────────┐
                    │                Job                    │
                    │  (pure data — no logic)               │
                    │                                       │
                    │  - name: String                       │
                    │  - duration: int                      │
                    │  - priority: Priority                 │
                    │  - deadline: int                      │
                    │  - userType: UserType                 │
                    │  - arrivalOrder: int                  │
                    └──────────────────────────────────────┘

  ┌──────────────────────┐    ┌──────────────────────┐
  │  Priority (enum)     │    │  UserType (enum)      │
  │  P0, P1, P2          │    │  ROOT, ADMIN, USER    │
  │  (lower = higher     │    │  (lower = higher      │
  │   priority)          │    │   privilege)          │
  └──────────────────────┘    └──────────────────────┘

  SCHEDULING FLOW:
  ────────────────
  scheduler.getSchedulingSequence(2 threads)
       │
       ▼
  strategy.schedule(jobs)  →  returns ordered List<Job>
       │
       ▼
  round-robin assign to threads:
    job[0] → Thread 1
    job[1] → Thread 2
    job[2] → Thread 1
    job[3] → Thread 2
    ...
```

| Class | One-line job |
|---|---|
| `Scheduler` | Holds jobs. Delegates ordering to strategy. Distributes via round-robin. |
| `Job` | Pure data object. No logic. Just fields + getters. |
| `SchedulingStrategy` | Interface. One method: `schedule(jobs)` returns ordered list. |
| `FCFSScheduler` | Sort by arrivalOrder. |
| `SJFScheduler` | Sort by duration, tie-break by priority. |
| `FPSScheduler` | Sort by priority, tie-break by userType, then duration. |
| `EDFScheduler` | Filter expired deadlines, sort remaining by deadline. |

---

## Phase 3 — Class Design (10 min)

Write skeletons first, fill bodies after.

### `Priority.java` + `UserType.java`
```java
enum Priority  { P0, P1, P2 }        // P0 = highest (ordinal 0)
enum UserType  { ROOT, ADMIN, USER }  // ROOT = highest (ordinal 0)
```

### `Job.java`
```java
class Job {
    private final String name;
    private final int duration;
    private final Priority priority;
    private final int deadline;
    private final UserType userType;
    private final int arrivalOrder;

    // constructor + getters + toString()
}
```

### `SchedulingStrategy.java`
```java
interface SchedulingStrategy {
    List<Job> schedule(List<Job> jobs);
}
```

### `Scheduler.java`
```java
class Scheduler {
    private final List<Job> jobs = new ArrayList<>();
    private SchedulingStrategy strategy;

    // addJob(Job)
    // setStrategy(SchedulingStrategy)      ← swap algorithm at runtime
    // getSchedulingSequence(int nThreads)  ← returns List<List<Job>>
}
```

---

## Phase 4 — Implementation (10 min)

### Write `Scheduler.getSchedulingSequence()` first

```java
public List<List<Job>> getSchedulingSequence(int numberOfThreads) {
    List<Job> ordered = strategy.schedule(new ArrayList<>(jobs));  // copy — don't mutate original

    List<List<Job>> threadQueues = new ArrayList<>();
    for (int i = 0; i < numberOfThreads; i++) {
        threadQueues.add(new ArrayList<>());
    }

    for (int i = 0; i < ordered.size(); i++) {
        threadQueues.get(i % numberOfThreads).add(ordered.get(i));  // round-robin
    }

    return threadQueues;
}
```

**Important:** Pass `new ArrayList<>(jobs)` to strategy — don't let the strategy mutate the original list, otherwise switching strategies later gives wrong results.

### Write the 4 strategies

**FCFS — sort by arrival order**
```java
jobs.sort(Comparator.comparingInt(Job::getArrivalOrder));
return jobs;
```

**SJF — shortest job first, tie-break by priority**
```java
jobs.sort(Comparator.comparingInt(Job::getDuration)
        .thenComparingInt(j -> j.getPriority().ordinal()));
return jobs;
```

**FPS — priority first, then userType, then duration**
```java
jobs.sort(Comparator.comparingInt((Job j) -> j.getPriority().ordinal())
        .thenComparingInt(j -> j.getUserType().ordinal())
        .thenComparingInt(Job::getDuration));
return jobs;
```

**EDF — filter expired, then sort by deadline**
```java
List<Job> valid = new ArrayList<>();
for (Job job : jobs) {
    if (job.getDeadline() > currentTime) valid.add(job);
}
valid.sort(Comparator.comparingInt(Job::getDeadline));
return valid;
```

### Key insight about enum ordinals
`Priority.P0.ordinal() = 0`, `P1 = 1`, `P2 = 2`.
Sorting by ordinal ascending puts P0 first — which is correct since P0 is highest priority.
Same logic applies to `UserType`.

---

## Phase 5 — Extensibility (5 min, discuss only)

| "What if..." | Answer |
|---|---|
| New scheduling algorithm | Implement `SchedulingStrategy` — zero changes to `Scheduler` (OCP) |
| Job priority changes at runtime | Add `setPriority()` to `Job`; re-run `getSchedulingSequence()` |
| Weighted round-robin (not equal distribution) | Replace round-robin loop with weighted assignment in `Scheduler` |
| Job cancellation | Add `removeJob(String name)` to `Scheduler` |
| Preemption (interrupt running job for higher priority) | Out of scope — needs actual thread management, not just ordering |
| Job dependencies (B runs after A) | Add `List<Job> dependencies` to `Job`; topological sort before scheduling |

---

## Time Budget

| Phase | Time |
|---|---|
| Requirements — ask 3-4 questions | 5 min |
| Core Entities — draw diagram | 5 min |
| Class Design — skeletons | 8 min |
| `getSchedulingSequence` + 4 strategies | 12 min |
| Extensibility discussion | 5 min |
| Demo / walkthrough | 5 min |
| **Total** | **~40 min** |

---

## What Interviewers Are Checking

| Checkpoint | What they want to see |
|---|---|
| Strategy pattern | `SchedulingStrategy` interface; swap algorithms without touching `Scheduler` |
| Job as pure data | No scheduling logic inside `Job` — just fields and getters |
| Correct comparator chaining | Know how to chain `.thenComparingInt()` for tie-breaking |
| Defensive copy in `schedule()` | Pass `new ArrayList<>(jobs)` not the original list |
| Round-robin understood | `i % numberOfThreads` — simple and correct |
| EDF edge case | Filter jobs where `deadline <= currentTime` before sorting |

---

## Design Patterns Used

| Pattern | Where |
|---|---|
| **Strategy** | `SchedulingStrategy` interface — swap FCFS/SJF/FPS/EDF at runtime via `setStrategy()` |
| **Facade** | `Scheduler` hides all ordering + distribution complexity behind `getSchedulingSequence()` |

---

## Common Mistakes to Avoid

- Putting scheduling logic inside `Job` — it's a data class, keep it pure
- Mutating the original `jobs` list inside strategy — always sort a copy
- Forgetting to handle EDF deadline filter — just sorting by deadline is wrong
- Using `priority == P0` instead of `ordinal()` for comparator — won't chain cleanly
- Not passing `currentTime` to `EDFScheduler` — it needs to know what "expired" means
