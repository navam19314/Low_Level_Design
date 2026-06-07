# Tic Tac Toe — 45-min Interview Script

---

## Time budget

| Step | What you do                        | Time   | Clock |
|------|------------------------------------|--------|-------|
| 1    | Requirements                       | 5 min  | 0–5   |
| 2    | Entities & relationships           | 3 min  | 5–8   |
| 3    | Class design (state + behavior)    | 12 min | 8–20  |
| 4    | Code + dry-run                     | 18 min | 20–38 |
| 5    | Extensibility                      | 5 min  | 38–43 |
| —    | Buffer / questions                 | 2 min  | 43–45 |

---

## Step 1 — Requirements (say this, 5 min)

> "Before I design anything, let me confirm scope."

**Ask the interviewer:**
- Fixed 3×3 or configurable N×N?
- Always 2 players, or can there be more?
- Do we need undo / AI opponent / network play?

**Then state out loud:**

```
IN SCOPE
1. Two players alternate placing X and O on a 3x3 grid
2. Player wins via completed row, column, or diagonal
3. Draw when all cells filled with no winner
4. Invalid moves rejected — occupied cell, out-of-bounds, wrong player, game over
5. Query game state and reset for a new game

OUT OF SCOPE
- UI / rendering
- AI opponent
- Network / multiplayer
- Undo / redo
- Variable board size
```

> "I'll design to these five requirements. Everything else I'll call out in Step 5."

---

## Step 2 — Entities & Relationships (3 min)

> "Three entities — anything that owns changing state or enforces rules gets its own class."

```
Game
  ├── Board          (owns the 3×3 grid)
  ├── Player × 2     (name + symbol — immutable value object)
  └── GameState      (IN_PROGRESS / WON / DRAW)
```

Sketch this on the whiteboard/notepad. No UML needed — boxes and arrows.

> "Game orchestrates everything. Board owns the physical grid.
>  Player is a pure value object — it holds a name and a mark, nothing else."

---

## Step 3 — Class Design (12 min)

### Map requirements → state (do this out loud)

| Requirement | What Game must track |
|-------------|----------------------|
| Two players alternate, X first | playerX, playerO, currentPlayer |
| 3×3 grid holds marks | Board |
| Win via row / col / diagonal | GameState, winner — Board detects the line |
| Draw when full, no winner | GameState (DRAW) |
| Invalid moves rejected | guards in makeMove |
| Query + reset | getters, reset() |

### Derive behavior from state

```
TicTacToeGame
  makeMove(player, row, col) → boolean   ← core action
  getCurrentPlayer()         → Player
  getGameState()             → GameState
  getWinner()                → Player    (null until WON)
  getBoard()                 → Board
  reset()

Board
  canPlace(row, col)         → boolean   ← guard before marking
  placeMark(row, col, mark)              ← mutate after guard passes
  checkWin(row, col, mark)   → boolean   ← O(N) — only lines through last move
  isFull()                   → boolean
  getCell(row, col)          → Symbol    ← query / render
  reset()
```

### Mention the O(N) insight here (senior signal)

> "For checkWin I'll only check the four lines that pass through the cell just
>  placed — row, column, and up to two diagonals. A winning line must include
>  the last move, so scanning the whole board is wasted work. That's O(N) per
>  move, not O(N²)."

---

## Step 4 — Code (18 min)

### Order to write (do it in this order)

1. **Enums + models** (3 min) — `Symbol`, `GameState`, `Player`
2. **Board** (7 min) — `canPlace`, `placeMark`, `checkWin`, `isFull`, `reset`, `getCell`
3. **TicTacToeGame** (5 min) — constructor, `makeMove`, `reset`, getters
4. **Driver dry-run** (3 min) — trace X wins scenario out loud while you write it

### makeMove — write this and narrate it

```java
public boolean makeMove(Player player, int row, int col) {
    if (state != GameState.IN_PROGRESS) return false;   // game already over
    if (player != currentPlayer)        return false;   // wrong turn
    if (!board.canPlace(row, col))      return false;   // occupied or OOB

    board.placeMark(row, col, player.mark());

    if (board.checkWin(row, col, player.mark())) {
        state  = GameState.WON;
        winner = player;
    } else if (board.isFull()) {
        state  = GameState.DRAW;
    } else {
        currentPlayer = (player == playerX) ? playerO : playerX;
    }

    return true;
}
```

> "Three guards at the top — each maps directly to a requirement.
>  After marking, I check win first, then draw, then just flip the turn.
>  The method returns false for any invalid input — callers don't need
>  to check state before calling."

### Dry-run out loud (do this after writing makeMove)

```
Move 1: Alice(X) → (0,0)   row0=1, col0=1, diag=1   → no win
Move 2: Bob(O)   → (1,0)   row1=1, col0=1+1=2        → no win
Move 3: Alice(X) → (0,1)   row0=2                    → no win
Move 4: Bob(O)   → (1,1)   row1=2, diag=1            → no win
Move 5: Alice(X) → (0,2)   row0=3 ✓                  → WON, winner=Alice(X)
```

---

## Step 5 — Extensibility (5 min)

Answer these before the interviewer asks.

**"What if the board is N×N?"**
> "Pass `int size` to the constructor. Replace the `SIZE` constant with an
>  instance field on Board. Everything else stays the same."

**"What if there are more than 2 players?"**
> "Replace the `playerX`/`playerO` pair with a `List<Player>` and an index.
>  After each non-winning move: `currentIndex = (currentIndex + 1) % players.size()`.
>  Win detection doesn't change — it checks the symbol of whoever just moved."

**"What if we need undo?"**
> "Add a `Deque<int[]> history` — push (row, col) on each move.
>  Undo: pop the last entry, set that cell to null on Board, revert
>  currentPlayer and state. One new Board method (`unmark`), no structural change."

---

## What NOT to do

| Don't | Why |
|-------|-----|
| Put win detection on Game | It needs to read every cell — Board owns the grid, so checkWin belongs on Board |
| Return void from makeMove | Caller needs to know if the move succeeded |
| Throw exceptions for invalid moves | Return false — it's not an exceptional condition, it's normal gameplay |
| Global board scan on every move | O(N) scoped to last move is the right answer — say it out loud |
| Skip the dry-run | Walking through a scenario is how you prove correctness in 45 min |
