# Tic Tac Toe — 45-min LLD Interview Walkthrough

**Target role:** SDE-2 (Amazon, Adobe, Microsoft, Atlassian, etc.)

> TicTacToe is a **classic game-state-machine problem**. Three senior signals:
> (a) **clean separation of Board (physical state) vs Game (rules)** — same layering
> as Chess, (b) **O(N) win detection** scoped to the last move instead of O(N²) full
> scan, (c) **state-machine guard on makeMove** — one place rejects all illegal
> actions, no scattered null-checks.

---

## Time budget (45 min)

| Step | Activity                                                              | Budget  | Cumulative |
|------|-----------------------------------------------------------------------|---------|------------|
| 1    | Requirements (scope explicitly)                                       | ~5 min  | 5          |
| 2    | Entities & Relationships                                              | ~3 min  | 8          |
| 3    | Class Design (Board vs Game separation, win detection strategy)       | ~12 min | 20         |
| 4    | Implementation (makeMove + checkWin + edge cases + dry-run)          | ~15 min | 35         |
| 5    | Extensibility (N×N board, more players, AI, online play)             | ~7 min  | 42         |
| —    | Wrap & questions                                                      | ~3 min  | 45         |

---

## Step 1 — Requirements (~5 min)

**Say out loud:**

> "Before I design, let me clarify requirements so we both agree on scope."

### Questions to ask

| Question | Why it matters |
|----------|----------------|
| Fixed 3×3 or configurable N×N? | Changes `SIZE` from constant to constructor param |
| Always exactly 2 players (X, O)? | Extensibility to N players changes turn-cycling |
| Who goes first — always X, or configurable? | Small policy decision |
| Do we need undo / replay? | Adds move history stack |
| Do we need an AI opponent? | Out of scope for 45 min — defer |

### Final requirements (state these explicitly)

**In scope:**
- 3×3 board, 2 players (X always first, O second)
- Players alternate turns; move = place your symbol on any empty cell
- Win: first player to fill a complete row, column, or diagonal
- Draw: all 9 cells filled with no winner
- Reject invalid moves: occupied cell, out of bounds, game already over

**Out of scope:** undo, AI, persistence, configurable board size, >2 players

---

## Step 2 — Entities & Relationships (~3 min)

```
   Player ──plays──► TicTacToeGame ──owns──► Board
     │                     │
  (name, Symbol)     (turn order,         (3×3 grid of Symbol)
                      win/draw detection,
                      GameStatus)
```

**Core entities:**

| Entity | State it must track | Key behaviours |
|--------|---------------------|----------------|
| `Board` | 3×3 grid of Symbol (nullable) | mark, symbolAt, isFull, render |
| `Player` | name, Symbol (X or O) | — (value object, no behaviour) |
| `TicTacToeGame` | currentPlayer, GameStatus, winner | makeMove → validates + marks + detects win/draw |

**Enums:** `Symbol` (X, O), `GameStatus` (IN_PROGRESS, WON, DRAW)  
**Value objects:** `Position(row, col)` as a record — immutable coordinate

---

## Step 3 — Class Design (~12 min)

### 3a. Board — owns physical state only

```
Board
  - Symbol[][] grid                    // null = empty cell

  + mark(Position, Symbol)             // throws if occupied / OOB
  + symbolAt(Position) → Symbol        // null = empty
  + isFull() → boolean                 // O(N²) scan — called at most once per move
  + render() → String                  // for debug / demo
```

**Design decision:** Board does NOT know about turns or win conditions.
Tell the interviewer: *"Board's only job is 'what's on the grid'. Win detection
lives in Game, because Game owns the concept of 'a line belonging to a player'."*

### 3b. TicTacToeGame — owns rules

```
TicTacToeGame
  - Board board
  - Player playerX, playerO
  - Player currentPlayer
  - GameStatus status
  - Player winner                      // null until WON

  + makeMove(Position) → boolean       // main entry point
  - checkWin(Position, Symbol) → bool  // O(N) — only lines through last move
  - countInLine(sym, startR, startC, dR, dC) → int
```

### 3c. Win detection — the O(N) insight

**Naive approach (mention then dismiss):** after every move, scan all rows,
all columns, both diagonals — O(N²) per move.

**Better approach (what we implement):** A new move at (r, c) can only create
a winner via lines that pass through (r, c): its row, its column, and at most
two diagonals. Check only those — O(N) per move, N=3 so effectively O(1).

```
checkWin(pos, sym):
  check row  pos.row        →  countInLine(sym, pos.row, 0,       0, +1)
  check col  pos.col        →  countInLine(sym, 0,       pos.col, +1, 0)
  if pos.row == pos.col:
    check main diagonal     →  countInLine(sym, 0,       0,       +1, +1)
  if pos.row + pos.col == N-1:
    check anti-diagonal     →  countInLine(sym, 0,       N-1,     +1, -1)
```

**Senior soundbite:** *"I check only the four lines through the last move — the
winning line, if any, must include it. This is O(N) not O(N²)."*

### 3d. State machine in makeMove

```
makeMove(pos):
  guard: status != IN_PROGRESS  → throw IllegalStateException("Game is over")
  board.mark(pos, currentPlayer.symbol)  → throws if OOB or occupied
  if checkWin(pos, symbol):
    status = WON; winner = currentPlayer
  else if board.isFull():
    status = DRAW
  else:
    currentPlayer = other player
```

One entry point, one state transition. The guard at the top means "game over"
is enforced in a single place — callers never need to check before calling.

---

## Step 4 — Implementation (~15 min)

**Code in this order:**

1. `Symbol`, `GameStatus`, `Position`, `Player` — 3 min (enums + records)
2. `Board.mark`, `Board.symbolAt`, `Board.isFull` — 4 min
3. `TicTacToeGame.makeMove` + `checkWin` + `countInLine` — 6 min
4. Dry-run a win scenario out loud — 2 min

### Dry-run — say this out loud (X wins top row)

```
Move 1: X marks (0,0) → checkWin: row0 = 1, col0 = 1, diag = 1  → no win
Move 2: O marks (1,0) → checkWin: row1 = 1, col0 = 1+1=wait no, O checks only O symbols
Move 3: X marks (0,1) → checkWin: row0 = X,X,. = 2 → no win
Move 4: O marks (1,1) → no win
Move 5: X marks (0,2) → checkWin: row0 = X,X,X = 3 ✓ → WON, winner = Alice(X)
```

---

## Step 5 — Extensibility (~7 min)

### "What if we need an N×N board?"

Pass `int size` to constructor. Replace `Board.SIZE` constant with instance field.
`TicTacToeGame` passes it into `Board` and uses it in `checkWin`. Zero other changes.

### "What if we have more than 2 players?"

Replace the `playerX`/`playerO` pair + toggle with a `List<Player>` + index counter:

```java
private final List<Player> players;
private int currentIndex = 0;

// after a non-winning move:
currentIndex = (currentIndex + 1) % players.size();
```

Win detection stays the same — just check the symbol of whoever just moved.

### "What if we want an undo feature?"

Add a `Deque<Move> history` to TicTacToeGame. On undo: pop the last move,
set the cell back to null in Board, revert currentPlayer, revert status.
Board needs a new `unmark(Position)` method — one addition, no structural change.

### "What if we need to support online/concurrent play?"

`makeMove` becomes `synchronized` or uses a per-game `ReentrantLock`.
The state-machine guard (checking `status` before mutation) means the critical
section is already clearly bounded — easy to wrap in a lock.

---

## Common interviewer follow-ups

| Question | 30-second answer |
|----------|-----------------|
| Why separate Board from Game? | Single Responsibility: Board = "what's on the grid", Game = "is this move legal / who won". Easier to test each in isolation. |
| Why O(N) win detection? | Any winning line must include the last move — checking only lines through it is sufficient and avoids redundant work. |
| Can TicTacToeGame be extended to other board games? | Yes — swap Board for a chess/checkers board and override checkWin. The makeMove guard pattern and turn cycling are reusable. |
| Why use records for Position and Player? | They're pure value objects — immutable by construction, free equals/hashCode, no boilerplate. |
| Why throw exceptions instead of returning error codes? | Callers shouldn't need to check return values for contract violations — exceptions make illegal state un-ignorable. |

---

## Anti-patterns to avoid

| Don't do | Why |
|----------|-----|
| Win-check scans the whole board on every move | O(N²) — mention and dismiss, show O(N) |
| Null-check `status` everywhere in the caller | Use the state-machine guard in `makeMove` instead |
| Board decides whose turn it is | Board = physical state only; turn order is Game's responsibility |
| Use `int` for player identity | A `Player` record is clearer and prevents mixing up indices |
| Mutable Position | Position is a coordinate — should be immutable (record) |
