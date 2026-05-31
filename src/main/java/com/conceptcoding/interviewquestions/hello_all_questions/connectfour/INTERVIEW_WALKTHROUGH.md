# Connect Four — 45-min LLD Interview Walkthrough

**Target role:** SDE‑2 (Adobe, Microsoft, Amazon, Atlassian, etc.)
**Source method:** Hello Interview *Delivery Framework* applied to the *Connect Four* problem breakdown.

A 45-min round gives you ~10 extra minutes over the 35-min budget — spend most of it on Step 4 (more robust implementation) and Step 5 (extensibility — that's where SDE‑2 signal lives).

---

## Time budget (45 min)

| Step | Activity                              | Budget    | Cumulative |
| ---- | ------------------------------------- | --------- | ---------- |
| 1    | Requirements                          | ~5 min    | 5          |
| 2    | Entities & Relationships              | ~4 min    | 9          |
| 3    | Class Design (state + behavior)       | ~12 min   | 21         |
| 4    | Implementation (`makeMove`, win check, dry run) | ~16 min | 37         |
| 5    | Extensibility (2–3 follow-ups)        | ~7 min    | 44         |
| —    | Wrap & questions                      | ~1 min    | 45         |

Watch the clock at minute **5** (Step 1 done), minute **21** (start coding), and minute **37** (extensibility).

---

## Mental models — internalize these BEFORE you walk in

These two pictures unlock the implementation. If you can draw them from memory, you'll code `placeDisc` and `checkWin` in under 5 minutes.

### M1. Coordinate system & gravity

```
column index:  0  1  2  3  4  5  6
            +--+--+--+--+--+--+--+
   row 0 -> |  |  |  |  |  |  |  |   <- TOP row (last disc to land in a column)
   row 1 -> |  |  |  |  |  |  |  |
   row 2 -> |  |  |  |  |  |  |  |
   row 3 -> |  |  |  |  |  |  |  |
   row 4 -> |  |  |  |  |  |  |  |
   row 5 -> |  |  |  |  |  |  |  |   <- BOTTOM row (first disc lands here)
            +--+--+--+--+--+--+--+
                  gravity v

placeDisc(col, color):
   scan row = (rows-1) down to 0  -> first null cell wins -> place + return row
canPlace(col):
   only check grid[0][col]   (top row empty  =>  column has room)
isFull():
   ALL of row 0 occupied  =>  board is full
```

**Why this saves time:** every fast Connect Four answer relies on the invariant *"the top row is the last to fill"*. Once you draw it once, `canPlace` and `isFull` collapse to one-liners.

### M2. The 4 direction vectors used by `checkWin`

Only 4 lines pass through any newly-dropped disc. We count outward from it in BOTH directions along each axis, then add 1 for the disc itself.

```
         (-1,-1)      (-1, 0)      (-1,+1)
              \          |          /
               \         |         /
                \        |        /
   (0,-1)  <----- X (just-dropped disc) -----> (0,+1)
                /        |        \
               /         |         \
              /          |          \
         (+1,-1)      (+1, 0)      (+1,+1)


   axis              dir       reverse
   ------------      -------   --------
   horizontal        (0,+1)    (0,-1)
   vertical          (+1, 0)   (-1, 0)
   diagonal  \       (+1,+1)   (-1,-1)
   diagonal  /       (-1,+1)   (+1,-1)
```

So `DIRECTIONS = { (0,1), (1,0), (1,1), (-1,1) }` — only 4 entries. For each, count in `+dir` AND in `-dir`, sum + 1, compare to `CONNECT (=4)`.

**Why this saves time:** the alternative (4 separate `checkRow`/`checkCol`/`checkDiag1`/`checkDiag2` loops) is twice the code, twice the bug surface, and reads as junior-level. The vector trick is the single biggest "senior signal" in this problem — practice writing it from muscle memory.

---

## STEP 1 — Requirements (~5 min)

### What to say out loud (opener)
> "Before I start designing, let me clarify scope and rules so we're aligned on what 'done' looks like."

### Probe the 4 themes
Ask 5–7 focused questions, then **write the answers on the board**.

| Theme               | Question to ask                                                                                       |
| ------------------- | ----------------------------------------------------------------------------------------------------- |
| Primary capabilities| "Two players alternating turns, dropping discs into columns — disc falls to the lowest empty cell?"   |
| Rules / completion  | "Win on 4-in-a-row horizontal, vertical, OR diagonal? Draw when board fills?"                         |
| Error handling      | "Reject moves into full columns, moves after the game ends, and out-of-turn moves?"                   |
| Scope boundaries    | "Backend logic only — no UI, no networking, no persistence, single game instance — confirm?"          |

Also confirm board size: **"Standard 7 columns × 6 rows, right?"**

### What to write on the board

```
Requirements
1. Two players alternate turns, dropping discs into a 7×6 grid.
2. Discs fall to the lowest empty cell in the chosen column.
3. A player wins by connecting 4 discs horizontally, vertically, or diagonally.
4. Game ends in a draw when the board is full with no winner.
5. Invalid moves are rejected: full column, out-of-turn, or moves after game over.
6. System exposes ways to query game state, current player, and winner.

Out of Scope
- UI / rendering
- Networked multiplayer
- Persistence / replay / move history
- Configurable board dimensions (parameterize-ready, but not user-facing)
- AI opponent
- Concurrent games
```

### Close the step
> "Does this match what you had in mind? Anything you'd add before I move to entities?"

**Anti-patterns to avoid here:** silent assumptions, asking 20 questions, jumping into code in minute 2.

---

## STEP 2 — Entities & Relationships (~4 min)

### What to say out loud
> "From these requirements, the core nouns I see are **Game**, **Board**, and **Player**. The orchestrator is **Game** — it drives the turn loop and enforces workflow rules. **Board** owns the grid and the data-level rules like 'can a disc land here' and 'did this move complete four-in-a-row'. **Player** is identity + disc color."

### Filter you're applying (say this if asked why so few classes)
> "If a noun **maintains changing state or enforces rules**, it becomes a class. If it's just an attribute, it's a field. So `DiscColor` is an enum, not a class — it carries no behavior. A 'cell' is just an array slot, not its own class."

### What to write on the board

```
Entities
- Game   (orchestrator: turns, state transitions, validation)
- Board  (owns 6×7 grid, placement, win detection)
- Player (identity: name + DiscColor)

Enums
- DiscColor   { RED, YELLOW }
- GameState   { IN_PROGRESS, WON, DRAW }

Relationships
- Game  has-a  Board
- Game  has-a  Player × 2  (playerRed, playerYellow)
- Game  references currentPlayer + (optional) winner
- Board contains DiscColor[rows][cols] — not its own class
```

### Diagram — boxes and arrows (draw this, no formal UML)

```
                  +------------------------+
                  |         Game           |  <- orchestrator
                  |  (turns, state, rules) |
                  +------------------------+
                   |          |          |
            has-a  |   has-a  |   has-a  |  has-a (x2)
                   v          v          v
            +-----------+     |   +-----------------+
            |   Board   |     |   |     Player      |
            | 6x7 grid  |     |   | name, DiscColor |
            +-----------+     |   +-----------------+
                              |       ^         ^
                              |       | playerRed
                              |       | playerYellow
                              |       | (currentPlayer points to one)
                              v
                       +---------------+
                       |   GameState   |   <- enum
                       | IN_PROGRESS / |
                       |  WON / DRAW   |
                       +---------------+
```

Narrate while drawing: *"Game is the orchestrator. Board owns the grid and the data-rules. Player is just identity. `currentPlayer` is a reference to one of the two players — not a separate object."*

> **Tip:** narrate ownership: *"Rules about cell occupancy live on Board because Board owns the grid. Rules about turn order live on Game because Game owns whose turn it is."*

### Skip formal UML
If asked: *"Would simplified class notation work? It's faster and easier to discuss interactively."*

---

## STEP 3 — Class Design (~12 min)

### What to say out loud
> "I'll work top-down — Game first, then Board, then Player. For each class I'll derive state and behavior directly from a requirement."

### Game — state ↔ requirement table (write this if you have room)

| Requirement                              | State Game must track                |
| ---------------------------------------- | ------------------------------------ |
| Two players alternate                    | `playerRed`, `playerYellow`, `currentPlayer` |
| Need to detect end-of-game               | `state: GameState`                   |
| Reject moves after game ends             | `state` (re-used)                    |
| Report the winner                        | `winner: Player?`                    |
| Players act on a shared grid             | `board: Board`                       |

### Game — behavior table

| Need from requirements              | Method on Game                              |
| ----------------------------------- | ------------------------------------------- |
| Players make moves                  | `boolean makeMove(Player p, int column)`    |
| Ask whose turn it is                | `Player getCurrentPlayer()`                 |
| Check overall game state            | `GameState getGameState()`                  |
| See who won                         | `Player getWinner()` (null if none)         |
| Inspect the board                   | `Board getBoard()`                          |

### Game class outline (write this on the board)

```java
public class ConnectFourGame {
    // ----- State -----
    private final Board board;
    private final Player playerRed;
    private final Player playerYellow;
    private Player currentPlayer;
    private GameState state;
    private Player winner;

    public ConnectFourGame(Player red, Player yellow) {
        this.board = new Board();         // 6x7 by default
        this.playerRed = red;
        this.playerYellow = yellow;
        this.currentPlayer = red;
        this.state = GameState.IN_PROGRESS;
    }

    // ----- Behavior -----
    public boolean makeMove(Player p, int col) { /* Step 4 */ }
    public Player getCurrentPlayer()           { return currentPlayer; }
    public GameState getGameState()            { return state; }
    public Player getWinner()                  { return winner; }
    public Board getBoard()                    { return board; }
}
```

### Board — state + behavior outline

```java
public class Board {
    private final int rows, cols;
    private final DiscColor[][] grid;

    public Board() { this(6, 7); }
    public Board(int rows, int cols) { ... }

    public int placeDisc(int col, DiscColor color);  // returns row, or -1
    public boolean canPlace(int col);
    public boolean isFull();
    public boolean checkWin(int row, int col, DiscColor color);
    public DiscColor getCell(int r, int c);
    public int getRows(); public int getCols();
}
```

### Player

```java
public class Player {
    private final String name;
    private final DiscColor color;
    // ctor + getters
}
```

### Diagram — class cards (whiteboard-friendly summary)

```
+--------------------------------+   +------------------------------+   +--------------------+
|       ConnectFourGame          |   |            Board             |   |       Player       |
+--------------------------------+   +------------------------------+   +--------------------+
| - board: Board                 |   | - rows, cols: int            |   | - name: String     |
| - playerRed, playerYellow      |   | - grid: DiscColor[][]        |   | - color: DiscColor |
| - currentPlayer: Player        |   +------------------------------+   +--------------------+
| - state: GameState             |   | + placeDisc(c, color): int   |   | + getName()        |
| - winner: Player               |   | + canPlace(c): bool          |   | + getColor()       |
+--------------------------------+   | + isFull(): bool             |   +--------------------+
| + makeMove(p, c): bool         |   | + checkWin(r, c, color): bool|
| + getCurrentPlayer()           |   | + getCell(r, c): DiscColor   |
| + getGameState()               |   +------------------------------+
| + getWinner()                  |
| + getBoard()                   |
+--------------------------------+

       owns                              owns
ConnectFourGame ---------> Board           ConnectFourGame ---------> Player (x2)
```

### The principle to verbalize — "Tell, Don't Ask"
> "Game doesn't reach into Board's array. Game says `board.placeDisc(col, color)` and `board.checkWin(...)` — the Board tells it yes/no. Workflow rules ('is it your turn?', 'has the game ended?') live in Game because Game owns turn state. Data rules ('is this column full?', 'did you just complete a 4-in-a-row?') live in Board because Board owns the grid."

### Why an enum for `GameState` (one sentence)
> "Using an enum instead of two booleans makes invalid combinations like *won and drawn at the same time* literally unrepresentable."

---

## STEP 4 — Implementation (~16 min)

### Open by asking
> "Do you want pseudo-code or real Java? Are there specific methods you want me to focus on?"

Default: real Java for `makeMove` + `placeDisc` + `checkWin`, in that order — they're the heart of the system.

### 4.1 `makeMove` — guard clauses first, then happy path

**Control-flow sketch** (draw this before coding so the structure is fixed in your head):

```
        makeMove(player, col)
                |
                v
   +----------------------------+
   | state != IN_PROGRESS ?     |--yes--> return false   (game ended)
   +----------------------------+
                | no
                v
   +----------------------------+
   | player != currentPlayer ?  |--yes--> return false   (wrong turn)
   +----------------------------+
                | no
                v
   +----------------------------+
   | row = board.placeDisc(...) |
   | row == -1 ?                |--yes--> return false   (col full / OOB)
   +----------------------------+
                | no
                v
   +----------------------------+
   | board.checkWin(r, c, col)? |--yes--> state = WON;  winner = player
   +----------------------------+
                | no
                v
   +----------------------------+
   | board.isFull() ?           |--yes--> state = DRAW
   +----------------------------+
                | no
                v
   currentPlayer = the other one
                |
                v
            return true
```

```java
public boolean makeMove(Player player, int column) {
    // ----- Guard clauses (edge cases up front) -----
    if (state != GameState.IN_PROGRESS) return false;   // game already over
    if (player != currentPlayer)        return false;   // wrong player's turn

    int row = board.placeDisc(column, player.getColor());
    if (row == -1) return false;                         // invalid column / full

    // ----- Happy path -----
    if (board.checkWin(row, column, player.getColor())) {
        state = GameState.WON;
        winner = player;
    } else if (board.isFull()) {
        state = GameState.DRAW;
    } else {
        currentPlayer = (player == playerRed) ? playerYellow : playerRed;
    }
    return true;
}
```

> Narrate while writing: *"Returning early on invalid inputs flattens the method — the happy path stays unindented. Nested ifs hide the actual logic."*

### 4.2 `placeDisc` — gravity, bottom-up scan

```java
public int placeDisc(int col, DiscColor color) {
    if (col < 0 || col >= cols) return -1;
    if (!canPlace(col))         return -1;

    for (int row = rows - 1; row >= 0; row--) {
        if (grid[row][col] == null) {
            grid[row][col] = color;
            return row;
        }
    }
    return -1;
}

public boolean canPlace(int col) {
    return col >= 0 && col < cols && grid[0][col] == null; // top row empty ⇒ space exists
}
```

> *"`canPlace` only checks the top row — if it's free, the column has room. Cheaper than scanning."*

### 4.3 `checkWin` — directional-vector trick (the key signal)

> **Say this:** *"Rather than four separate methods for horizontal, vertical, and the two diagonals, I'll parameterize direction as a vector. The same counting logic handles all four — I count outward from the just-placed disc in both directions along each axis and look for a total of 4."*

```java
private static final int CONNECT = 4;
private static final int[][] DIRECTIONS = {
        { 0, 1 },   // horizontal
        { 1, 0 },   // vertical
        { 1, 1 },   // diagonal  \
        { -1, 1 }   // diagonal  /
};

public boolean checkWin(int row, int col, DiscColor color) {
    if (!inBounds(row, col) || grid[row][col] != color) return false;

    for (int[] d : DIRECTIONS) {
        int count = 1
                + countInDirection(row, col,  d[0],  d[1], color)
                + countInDirection(row, col, -d[0], -d[1], color);
        if (count >= CONNECT) return true;
    }
    return false;
}

private int countInDirection(int row, int col, int dr, int dc, DiscColor color) {
    int count = 0;
    int r = row + dr, c = col + dc;
    while (inBounds(r, c) && grid[r][c] == color) {
        count++; r += dr; c += dc;
    }
    return count;
}

private boolean inBounds(int r, int c) {
    return r >= 0 && r < rows && c >= 0 && c < cols;
}
```

> **Why this is the senior move:** *"We only check the four lines that pass through the disc we just dropped — O(1) lines, each ≤ 7 cells — instead of scanning the entire board after every move. And one vectorized routine instead of four near-duplicates."*

### 4.4 Verification — trace a concrete scenario (1–2 min)

Write this on the board to prove the code works:

```
Initial: empty 6×7 board, currentPlayer = RED, state = IN_PROGRESS

makeMove(RED,    3):  row=5, no win, switch → YELLOW
makeMove(YELLOW, 3):  row=4, no win, switch → RED
makeMove(RED,    4):  row=5, no win, switch → YELLOW
makeMove(YELLOW, 4):  row=4, no win, switch → RED
makeMove(RED,    5):  row=5, no win, switch → YELLOW
makeMove(YELLOW, 2):  row=5, no win, switch → RED
makeMove(RED,    6):  row=5
   checkWin(5,6,RED):
     horizontal (0,1):  left count = RED@(5,5)+RED@(5,4)+RED@(5,3) = 3
                        right count = 0
                        total = 1 + 3 + 0 = 4  ⇒  WIN
   state = WON, winner = RED   ✓

makeMove(YELLOW, 0):  state != IN_PROGRESS ⇒ returns false  ✓ (rejected correctly)
```

**Board state at the winning move** (sketch this — it makes the trace visual):

```
column:        0   1   2   3   4   5   6
            +---+---+---+---+---+---+---+
   row 0    |   |   |   |   |   |   |   |
   row 1    |   |   |   |   |   |   |   |
   row 2    |   |   |   |   |   |   |   |
   row 3    |   |   |   |   |   |   |   |
   row 4    |   |   |   | Y | Y |   |   |
   row 5    |   |   | Y | R | R | R | R |    <-- four RED in row 5,
            +---+---+---+---+---+---+---+        cols 3..6  ⇒  WIN

checkWin counts outward from (5,6) along axis (0, ±1):
   (5,5)=R  (5,4)=R  (5,3)=R   then (5,2)=Y → stop.
   left=3, right=0, +1 self = 4  ⇒  WIN.
```

If you spot a bug mid-trace, **fix it on the spot** — interviewers explicitly grade this as a positive signal.

---

## STEP 5 — Extensibility (~7 min)

This is where SDE‑2 signal is won. Pre-prepare 3 follow-ups. **You're pointing, not rewriting** — name the small additions; don't draft full classes unless asked.

### 5.1 "Make the board size configurable (NxM, Connect-K)"

> *"The Board constructor already accepts `rows, cols` — I just didn't expose it. I'd promote `CONNECT` from a constant to a constructor parameter. The win-check is direction-agnostic, so it already generalises to any K. Zero changes to Game."*

### 5.2 "Add undo"

> *"All state mutations flow through one method — `makeMove`. That's the only place I need to hook. I'd add a `MoveSnapshot` value object capturing (row, col, previousPlayer, previousState, previousWinner) and a `Deque<MoveSnapshot>` in Game. Before mutating in `makeMove`, push a snapshot. `undo()` pops, calls a new `board.clearCell(row, col)`, and restores the three fields. Board gains one method; Game gains two; Player and the win-check are untouched."*

Minimal sketch (only if asked to show):

```java
class MoveSnapshot {
    final int row, col;
    final Player previousPlayer;
    final GameState previousState;
    final Player previousWinner;
}

class ConnectFourGame {
    private final Deque<MoveSnapshot> history = new ArrayDeque<>();

    public boolean makeMove(Player p, int col) {
        // ... guards ...
        history.push(new MoveSnapshot(/* current pre-state */));
        // ... existing logic ...
    }

    public boolean undo() {
        if (history.isEmpty()) return false;
        MoveSnapshot s = history.pop();
        board.clearCell(s.row, s.col);
        currentPlayer = s.previousPlayer;
        state = s.previousState;
        winner = s.previousWinner;
        return true;
    }
}
```

### 5.3 "Add an AI opponent"

> *"I'd promote `Player` to an interface with `int chooseMove(GameView view)`. `HumanPlayer` reads from stdin/UI; `BotPlayer` runs a strategy (random, heuristic, minimax). Game stays untouched because it already calls `makeMove(player, column)` — it never cared *how* the column was decided. `GameView` is a read-only projection of the board so the bot can't cheat by mutating state directly."*

### 5.4 Other "what-if" answers (have one-liners ready)

| Follow-up                                  | Answer                                                                                              |
| ------------------------------------------ | --------------------------------------------------------------------------------------------------- |
| "Persist game state"                       | Inject a `GameRepository` interface via constructor (DIP). Save in `makeMove`; load via factory.    |
| "Notify subscribers when game ends"        | Observer pattern — Game publishes `GameEvent`s; UI / logger / analytics subscribe.                  |
| "Different win rules per variant"          | `WinStrategy` interface; inject into Board. Connect4 = 4-in-a-row; Gomoku = 5-in-a-row.             |
| "Spectator mode / replay"                  | Build on top of the undo `MoveSnapshot` stack — it's already a complete move log.                   |
| "Concurrent moves over network"            | Server-side single-threaded `makeMove`, or synchronize on Game. Out of scope for LLD though.        |

---

## Design Patterns — Hello Interview's canonical 8, and WHEN to mention each

The single biggest pattern mistake at SDE‑2 level isn't *not knowing* patterns — it's **forcing them into the wrong step**. Patterns volunteered in Step 1, 2, or 3 sound rehearsed; the same patterns named in Step 5 sound senior.

> **Hello Interview's stance** (worth memorizing): *"Patterns arise from good design decisions, not the other way around. Most interview designs use zero to two patterns maximum."*
>
> **Geography note (matters for you):** US interviews evaluate design quality without explicit pattern names. **India-based interviews (including Adobe/Microsoft/Amazon India) expect candidates to identify patterns by name.** Since you're interviewing in India, err on the side of **explicitly naming** the pattern when it fits — but still only when it fits.

### The 5-step timing rule (universal — applies to every LLD problem)

| Step                       | Use a pattern here?                                                                 | Why                                                                                          |
| -------------------------- | ----------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------- |
| **1. Requirements**        | **Never.**                                                                          | You're gathering scope. Naming patterns here = you've decided the design before clarifying. |
| **2. Entities**            | **Sometimes** — if a clear Strategy seam exists, declare the interface as an entity. | E.g., `SpotLookupStrategy` in Parking Lot, `DispatchStrategy` in Elevator — both belong here. |
| **3. Class Design**        | **YES, when the pattern earns rent in the base.** Name it explicitly.               | India-based interviews expect candidates to name patterns when applied. Don't artificially defer. For Connect Four specifically, the base doesn't need one — and that's OK. |
| **4. Implementation**      | **No new patterns.**                                                                | Implement what Step 3 designed.                                                              |
| **5. Extensibility**       | **YES — for *additional* patterns triggered by follow-up prompts.**                  | Every "what if..." maps to a pattern. Naming it = senior signal.                            |

> **Connect Four specifically:** the base design genuinely doesn't *need* a pattern — encapsulation + Information Expert do the work, the win-checking constant K=4 isn't pluggable until the interviewer asks for Connect-K. So your senior one-liner at the end of Step 3 is:
> *"The base design relies on Information Expert and Tell-Don't-Ask — no GoF pattern earns rent here yet. When extensibility prompts come, I'll point out where Strategy (Connect-K, AI player) and Memento (undo) would land."*
>
> Contrast with Parking Lot / Elevator where `SpotLookupStrategy` / `DispatchStrategy` ARE in the base — *those* problems have a pluggable policy from the start, Connect Four doesn't.

### Hello Interview's canonical 8 × interviewer trigger phrase

When the interviewer in Step 5 (or a senior-level Step 3 deep-dive) says something matching the **trigger**, name the **pattern**. That's how it sounds natural.

| # | Pattern              | Category   | Trigger phrase from interviewer                                                | What you say (one sentence)                                                                                       |
| - | -------------------- | ---------- | ------------------------------------------------------------------------------ | ----------------------------------------------------------------------------------------------------------------- |
| 1 | **Strategy** ⭐       | Behavioral | "different rules" · "variants" · "swap at runtime" · "piles of if/else by type" | *"Promote X to an interface; inject the concrete implementation. Replaces conditionals with polymorphism."*       |
| 2 | **Observer**         | Behavioral | "notify multiple" · "broadcast" · "event" · "when X happens, also do Y, Z, W"  | *"X publishes events; subscribers register independently. Decouples X from how the world reacts."*                |
| 3 | **State Machine**    | Behavioral | "behavior depends on state" · "complex transitions" · "state" repeated in reqs | *"Each state is its own class with its own transitions — and I'd draw the state diagram on the board to show it."* |
| 4 | **Factory** (Method) | Creational | "support different types" · "handle multiple variants" · creation logic varies | *"Centralize creation behind a factory method; callers stop depending on concrete classes."*                       |
| 5 | **Builder**          | Creational | "many optional fields" · "complicated construction" · "configuration object"   | *"Builder collects fields incrementally and validates on `build()` — beats a 12-arg constructor."*                 |
| 6 | **Singleton**        | Creational | "exactly one" · "global" · "shared resource"                                   | *"I'd resist textbook Singleton — it hurts tests and hides dependencies. **Inject a single instance via DI** instead."* |
| 7 | **Decorator**        | Structural | "optional features" · "stack behaviors" · "combine enhancements"               | *"Wrap X in decorators that each add one concern (logging, encryption, retry). Avoids the subclass explosion."*   |
| 8 | **Facade**           | Structural | "hide complexity" · "single entry point" · "wrap subsystem"                    | *"A clean facade over the messy parts. Orchestrators are usually facades — you're often already building one."*    |

> **⭐ Strategy is the #1 priority pattern.** HI's exact words: *"If you learn one pattern from this page, make it this one."* It directly tests polymorphism + composition — the core of OO.

### Three rules that make pattern mentions sound natural (not forced)

1. **Cap at 2 patterns** for the whole interview (HI's "zero to two maximum"). Beyond that = forcing. Pick the two strongest from the follow-ups you actually get.
2. **Always name the concrete win in the same breath.** *"Strategy here because win-rules need to swap at runtime"* > *"I'd use Strategy."* The win is what separates senior from "I memorized GoF."
3. **Never volunteer a pattern without a trigger.** Trigger = either an interviewer phrase from the table above, or a concrete need you can point to in your own design. Volunteering *"this could be Visitor"* mid-Step-3 is the #1 over-engineering red flag.

### How this maps to Connect Four specifically

**What's naturally present in the base design** — name these by *principle*, and for the two genuine patterns, by name:

- **Information Expert** (GRASP principle) — Board owns grid + `checkWin`; Game owns turn + state.
- **Tell, Don't Ask** (principle) — Game asks `board.checkWin(...)`, never peeks at the array.
- **Facade (#8)** — *ConnectFourGame is a facade* over `Board`, `Player`, and `GameState`. You're building this without trying. Name it once when you describe Game in Step 2: *"Game is the orchestrator — effectively a facade over Board and Player so callers only see one entry point."*
- **State Machine (#3) — lite** — `GameState` enum is a 3-state machine. Don't promote to full State pattern unless the interviewer adds states with materially different behavior. If they do (e.g., PAUSED, ABORTED with different `makeMove` semantics), then promote.

**What to invoke if the interviewer pulls the matching follow-up in Step 5:**

| Follow-up                                  | Pattern (HI's 8)             | Your line                                                                                            |
| ------------------------------------------ | ---------------------------- | ---------------------------------------------------------------------------------------------------- |
| "Different win rules (Connect-K / Gomoku)" | **Strategy (#1)** ⭐         | *"Promote win-checking to a `WinStrategy` interface; inject into Board. Same direction-vector code, different `CONNECT` value."* |
| "AI opponent / pluggable players"          | **Strategy (#1)** ⭐ on Player | *"`Player` becomes an interface with `chooseMove(GameView)`. `HumanPlayer` reads stdin; `BotPlayer` runs minimax. Game is unchanged."* |
| "Notify UI / logger / analytics when game ends" | **Observer (#2)**       | *"Game publishes `GameEvent`s; UI, analytics, logger subscribe. Decouples Game from how the world reacts."* |
| "Add PAUSED / ABORTED states with their own rules" | **State Machine (#3)** | *"Promote `GameState` to State classes — each owns its own `makeMove` behavior. I'd draw the state diagram before coding."* |
| "Support multiple game variants from one factory" | **Factory (#4)**        | *"`GameFactory.create(VariantKind)` returns the right Game subtype. Callers don't depend on concrete variants."* |

> **For "add undo":** HI's canonical 8 does NOT include Command or Memento. So **don't lead with those names** — describe the technique: *"All state mutations flow through `makeMove`, so I'd snapshot pre-state into a stack before mutating; `undo()` pops and restores."* If your interviewer prompts for the pattern name, *then* mention Memento — but it's not in the HI cheat sheet, so they may not be looking for it.

**Patterns to actively refuse if the interviewer baits you:**

- **Singleton on Board** — there's one Board per *game instance*, not one per process. Refuse politely: *"I'd avoid Singleton — it would block multi-game support and complicate tests. If we truly need one game per process, I'd DI a single instance."*
- **Builder for the no-arg `Board()` / 2-arg `Game(red, yellow)`** — academic. HI explicitly warns: *"most interview problems involve simple domain objects with 2-4 required fields where normal constructors suffice."*
- **Factory for `Player` / `DiscColor`** — only 2 colors, 2 players. A constructor is clearer.
- **Decorator on Player / Board** — wrong shape; no stackable optional behaviors here.

---

## Interview deep-dives — the questions you'll definitely get asked

This section covers the five follow-up questions almost every SDE‑2 LLD round asks once Steps 1–5 are done.

### 1. Complexity (Big-O) — have this table mentally ready

| Operation              | Time                                                                | Space                  | Notes                                                                              |
| ---------------------- | ------------------------------------------------------------------- | ---------------------- | ---------------------------------------------------------------------------------- |
| `makeMove(p, c)`       | `O(R + K)` ≈ **O(1)** for fixed 6×7, K=4                            | O(1)                   | Dominated by `placeDisc` + `checkWin`                                              |
| `placeDisc(c, color)`  | `O(R)`                                                              | O(1)                   | Bottom-up scan to find first empty cell                                            |
| `canPlace(c)`          | **O(1)**                                                            | O(1)                   | Top-row check only                                                                 |
| `isFull()`             | `O(C)`                                                              | O(1)                   | Top-row sweep — every column full ⇒ board full                                     |
| `checkWin(r, c, color)`| `O(4 × K)` = **O(1)** for fixed K=4                                 | O(1)                   | 4 directions × max K cells outward each way                                        |
| Storage (grid)         | —                                                                   | **O(R × C)** = O(42)   | The grid                                                                           |

> **Senior callout:** *"`checkWin` is O(1) for fixed K because we only inspect the 4 lines through the just-dropped disc, not the whole board. A naive 'scan every row + column + both diagonals' would be O(R × C) per move."*

### 2. Concurrency / thread-safety

For a turn-based local game it's single-threaded. For an online server hosting many games:

- **One game per thread (single-writer per game)** is the cleanest model — no locks, no races. A per-game executor receives moves from a queue.
- If you must share state across threads (e.g., reads from a spectator), use a simple lock or copy-on-read.

```java
public class ConnectFourGame {
    private final Object lock = new Object();

    public boolean makeMove(Player player, int column) {
        synchronized (lock) {
            // existing guard clauses + happy path
            return doMakeMoveUnlocked(player, column);
        }
    }

    public Player getCurrentPlayer() { synchronized (lock) { return currentPlayer; } }
    public GameState getGameState()  { synchronized (lock) { return state; } }
    // ... etc.
}
```

> **Senior callout:** *"For high throughput I'd skip locks entirely — one thread per game, moves arrive on a per-game queue. Lock-free by construction, and matches the natural turn-based semantics."*

### 3. Testing — what to write tests for

The base design is testable without I/O because Game/Board don't touch stdin — only `PlayGame.main` does.

| Test category    | Cases to cover                                                                                              |
| ---------------- | ----------------------------------------------------------------------------------------------------------- |
| Happy path       | Red wins horizontally / vertically / diag-`\` / diag-`/`; Yellow wins same set; Draw on a full no-winner board |
| Guard clauses    | Move after WON; move when state is DRAW; move when it's the other player's turn; move into a full column; move into out-of-bounds column |
| Win-check edges  | Exactly 4 in a row ⇒ WIN; only 3 ⇒ no win; 5+ same color ⇒ still WIN (counts the moment 4 form)             |
| State invariants | WON is terminal — no transitions out; DRAW is terminal; winner field is null iff state ≠ WON               |

```java
@Test
void redWinsOnHorizontalFour() {
    ConnectFourGame g = newGame();
    g.makeMove(red, 0);  g.makeMove(yellow, 0);
    g.makeMove(red, 1);  g.makeMove(yellow, 1);
    g.makeMove(red, 2);  g.makeMove(yellow, 2);
    assertTrue(g.makeMove(red, 3));
    assertEquals(GameState.WON, g.getGameState());
    assertEquals(red, g.getWinner());
}

@Test
void moveAfterWinIsRejected() {
    ConnectFourGame g = playToRedWin();
    assertFalse(g.makeMove(yellow, 5));
    assertEquals(red, g.getWinner());   // state didn't change
}

@Test
void fullColumnIsRejected() {
    ConnectFourGame g = newGame();
    fillColumn(g, /* col */ 3);                 // 6 alternating drops
    assertFalse(g.makeMove(g.getCurrentPlayer(), 3));
}
```

> **Senior callout:** *"The reason this is testable without mocks is that Game's only inputs are method arguments — no `Scanner`, no clock, no random. I/O lives in `PlayGame`."*

### 4. SOLID mapping (mention by letter when asked)

| Letter                       | Where it shows up                                                                                          |
| ---------------------------- | ---------------------------------------------------------------------------------------------------------- |
| **S** Single Responsibility  | Game = workflow rules. Board = grid + win-check. Player = identity. Three reasons to change → three classes. |
| **O** Open/Closed            | `CONNECT` and `DIRECTIONS` are constants — closed for modification. Win-checking opens for extension via a future `WinStrategy` interface (Connect-K, Gomoku). |
| **L** Liskov Substitution    | When Player becomes an interface, `HumanPlayer` and `BotPlayer` substitute cleanly — same `chooseMove(GameView)` contract. |
| **I** Interface Segregation  | Game exposes 5 narrow methods, not one fat `getState()`. Board exposes 4 focused queries instead of returning the grid. |
| **D** Dependency Inversion   | Game depends on `Board` (the abstraction), never on `int[][]`. When AI lands, Game depends on the `Player` interface, not concrete `HumanPlayer`. |

### 5. "Summarize your design in 30 seconds" — memorize this script

> *"Three classes: Game, Board, Player. Game is the orchestrator — effectively a facade over Board and Player exposing one mutation method, `makeMove`. Board owns the 6×7 grid and the placement and win-detection rules; it uses a direction-vector trick so `checkWin` is O(1) by inspecting only the 4 lines through the just-dropped disc. Player is identity plus DiscColor. `GameState` is an enum so 'won AND drawn' is unrepresentable. The base design is pattern-free — encapsulation and Information Expert do the work — and absorbs follow-ups like undo, Connect-K, AI player, or game-end notifications without rewrites."*

That's ~30 seconds. Hits: structure, the algorithmic insight (direction vectors), the senior framing (no forced patterns), and extensibility — in one breath.

---

## Closing soundbites (memorize these)

- **Opening:** *"Before I design, let me clarify scope and rules."*
- **Moving to entities:** *"From these requirements, the core entities are Game, Board, Player. The orchestrator is Game."*
- **Defending tell-don't-ask:** *"Win-checking belongs on Board because Board owns the grid. Game just asks `board.checkWin(...)` — it doesn't peek inside."*
- **Before coding:** *"Pseudo-code or real Java? Any specific methods you want me to focus on?"*
- **During verification:** *"Let me trace through a quick scenario to confirm this works."*
- **On extensibility:** *"All state mutations flow through `makeMove`, so adding X means snapshotting / hooking at that one point — the rest of the design doesn't change."*

---

## Top mistakes that lose points

- Diving into code in minute 2 — no requirements, no entity sketch.
- Forgetting the **out-of-scope list** → blindsided when interviewer probes UI / multiplayer / persistence.
- Designing four separate `checkWinHorizontal/Vertical/...` methods → screams duplication.
- Exposing `int[][] getGrid()` from Board so Game can scan it itself → encapsulation violation.
- Skipping the dry run — many interviewers grade verification explicitly.
- Pattern-stuffing (`Singleton` on Board, `Factory` for 2 player types) — only introduce a pattern when you can name what it concretely buys.
- Silent assumptions: *"I'll assume 6×7"* said out loud is fine. Just deciding 6×7 silently is a trap.
- Running over Step 3 — if you've spent 18 min on class design without a single method body, **abort and start coding**.

---

## Files in this folder (your reference implementation)

| File                                   | What it shows                                                              |
| -------------------------------------- | -------------------------------------------------------------------------- |
| `model/DiscColor.java`                 | Enum — RED / YELLOW                                                        |
| `model/GameState.java`                 | Enum — IN_PROGRESS / WON / DRAW (invalid states unrepresentable)           |
| `model/Player.java`                    | Identity: name + color (immutable)                                         |
| `model/Board.java`                     | Owns the grid; `placeDisc`, `canPlace`, `isFull`, `checkWin` (vectorized)  |
| `ConnectFourGame.java`                 | Orchestrator: `makeMove`, turn switching, state transitions                |
| `PlayGame.java`                        | Minimal driver — wires players + scanner loop                              |

Run from the project root:

```bash
mvn -q compile exec:java \
  -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.connectfour.PlayGame
```
