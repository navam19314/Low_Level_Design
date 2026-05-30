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
  -Dexec.mainClass=com.conceptcoding.interviewquestions.connectfour.PlayGame
```
