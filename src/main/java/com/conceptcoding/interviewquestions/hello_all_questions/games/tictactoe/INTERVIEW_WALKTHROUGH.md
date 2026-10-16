# Tic-Tac-Toe

> 25–30 min round. **Reported at Ethos** (frontend side), and a common warm-up for backend rounds. Phases: **Requirements (3) → Entities (2) → Class Design (5) → Implementation (10) → Extensibility (5)**.
> Expect these follow-ups: *N×N*, *O(1) win check*, *play against a bot*, *undo*.

---

## 0. The problem in plain words (read first, don't say it in the interview)

**You know the game.** Two players, X and O, take turns on a 3×3 grid. X goes first. Three in a row (a row, a column or a diagonal) wins. If the grid fills up with no winner, it's a draw.

**The whole system is 1 action:**
```
makeMove(X, row 0, col 2)  →  check it's legal → place it → won? draw? → otherwise the other player's turn
```

**Real world → code**
| In the real world | In the code |
|---|---|
| The grid | `Board`: an N×N array of marks |
| X and O | `Symbol` enum (the player *is* the symbol) |
| "Still playing / someone won / draw" | `GameState` enum |
| The referee: whose turn, is it over? | `TicTacToeGame` |

**Only 2 things are tricky:**
1. **Check the win cheaply.** Don't scan the whole board. Only the lines through the **last move** can have changed: its row, its column and the two diagonals. That's one loop.
2. **Reject illegal moves, with the reason:** the game is over, it's not your turn, off the board, the cell is taken.

**Picture this run before you start coding:**
```
X(0,0)  O(1,0)  X(0,1)  O(1,1)  X(0,2)   → X X X on the top row → WON, winner X
O tries (2,2) now                        → "Game is over"
O tries to move first in a new game      → "Not O's turn"
9 moves, no line                         → DRAW
```

---

## 1. Requirements (~3 min)

**Ask:** 3×3 only, or N×N? Two humans, or against a bot? Undo?

```
1. Two players, X first, alternating turns on an N×N board (N ≥ 3)
2. makeMove(player, row, col): reject an out-of-turn move, off the board, a taken cell, or after the game ends
3. Detect a win (full row, column or diagonal) and a draw (board full)
4. Safe with a double click (two requests at once)

Out of scope: O(1) win check, bot, undo, Gomoku, scores
```

---

## 2. Entities & Relationships (~2 min)

```
TicTacToeGame   turn, state, winner      ← the referee
   └── Board    grid, move count         ← the grid + "is this line complete?"
Symbol (X, O)   GameState (IN_PROGRESS, WON, DRAW)
```
- **The Board knows nothing about turns; the Game knows nothing about cells.** That's the one design point to say out loud.
- **No Player class:** a player is just their symbol. Add names when the interviewer asks (Extension 2).

---

## 3. Class Design (~5 min)

```
TicTacToeGame   makeMove(player, row, col) → GameState · getTurn · getState · getWinner
Board           place(row, col, mark) · hasWon(row, col, mark) · isFull()
Symbol          X, O
GameState       IN_PROGRESS, WON, DRAW
```
- **Thread safety:** `makeMove` is `synchronized`, so a double click can't make two moves.

### Design patterns (how to say it)
**None in the base, on purpose.** *"It's two small classes with clear jobs. A pattern would just add classes."*
**When one appears:** a bot is a **Strategy** (`MoveStrategy`: random, win-or-block, minimax), in Extension 4.
**Not used:** the State pattern (the game state is just IN_PROGRESS / WON / DRAW, with no per-state behaviour) and Singleton (each game is its own object).

---

## 4. Implementation (~10 min)

**Write it in this order:** `Symbol`, `GameState` → `Board` (`hasWon` is the crux) → `TicTacToeGame.makeMove`.

```java
public enum Symbol { X, O }
public enum GameState { IN_PROGRESS, WON, DRAW }

public class Board {
    private final int size;
    private final Symbol[][] grid;                          // null = empty
    private int moves;

    public Board(int size) {
        if (size < 3) throw new IllegalArgumentException("Board size must be at least 3");
        this.size = size;
        this.grid = new Symbol[size][size];
    }

    public void place(int row, int col, Symbol mark) {
        if (row < 0 || row >= size || col < 0 || col >= size) throw new IllegalArgumentException("(" + row + "," + col + ") is off the board");
        if (grid[row][col] != null) throw new IllegalArgumentException("(" + row + "," + col + ") is taken");
        grid[row][col] = mark;
        moves++;
    }

    // only the lines through (row, col) can have changed: its row, its column, the 2 diagonals
    public boolean hasWon(int row, int col, Symbol mark) {
        boolean rowWin = true, colWin = true, diagWin = true, antiDiagWin = true;
        for (int i = 0; i < size; i++) {
            if (grid[row][i] != mark) rowWin = false;
            if (grid[i][col] != mark) colWin = false;
            if (grid[i][i] != mark) diagWin = false;
            if (grid[i][size - 1 - i] != mark) antiDiagWin = false;
        }
        return rowWin || colWin || diagWin || antiDiagWin;
    }

    public boolean isFull() { return moves == size * size; }
}
```

```java
public class TicTacToeGame {
    private final Board board;
    private Symbol turn = Symbol.X;                         // X always goes first
    private GameState state = GameState.IN_PROGRESS;
    private Symbol winner;

    public TicTacToeGame(int size) { this.board = new Board(size); }

    public synchronized GameState makeMove(Symbol player, int row, int col) {
        if (state != GameState.IN_PROGRESS) throw new IllegalStateException("Game is over: " + state);
        if (player != turn) throw new IllegalStateException("Not " + player + "'s turn");
        board.place(row, col, player);                      // throws if off the board or taken

        if (board.hasWon(row, col, player)) {
            state = GameState.WON;
            winner = player;
        } else if (board.isFull()) {
            state = GameState.DRAW;
        } else {
            turn = (turn == Symbol.X) ? Symbol.O : Symbol.X;
        }
        return state;
    }
    // getters: getTurn, getState, getWinner
}
```
**Why the order matters:** check for a win **before** a draw. The last move can fill the board *and* win.

**Verify out loud:** walk through the run from section 0. Say: *"`hasWon` is O(N): it checks 4 lines of N cells. If they want O(1), I'd keep running counters per line (Extension 1)."*

---

## 5. Extensibility (~5 min)

Name **which class changes**, write the few new lines, and don't redesign the rest.

**Quick revision:**

| # | Interviewer asks | One-line answer |
|---|---|---|
| 1 | "Make the win check O(1)" | Keep a counter per row, column and diagonal: X adds +1, O adds −1; ±N means a win |
| 2 | "Player names, scores over many games" | A `Player` (name, symbol, wins) + a new game each round |
| 3 | "Undo the last move" | A stack of moves; pop, clear the cell, give the turn back |
| 4 | "Play against the computer" | Strategy: `MoveStrategy` (first empty → win-or-block → minimax) |
| 5 | "Gomoku: 5 in a row on 15×15" | Count matching marks in 4 directions from the last move |
| 6 | "Thousands of online games" | One `TicTacToeGame` per game id; each locks only itself |

---

### 1. "Make the win check O(1)"
**Idea:** X adds **+1** and O adds **−1** to the counter of its row, its column, and each diagonal it's on. A line is won the moment its counter reaches **+N** (all X) or **−N** (all O).
```java
// Board: new fields
private final int[] rowSum = new int[size], colSum = new int[size];
private int diagSum, antiDiagSum;

public boolean placeAndCheckWin(int row, int col, Symbol mark) {
    grid[row][col] = mark;
    int delta = mark == Symbol.X ? 1 : -1;
    rowSum[row] += delta;
    colSum[col] += delta;
    if (row == col) diagSum += delta;
    if (row + col == size - 1) antiDiagSum += delta;

    int target = size * delta;                            // +N for X, −N for O
    return rowSum[row] == target || colSum[col] == target || diagSum == target || antiDiagSum == target;
}
```
**Check:** X on the top row → rowSum[0] = +3 → win · X, O, X in a row → +1 −1 +1 = +1 → no win.
**Say:** *"A mixed line can never reach ±N, because one O cancels an X. So a counter hitting ±N means the whole line is one player."*

---

### 2. "Player names and scores over many games"
```java
public class Player {
    private final String name;
    private final Symbol symbol;
    private int wins;
    public void addWin() { wins++; }
    // constructor, getters
}

// Match: two Players + a fresh TicTacToeGame each round
// after a game: if (game.getState() == WON) playerFor(game.getWinner()).addWin();
// next round: swap who plays X, so both get to go first
```
**Say:** *"The game still only knows symbols. Names and scores are a layer on top, so the game doesn't change."*

---

### 3. "Undo the last move"
```java
// TicTacToeGame
private final Deque<int[]> history = new ArrayDeque<>();      // {row, col}
// makeMove(): after board.place(...) → history.push(new int[] { row, col });

public synchronized void undo() {
    if (history.isEmpty()) throw new IllegalStateException("Nothing to undo");
    int[] last = history.pop();
    board.clear(last[0], last[1]);                             // grid[r][c] = null; moves--
    if (state == GameState.IN_PROGRESS) {
        turn = (turn == Symbol.X) ? Symbol.O : Symbol.X;       // give the turn back
    }                                                          // (a winning/drawing move never switched it)
    state = GameState.IN_PROGRESS;                             // undoing the last move reopens the game
    winner = null;
}
```
**Check:** X(0,0), O(1,1), undo → (1,1) is empty and it's O's turn again · undo X's winning move → IN_PROGRESS, X to move again · undo on an empty history → error.

---

### 4. "Play against the computer"
**Idea:** how the computer picks a move is a rule that gets smarter over time, so it's a **Strategy**.
```java
public interface MoveStrategy {
    int[] chooseMove(Symbol[][] grid, Symbol me);              // {row, col}
}

public class WinOrBlockStrategy implements MoveStrategy {
    public int[] chooseMove(Symbol[][] grid, Symbol me) {
        Symbol them = (me == Symbol.X) ? Symbol.O : Symbol.X;
        int[] win = completingMove(grid, me);                  // 1. can I win right now?
        if (win != null) return win;
        int[] block = completingMove(grid, them);              // 2. must I block them?
        if (block != null) return block;
        return firstEmpty(grid);                               // 3. otherwise, anything
    }

    // try each empty cell: would this mark complete a line there?
    private int[] completingMove(Symbol[][] grid, Symbol mark) {
        for (int r = 0; r < grid.length; r++) {
            for (int c = 0; c < grid.length; c++) {
                if (grid[r][c] != null) continue;
                grid[r][c] = mark;                             // try it
                boolean wins = lineDone(grid, r, c, mark);     // the same loop as Board.hasWon
                grid[r][c] = null;                             // undo the try
                if (wins) return new int[] { r, c };
            }
        }
        return null;
    }
}
// computer's turn: int[] m = strategy.chooseMove(board.copyOfGrid(), O);  game.makeMove(O, m[0], m[1]);
```
**Check:** X has (0,0) and (0,1) → the computer (O) blocks at (0,2) · when O can complete a line, it takes the winning move.
**Say:** *"Easy, medium and hard are three strategies. Hard is minimax: try every move, and assume the opponent plays perfectly."*

---

### 5. "Gomoku: 5 in a row on a 15×15 board"
**Idea:** a full line is no longer needed, just **K in a row**. From the last move, count matching marks in both directions along each of the 4 lines.
```java
public boolean hasWon(int row, int col, Symbol mark) {          // k = 5
    int[][] directions = { {0, 1}, {1, 0}, {1, 1}, {1, -1} };    // row, column, diagonal, anti-diagonal
    for (int[] d : directions) {
        int count = 1 + countFrom(row, col, d[0], d[1], mark) + countFrom(row, col, -d[0], -d[1], mark);
        if (count >= k) return true;
    }
    return false;
}

private int countFrom(int row, int col, int dr, int dc, Symbol mark) {   // walk one way until a different mark
    int count = 0;
    int r = row + dr, c = col + dc;
    while (r >= 0 && r < size && c >= 0 && c < size && grid[r][c] == mark) {
        count++;
        r += dr;
        c += dc;
    }
    return count;
}
```
**Check:** 4 X's with a gap → no win · fill the gap → win · works along the edges and on the anti-diagonal.
**Say:** *"Only `hasWon` changes. This version also works for normal tic-tac-toe with k = N."*

---

### 6. "Thousands of online games"
```java
private final Map<String, TicTacToeGame> games = new ConcurrentHashMap<>();

public GameState move(String gameId, Symbol player, int row, int col) {
    return games.get(gameId).makeMove(player, row, col);      // synchronized on THAT game only
}
```
**Say:** *"Each game has its own lock, so games never wait for each other. Across servers, I'd route all moves for one game to the same server (by game id), or store the board with a version number and reject stale moves."*

---

```bash
mvn -q compile exec:java -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.games.tictactoe.TicTacToeDriver
```
