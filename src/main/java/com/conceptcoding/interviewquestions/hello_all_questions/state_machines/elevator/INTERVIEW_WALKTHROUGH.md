# Elevator System

> **The "state machine / scheduling" option** on the Ethos list. Hard to finish fully in 45 minutes, so the skill is **scoping**: one cab's movement algorithm + a dispatch strategy for many cabs, and say what you're leaving out.
>
> **The crux (what's really being tested):**
> 1. **The movement algorithm (SCAN):** keep going one way serving every stop, then turn around. Two sorted sets make it trivial.
> 2. **Two kinds of button:** a **hall** call (choose *which* cab) vs a **cab** button (that cab, no choice).
> 3. **Dispatch as a Strategy:** "nearest" is subtly wrong; direction matters.
>
> **Family:** F3 State machine + F5 Swappable policy. See [foundations](../../00_AMAZON_LLD_FOUNDATIONS.md).

---

## 1. Plain-language picture

### How a real lift behaves (SCAN)
```
You're in a lift at floor 5, going UP. Stops pressed: 8 (above) and 3 (below).
A sensible lift: go up to 8 first (you're already going up), THEN turn around and go down to 3.
A silly lift: rush to whichever is nearer (3), then back up to 8, then ... people above wait forever.
```
That's **SCAN** (the same algorithm disks use to move the read head): sweep one direction, serving every stop on the way, then reverse.

### Two sorted lists do all the work
```
upQueue   = stops above me, sorted ascending   → next stop going up   = smallest
downQueue = stops below me, sorted descending  → next stop going down = largest
```
A `TreeSet` keeps each list sorted and drops duplicates for free (five people pressing 7 = one stop at 7).

### Two kinds of button
- **Hall button** (on a floor, UP/DOWN): *some* lift should come. The **controller** decides which: that's the dispatch strategy.
- **Cab button** (inside a lift): *this* lift goes there. No decision to make.

### Why "send the nearest lift" is wrong
```
Floor 6, someone presses DOWN.
Lift A is at 8 going UP (to 10): only 2 floors away, but it must go to 10 first, then come back.
Lift B is at 9 going DOWN: 3 floors away, and it will pass floor 6 anyway.
→ Send B.
```
So dispatch checks, best first:
1. A lift **already heading that way** that hasn't passed the floor.
2. The nearest **truly idle** lift (stopped *and* nothing queued).
3. The nearest lift overall.

---

## 2. From story to design

| # | Decision | Rejected | Why |
|---|---|---|---|
| D1 | **SCAN** with `upQueue` (ascending) + `downQueue` (descending) `TreeSet`s | one FIFO queue; nearest-first | FIFO zig-zags the lift; nearest-first starves far floors. SCAN is fair and efficient. |
| D2 | `TreeSet` | `ArrayList` + sorting | Sorted inserts and no duplicate stops for free; `pollFirst()` gives the next stop in O(log n). |
| D3 | `Elevator` owns its own movement; `ElevatorController` owns dispatch | one god class | One cab's algorithm vs a building-wide decision: separate reasons to change. |
| D4 | `DispatchStrategy` interface | the dispatch logic in the controller | Nearest-direction today; least-busy, zoning, or energy-saving tomorrow. |
| D5 | Tier 2 "idle" = direction IDLE **and** no pending stops | direction IDLE only | A cab that just got a stop is still IDLE until its next tick. Without this, every call piles onto one cab (a bug we caught in the driver). |
| D6 | `advance()` = one tick; each cab moves to its next stop | real-time threads per cab | Deterministic and testable; a timer can call `advance()` in production. |
| D7 | `Elevator` methods `synchronized` | no locking | Hall calls arrive on request threads while the tick thread moves cabs; `TreeSet` isn't thread-safe. |
| D8 | Validate floors (0..top), no UP on the top floor, unknown cab id → throw | silently ignoring | Bad input should fail loudly. |
| D9 | Out of base: doors, capacity, floor-by-floor motion, maintenance mode, direction-aware pickups | modelling all | Say it out loud: *"I'll get the scheduling right first."* Follow-ups (§5). |

### Class shape
```
ElevatorController                       ← the building's brain
  List<Elevator> · topFloor · DispatchStrategy
  callElevator(floor, UP/DOWN) → cabId    (hall button: strategy picks the cab)
  selectFloor(cabId, floor)               (cab button)
  advance() → one tick for every cab

Elevator                                 ← one cab, SCAN
  id · currentFloor · Direction · TreeSet upQueue (asc) · TreeSet downQueue (desc)
  addStop(floor) · moveToNextStop() → floor or −1

«interface» DispatchStrategy  select(elevators, floor, direction)
  └── NearestDispatchStrategy (3 tiers)
enum Direction { UP, DOWN, IDLE }
```

---

## 3. Patterns that earn their place

| Pattern | In the base? | Problem it solves | Without it |
|---|---|---|---|
| **Strategy** (`DispatchStrategy`) | **Yes** | different buildings/times want different dispatch (nearest, least busy, zoned) | dispatch `if`s inside the controller |
| **State** for a cab (IDLE / MOVING / DOORS_OPEN / MAINTENANCE) | No | when doors and maintenance are added, behaviour differs per state | Q1. In the base, `Direction` alone is enough. |

**Say:** *"SCAN per cab with two TreeSets; dispatch is a Strategy because the right policy depends on the building."*

**Tempting but wrong:** a `Floor` class per floor (no behaviour), a `Request` class hierarchy (a floor number is enough), a Singleton controller, the State pattern before doors exist.

---

## 4. The 45-minute run

### Clarify (min 0–5)
> **You:** N floors, M lifts. Hall buttons UP/DOWN on each floor; floor buttons inside each lift. Right?
> **Interviewer:** Yes.
> **You:** The main goal is a sensible movement order and choosing which lift answers a hall call?
> **Interviewer:** Yes.
> **You:** I'll model time as ticks (each tick a lift moves to its next stop), and leave doors, weight limits and maintenance for later. OK?
> **Interviewer:** Fine.

```
In scope:  per-cab SCAN · hall call → dispatch strategy · cab button → that cab
           ticks · validation (floor range, no UP at the top) · thread-safe
Out:       doors, capacity, floor-by-floor animation, maintenance, peak-hour modes
```

### Timeline
| Min | Do |
|---|---|
| 5–10 | Explain SCAN with the 5 → 8 → 3 example. Class shape. |
| 10–22 | **`Elevator`**: two TreeSets, `addStop`, **`moveToNextStop`**. |
| 22–30 | `DispatchStrategy` + **`NearestDispatchStrategy`** (3 tiers). |
| 30–36 | `ElevatorController`: `callElevator`, `selectFloor`, `advance`, validation. |
| 36–40 | Dry run: the "right direction beats nearest" case. |
| 40–45 | Follow-ups. |

### The code ([Elevator.java](Elevator.java), [strategy/NearestDispatchStrategy.java](strategy/NearestDispatchStrategy.java), [ElevatorController.java](ElevatorController.java))
```java
public class Elevator {
    private final int id;
    private int currentFloor;
    private Direction direction = Direction.IDLE;
    private final TreeSet<Integer> upQueue   = new TreeSet<>();                            // ascending
    private final TreeSet<Integer> downQueue = new TreeSet<>(Comparator.reverseOrder());   // descending

    public Elevator(int id) { this.id = id; }

    public synchronized void addStop(int floor) {
        if      (floor > currentFloor) upQueue.add(floor);
        else if (floor < currentFloor) downQueue.add(floor);     // same floor: doors just open
    }

    // SCAN: keep going the same way while there are stops; otherwise turn around; otherwise idle
    public synchronized int moveToNextStop() {
        if (direction == Direction.IDLE) {
            if      (!upQueue.isEmpty())   direction = Direction.UP;
            else if (!downQueue.isEmpty()) direction = Direction.DOWN;
            else return -1;
        }
        if (direction == Direction.UP) {
            if (!upQueue.isEmpty()) {
                currentFloor = upQueue.pollFirst();               // lowest stop above
            } else if (!downQueue.isEmpty()) {
                direction = Direction.DOWN;                       // turn around
                currentFloor = downQueue.pollFirst();             // highest stop below
            } else {
                direction = Direction.IDLE;
                return -1;
            }
        } else {
            if (!downQueue.isEmpty()) {
                currentFloor = downQueue.pollFirst();
            } else if (!upQueue.isEmpty()) {
                direction = Direction.UP;
                currentFloor = upQueue.pollFirst();
            } else {
                direction = Direction.IDLE;
                return -1;
            }
        }
        return currentFloor;
    }
    // + synchronized getCurrentFloor, getDirection, getPendingCount
}

public interface DispatchStrategy {
    Elevator select(List<Elevator> elevators, int floor, Direction direction);
}

public class NearestDispatchStrategy implements DispatchStrategy {
    @Override
    public Elevator select(List<Elevator> elevators, int floor, Direction direction) {
        // Tier 1: heading the right way and hasn't passed the floor yet
        Elevator best = null;
        int bestDist = Integer.MAX_VALUE;
        for (Elevator e : elevators) {
            if (e.getDirection() != direction) continue;
            if (direction == Direction.UP   && e.getCurrentFloor() > floor) continue;
            if (direction == Direction.DOWN && e.getCurrentFloor() < floor) continue;
            int d = Math.abs(e.getCurrentFloor() - floor);
            if (d < bestDist) { bestDist = d; best = e; }
        }
        if (best != null) return best;

        // Tier 2: nearest TRULY idle cab (stopped and nothing queued)
        bestDist = Integer.MAX_VALUE;
        for (Elevator e : elevators) {
            if (e.getDirection() != Direction.IDLE || e.getPendingCount() > 0) continue;
            int d = Math.abs(e.getCurrentFloor() - floor);
            if (d < bestDist) { bestDist = d; best = e; }
        }
        if (best != null) return best;

        // Tier 3: nearest overall; a tie goes to the cab with fewer stops queued
        bestDist = Integer.MAX_VALUE;
        for (Elevator e : elevators) {
            int d = Math.abs(e.getCurrentFloor() - floor);
            if (d < bestDist || (d == bestDist && e.getPendingCount() < best.getPendingCount())) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }
}

public class ElevatorController {
    private final List<Elevator> elevators;
    private final int topFloor;
    private final DispatchStrategy dispatchStrategy;

    public ElevatorController(List<Elevator> elevators, int topFloor, DispatchStrategy dispatchStrategy) {
        if (elevators.isEmpty()) throw new IllegalArgumentException("Need at least one elevator");
        this.elevators = new ArrayList<>(elevators);
        this.topFloor = topFloor;
        this.dispatchStrategy = dispatchStrategy;
    }

    public int callElevator(int floor, Direction direction) {      // hall button
        checkFloor(floor);
        if (direction == Direction.IDLE) throw new IllegalArgumentException("A hall call is UP or DOWN");
        if (floor == topFloor && direction == Direction.UP)  throw new IllegalArgumentException("No UP button on the top floor");
        if (floor == 0 && direction == Direction.DOWN)       throw new IllegalArgumentException("No DOWN button on the ground floor");
        Elevator best = dispatchStrategy.select(elevators, floor, direction);
        best.addStop(floor);
        return best.getId();
    }

    public void selectFloor(int elevatorId, int floor) {            // cab button
        checkFloor(floor);
        for (Elevator e : elevators) {
            if (e.getId() == elevatorId) { e.addStop(floor); return; }
        }
        throw new NoSuchElementException("No elevator " + elevatorId);
    }

    public List<String> advance() {                                  // one tick
        List<String> stops = new ArrayList<>();
        for (Elevator e : elevators) {
            int at = e.moveToNextStop();
            if (at != -1) stops.add("Elevator-" + e.getId() + " → floor " + at + " [" + e.getDirection() + "]");
        }
        return stops;
    }

    private void checkFloor(int floor) {
        if (floor < 0 || floor > topFloor) throw new IllegalArgumentException("Floor " + floor + " is outside 0.." + topFloor);
    }
}
```

### Dry run: SCAN, then dispatch
```
Cab at 5 going UP, stops 8 (up queue) and 3 (down queue):
  tick 1: UP, upQueue {8} → 8
  tick 2: UP, upQueue empty → turn DOWN → downQueue {3} → 3
  tick 3: nothing → IDLE, −1

DOWN call at 6.  E1 at 8 going UP;  E2 at 9 going DOWN.
  Tier 1 wants direction DOWN and floor ≥ 6 → E2 qualifies (9 ≥ 6); E1 is going UP → skipped
  → E2, even though E1 is 1 floor nearer.
```

---

## 5. Follow-ups: answer out loud first, then open

<details>
<summary><b>Q1. Add doors and maintenance mode. Does the cab need a State pattern now?</b></summary>

With doors, a cab has real states with different behaviour: `IDLE`, `MOVING`, `DOORS_OPEN`, `MAINTENANCE`. Moving while the doors are open must be impossible; a cab in maintenance must refuse new stops. Start with an **enum + allowed transitions**:
```java
enum CabState {
    IDLE, MOVING, DOORS_OPEN, MAINTENANCE;
    boolean canMoveTo(CabState next) {
        switch (this) {
            case IDLE:        return next == MOVING || next == DOORS_OPEN || next == MAINTENANCE;
            case MOVING:      return next == DOORS_OPEN;                 // must stop and open, never jump to idle mid-shaft
            case DOORS_OPEN:  return next == IDLE || next == MOVING;
            case MAINTENANCE: return next == IDLE;
            default:          return false;
        }
    }
}
```
Move to class-per-state (State pattern) when the states start doing different **work** (MAINTENANCE ignores calls, DOORS_OPEN runs a close timer, MOVING checks sensors). The dispatcher simply skips cabs in MAINTENANCE.
</details>

<details>
<summary><b>Q2. Someone on floor 6 pressed DOWN, but the cab stops there on its way UP, and they get in going the wrong way.</b></summary>

The base adds hall calls as plain stops, which is a simplification; say so. Fix: keep hall calls **with their direction**: `hallUp` and `hallDown` sets. Going UP, stop at a floor only if it's in the cab's own stops (`upQueue`) **or** in `hallUp`. A DOWN call at 6 is served on the way **down**. That's the LOOK/collective-control behaviour of real lifts.
</details>

<details>
<summary><b>Q3. Different dispatch policy: send the least busy cab.</b></summary>

A new Strategy, with no controller changes:
```java
public class LeastBusyDispatchStrategy implements DispatchStrategy {
    @Override
    public Elevator select(List<Elevator> elevators, int floor, Direction direction) {
        Elevator best = null;
        for (Elevator e : elevators) {
            if (best == null
                    || e.getPendingCount() < best.getPendingCount()
                    || (e.getPendingCount() == best.getPendingCount()
                        && Math.abs(e.getCurrentFloor() - floor) < Math.abs(best.getCurrentFloor() - floor))) {
                best = e;
            }
        }
        return best;
    }
}
// new ElevatorController(cabs, 20, new LeastBusyDispatchStrategy())
```
Also **zoning** (cabs 1–2 serve floors 0–20, cabs 3–4 serve 21–40) for tall buildings, and the **morning up-peak** mode: send idle cabs back to the lobby.
</details>

<details>
<summary><b>Q4. Capacity: the cab is full.</b></summary>

Track `currentLoadKg` (sensor) or a passenger count. A full cab **skips hall-call stops** but still stops for its own cab buttons (people want to get out). The dispatcher prefers cabs with room. If a hall call is skipped, keep it pending in the controller and re-dispatch it.
</details>

<details>
<summary><b>Q5. Make movement realistic: one floor per tick, not jumping straight to the next stop.</b></summary>

`tick()` moves `currentFloor ± 1` towards the next stop (`upQueue.first()` / `downQueue.first()`, *peek*, don't poll), and opens the doors (polls the stop) only on arrival. Stops added *between* the cab and its next stop get served on the way automatically, because the TreeSet keeps them sorted. Same data structure, finer steps.
</details>

<details>
<summary><b>Q6. Concurrency: who calls `advance()`, and what's locked?</b></summary>

A single `ScheduledExecutorService` calls `advance()` every second. Hall calls and cab buttons come from other threads. Each `Elevator`'s methods are `synchronized` (the TreeSets aren't thread-safe), so a call added mid-tick is either in this move or the next, never corrupting the sets. The dispatcher reads `getDirection()`/`getCurrentFloor()` without holding all cabs' locks; that's slightly stale, which is fine for a heuristic. The driver runs 200 concurrent calls while ticking: every stop is served.
</details>

<details>
<summary><b>Q7. How would you know it's working in production?</b></summary>

- **Metrics:** average and p95 **wait time** (call → doors open) and **ride time**, per floor and per hour; cab utilisation; door-open time.
- **Alarms:** a cab stuck MOVING for too long (sensor failure), a floor with very long waits (bad dispatch for that zone), a cab in MAINTENANCE.
- **Logs:** every call with its timestamp, the chosen cab and the tier that chose it, to tune the strategy.
</details>

<details>
<summary><b>Q8. How do you test it?</b></summary>

SCAN order (at 5 with stops 8 and 3 → 8, 3, idle), nearest idle, "right direction beats nearest", calls spread across cabs (not piled on one), validation (floor range, UP on the top floor, unknown cab), and 200 concurrent calls with ticking → 0 pending at the end. All are in the driver. Ticks make everything deterministic: no sleeping, no real time.
</details>

---

## 6. Traps
1. Nearest-first scheduling (it starves far floors) or FIFO (zig-zags).
2. Treating hall calls and cab buttons the same.
3. "Nearest cab" dispatch that ignores direction.
4. Counting a cab with queued stops as idle, so every call goes to the same cab.
5. Modelling doors, weight, floors and people before the scheduling works.
6. No locking on the stop sets while calls and ticks overlap.

## 7. Recall check
1. Explain SCAN with "at 5 going up, stops 8 and 3".
2. Why two TreeSets, and why one is in reverse order?
3. Hall call vs cab button: who decides the cab for each?
4. The 3 dispatch tiers, and why tier 1 beats plain "nearest".
5. Why must tier 2 also check `getPendingCount() == 0`?

**Rebuild in 10 minutes:** `Elevator` (two TreeSets, `addStop`, `moveToNextStop`) · `DispatchStrategy` + 3-tier nearest · controller `callElevator` / `selectFloor` / `advance`.

---

**Files:** `ElevatorController` · `Elevator` · `strategy/` (`DispatchStrategy`, `NearestDispatchStrategy`) · `model/Direction` · `ElevatorDriver` (SCAN order, nearest idle, right direction beats nearest, hall + cab mixed, validation, 200 concurrent calls)
```bash
mvn -q compile exec:java -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.state_machines.elevator.ElevatorDriver
```
