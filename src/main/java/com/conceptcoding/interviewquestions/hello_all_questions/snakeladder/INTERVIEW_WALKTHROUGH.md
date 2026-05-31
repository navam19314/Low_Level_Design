# Snake & Ladder — Interview Walkthrough

A 45-minute SDE-2 LLD round. The problem is small on purpose — interviewers want to see if you can deliver a *finished* solution in 30 minutes with clean separation of concerns, then spend 15 minutes on extensions. The signal here is **don't over-engineer**.

---

## Mental Model

```
   ┌────────────────────────────────────────────────┐
   │   Game (orchestrator)                          │
   │   - turn order (Deque<Player>)                 │
   │   - status (IN_PROGRESS / FINISHED)            │
   │   - winner                                     │
   │   - event log                                  │
   │                                                │
   │   asks ─────────►  Dice.roll()                 │
   │   asks ─────────►  Board.applyJump(square)     │
   │                                                │
   │   updates player.position                      │
   │   rotates turn queue                           │
   └────────────────────────────────────────────────┘

   Board (immutable topology)        Dice (Strategy)
   ┌────────────────────────┐        ┌──────────────────────┐
   │ size                   │        │ StandardDice  (1d6)  │
   │ snakes:  head → tail   │        │ FixedSequenceDice    │
   │ ladders: bottom → top  │        │   (test double)      │
   │ applyJump(sq) → newSq  │        │ <future: TwoDice>    │
   │ validate() on build    │        └──────────────────────┘
   └────────────────────────┘
```

**The one design decision worth defending in this problem:** put the snake-or-ladder resolution on `Board.applyJump()`, not in `Game`. `Game` just asks "where do I end up after landing on square N?" and Board answers. This is **Tell, Don't Ask** — Game doesn't peek at the snake/ladder maps and branch.

---

## Step 1 — Requirements (5 min)

**Functional:**
1. 2+ players take turns rolling a die.
2. Roll moves you forward by the die value.
3. Landing on a snake head → drop to the tail.
4. Landing on a ladder bottom → climb to the top.
5. First player to reach exactly `size` wins.
6. Overshooting `size` → no move (stay put), turn passes.

**Clarify out loud:**
- Single die (1d6)?  → yes, default
- Does rolling a 6 grant an extra turn? → no for v1, configurable extension
- Can a square be both a snake head and a ladder bottom? → no, board validation rejects it
- Do snakes/ladders chain? (land on ladder top that's also a snake head) → no, single resolution

**Non-functional:**
- In-memory only, single JVM, single thread (turn-based game has no concurrency)
- Deterministic when the dice is deterministic (testability requirement)

**Out of scope** — state explicitly:
- Persistence (game-resume from disk)
- Network / multiplayer over wire
- AI players / strategy
- UI rendering

---

## Step 2 — Entities (5 min)

| Entity   | Purpose | Lifetime |
|----------|---------|----------|
| `Player` | identity + current position | one per player, lives entire game |
| `Board`  | topology — size, snakes map, ladders map | immutable after construction |
| `Dice`   | "give me a number" — Strategy seam | injected into Game |
| `Game`   | orchestrator — owns turn order, status, winner | one per game session |

**Value objects:**
- `Game.Status` enum: `IN_PROGRESS`, `FINISHED`

**Key invariants:**
- Player position ∈ [0, board.size]
- Snake head > tail; ladder bottom < top
- No square is both a snake head and a ladder bottom

---

## Step 3 — Class Design (10 min)

### `Dice` — Strategy

```java
public interface Dice {
    int roll();
}

public class StandardDice implements Dice {
    public int roll() { return ThreadLocalRandom.current().nextInt(1, 7); }
}

public class FixedSequenceDice implements Dice { /* returns pre-programmed sequence */ }
```

**Why Strategy here on day one?** Apply the **one-sentence test**: *"Do I need at least two implementations on day one?"* — Yes: a real die (production) and a test-fake (so scenarios are reproducible). That's the canonical green light for pre-baking a pattern.

### `Board` — immutable topology + the Tell-Don't-Ask seam

```java
public class Board {
    private final int size;
    private final Map<Integer, Integer> snakes;   // head → tail
    private final Map<Integer, Integer> ladders;  // bottom → top

    public int applyJump(int position) {
        if (snakes.containsKey(position))  return snakes.get(position);
        if (ladders.containsKey(position)) return ladders.get(position);
        return position;
    }
}
```

`Game` never looks inside snakes/ladders. It calls `board.applyJump(target)` and uses the result. If we later add "trampoline squares" that send you forward 5, that's a Board-internal change — Game doesn't touch.

### `Game` — orchestrator

```java
public Player playTurn() {
    Player p = turnOrder.removeFirst();
    int roll   = dice.roll();
    int target = p.getPosition() + roll;

    if (target > board.getSize()) {           // overshoot — stay
        turnOrder.addLast(p);
        return p;
    }
    int landed = board.applyJump(target);     // snake / ladder resolution
    p.setPosition(landed);

    if (landed == board.getSize()) {
        winner = p;
        status = Status.FINISHED;
        return p;
    }
    turnOrder.addLast(p);
    return p;
}
```

The `Deque` is doing the turn-rotation work — `removeFirst` + `addLast`. No mod-arithmetic, no index bookkeeping.

---

## Step 4 — Implementation (15 min)

**Order to write in (so you always have something runnable):**
1. `Dice` + `StandardDice` + `FixedSequenceDice` (5 min) — the test seam
2. `Player` (1 min) — trivial
3. `Board` with `applyJump` + `validate()` (5 min)
4. `Game.playTurn()` + `playToFinish()` (4 min)

**Driver scenarios I'd actually code in 45 min** (skip if time is tight, mention they exist):
- ladder-climb with fixed dice
- snake-bite with fixed dice
- overshoot
- exact-win
- bad topology rejected
- random full game

The **fixed-sequence dice** is what makes the first 5 scenarios deterministic. Without that Strategy seam, you can't write a deterministic snake-bite test.

---

## Step 5 — Extensibility / Deep-Dive (10 min)

> This is where the interview is *won or lost*. Below are the questions interviewers love.

### Q1. *How would you support "rolling a 6 grants an extra turn"?*

Add a config to `Game` (or a `TurnRules` strategy):

```java
public class Game {
    private final boolean grantExtraTurnOnSix;

    public Player playTurn() {
        Player p = turnOrder.removeFirst();
        int roll = dice.roll();
        // ... apply move ...
        if (roll == 6 && grantExtraTurnOnSix && status != Status.FINISHED) {
            turnOrder.addFirst(p);   // same player goes again
        } else {
            turnOrder.addLast(p);
        }
    }
}
```

**Senior signal:** point out the *anti-cheese* rule — three consecutive 6s should reset the player to 0 (real-life variant). That's a small `consecutiveSixes` counter on Player.

### Q2. *Two dice instead of one — what changes?*

Nothing in `Game` — the Strategy seam absorbs it:

```java
public class TwoDice implements Dice {
    public int roll() {
        return ThreadLocalRandom.current().nextInt(1,7)
             + ThreadLocalRandom.current().nextInt(1,7);
    }
}
```

Range becomes [2..12], overshoot logic unchanged. *This is the payoff for putting Strategy on day one.*

### Q3. *Two players on the same square — does anything happen?*

Standard rules: no. Variant rules: the latecomer "kills" the existing occupant (sends them back to 0). To support: `Game.playTurn` after computing `landed`, scan all *other* players — if any is at `landed`, send them to 0. O(n) per turn, n = player count, fine.

### Q4. *How do you make this testable without flakiness?*

That's exactly what `FixedSequenceDice` is for. Real interview tactic: bring it up *yourself* when explaining the Dice strategy. Interviewers love seeing testability called out without being prompted.

### Q5. *Persistence — resume a game from disk?*

Snapshot what's mutable:
- per-Player: id, position
- Game: status, winner-id, turn-order-by-id, eventLog (optional)

Board is immutable — serialize once with the game ID. Dice has no state (well, FixedSequenceDice has an index — only relevant for tests).

Resume = reconstruct Board from snapshot + recreate players in order + replay nothing (we stored final state, not history).

### Q6. *Concurrent games — what's shared?*

Each `Game` instance is self-contained. The only shareable thing is `Board` (immutable after construction) and `StandardDice` (stateless — `ThreadLocalRandom` is per-thread). So one `Board` + one `Dice` can fan out to thousands of concurrent games trivially. Each `Game` owns its `Players` + `turnOrder`.

### Q7. *What if I want to log every move + render the board after each turn?*

Two options:
- **Pull**: caller invokes `game.getEventLog()` after `playToFinish()`. (What we built.)
- **Push**: Observer pattern. `Game` accepts a `List<GameEventListener>` — emits `onPlayerMoved(player, from, to, jumpKind)`. Decouples logging / rendering / analytics from Game's loop.

For v1, pull is fine. If the interviewer asks "what if I want to stream moves to a UI in real-time", *that's* when you introduce the Observer pattern. Don't pre-bake it.

### Q8. *Find shortest-path-to-win — minimum dice rolls assuming favorable rolls.*

This is a graph problem disguised as an LLD question. Model the board as a graph: node = square, edges = each die outcome 1..6 (with snake/ladder resolution applied to the target). BFS from 0 to `size` gives shortest path. O(V + E) = O(size × 6).

Quick code sketch:
```java
public int minRolls() {
    int[] dist = new int[board.getSize() + 1];
    Arrays.fill(dist, -1);
    Queue<Integer> q = new ArrayDeque<>();
    q.add(0); dist[0] = 0;
    while (!q.isEmpty()) {
        int sq = q.poll();
        for (int r = 1; r <= 6; r++) {
            int next = sq + r;
            if (next > board.getSize()) continue;
            next = board.applyJump(next);
            if (dist[next] == -1) {
                dist[next] = dist[sq] + 1;
                q.add(next);
            }
        }
    }
    return dist[board.getSize()];
}
```

Note: this lives on `Game` or a separate `BoardAnalyzer` — **not** on `Board` itself. Board is geometry; analysis is a separate concern.

---

## Patterns Used (with timing)

| Pattern | Where | When introduced | Why |
|---------|-------|----------------|-----|
| **Strategy** | `Dice` interface | Day 1 | Need real + test-fake on day one (passes the one-sentence test) |
| **Tell-Don't-Ask** | `Board.applyJump` | Day 1 | Game shouldn't peek at snake/ladder maps |
| **Immutable value** | `Board` (after construction) | Day 1 | Shareable across games; thread-safe by construction |

**Patterns I did NOT pre-bake:**
- **State** — Game has 2 states (IN_PROGRESS / FINISHED). Two states = enum + if-statement. Class-per-state would be overkill. (Compare: VendingMachine has 3+ states with distinct behavior per state, so it earns the pattern.)
- **Observer** — only one consumer (the driver) reads the log. Re-introduce when "stream moves to UI" comes up in Step 5.
- **Factory** — no piece-type variation. The two Dice implementations are passed by the caller directly.

---

## Top Mistakes to Avoid

1. **Putting snake/ladder branching in `Game`.** Game becomes "if board.snakes.contains(target) else if board.ladders.contains(target) else …". `Board.applyJump` collapses that to one call.
2. **Hard-coding the dice as `Math.random()`** in `Game`. Now your tests are flaky. Strategy on day one.
3. **Allowing `playTurn` after FINISHED.** Always check status first; throw IllegalStateException.
4. **Position type ambiguity** — is "position 0" off-board or square 0? Pick one (we use 0 = off-board / start) and document.
5. **Validating the board lazily.** If you only catch bad topology when applyJump is called, the symptom is far from the cause. Validate in constructor.
6. **Modeling the board as `int[100]`** with sentinels for snakes/ladders. Two maps is far cleaner — and supports arbitrary board sizes for testing (we use a 10-square board in scenario 5).
7. **Forgetting overshoot.** Easy to miss. Real-game rule: target > size → stay put.

---

## Closing Soundbite (60 seconds)

> "The core design choice is putting snake/ladder resolution on `Board.applyJump()` so `Game` is just a turn loop — pull a player, roll, ask the board where they end up, rotate. The Dice Strategy is the one pattern I'd bake in on day one because I need a deterministic test-fake from the start; without it, every snake/ladder scenario is flaky. Two states (IN_PROGRESS / FINISHED) don't earn a State pattern — that's an enum. I'd reach for Observer only if the interviewer asks for live move-streaming. Extensions I'd defend: roll-6-extra-turn lives on a `TurnRules` toggle, two-dice is a new Strategy implementation with zero changes to Game, and minimum-rolls-to-win is a BFS on a board-as-graph view — analysis lives on `BoardAnalyzer`, not `Board`, because geometry and analytics are different concerns."

---

## File Index

```
snakeladder/
├── Dice.java                 # Strategy interface
├── StandardDice.java         # 1d6 random
├── FixedSequenceDice.java    # deterministic test double
├── Player.java               # id, name, position
├── Board.java                # size + snakes + ladders + applyJump + validate
├── Game.java                 # turn loop + status + winner + event log
├── SnakeLadderDriver.java    # 7 scenarios incl. snake bite + ladder climb + exact win
└── INTERVIEW_WALKTHROUGH.md  # this file
```
