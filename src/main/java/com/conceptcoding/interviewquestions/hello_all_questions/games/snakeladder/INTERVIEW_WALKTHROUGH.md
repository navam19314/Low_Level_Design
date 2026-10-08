# Snake & Ladder

> **A common 25–30 minute "game" question** (Adobe-frequent; on the Ethos list as a game/state-machine option).
>
> **The crux (what's really being tested):**
> 1. **Where rules live:** the `Board` knows *the map* (where a snake or ladder sends you); the `Game` knows *the rules* (turns, overshoot, winning).
> 2. **Testability:** dice behind an interface, so tests can make the dice land exactly on a snake.
> 3. **Validating the board:** no loops, no impossible jumps.
>
> **Family:** F6 Board / turn game. See [foundations](../../00_AMAZON_LLD_FOUNDATIONS.md).

---

## 1. Plain-language picture

```
Squares 1..100. Everyone starts at 0 (off the board).
On your turn: roll a die, move forward.
  Land on a ladder's bottom → climb to its top.        6 → 26
  Land on a snake's head    → slide to its tail.       99 → 54
  Roll past 100             → you don't move.          97 + 5 = 102 → stay at 97
  Land exactly on 100       → you win.
Turns go round in a circle.
```

### Who knows what
- **Board:** a map of jumps. Ask it *"I landed on 99, where do I end up?"* → *"54"*. The game never asks "is this a snake?". It just asks where to go (**tell, don't ask**).
- **Game:** whose turn (a queue: take from the front, put back at the end), rolling, overshoot, win.
- **Dice:** an interface. A real one returns random 1–6; a **fake** one returns a fixed sequence, so a test can say "roll 5, then 5" and land exactly on a snake.

### A broken board
A ladder from 4 to 15 and a snake from 15 to 3: landing on 4 takes you to 15, which is a snake head. Do you slide or not? Rather than invent a rule, **reject the board**: no jump may end where another starts.

---

## 2. From story to design

| # | Decision | Rejected | Why |
|---|---|---|---|
| D1 | Snakes and ladders as two `Map<Integer, Integer>` on the `Board` | `Snake` / `Ladder` / `Cell` classes | They're just "from → to". Classes add files with no behaviour. |
| D2 | `board.applyJump(square)` returns the final square | the game checking `isSnake` / `isLadder` | Tell, don't ask: adding a "teleport" square later changes only the board. |
| D3 | Rules (overshoot, exact win, turn order) in `Game` | on `Board` | The board is the map; the rules can vary (house rules) without touching it. |
| D4 | `Dice` interface + `StandardDice` + `FixedSequenceDice` | `random.nextInt` inside `Game` | Two implementations on day 1 (real + test). Deterministic tests. |
| D5 | Turn order = `ArrayDeque<Player>`: `removeFirst` → play → `addLast` | an index with modulo | Natural rotation; removing a player who leaves is easy. |
| D6 | **Validate the board in the constructor**: head > tail, bounds, no chained jumps | trusting the input | A bad board fails at once, not in the middle of a game. |
| D7 | `Player.setPosition` is package-private | public setter | Only the game moves players. |
| D8 | An event log of each turn | printing inside the game | Testable output, and useful for a UI replay. |

### Class shape
```
Game                               ← rules
  Board · Dice · Deque<Player> turnOrder · status · winner · eventLog
  playTurn() · playToFinish(maxTurns)

Board                              ← the map (immutable, validated)
  size · Map snakes (head→tail) · Map ladders (bottom→top)
  applyJump(square) → final square · standard100()

«interface» Dice  roll()
  ├── StandardDice        (random 1..6)
  └── FixedSequenceDice   (test double)
Player { id, name, position }
```

---

## 3. Patterns that earn their place

| Pattern | Problem it solves | Without it |
|---|---|---|
| **Strategy** (`Dice`) | real vs fake dice; later two dice, weighted dice | random numbers inside `Game` → flaky tests, rules hard-coded |

**Say:** *"Dice is a Strategy, because I need two implementations on day 1: the real one and a fixed one for tests."*

**Tempting but wrong:** a `Cell` class per square (100 objects with no behaviour), `Snake extends Jump` hierarchies, a Singleton game, the State pattern for IN_PROGRESS/FINISHED (an enum is enough).

---

## 4. The 30-minute run

### Clarify (2 min)
> **You:** 100 squares, any number of players (at least 2), one six-sided die. Overshooting 100 = stay put; exact 100 wins?
> **Interviewer:** Yes.
> **You:** Extra turn on a 6, or needing a 6 to start?
> **Interviewer:** Not now; maybe later.
> **You:** Can a ladder end on a snake's head?
> **Interviewer:** Your call.
> **You:** I'll reject such boards, so a jump never chains.

### Timeline
| Min | Do |
|---|---|
| 2–5 | Class shape. Say: *"Board = map, Game = rules, Dice = Strategy for testability."* |
| 5–12 | **`Board`** with validation + `applyJump`. |
| 12–20 | **`Game.playTurn`** (queue rotation, overshoot, jump, win). |
| 20–24 | `Dice` + `StandardDice` + `FixedSequenceDice`, `Player`. |
| 24–30 | Dry run with fixed dice + follow-ups. |

### The code ([Board.java](Board.java), [Game.java](Game.java), [Dice.java](Dice.java))
```java
public class Board {
    private final int size;
    private final Map<Integer, Integer> snakes;     // head → tail
    private final Map<Integer, Integer> ladders;    // bottom → top

    public Board(int size, Map<Integer, Integer> snakes, Map<Integer, Integer> ladders) {
        if (size < 10) throw new IllegalArgumentException("Board must have at least 10 squares");
        this.size = size;
        this.snakes = new HashMap<>(snakes);
        this.ladders = new HashMap<>(ladders);
        validate();
    }

    private void validate() {
        for (var e : snakes.entrySet()) {
            int head = e.getKey(), tail = e.getValue();
            if (head <= tail)              throw new IllegalArgumentException("Snake head " + head + " must be > tail " + tail);
            if (head >= size || tail < 1)  throw new IllegalArgumentException("Snake out of bounds: " + head + "->" + tail);
            if (ladders.containsKey(head)) throw new IllegalArgumentException("Square " + head + " is both snake head and ladder bottom");
        }
        for (var e : ladders.entrySet()) {
            int bottom = e.getKey(), top = e.getValue();
            if (bottom >= top)             throw new IllegalArgumentException("Ladder bottom " + bottom + " must be < top " + top);
            if (top > size || bottom <= 1) throw new IllegalArgumentException("Ladder out of bounds: " + bottom + "->" + top);
        }
        // applyJump resolves ONE jump, so no jump may end where another starts
        Map<Integer, Integer> all = new HashMap<>(snakes);
        all.putAll(ladders);
        for (int end : all.values()) {
            if (all.containsKey(end)) throw new IllegalArgumentException("Square " + end + " ends one jump and starts another");
        }
    }

    // tell, don't ask: "where do I end up if I land here?"
    public int applyJump(int position) {
        if (snakes.containsKey(position))  return snakes.get(position);
        if (ladders.containsKey(position)) return ladders.get(position);
        return position;
    }

    public int getSize() { return size; }
}

public class Game {
    public enum Status { IN_PROGRESS, FINISHED }

    private final Board board;
    private final Dice dice;
    private final Deque<Player> turnOrder;
    private Status status = Status.IN_PROGRESS;
    private Player winner;

    public Game(Board board, Dice dice, List<Player> players) {
        if (players == null || players.size() < 2) throw new IllegalArgumentException("Need at least 2 players");
        this.board = board;
        this.dice = dice;
        this.turnOrder = new ArrayDeque<>(players);
    }

    public Player playTurn() {
        if (status == Status.FINISHED) throw new IllegalStateException("Game over");
        Player p = turnOrder.removeFirst();
        int roll = dice.roll();
        int target = p.getPosition() + roll;

        if (target > board.getSize()) {            // overshoot: stay put
            turnOrder.addLast(p);
            return p;
        }
        int landed = board.applyJump(target);
        p.setPosition(landed);
        if (landed == board.getSize()) {           // exact finish
            winner = p;
            status = Status.FINISHED;
            return p;
        }
        turnOrder.addLast(p);
        return p;
    }
    // + playToFinish(maxTurns), event log, getters
}

public interface Dice { int roll(); }

public class StandardDice implements Dice {
    public int roll() { return ThreadLocalRandom.current().nextInt(1, 7); }
}

public class FixedSequenceDice implements Dice {      // test double: 5, 1, 5, 1, ...
    private final int[] sequence;
    private int index = 0;
    public FixedSequenceDice(int... sequence) {
        if (sequence.length == 0) throw new IllegalArgumentException("Need at least one roll in the sequence");
        this.sequence = sequence;
    }
    public int roll() { return sequence[index++ % sequence.length]; }
}
// Player { id, name, position (0 = not on the board yet) }
```

### Dry run (snake 10 → 2, dice 5, 1, 5, 1)
```
Alice: 0 + 5 = 5   → no jump → 5     → back of the queue
Bob:   0 + 1 = 1   → 1
Alice: 5 + 5 = 10  → applyJump(10) = 2 (snake) → 2
Overshoot (size 10, Alice at 8, rolls 5): 13 > 10 → stays at 8, turn passes
```

---

## 5. Follow-ups: answer out loud first, then open

<details>
<summary><b>Q1. Rolling a 6 gives another turn (max 3 in a row).</b></summary>

In `playTurn`, after moving, if `roll == 6` and the player hasn't won, put them back at the **front** of the queue (`addFirst`) instead of the back. Keep a count of consecutive sixes; on the third 6, many house rules cancel the turn's moves. Only `Game` changes; the board doesn't.
</details>

<details>
<summary><b>Q2. You need a 6 to get on the board.</b></summary>

`if (p.getPosition() == 0 && roll != 6) { turnOrder.addLast(p); return p; }` at the start of the move. One rule, in `Game`. If rules multiply (extra turns, needing a 6, bounce-back overshoot), pull them into a `RuleSet` object so variants can be swapped without editing `Game`.
</details>

<details>
<summary><b>Q3. Two dice, or a weighted die for a "power-up".</b></summary>

New `Dice` implementations: `TwoDice` returns `d1.roll() + d2.roll()` (2..12). `Game` doesn't change; that's the Strategy payoff.
</details>

<details>
<summary><b>Q4. Overshoot rule change: bounce back (at 98, roll 5 → 100, then back to 97).</b></summary>

`target = size - (target - size)` when `target > size`, then apply the jump as usual. Only the overshoot branch in `playTurn` changes.
</details>

<details>
<summary><b>Q5. Online multiplayer: many games, players on different devices.</b></summary>

A `GameService` with `Map<gameId, Game>`. `playTurn(gameId, playerId)` checks it's that player's turn (the front of the queue), and is synchronized **per game** so a double-tap doesn't roll twice. Roll the dice on the **server** (never trust a client's roll). Push each event to all players over a WebSocket.
</details>

<details>
<summary><b>Q6. How do you test it?</b></summary>

`FixedSequenceDice` makes every scenario exact: a ladder climb, a snake bite, overshoot, the exact win, each bad-board rejection (inverted snake, overlap, chained jump), plus one full game with real dice to completion. All are in the driver.
</details>

---

## 6. Traps
1. Random numbers inside `Game`: no deterministic tests.
2. The game asking "is this a snake?" instead of the board answering "where do I go?".
3. `Cell` objects for 100 squares.
4. No board validation (inverted snakes, chained jumps).
5. Moving past 100 and capping at 100 (that's a different rule).

## 7. Recall check
1. What does `Board` know vs `Game`?
2. Why is `Dice` an interface? Name the two day-1 implementations.
3. What does `validate()` reject, and why is "no chained jumps" needed?
4. Write `playTurn` from memory.

**Rebuild in 8 minutes:** `Board` (two maps + `validate` + `applyJump`) · `Game.playTurn` (removeFirst → roll → overshoot? → jump → win? → addLast) · `Dice` + `FixedSequenceDice`.

---

**Files:** `Game` · `Board` · `Dice` / `StandardDice` / `FixedSequenceDice` · `Player` · `SnakeLadderDriver` (standard board, ladder, snake, overshoot, exact win, 3 bad boards, a full random game)
```bash
mvn -q compile exec:java -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.games.snakeladder.SnakeLadderDriver
```
