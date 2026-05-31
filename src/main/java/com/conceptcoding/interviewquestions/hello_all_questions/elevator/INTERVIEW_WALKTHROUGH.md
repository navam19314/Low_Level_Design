# Elevator System — 45-min LLD Interview Walkthrough

**Target role:** SDE‑2 (Amazon, Adobe, Microsoft, Atlassian, etc.)
**Source method:** Hello Interview *Delivery Framework* applied to the *Elevator* problem breakdown.

> Elevator is the most algorithm-heavy of the common LLD problems. The interview rewards three things: a clean **state machine**, a correct **SCAN tick loop** (especially the "stop without moving" rule), and a **direction-aware dispatch** that beats naive nearest-elevator. Get these three right and you're senior.

---

## Time budget (45 min)

| Step | Activity                                                                  | Budget   | Cumulative |
| ---- | ------------------------------------------------------------------------- | -------- | ---------- |
| 1    | Requirements                                                              | ~5 min   | 5          |
| 2    | Entities & Relationships                                                  | ~4 min   | 9          |
| 3    | Class Design (state + behavior)                                           | ~10 min  | 19         |
| 4    | Implementation (`step` SCAN + dispatch + dry run)                         | ~18 min  | 37         |
| 5    | Extensibility (3 follow-ups)                                              | ~7 min   | 44         |
| —    | Wrap & questions                                                          | ~1 min   | 45         |

Step 4 is slightly longer here than for other problems because the SCAN algorithm has 5 explicit branches you need to talk through and trace.

Watch the clock at minute **5** (Step 1 done), minute **19** (start coding), minute **37** (extensibility).

---

## Mental models — internalize these BEFORE you walk in

These three diagrams are the **entire algorithmic content** of the problem. If you can draw them from memory, every line of code follows.

### M1. Elevator state machine

```
                         requests pending
                       (pick direction by nearest)
       +------------------------------------------------+
       |                                                |
       v                                                |
   +--------+    requests above & no req here    +------+------+
   |  IDLE  |  --------------------------------> |     UP      |
   |        |                                    |             |
   +--------+                                    +-------------+
       ^                                                |
       |  requests below & no req here                  | nothing above
       |  +---------------------------+                 | (reverse)
       |  |                           v                 v
       |  |                       +-------------+   (no move this tick)
       |  |                       |    DOWN     |
       |  |                       |             |
       |  |                       +-------------+
       |  |                            |
       +--+----------------------------+  nothing below (reverse)
                                          (no move this tick)
       ^
       |  requests empty after stop
       +-----------------------------  (any state -> IDLE)
```

**Key transitions to memorize:**
- IDLE + new request → pick direction by **nearest** request (tie-break: lower floor)
- UP / DOWN at boundary with no requests ahead → **reverse**, no movement this tick
- Any state → IDLE when the request set empties (typically right after a stop)

### M2. The SCAN tick — 5 branches in priority order

This is the heart of `Elevator.step()`. The order matters: each branch returns/skips the rest of the tick.

```
   step()
     |
     v
   1. requests empty ?
       YES -> direction = IDLE; return
       NO  v

   2. direction == IDLE ?
       YES -> pick direction by nearest request (don't move yet — fall through)
       NO  v

   3. Should I STOP at the current floor?
       (matching pickup-in-direction OR destination-here is in requests)
       YES -> remove those requests
              if requests now empty -> direction = IDLE
              return        <-- door cycle counts as this tick; no movement
       NO  v

   4. Anything ahead in current direction?
       NO  -> reverse direction; return        <-- no movement this tick
       YES v

   5. Move one floor in current direction.
```

**Why each branch matters:**
- **Branch 1** is the rest state. Without it, an idle elevator wobbles.
- **Branch 2** "wakes up" idle cars without committing to a move yet — bias toward the nearest call.
- **Branch 3** is the **"stop = no move" rule** — many candidates miss this and the elevator skips the floor.
- **Branch 4** prevents the elevator from running past the top/bottom of its work.
- **Branch 5** is the only branch that actually changes `currentFloor`.

### M3. Dispatch priority tiers (Controller.selectBestElevator)

Naive nearest-elevator is wrong. The right answer is direction-aware, three tiers:

```
   requestElevator(floor, PICKUP_UP_or_DOWN)
        |
        v
   +--------------------------------------------------------+
   | Tier 1: an elevator MOVING in the requested direction  |
   |         AND still APPROACHING this floor               |
   |         (not past it)                                  |
   +--------------------------------------------------------+
            |                       found -> assign
            v not found
   +--------------------------------------------------------+
   | Tier 2: the NEAREST IDLE elevator                      |
   +--------------------------------------------------------+
            |                       found -> assign
            v not found
   +--------------------------------------------------------+
   | Tier 3: the NEAREST elevator overall                   |
   |         (will service this after its current run)      |
   +--------------------------------------------------------+
```

**Why this beats naive nearest:**
> *"An UP elevator that just passed floor 5 going to floor 9 is 'nearest' to a new PICKUP_DOWN at floor 5, but it's the wrong choice — the passenger has to wait until it finishes up + comes back down. A different car that's IDLE at floor 2 will serve floor 5 going DOWN much faster, even though it's farther away."*

---

## STEP 1 — Requirements (~5 min)

### What to say out loud (opener)
> "Before I start designing, let me clarify scope and rules. Elevator scheduling is sensitive to a few specifics so I want to nail them down up front."

### Probe the 4 themes

| Theme               | Question to ask                                                                                              |
| ------------------- | ------------------------------------------------------------------------------------------------------------ |
| Primary capabilities| "3 elevators, 10 floors 0–9. Hall calls outside (UP / DOWN), destination buttons inside. Simulation advances by discrete ticks via `step()`?" |
| Rules / completion  | "Pickup needs a direction; destination doesn't. Same-floor request is a no-op success. Stopping at a floor takes one tick — elevator doesn't move that tick. Reversing also takes a tick. Correct?" |
| Error handling      | "Invalid floor → reject. DESTINATION via the hall-call API → reject. Duplicate request → ignore (Set dedupes). Confirm?" |
| Scope boundaries    | "No weight / passenger limit, no door open/close timing, no emergency stop, no dynamic reconfiguration, no UI — confirm?" |

### What to write on the board

```
Functional Requirements
1. 3 elevators serve floors 0..9.
2. Hall call: user requests an elevator from a floor + direction (UP or DOWN).
3. Destination: passenger inside an elevator picks a floor.
4. Simulation runs in discrete ticks: controller.step() advances time by 1.
5. Each elevator services pickup-in-direction + destination requests as it passes.
6. Invalid requests return false (out-of-bounds floor, DESTINATION via hall-call API).
7. Same-floor request: no-op success (already here).

Out of Scope
- Weight / passenger capacity
- Door open/close mechanics + timing
- Emergency stop, fire mode, maintenance lockout
- Dynamic floor count / elevator count
- UI / rendering
- Persistence
- Multi-building coordination
```

### Close the step
> "Does this match what you had in mind? Anything you'd add before I move to entities?"

---

## STEP 2 — Entities & Relationships (~4 min)

### What to say out loud
> "Three classes plus one Strategy interface: **ElevatorController** (orchestrator + facade), **Elevator** (one cab — owns its floor, direction, pending requests, and its own SCAN tick), **Request** (a stop — floor + type), and **DispatchStrategy** (which elevator services a hall call — naive nearest? direction-aware? least-busy?). Dispatch is genuinely pluggable, so it goes in the base as a Strategy — not deferred to extensibility."

### Why no `Passenger`, `Building`, `HallButton`, `CabPanel` class
> "These are actors and physical interfaces, not entities with state we manage. The hall button just makes a `requestElevator(floor, PICKUP_UP)` call; the cab panel calls `elevator.addRequest(new Request(floor, DESTINATION))`. We don't model the button — we model the *event* it generates. That's what `Request` is."

### What to write on the board

```
Entities
- ElevatorController   (orchestrator + facade: requestElevator, step)
- Elevator             (one cab: currentFloor, direction, requests, SCAN step)
- Request              (a stop: floor + RequestType)
- DispatchStrategy     (interface — Strategy pattern; picks which elevator services a hall call)

Enums
- Direction    { UP, DOWN, IDLE }
- RequestType  { PICKUP_UP, PICKUP_DOWN, DESTINATION }

Relationships
- Controller owns      List<Elevator>
- Controller has-a     DispatchStrategy           (injected — pluggable algorithm)
- Elevator   owns      Set<Request>
- Request    equals/hashCode by (floor, type) so Set dedupes
```

> **Why introduce Strategy already in Step 2?** Different elevator-dispatch algorithms (naive-nearest, direction-aware, least-busy, energy-optimal) are a textbook Strategy seam — the old "concept coding" YouTube version of this problem builds them upfront, and that's the right call. Naming Strategy here (instead of deferring to Step 5) is the senior SDE‑2 signal — especially in India-based interviews where patterns are expected to be named when applied.

### Diagram — boxes and arrows

```
   +----------------------+                  +-------------------------------+
   | DispatchStrategy     |<-------has-a---- |      ElevatorController       |   <- orchestrator + facade
   |   (interface)        |                  |  requestElevator / step()     |
   +----------------------+                  +-------------------------------+
        ^         ^                                       |
        |         |                                  owns | (List<Elevator>)
        |         |                                       v
   +-----------+ +----------+         +--------------+   +--------------+   +--------------+
   |Direction- | |Nearest   |         |  Elevator E1 |   |  Elevator E2 |   |  Elevator E3 |
   |Aware      | |Dispatch  |         |  - floor     |   |     ...      |   |     ...      |
   +-----------+ +----------+         |  - direction |   |              |   |              |
                                      |  - requests  |   +--------------+   +--------------+
                                      +--------------+
                                            |
                                            | element (Set<Request>)
                                            v
                                      +--------------+
                                      |  Request     |
                                      |  floor, type |
                                      +--------------+
```

> **Why `Set<Request>` and not `Queue<Request>`?** Because elevators *don't service requests in arrival order* — they service them in *position order* along their current sweep. A Set lets us check "is there a stop at the current floor?" cheaply, and equals/hashCode by (floor, type) auto-dedupes spam. Order is computed at tick time from `currentFloor` + `direction`, not stored.

---

## STEP 3 — Class Design (~10 min)

### Work top-down: Controller → Elevator → Request.

### ElevatorController — state ↔ requirement table

| Requirement                                       | State Controller must own                                    |
| ------------------------------------------------- | ------------------------------------------------------------ |
| 3 elevators                                       | `List<Elevator> elevators`                                   |
| Validate floor range                              | `int minFloor`, `int maxFloor`                               |
| WHICH elevator services a hall call               | `DispatchStrategy dispatchStrategy` (Strategy pattern)       |

### ElevatorController — behavior table

| Need from requirements              | Method                                     |
| ----------------------------------- | ------------------------------------------ |
| Hall call from a floor              | `boolean requestElevator(floor, type)` — delegates to strategy |
| Advance the simulation by one tick  | `void step()`                              |

### Elevator — state ↔ requirement table

| Requirement                                       | State Elevator must own              |
| ------------------------------------------------- | ------------------------------------ |
| Knows where it is                                  | `int currentFloor`                   |
| Knows which way it's going                         | `Direction direction`                |
| Has work to do                                     | `Set<Request> requests`              |
| Floor bounds for validation                        | `int minFloor`, `int maxFloor`       |

### Elevator — behavior table

| Need from requirements              | Method                                     |
| ----------------------------------- | ------------------------------------------ |
| Accept a destination from cab       | `boolean addRequest(Request)`              |
| Advance one tick                    | `void step()` (the SCAN algorithm)         |
| Read state for dispatch decisions   | `getCurrentFloor()`, `getDirection()`, `hasRequestsAhead(dir)` |

### Class outlines (write these on the board)

```java
public class ElevatorController {
    private final List<Elevator>   elevators;
    private final int              minFloor, maxFloor;
    private final DispatchStrategy dispatchStrategy;   // injected — Strategy pattern

    public boolean requestElevator(int floor, RequestType type) { /* Step 4 */ }
    public void    step()                                        { /* Step 4 */ }
}

public class Elevator {
    private int currentFloor;
    private Direction direction;
    private final Set<Request> requests;
    private final int minFloor, maxFloor;

    public boolean addRequest(Request r) { /* Step 4 */ }
    public void    step()                 { /* SCAN — Step 4 */ }
    // getters + hasRequestsAhead
}

public final class Request {
    private final int floor;
    private final RequestType type;
    // ctor + getters + equals/hashCode by (floor, type)
}
```

### DispatchStrategy — Strategy pattern

```java
public interface DispatchStrategy {
    Elevator select(List<Elevator> elevators, Request request);
}

// Production-grade default: 3-tier priority (moving-toward → idle → nearest).
public class DirectionAwareDispatchStrategy implements DispatchStrategy { ... }

// Naive baseline: closest by absolute distance, ignores direction. Kept for contrast.
public class NearestDispatchStrategy implements DispatchStrategy { ... }
```

> **Say this out loud while writing:** *"Dispatch is genuinely pluggable — naive nearest, direction-aware, least-busy, future ML-driven. Strategy is the right pattern, and I'd introduce it now rather than retrofit it. Default is `DirectionAwareDispatchStrategy` — that's the 3-tier logic; `NearestDispatchStrategy` is kept as an alternative because it's a useful contrast and good for testing."*

### Diagram — class cards

```
+----------------------------------+   +--------------------------+   +-------------------+
|        ElevatorController        |   |        Elevator          |   |     Request       |
+----------------------------------+   +--------------------------+   +-------------------+
| - elevators: List<Elevator>      |   | - currentFloor: int      |   | - floor: int      |
| - minFloor, maxFloor: int        |   | - direction: Direction   |   | - type: RequestT. |
| - dispatchStrategy:              |   | - requests: Set<Request> |   +-------------------+
|     DispatchStrategy             |   | - minFloor, maxFloor     |   | + equals/hashCode |
+----------------------------------+   +--------------------------+   |    by (floor, type)|
| + requestElevator(f, type): bool |   | + addRequest(r): bool    |   +-------------------+
| + step()                         |   | + step()  // SCAN        |
+----------------------------------+   | + hasRequestsAhead(dir)  |
                                       +--------------------------+

+--------------------------+
| <<interface>>            |          implements: DirectionAwareDispatchStrategy,
| DispatchStrategy         | <------+              NearestDispatchStrategy
+--------------------------+
| + select(elevators, r):  |
|     Elevator             |
+--------------------------+

Controller --owns--> List<Elevator>     Elevator --owns--> Set<Request>
Controller --has-a-> DispatchStrategy   (Strategy pattern, injected)
```

### Principle to verbalize — Information Expert
> "Each Elevator owns the rules for *its own* movement — what it should do this tick is a question only it can answer because only it knows its current floor, direction, and pending stops. So `step()` lives on Elevator, not on Controller. Controller's only job is to (a) decide which elevator gets a new hall call and (b) tick everyone. Two distinct responsibilities, two classes."

---

## STEP 4 — Implementation (~18 min)

### Open by asking
> "Real Java or pseudo-code? I'll walk through SCAN in `Elevator.step()` first because it's the heart of the algorithm, then dispatch in the Controller, then dry-run a hall call."

### 4.1 `Elevator.step()` — SCAN algorithm with 5 branches

```java
public void step() {
    // 1. Nothing to do.
    if (requests.isEmpty()) {
        direction = Direction.IDLE;
        return;
    }

    // 2. Idle with work: pick direction by nearest request.
    if (direction == Direction.IDLE) {
        Request nearest = findNearestRequest();
        direction = (nearest.getFloor() > currentFloor) ? Direction.UP : Direction.DOWN;
    }

    // 3. Stop here? Consume any matching pickup-in-direction AND any destination.
    RequestType matchingPickup =
            (direction == Direction.UP) ? RequestType.PICKUP_UP : RequestType.PICKUP_DOWN;
    Request pickupHere      = new Request(currentFloor, matchingPickup);
    Request destinationHere = new Request(currentFloor, RequestType.DESTINATION);
    boolean stopped = requests.remove(pickupHere) | requests.remove(destinationHere);
    if (stopped) {
        if (requests.isEmpty()) direction = Direction.IDLE;
        return;                          // door cycle = this tick; no movement
    }

    // 4. Nothing ahead in this direction -> reverse, no move this tick.
    if (!hasRequestsAhead(direction)) {
        direction = (direction == Direction.UP) ? Direction.DOWN : Direction.UP;
        return;
    }

    // 5. Move one floor.
    currentFloor += (direction == Direction.UP) ? 1 : -1;
}
```

**Three callouts to deliver out loud while writing this:**

1. *"The order of branches matters. Empty-check first so we go IDLE cleanly. Idle-with-work second so we pick a direction before checking anything else. Stop-here BEFORE reverse-check, because if we're sitting on a floor we need to stop, we don't want to reverse past it."*

2. *"Branch 3 uses `|` not `||` on the two `remove` calls — short-circuiting would let a PICKUP_UP at the same floor block consumption of a DESTINATION at that same floor. Bitwise-or forces both to execute."*

3. *"Stop-here and reverse both `return` without moving. A real elevator's door-cycle takes time; in our discrete model that time IS this tick. Move-one-floor is the ONLY branch that changes currentFloor."*

### 4.2 `Elevator.addRequest()` — guarded entry from the cab

```java
public boolean addRequest(Request request) {
    int f = request.getFloor();
    if (f < minFloor || f > maxFloor) return false;
    if (f == currentFloor)             return true;       // already here — no-op success
    return requests.add(request);                          // Set returns false on duplicate
}
```

> *"Same-floor is a no-op success — the passenger requested the floor they're already on, that's not an error. Out-of-bounds is a hard reject. Duplicate is silently dedup'd by the Set."*

### 4.3 `ElevatorController.requestElevator()` + dispatch

```java
public boolean requestElevator(int floor, RequestType type) {
    if (floor < minFloor || floor > maxFloor) return false;
    if (type == RequestType.DESTINATION)      return false;   // hall calls only

    Request request = new Request(floor, type);
    Elevator best = dispatchStrategy.select(elevators, request); // delegate to Strategy
    if (best == null) return false;
    return best.addRequest(request);
}
```

The default Strategy is `DirectionAwareDispatchStrategy`, which implements the 3-tier priority:

```java
public Elevator select(List<Elevator> elevators, Request request) {
    Elevator best = findMovingToward(elevators, request);   // Tier 1
    if (best != null) return best;

    best = findNearestIdle(elevators, request.getFloor());  // Tier 2
    if (best != null) return best;

    return findNearest(elevators, request.getFloor());      // Tier 3 — fallback
}

private Elevator findMovingToward(List<Elevator> elevators, Request request) {
    int floor = request.getFloor();
    Direction wanted = (request.getType() == RequestType.PICKUP_UP) ? Direction.UP : Direction.DOWN;

    Elevator best = null;
    int bestDistance = Integer.MAX_VALUE;
    for (Elevator e : elevators) {
        if (e.getDirection() != wanted) continue;
        // Must be APPROACHING the floor, not past it.
        if (wanted == Direction.UP   && e.getCurrentFloor() > floor) continue;
        if (wanted == Direction.DOWN && e.getCurrentFloor() < floor) continue;

        int d = Math.abs(e.getCurrentFloor() - floor);
        if (d < bestDistance) { bestDistance = d; best = e; }
    }
    return best;
}
```

> **Senior callout:** *"The 'approaching, not past' check in Tier 1 is critical. An elevator at floor 7 going UP is technically 'going up' but it can't pick up an UP call at floor 5 without first finishing its current sweep upward and coming back — so it's NOT a Tier-1 match. Without this check, you'd assign passengers to elevators that go the wrong way first. And the whole 3-tier logic lives in the Strategy, not the Controller — so swapping in a `LeastBusyDispatchStrategy` is a one-line constructor change."*

### 4.4 Verification — dry-run a hall call

```
Setup: 3 elevators E1, E2, E3 all IDLE at floor 0. minFloor=0, maxFloor=9.

Action 1: requestElevator(5, PICKUP_UP)
   selectBestElevator: Tier 1 — no moving elevators -> null
                       Tier 2 — all three are idle; nearest to floor 5 is E1@0 (d=5)
                       returns E1
   E1.addRequest(Request(5, PICKUP_UP)) -> requests = {(5, PICKUP_UP)}

Tick t=0:
   E1.step():
     1. requests not empty
     2. direction == IDLE: nearest is (5, PICKUP_UP). 5 > 0 -> direction = UP
     3. stop here? pickup(0, PICKUP_UP) not in set; destination(0) not in set -> no
     4. requests ahead UP? floor 5 > 0 -> yes
     5. move: currentFloor = 1
   E2.step(): no requests -> IDLE
   E3.step(): no requests -> IDLE

Ticks t=1..4:
   E1 moves 2, 3, 4, 5 (currentFloor reaches 5 at end of t=4)

Tick t=5 (NOT shown above — happens next iteration of step):
   E1.step():
     1. requests not empty
     2. direction == UP (skip)
     3. stop here? pickup(5, PICKUP_UP) in set -> remove it. requests now empty.
        empty -> direction = IDLE; return                                    ✓
   E1 is now @ floor 5, IDLE, no requests

Total: 5 ticks to arrive + 1 tick to stop = 6 ticks (or 5 if you count "arrival" as the stop tick).

Now: addRequest from inside E1 — passenger wants floor 8.
   E1.addRequest(Request(8, DESTINATION)) -> requests = {(8, DESTINATION)}

Tick: step() again — E1 picks UP (nearest req is above), moves 5->6, 6->7, 7->8,
      then stops on the 4th tick. Total 3 movement ticks + 1 stop tick.

Action 3: requestElevator(2, PICKUP_DOWN) while E1 is somewhere up @ 8 IDLE,
          E2 @ 0 IDLE, E3 @ 0 IDLE.
   selectBestElevator: Tier 1 — no UP/DOWN movers -> null
                       Tier 2 — three idles; E2@0 and E3@0 both d=2 (tie); E1@8 d=6
                                returns E2 (first-encountered tie-break)
   E2.addRequest(Request(2, PICKUP_DOWN)) -> requests = {(2, PICKUP_DOWN)}
   Next tick E2 picks UP (since 2 > 0), goes 0->1->2.
   At floor 2: stop-here? PICKUP_DOWN at 2, but matchingPickup is PICKUP_UP (direction UP).
              pickup(2, PICKUP_UP) NOT in set. destination(2) NOT in set. No stop.
   Branch 4: anything UP? No. -> reverse to DOWN, no move.
   Next tick: direction DOWN, stop-here check uses PICKUP_DOWN -> hit! Stop, IDLE. ✓
```

> **Senior callout from the trace:** *"The PICKUP_DOWN at floor 2 case is interesting — the elevator passes floor 2 going UP, doesn't stop (because the request wants DOWN), reaches the end of UP work, reverses with no movement, then on the next tick stops at floor 2 going DOWN. Two extra ticks vs. naive 'stop on any matching floor' — but those two ticks are correct because we're matching the passenger's wanted direction."*

---

## STEP 5 — Extensibility (~7 min)

### 5.1 "Add a least-busy / energy-optimal dispatch algorithm"

> *"`DispatchStrategy` is already the seam — that's why it's in the base. To add a new policy I'd implement `LeastBusyDispatchStrategy` (picks the elevator with the fewest pending requests) or `EnergyOptimalDispatchStrategy` (prefers elevators already moving in the right direction to avoid spin-up cost). Constructor argument flips at boot; zero changes to Controller, Elevator, or Request."*

### 5.2 "Express elevators / restricted floors"

> *"Add `Set<Integer> servicableFloors` to Elevator (defaults to all floors). `addRequest` rejects floors not in the set. Dispatch in Controller filters elevators by `servicableFloors.contains(floor)` before applying the 3 tiers. Express elevators just have a small set — `{0, 5, 9}` for a high-rise sky lobby."*

### 5.3 "Concurrent hall calls + ticks"

> *"Two issues. (a) A hall call mid-tick can race with `step()` — fix with method-level `synchronized` on Controller, or have `requestElevator` enqueue to a thread-safe inbox the elevator drains at the top of `step()`. (b) Two threads both adding requests to the SAME elevator — Set add is itself atomic in `ConcurrentHashMap.newKeySet()`, but you'd want to ensure step + addRequest don't trample shared state mid-tick."* (Code in deep-dive section below.)

### 5.4 Other "what-if" answers

| Follow-up                                  | Answer                                                                                              |
| ------------------------------------------ | --------------------------------------------------------------------------------------------------- |
| "Weight / passenger capacity"              | Add `currentLoad` + `capacity` on Elevator. `addRequest` rejects when full. Boarding/alighting deltas update load at stops. |
| "Cancellation (passenger changed mind)"    | Add `removeRequest(request)` — `requests.remove(...)`. No-op if already gone. |
| "Energy-aware parking"                     | After IDLE, drift elevators to "home" floors (lobby, even-floor spread) on a background tick.       |
| "Predict demand (rush-hour bias)"          | New `TimeOfDayDispatchStrategy` implementation — slot it into the existing Strategy seam.            |
| "Door open/close timing"                   | Replace `return` after stop with a multi-tick `DOORS_OPEN` state — adds 2 tick states.              |
| "Floor-level analytics / VIP override"     | Observer — Controller publishes `HallCallReceived` / `ElevatorArrived` events.                      |
| "Persist across restart"                   | Inject `ElevatorStateRepository`; write on every `addRequest` / state transition.                   |

---

## Design Patterns — Hello Interview's canonical 8, and WHEN to mention each

The single biggest pattern mistake at SDE‑2 level isn't *not knowing* patterns — it's **forcing them into the wrong step**. Patterns volunteered in Step 1, 2, or 3 sound rehearsed; the same patterns named in Step 5 sound senior.

> **HI's stance:** *"Patterns arise from good design decisions, not the other way around. Most interview designs use zero to two patterns maximum."*
>
> **Geography note (matters for you):** India-based interviews expect candidates to name patterns explicitly. Err on the side of naming when it fits.

### The 5-step timing rule

| Step                       | Use a pattern here?                                                                 |
| -------------------------- | ----------------------------------------------------------------------------------- |
| **1. Requirements**        | **Never.** You're scoping — patterns are an implementation concern.                |
| **2. Entities**            | **Sometimes** — if you already see a clear Strategy seam (e.g., dispatch), declare the interface as one of the entities. *That's what we did here with `DispatchStrategy`.* |
| **3. Class Design**        | **YES, when the pattern earns rent in the base.** Name it explicitly — India-based interviews expect candidates to identify patterns by name when applied. Don't artificially defer to Step 5 if the design genuinely needs the seam now. |
| **4. Implementation**      | **No new patterns.** Implement what Step 3 designed.                               |
| **5. Extensibility**       | **YES — for the *additional* patterns the interviewer's follow-up prompts trigger.** |

### Hello Interview's canonical 8 × interviewer trigger

| # | Pattern              | Category   | Trigger phrase                                                                | One-line response                                                                                       |
| - | -------------------- | ---------- | ------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------- |
| 1 | **Strategy** ⭐       | Behavioral | "different rules" · "variants" · "swap at runtime"                              | *"Promote X to an interface; inject the concrete implementation."*                                       |
| 2 | **Observer**         | Behavioral | "notify multiple" · "broadcast" · "event"                                       | *"X publishes events; subscribers register independently."*                                              |
| 3 | **State Machine**    | Behavioral | "behavior depends on state" · "complex transitions"                             | *"Each state is its own class with its own transitions; I'd draw the state diagram on the board."*       |
| 4 | **Factory** (Method) | Creational | "support different types" · "multiple variants"                                 | *"Centralize creation; callers stop depending on concrete classes."*                                      |
| 5 | **Builder**          | Creational | "many optional fields" · "complicated construction"                             | *"Builder collects fields incrementally; `build()` validates."*                                          |
| 6 | **Singleton**        | Creational | "exactly one" · "global"                                                         | *"I'd resist textbook Singleton — DI a single instance instead."*                                         |
| 7 | **Decorator**        | Structural | "optional features" · "stack behaviors"                                          | *"Wrap X in decorators, each adding one concern. Avoids subclass explosion."*                            |
| 8 | **Facade**           | Structural | "hide complexity" · "single entry point"                                         | *"Orchestrators usually ARE facades."*                                                                    |

> **⭐ Strategy is the #1 priority pattern.** HI: *"If you learn one pattern from this page, make it this one."*

### Three rules to sound natural

1. **Cap at 2 patterns total** in one interview.
2. **Always name the concrete win in the same breath.** *"Strategy here because we want pluggable dispatch algorithms"* > *"I'd use Strategy."*
3. **Never volunteer a pattern without a trigger.**

### How this maps to Elevator specifically

**Already in the BASE design — name these by pattern *and* principle:**

- **Strategy (#1)** ⭐ — `DispatchStrategy` interface with `DirectionAwareDispatchStrategy` (default 3-tier) + `NearestDispatchStrategy` (naive baseline) implementations. Dispatch policy is genuinely pluggable, so Strategy earns rent immediately — not in Step 5. **Mention by name in Step 2 + Step 3.**
- **State Machine (#3)** — Elevator IS a state machine: `IDLE / UP / DOWN` with explicit transitions in `step()`. **Draw M1 from Mental Models.** *"`step()` is the single transition function — the 5 branches are the transition rules."*
- **Facade (#8)** — `ElevatorController` IS a facade over the elevator fleet + the dispatch strategy. Name it once in Step 2.
- **Information Expert** (GRASP principle) — Each elevator owns its own SCAN tick because only it knows its floor/direction/requests.

**Reach for these only on the matching Step-5 follow-up:**

| Follow-up                                              | Pattern (HI's 8)             | Your line                                                                                            |
| ------------------------------------------------------ | ---------------------------- | ---------------------------------------------------------------------------------------------------- |
| "Add LeastBusy / EnergyOptimal / TimeOfDay scheduling" | **Strategy (#1)** ⭐         | *"`DispatchStrategy` is already the seam — new policy is just a new implementation. Constructor swap, zero changes elsewhere."* |
| "Notify analytics / building dashboard"                | **Observer (#2)**            | *"Controller publishes `HallCallReceived` / `ElevatorArrived` events. Analytics, logger, UI subscribe independently."* |
| "Multiple elevator KINDS (passenger / freight / VIP)"  | **Factory (#4)**             | *"`ElevatorFactory.create(ElevatorKind)` produces pre-configured elevators (different capacities, floor sets, speeds)."* |
| "Different building configs"                           | **Builder (#5)**             | *"`BuildingBuilder` collects elevators, floor count, dispatch strategy, then `build()`."*            |

**Patterns to actively refuse if interviewer baits you:**

- **Singleton on ElevatorController** — kills tests, blocks multi-building extensions.
- **Factory for `Direction` / `RequestType`** — enums.
- **Decorator on Elevator** — wrong shape; behaviors aren't stackable here.
- **Full State pattern on Direction** (one class per UP/DOWN/IDLE) — overkill when the transition function is 20 lines. Mention State *Machine* (the concept), not the GoF *State pattern* (subclassing each state).

### One sentence to say at the end of Step 3

> *"The base design names three patterns out loud: Strategy for dispatch, State Machine on Elevator, and Facade on Controller. Strategy is in the base because dispatch policy is genuinely pluggable — I'd rather show the seam now than retrofit it. State Machine and Facade are descriptive — they're what the problem actually is."*

---

## Interview deep-dives — the questions you'll definitely get asked

### 1. Complexity (Big-O)

Let `N` = number of elevators, `R` = max requests on one elevator at a time, `F` = floor count.

| Operation                                | Time                                  | Space     | Notes                                                                              |
| ---------------------------------------- | ------------------------------------- | --------- | ---------------------------------------------------------------------------------- |
| `Controller.requestElevator(f, t)`       | **`O(N + R)`**                        | O(1)      | `O(N)` to pick best, `O(1)` Set add (but `R` if iterating-to-decide internally)    |
| `Controller.step()`                      | **`O(N · R)`**                        | O(1)      | Each elevator scans its request set once per tick                                  |
| `Elevator.step()`                        | **`O(R)`**                            | O(1)      | findNearestRequest + hasRequestsAhead each scan the Set once                       |
| `Elevator.addRequest(r)`                 | **`O(1)`**                            | O(1)      | HashSet add                                                                        |
| `selectBestElevator`                     | `O(N)`                                | O(1)      | One pass per tier; up to 3 passes                                                  |
| Storage — requests per elevator          | —                                     | **O(R)**  | bounded by ~2F (each floor × 2 pickup directions + destinations)                   |
| Storage — controller                     | —                                     | **O(N)**  | Just the elevator list                                                             |

> **Senior callout:** *"`step()` is `O(R)` because we walk the request Set twice — once to find nearest when idle, once for `hasRequestsAhead`. If R got huge, I'd maintain two sorted structures (one ascending, one descending by floor) so both lookups are O(log R) or O(1). For 10 floors and at most a few dozen pending stops, the Set scan is fine."*

### 2. Concurrency / thread-safety

**The race:** a hall call arrives from one thread mid-`step()` on another thread. Without sync, the elevator's request Set is mutated while step is iterating it → `ConcurrentModificationException` or lost requests.

**Simplest correct fix:**

```java
public class ElevatorController {
    public synchronized boolean requestElevator(int floor, RequestType type) { /* ... */ }
    public synchronized void    step()                                       { /* ... */ }
}
```

**Higher-throughput option — inbox pattern:**

```java
public class Elevator {
    private final Queue<Request> inbox = new ConcurrentLinkedQueue<>();
    private final Set<Request> requests = new HashSet<>();   // single-writer (step thread)

    public boolean addRequest(Request r) {
        // ... validation ...
        inbox.offer(r);
        return true;
    }

    public void step() {
        // Drain inbox at the top of each tick — no shared mutation during SCAN.
        Request r;
        while ((r = inbox.poll()) != null) {
            requests.add(r);
        }
        // ... existing SCAN logic operates on `requests` alone ...
    }
}
```

> **Senior callout:** *"The inbox pattern decouples 'producer threads add requests' from 'single thread runs SCAN'. No locks needed because the SCAN-internal state is only mutated by the step thread. Same idea as actor-model mailboxes."*

### 3. Testing — what to write tests for

| Test category               | Cases to cover                                                                                       |
| --------------------------- | ----------------------------------------------------------------------------------------------------- |
| SCAN — branch by branch     | (1) empty → IDLE; (2) idle + req → pick direction; (3) stop = no movement; (4) reverse = no movement; (5) move one floor |
| Direction picking (tie)     | Two equidistant requests above and below → deterministic tie-break (lower floor wins)                |
| Stop-here matching          | UP elevator at floor 5 with PICKUP_UP at 5 → stops. With PICKUP_DOWN at 5 → does NOT stop on this pass. |
| Bounds                      | addRequest(-1) → false; addRequest(maxFloor + 1) → false                                              |
| Dispatch tiers              | (T1) two cars moving UP, one approaching, one past → picks approaching; (T2) all idle → nearest; (T3) all busy in wrong direction → nearest fallback |
| Invalid hall calls          | DESTINATION via `requestElevator` → false; out-of-bounds floor → false                               |
| Whole-system smoke          | Inject N requests, step until all IDLE, assert all serviced                                          |

```java
@Test
void upElevator_doesNotStopFor_pickupDown_atSameFloor() {
    Elevator e = new Elevator("E", 0, 9);
    e.addRequest(new Request(7, RequestType.DESTINATION));   // forces UP from 0
    while (e.getDirection() == Direction.IDLE) e.step();
    // Move it to floor 5 by ticking a few times.
    while (e.getCurrentFloor() < 5) e.step();

    e.addRequest(new Request(5, RequestType.PICKUP_DOWN));   // wrong direction
    int floorBefore = e.getCurrentFloor();
    e.step();
    // It should NOT have stopped — should have moved on toward floor 7.
    assertEquals(floorBefore + 1, e.getCurrentFloor());
}

@Test
void dispatch_prefersApproachingElevator_overFartherIdle() {
    Elevator approaching = new Elevator("E1", 0, 9);
    Elevator idle        = new Elevator("E2", 0, 9);
    // Make E1 at floor 2 going UP toward 9
    approaching.addRequest(new Request(9, RequestType.DESTINATION));
    while (approaching.getCurrentFloor() < 2) approaching.step();

    ElevatorController c = new ElevatorController(List.of(approaching, idle), 0, 9);
    Elevator chosen = c.selectBestElevator(new Request(5, RequestType.PICKUP_UP));
    assertSame(approaching, chosen);
}
```

> **Senior callout:** *"Each branch of SCAN gets its own test. The trickiest is 'stop here doesn't move' — easy to write code that decrements `currentFloor` and then stops, which is wrong. A targeted test catches that."*

### 4. SOLID mapping

| Letter                       | Where it shows up                                                                                          |
| ---------------------------- | ---------------------------------------------------------------------------------------------------------- |
| **S** Single Responsibility  | Controller = dispatch + tick. Elevator = movement + SCAN. Request = a stop. Three reasons to change.       |
| **O** Open/Closed            | `selectBestElevator` is closed for modification but open for extension via a future `DispatchStrategy`. New elevator kinds slot in without touching SCAN. |
| **L** Liskov Substitution    | If `Elevator` becomes an abstract base with `PassengerElevator` / `FreightElevator` subclasses, both must honor the same `step()` / `addRequest` contract — same external behavior, different capacity/floor rules. |
| **I** Interface Segregation  | Controller's public API is 2 narrow methods. Elevator's mutators are `addRequest` + `step`; readers are explicit getters — no fat `getState()` dump. |
| **D** Dependency Inversion   | Controller depends on `Elevator` (its public methods), not on the request Set. When `DispatchStrategy` lands, Controller depends on the interface. |

### 5. "Summarize your design in 30 seconds"

> *"Three classes plus one Strategy interface: ElevatorController, Elevator, Request, and `DispatchStrategy`. Controller is the orchestrator and facade — it delegates hall-call dispatch to an injected `DispatchStrategy` (default is `DirectionAwareDispatchStrategy` with a three-tier priority: moving-toward-and-approaching → nearest-idle → nearest-anything; a naive `NearestDispatchStrategy` ships as an alternative). Each Elevator is a state machine — `IDLE / UP / DOWN` — whose `step()` runs SCAN: empty → IDLE; idle-with-work → pick direction by nearest; stop here → consume matching requests without moving this tick; reverse if nothing ahead; otherwise move one floor. Request is an immutable (floor, type) tuple with equals/hashCode so the Set auto-dedupes. Strategy is in the base because dispatch policy is genuinely pluggable — not deferred to extensibility. Future scheduling algorithms (LeastBusy, EnergyOptimal, TimeOfDay) just slot in as new `DispatchStrategy` implementations."*

That's ~45 seconds. Hits: structure, Strategy-in-base, dispatch tiers, state machine, the 5-branch SCAN, deduping Request, and the headline extension path.

---

## Closing soundbites (memorize these)

- **Opening:** *"Before I design, let me clarify the simulation semantics — discrete ticks, hall vs. destination, same-floor as no-op."*
- **Why no Passenger class:** *"Passengers are actors; we model the events they generate (`Request`), not the actors themselves."*
- **State machine call-out:** *"Each elevator is a state machine: IDLE, UP, DOWN. `step()` is the single transition function with 5 branches."*
- **Defending Info Expert:** *"`step()` lives on Elevator because only the elevator knows its own floor, direction, and pending stops."*
- **Stop-without-move:** *"Stopping consumes the request and ends the tick — no movement on a stop. That's how a real door cycle 'costs time'."*
- **Direction-aware dispatch:** *"Naive nearest-elevator is wrong — an UP-bound elevator past your floor can't pick you up for a DOWN call. Tier 1 filters for 'moving in the wanted direction AND still approaching'."*
- **Set vs Queue:** *"Requests are a Set, not a Queue — we don't service them in arrival order, we service them in position order along the current sweep."*
- **On extensibility:** *"The natural strategy extension is `DispatchStrategy` — same Controller signature, pluggable algorithm."*

---

## Top mistakes that lose points

- **Adding `Passenger`, `Button`, `Floor` classes** — none have managed state.
- **Storing requests as `Queue`** — implies arrival-order service, which is wrong.
- **Moving on the tick the elevator stops** — door cycle takes a tick.
- **Reversing direction AND moving in the same tick** — also wrong; reverse takes a tick.
- **Treating naive nearest-elevator as correct dispatch** — misses the "approaching, not past" check.
- **Putting `step()` on the Controller** — violates Info Expert; only the elevator knows what to do.
- **No equals/hashCode on Request** — Set won't dedupe; spam-clicking a button gives you N stops.
- **Using `||` on the two stop-here removes** — short-circuits the destination removal when a pickup is also present.
- **Allowing DESTINATION through `requestElevator`** — that's a hall-call API.
- **Forgetting the same-floor no-op-success rule** — `addRequest(currentFloor) → false` would be wrong; pressing the button for the floor you're on isn't an error.
- **Pattern-stuffing in Step 3** — save Strategy for Step 5.
- **Skipping the dry run** — SCAN is precisely the kind of code where a 6-tick trace catches off-by-ones.

---

## Files in this folder (your reference implementation)

| File                                              | What it shows                                                              |
| ------------------------------------------------- | -------------------------------------------------------------------------- |
| `model/Direction.java`                            | Enum — UP / DOWN / IDLE                                                    |
| `model/RequestType.java`                          | Enum — PICKUP_UP / PICKUP_DOWN / DESTINATION                               |
| `model/Request.java`                              | Immutable (floor, type) with equals/hashCode so the Set dedupes            |
| `strategy/DispatchStrategy.java`                  | **Strategy interface** — pluggable hall-call dispatch                      |
| `strategy/DirectionAwareDispatchStrategy.java`    | Production default — 3-tier priority (approaching → idle → nearest)        |
| `strategy/NearestDispatchStrategy.java`           | Naive baseline — closest by absolute distance, ignores direction           |
| `Elevator.java`                                   | State machine + SCAN `step()` with 5-branch decision tree                  |
| `ElevatorController.java`                         | Facade — injects Strategy, ticks the fleet                                 |
| `ElevatorDriver.java`                             | Scenario harness — hall call, destination, opposite-direction call, invalid inputs |

Run from the project root:

```bash
mvn -q compile exec:java \
  -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.elevator.ElevatorDriver
```
