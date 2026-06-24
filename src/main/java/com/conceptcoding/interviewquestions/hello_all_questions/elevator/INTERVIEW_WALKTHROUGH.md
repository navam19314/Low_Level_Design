# Elevator — 45-min LLD Interview Walkthrough

**Target:** SDE-2 at Amazon, Adobe, Microsoft, Flipkart, etc.

> The elevator problem has three things an interviewer is looking for: a clean **state machine**, a correct **SCAN algorithm** (serve floors in direction order, flip when done), and a **smarter-than-nearest dispatch**. Nail these three and the rest follows.

---

## Time Budget

| Step | What you're doing                                      | Time  |
|------|--------------------------------------------------------|-------|
| 1    | Requirements                                           | 5 min |
| 2    | Entities + relationships                               | 4 min |
| 3    | Class design (state + methods)                         | 10 min|
| 4    | Code: SCAN step() + dispatch + dry-run                 | 18 min|
| 5    | Extensibility (follow-up questions)                    | 7 min |

Check the clock at **minute 5** (requirements done), **minute 19** (start coding), **minute 37** (extensibility).

---

## Mental Models — draw these before you write a line of code

### M1. Elevator Direction (State Machine)

An elevator has 3 states: **IDLE**, **UP**, **DOWN**.

```
  Have stops above?          Have stops above?
  ┌──────────────────────────────────────────┐
  │                                          │
IDLE ──(stops above)──► UP ──(upQueue done)──► DOWN
  │                                          │
  └──────────────────────────────────────────┘
  ▲                                          │
  └──────────── both queues empty ───────────┘
```

Plain English:
- Start IDLE. Get a stop above → go UP.
- Serve all UP stops in order (lowest first). Run out → flip to DOWN.
- Serve all DOWN stops in order (highest first). Run out → go IDLE.

### M2. Why Two TreeSets?

Think of it like two to-do lists the elevator carries:

```
upQueue   [3, 5, 8]   ← floors above, sorted low→high  (natural TreeSet order)
downQueue [7, 4, 2]   ← floors below, sorted high→low  (reverse TreeSet order)
```

- `addStop(floor)`:  floor above current → put in upQueue. Floor below → downQueue.
- Going UP: take from upQueue front (lowest). Run out? Flip DOWN, take downQueue front (highest).
- Going DOWN: take from downQueue front (highest). Run out? Flip UP, take upQueue front (lowest).
- `pollFirst()` always gives the next correct floor — no sorting needed, no index tracking.

**step() returns the floor it stopped at, or -1 if idle.**

### M3. Dispatch — Pick the Right Elevator

Naive "nearest elevator" is wrong. An elevator at floor 8 heading UP is "nearest" to a DOWN call at floor 6 — but it already passed floor 6. It will go up first, then come back. Wrong choice.

Use 3 tiers, in order:

```
Hall call: floor 6, direction UP

Tier 1 — moving the same way, not past the floor yet   ← best, no detour needed
          (elevator going UP at floor 3 → qualifies)
          (elevator going UP at floor 8 → already past floor 6, skip)

Tier 2 — nearest IDLE elevator                         ← second choice

Tier 3 — nearest elevator overall                      ← fallback (everyone is busy)
```

Pick the first tier that has a match.

---

## Step 1 — Requirements (~5 min)

**Say aloud:**
> "Let me clarify scope before designing — elevator scheduling is sensitive to a few specifics."

**Four things to confirm:**

| Theme | Question to ask |
|-------|----------------|
| Basic scope | "How many elevators and floors? Hall calls (UP/DOWN outside) + destination buttons (inside)?" |
| Simulation | "Does time advance via `step()` ticks, not real threads?" |
| Edge cases | "Same-floor request = no-op success? Duplicate stop = ignore?" |
| Out of scope | "No capacity, no door timing, no emergency stop — confirm?" |

**Write on the board:**
```
Functional Requirements:
1. N elevators, M floors.
2. Hall call: callElevator(floor, UP/DOWN) — pressed outside elevator.
3. Destination: selectFloor(elevatorId, floor) — pressed inside elevator.
4. Simulation: controller.advance() advances all elevators one stop.
5. SCAN: serve floors in direction order; reverse at boundary.

Out of Scope: capacity, door timing, emergency stop, UI, persistence.
```

---

## Step 2 — Entities (~4 min)

**Three classes + one interface:**

```
ElevatorController   orchestrator + facade
Elevator             one cab — owns floor, direction, SCAN queues
Direction            enum: UP / DOWN / IDLE
DispatchStrategy     interface (Strategy pattern) — pluggable hall-call dispatch
```

**Why no `Request` class or `RequestType` enum?**
> Hall calls need a direction (UP/DOWN). In-cab selections don't — a floor number is enough. The two operations have different APIs: `callElevator(floor, direction)` vs `selectFloor(elevatorId, floor)`. No shared "Request" type needed; we'd just be wrapping an int.

**Why no `Floor`, `Button`, `Passenger`?**
> They have no managed state. A button press is an event — model the event as a method call, not the button.

**Relationships:**
```
Controller --owns-->   List<Elevator>
Controller --has-a-->  DispatchStrategy   (injected — Strategy pattern)
Elevator   --has-->    upQueue, downQueue (TreeSet<Integer>)
```

---

## Step 3 — Class Design (~10 min)

### Elevator

```java
public class Elevator {
    private final int id;
    private int currentFloor;
    private Direction direction;
    private final TreeSet<Integer> upQueue;    // ascending — natural order
    private final TreeSet<Integer> downQueue;  // descending — reverse order

    public void addStop(int floor)  // floor > current → upQueue; floor < current → downQueue
    public int  step()              // one SCAN tick; returns floor stopped at, or -1 if idle
    // getters: getId(), getCurrentFloor(), getDirection(), getPendingCount()
}
```

### ElevatorController

```java
public class ElevatorController {
    private final List<Elevator>   elevators;
    private final DispatchStrategy dispatchStrategy;

    public void callElevator(int floor, Direction direction)  // hall call
    public void selectFloor(int elevatorId, int floor)         // in-cab button
    public void step()                                         // advance all elevators one tick
}
```

### DispatchStrategy

```java
public interface DispatchStrategy {
    Elevator select(List<Elevator> elevators, int floor, Direction direction);
}

// Default: NearestDispatchStrategy — 3-tier priority (see M3)
```

**Patterns to name here:**
- **Strategy** — `DispatchStrategy` is genuinely pluggable; different algorithms can be swapped without touching Controller or Elevator. Name it in Step 2 when you introduce the interface.
- **State Machine** — Elevator IS a state machine (IDLE/UP/DOWN). Draw M1.
- **Facade** — `ElevatorController` hides the fleet + dispatch behind two clean methods.

---

## Step 4 — Implementation (~18 min)

### 4.1 Elevator.addStop()

```java
public void addStop(int floor) {
    if      (floor > currentFloor) upQueue.add(floor);
    else if (floor < currentFloor) downQueue.add(floor);
    // same floor: no-op — elevator is already here
}
```

### 4.2 Elevator.step() — the SCAN algorithm

```java
public int step() {
    // Wake up: pick direction if idle
    if (direction == Direction.IDLE) {
        if      (!upQueue.isEmpty())   direction = Direction.UP;
        else if (!downQueue.isEmpty()) direction = Direction.DOWN;
        else return -1;
    }

    if (direction == Direction.UP) {
        if (!upQueue.isEmpty()) {
            currentFloor = upQueue.pollFirst();          // lowest floor above
        } else if (!downQueue.isEmpty()) {
            direction    = Direction.DOWN;               // reverse at top
            currentFloor = downQueue.pollFirst();
        } else {
            direction = Direction.IDLE;
            return -1;
        }
    } else {  // DOWN
        if (!downQueue.isEmpty()) {
            currentFloor = downQueue.pollFirst();        // highest floor below
        } else if (!upQueue.isEmpty()) {
            direction    = Direction.UP;                 // reverse at bottom
            currentFloor = upQueue.pollFirst();
        } else {
            direction = Direction.IDLE;
            return -1;
        }
    }
    return currentFloor;
}
```

**Three things to say while coding this:**
1. *"The IDLE check at the top wakes the elevator without moving. The actual move is always `pollFirst()`."*
2. *"When upQueue runs out, we flip DOWN right there and take the next downQueue floor in the same tick — that's the SCAN reversal."*
3. *"step() returns the floor it stopped at, or -1. The Controller can print/log each stop."*

### 4.3 ElevatorController.callElevator() + dispatch

```java
public void callElevator(int floor, Direction direction) {
    Elevator best = dispatchStrategy.select(elevators, floor, direction);
    if (best != null) best.addStop(floor);
}
```

Dispatch strategy — 3 tiers in order:

```java
// Tier 1: moving in the right direction and hasn't passed the floor
for (Elevator e : elevators) {
    if (e.getDirection() != direction) continue;
    if (direction == UP   && e.getCurrentFloor() > floor) continue;  // already past
    if (direction == DOWN && e.getCurrentFloor() < floor) continue;
    // ... track nearest
}

// Tier 2: nearest idle
// Tier 3: nearest overall (fallback)
```

### 4.4 Dry-Run (do this at the board)

```
Setup: one elevator, floor=0, IDLE.
addStop(3), addStop(5), addStop(8) → all go to upQueue = {3, 5, 8}

step() tick 1: IDLE → direction = UP; pollFirst = 3.  Returns 3.  [UP]
step() tick 2: pollFirst = 5.  Returns 5.  [UP]
step() tick 3: pollFirst = 8.  Returns 8.  [UP]
step() tick 4: upQueue empty, downQueue empty → IDLE, return -1.

Now addStop(2) when at floor 8: 2 < 8 → downQueue = {2}
step() tick 5: direction = DOWN; pollFirst = 2.  Returns 2.  [DOWN]
```

---

## Step 5 — Extensibility (~7 min)

| Follow-up | Answer |
|-----------|--------|
| "Add least-busy dispatch" | `LeastBusyDispatchStrategy implements DispatchStrategy` — picks elevator with fewest pending stops. Constructor swap, zero changes to Controller or Elevator. |
| "Express elevators / restricted floors" | Add `Set<Integer> serviceable` to Elevator. `addStop` rejects floors not in set. Dispatch filters by serviceable before the 3 tiers. |
| "Passenger capacity" | Add `currentLoad` + `maxCapacity` to Elevator. `addStop` rejects when full. Update load at each stop. |
| "Analytics / floor dashboard" | Observer — Controller publishes `ElevatorArrived` events. Analytics subscriber registers independently. |
| "Different building configs" | Builder — `BuildingBuilder` collects elevator count, floors, dispatch strategy, then `build()`. |
| "Make it thread-safe" | See below — this is a common deep-dive. |

### Thread Safety Deep-Dive

**The problem in our current design:**

Our `step()` loop is meant to be called by a single simulation thread. But in a real building, hall-call buttons (`callElevator`) and in-cab buttons (`selectFloor`) can fire from any thread at any time — even while `step()` is running.

The race condition:
```
Thread A (tick loop):    step() is inside Elevator.step(), reading upQueue
Thread B (hall button):  callElevator() calls addStop(), which writes to upQueue
                         → ConcurrentModificationException or corrupted TreeSet state
```

**Fix 1 — Synchronized methods on Controller (simplest, mention this first)**

```java
public synchronized void callElevator(int floor, Direction direction) { ... }
public synchronized void selectFloor(int elevatorId, int floor)        { ... }
public synchronized void step()                                         { ... }
```

One lock on the Controller serializes all access. Simple. Fine for a building with low call frequency.

**Fix 2 — Inbox queue per Elevator (better, mention if interviewer pushes)**

The idea: `callElevator` / `selectFloor` just drop a floor number into a thread-safe inbox. `step()` drains the inbox at the start of each tick into the TreeSets. The TreeSets are only ever touched by the step thread.

```java
public class Elevator {
    private final ConcurrentLinkedQueue<Integer> inbox = new ConcurrentLinkedQueue<>();
    private final TreeSet<Integer> upQueue   = new TreeSet<>();
    private final TreeSet<Integer> downQueue = new TreeSet<>(Comparator.reverseOrder());

    public void addStop(int floor) {
        inbox.offer(floor);   // any thread can call this safely
    }

    public int step() {
        // drain inbox first — single writer, no lock needed
        Integer f;
        while ((f = inbox.poll()) != null) {
            if      (f > currentFloor) upQueue.add(f);
            else if (f < currentFloor) downQueue.add(f);
        }
        // ... rest of SCAN logic unchanged ...
    }
}
```

Why this is better:
- `ConcurrentLinkedQueue` is lock-free for producers — button presses never block.
- TreeSets (the expensive sorted structures) are single-writer → no synchronization needed on them.
- Producer threads and the SCAN thread never contend on the same data structure.

**What to say in the interview:**

> *"Our current design is single-threaded discrete ticks — that's the correct starting point for a 45-min round. If the interviewer asks about threads, I'd say: the race is between addStop (button press threads) and step (tick thread) both touching the same TreeSets. Simplest fix: synchronize callElevator/selectFloor/step on the Controller. Better fix: per-elevator inbox — producers drop into a ConcurrentLinkedQueue, step drains it at tick start. TreeSets stay single-writer so no locks needed there. Same inbox-pattern as actor-model mailboxes."*

---

## Common Mistakes That Lose Points

- **Adding Floor, Button, Passenger classes** — no managed state, don't model them.
- **Using a Queue instead of sorted structure** — elevators don't service in arrival order, they service in position order.
- **Moving when reversing** — reversing direction takes a tick; floor doesn't change on that tick.
- **Naive nearest dispatch** — an elevator that's already passed the floor can't serve a direction-specific call without backtracking. The "hasn't passed yet" check is mandatory.
- **Skipping the dry-run** — SCAN is exactly the kind of algorithm where a trace catches an off-by-one.
- **Singleton on Controller** — kills tests, blocks multi-building extension.
- **Pattern-stuffing** — Strategy and Facade belong here; don't force Decorator/Observer into the base design.

---

## 30-Second Summary (memorize this)

> *"Three classes and one interface: ElevatorController, Elevator, Direction, and DispatchStrategy. Each Elevator is a state machine — IDLE/UP/DOWN — using two TreeSets: upQueue (ascending) for floors above, downQueue (descending) for floors below. step() runs SCAN: wake up if idle, pollFirst from the current-direction queue, flip direction when that queue empties. Controller's callElevator delegates to an injected DispatchStrategy using 3-tier priority: moving-toward-and-approaching first, then nearest idle, then nearest overall. selectFloor goes straight to the specific elevator. Dispatch is pluggable via Strategy — different policies slot in without touching Elevator or Controller."*

---

## Files in This Package

| File | Purpose |
|------|---------|
| `model/Direction.java` | Enum: UP / DOWN / IDLE |
| `Elevator.java` | State machine + TreeSet SCAN |
| `strategy/DispatchStrategy.java` | Strategy interface |
| `strategy/NearestDispatchStrategy.java` | 3-tier dispatch (default) |
| `ElevatorController.java` | Facade — dispatch + tick |
| `ElevatorDriver.java` | 3 scenarios: SCAN order, two-elevator dispatch, hall + in-cab mixed |

```bash
mvn -q compile exec:java \
  -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.elevator.ElevatorDriver
```
