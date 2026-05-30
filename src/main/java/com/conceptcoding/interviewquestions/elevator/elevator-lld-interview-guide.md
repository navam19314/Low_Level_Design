    # Elevator LLD — Interview Guide (HelloInterview Framework)

Total time: ~40 minutes across 5 phases.

---

## Phase 1 — Requirements (5 min)

Say this out loud before writing a single line.

**Questions to ask:**
- How many elevators and floors?
- Hall buttons: simple UP/DOWN, or destination dispatch (user picks floor from hallway)?
- Can a user press multiple floors inside the elevator?
- Real-time simulation, or discrete step-by-step?
- Out of scope: weight limits, door mechanics, emergency stops?

**Agree on this scope (write it in a corner of the board):**
- N elevators, M floors
- UP/DOWN hall buttons on each floor (external requests)
- Floor buttons inside each elevator (internal/destination requests)
- Threading model: each elevator runs on its own thread
- Out of scope: weight, doors, emergency stops, maintenance mode

---

## Phase 2 — Core Entities (5 min)

Draw these boxes and relationships before writing code.

```
                        ┌─────────────────────────────────────────────┐
                        │              ElevatorSystem                  │
                        │                                              │
                        │  - controllers: List<ElevatorController>     │
                        │  - strategy: ElevatorSelectionStrategy       │
                        │                                              │
                        │  + requestElevator(floor, direction)  ← hall button  │
                        │  + selectFloor(elevatorId, floor)     ← inside button│
                        │  + setStrategy(strategy)              ← swap algo    │
                        └──────────────┬──────────────────────────────┘
                                       │ creates & holds N controllers
                     ┌─────────────────┼─────────────────┐
                     ▼                 ▼                  ▼
        ┌────────────────────┐        ...      ┌────────────────────┐
        │  ElevatorController│                 │  ElevatorController│
        │   (Elevator 1)     │                 │   (Elevator N)     │
        │                    │                 │                    │
        │  - upQueue (minPQ) │                 │  - upQueue (minPQ) │
        │  - downQueue(maxPQ)│                 │  - downQueue(maxPQ)│
        │  - lock: Object    │                 │  - lock: Object    │
        │                    │                 │                    │
        │  + submitRequest() │                 │  + submitRequest() │
        │  + run()  ← Thread │                 │  + run()  ← Thread │
        └────────┬───────────┘                 └────────────────────┘
                 │ owns
                 ▼
        ┌────────────────────┐
        │     ElevatorCar    │
        │                    │
        │  - id: int         │
        │  - currentFloor    │
        │  - direction       │
        │                    │
        │  + moveElevator()  │
        │  + getDirection()  │
        │  + getCurrentFloor │
        └────────────────────┘


        ┌──────────────────────────────────────┐
        │      <<interface>>                   │
        │   ElevatorSelectionStrategy          │
        │                                      │
        │  + selectElevator(controllers,       │
        │       floor, direction)              │
        └──────────┬───────────────────────────┘
                   │ implemented by
          ┌────────┴────────┐
          ▼                 ▼
 ┌─────────────────┐  ┌──────────────────┐
 │NearestElevator  │  │  LeastBusy       │
 │Strategy         │  │  Strategy        │
 │                 │  │                  │
 │ Tier 1: same    │  │ Pick elevator    │
 │  direction,     │  │ with fewest      │
 │  not passed     │  │ pending requests │
 │ Tier 2: idle    │  │                  │
 │ Tier 3: first   │  │                  │
 └─────────────────┘  └──────────────────┘


        ┌──────────────────┐
        │ ElevatorDirection│   ← enum
        │  UP              │
        │  DOWN            │
        │  IDLE            │
        └──────────────────┘

  REQUEST FLOW:
  ─────────────
  Hall button pressed (Floor 3, UP)
       │
       ▼
  ElevatorSystem.requestElevator(3, UP)
       │
       ▼
  strategy.selectElevator(...)  ──→  picks best ElevatorController
       │
       ▼
  controller.submitRequest(3)
       │
       ├── floor >= currentFloor? → upQueue.offer(3)   [min-heap]
       └── floor <  currentFloor? → downQueue.offer(3) [max-heap]
       │
       ▼
  lock.notify()  →  wakes up elevator thread
       │
       ▼
  run() loop: poll upQueue → car.moveElevator(3)
```

| Class | One-line job |
|---|---|
| `ElevatorSystem` | Entry point. Receives hall calls + internal presses. Delegates to right elevator. |
| `ElevatorController` | Manages one elevator. Holds SCAN queues. Runs on its own thread. |
| `ElevatorCar` | Physical elevator. Knows current floor and direction. Moves. |
| `ElevatorSelectionStrategy` | Interface. Decides which elevator to assign to a request. |
| `ElevatorDirection` (enum) | UP / DOWN / IDLE |

---

## Phase 3 — Class Design (10 min)

Write the class skeletons — fields and method signatures first, bodies later.

### `ElevatorDirection.java`
```java
enum ElevatorDirection { UP, DOWN, IDLE }
```

### `ElevatorCar.java`
```java
class ElevatorCar {
    private final int id;
    private int currentFloor;          // starts at 0
    private ElevatorDirection direction; // starts IDLE

    // getters: getId(), getCurrentFloor(), getDirection()
    // setter:  setDirection(ElevatorDirection)
    // moveElevator(int destinationFloor)  ← moves floor by floor, prints each floor
}
```

### `ElevatorController.java`  ← the most important class
```java
class ElevatorController implements Runnable {
    private final ElevatorCar car;
    private final PriorityBlockingQueue<Integer> upQueue;   // min-heap
    private final PriorityBlockingQueue<Integer> downQueue; // max-heap

    // submitRequest(int floor)   ← adds to correct queue, wakes thread
    // run()                      ← SCAN loop: process UP, then DOWN, sleep when idle
    // getCurrentFloor(), getDirection(), getPendingCount()  ← used by strategies
}
```

### `ElevatorSelectionStrategy.java`
```java
interface ElevatorSelectionStrategy {
    ElevatorController selectElevator(List<ElevatorController> controllers,
                                      int floor, ElevatorDirection direction);
}
```

### `ElevatorSystem.java`
```java
class ElevatorSystem {
    private final List<ElevatorController> controllers;
    private ElevatorSelectionStrategy strategy;

    // ElevatorSystem(int totalElevators, ElevatorSelectionStrategy)
    //   → creates ElevatorCar + ElevatorController + starts Thread for each

    // requestElevator(int floor, ElevatorDirection direction)  ← hall button
    // selectFloor(int elevatorId, int floor)                   ← inside button
    // setStrategy(ElevatorSelectionStrategy)                   ← swap at runtime
}
```

---

## Phase 4 — Implementation (10 min)

### Write `ElevatorCar.moveElevator()` first (simplest, warms you up)

```java
public void moveElevator(int destinationFloor) {
    if (currentFloor == destinationFloor) {
        System.out.println("Elevator " + id + ": doors open at floor " + currentFloor);
        return;
    }
    System.out.println("Elevator " + id + ": doors closing");
    direction = destinationFloor > currentFloor ? ElevatorDirection.UP : ElevatorDirection.DOWN;
    int step = direction == ElevatorDirection.UP ? 1 : -1;

    while (currentFloor != destinationFloor) {
        sleep(10);
        currentFloor += step;
        System.out.println("Elevator " + id + ": floor " + currentFloor + " [" + direction + "]");
    }
    System.out.println("Elevator " + id + ": doors open at floor " + currentFloor);
}
```

### Write `ElevatorController` — the SCAN algorithm

**The two queues:**
- `upQueue` = min-heap → serves lowest floor first going UP
- `downQueue` = max-heap → serves highest floor first going DOWN

```java
ElevatorController(ElevatorCar car) {
    this.car = car;
    upQueue   = new PriorityBlockingQueue<>();
    downQueue = new PriorityBlockingQueue<>(11, (a, b) -> b - a);  // max-heap
}

void submitRequest(int floor) {
    if (floor >= car.getCurrentFloor()) {
        if (!upQueue.contains(floor))   upQueue.offer(floor);
    } else {
        if (!downQueue.contains(floor)) downQueue.offer(floor);
    }
    synchronized (lock) { lock.notify(); }
}

public void run() {
    while (true) {
        synchronized (lock) {
            while (upQueue.isEmpty() && downQueue.isEmpty()) {
                car.setDirection(ElevatorDirection.IDLE);
                lock.wait();   // sleep until a request arrives
            }
        }
        while (!upQueue.isEmpty())   car.moveElevator(upQueue.poll());
        while (!downQueue.isEmpty()) car.moveElevator(downQueue.poll());
    }
}
```

### Write `NearestElevatorStrategy` — 3-tier fallback

```java
ElevatorController selectElevator(controllers, floor, direction) {
    // Tier 1: elevator already going same direction, hasn't passed the floor
    best = null, minDist = MAX_INT
    for each controller:
        sameDir    = controller.getDirection() == direction
        notPassed  = (UP  && controller.getCurrentFloor() <= floor)
                  || (DOWN && controller.getCurrentFloor() >= floor)
        if sameDir && notPassed && distance < minDist → update best

    if best != null → return best

    // Tier 2: idle elevator
    for each controller:
        if IDLE → return it

    // Tier 3: any elevator (first one)
    return controllers.get(0)
}
```

### Write `ElevatorSystem` (glue — fast to write)

```java
ElevatorSystem(int n, ElevatorSelectionStrategy strategy) {
    for i in 1..n:
        controller = new ElevatorController(new ElevatorCar(i))
        controllers.add(controller)
        new Thread(controller, "Elevator-" + i).start()
}

void requestElevator(int floor, ElevatorDirection direction) {
    strategy.selectElevator(controllers, floor, direction).submitRequest(floor);
}

void selectFloor(int elevatorId, int floor) {
    controllers.get(elevatorId - 1).submitRequest(floor);
}
```

---

## Phase 5 — Extensibility (5 min, discuss only — don't code)

Be ready for these follow-ups:

| "What if..." | Answer |
|---|---|
| Express elevators (only serve certain floors) | Add `Set<Integer> servedFloors` to `ElevatorCar`; validate in `submitRequest` |
| Cancel a request | Add `cancelRequest(int floor)` to `ElevatorController` — remove from both queues |
| Different dispatch strategy | Already handled — `setStrategy()` on `ElevatorSystem` swaps at runtime (Strategy pattern) |
| Multiple buildings | `ElevatorSystem` becomes building-scoped; add a `BuildingManager` above it |
| Emergency stop | Add `ElevatorState` enum (NORMAL / MAINTENANCE / EMERGENCY); controller skips non-NORMAL elevators during dispatch |

---

## Time Budget

| Phase | Time |
|---|---|
| Requirements — ask 3-4 questions, agree on scope | 5 min |
| Core Entities — draw the 3-box diagram | 5 min |
| Class Design — skeletons for all 5 classes | 10 min |
| `moveElevator` + SCAN loop + `NearestElevatorStrategy` | 12 min |
| `ElevatorSystem` glue + `Demo` | 5 min |
| Extensibility discussion | 3 min |
| **Total** | **~40 min** |

---

## What Interviewers Are Checking

| Checkpoint | What they want to see |
|---|---|
| Did you ask clarifying questions? | At least 2-3 before writing anything |
| Class separation | `ElevatorCar` (entity) vs `ElevatorController` (logic) vs `ElevatorSystem` (facade) |
| SCAN algorithm | Two queues (min-heap UP, max-heap DOWN), not FIFO |
| Thread safety | `synchronized` + `wait/notify`, not busy-polling |
| Strategy pattern | Can you swap dispatch algorithms without changing other classes? |
| Encapsulation | Private fields, getters — no public mutable state |

---

## Things That Will Get You Marked Down

- Writing a single `Elevator` class with all logic inside (no separation of concerns)
- Using a single queue (FIFO) — shows you don't know elevator algorithms
- Not handling threading (no `wait/notify`, just `while(true)` busy loop)
- Hardcoding the dispatch logic instead of using a Strategy
- Not asking requirements — jumping straight to code

---

## Order to Write Code in the Interview

1. `ElevatorDirection.java` — 1 min (trivial, gets you started)
2. `ElevatorCar.java` skeleton + `moveElevator()` — 5 min
3. `ElevatorController.java` — 12 min ← most marks here
4. `ElevatorSelectionStrategy.java` + `NearestElevatorStrategy.java` — 6 min
5. `ElevatorSystem.java` — 4 min
6. `Demo.java` — 2 min
