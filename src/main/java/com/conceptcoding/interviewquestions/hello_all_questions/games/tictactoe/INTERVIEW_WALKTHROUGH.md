# Tic-Tac-Toe

> **Reported at Ethos** (frontend side). It's also a common 30-minute warm-up for backend rounds. Expect the follow-ups: *N×N*, *O(1) win check*, *play against a bot*, *undo*.
>
> **The crux (what's really being tested):**
> 1. **Clean responsibilities:** `Board` knows cells and lines; `Game` knows turns, rules and who won.
> 2. **The O(1) win check:** running counters per row, column and diagonal, not rescanning the board.
> 3. **Validation with reasons:** wrong turn, taken cell, off the board, game over.
>
> **Family:** F6 Board / turn game. See [foundations](../../00_AMAZON_LLD_FOUNDATIONS.md).

---

## 1. Plain-language picture

### The game
Two players take turns marking cells on a 3×3 grid; X goes first. Three of your marks in a row, column or diagonal wins. A full board with no winner is a draw.

### The counter trick (the senior signal)
Instead of looking at the board after every move, keep a **score per line**:
```
X adds +1, O adds −1 to: its row, its column, and the diagonal(s) it's on.
A line's score hits +3 → three X's in it. −3 → three O's. (For N×N: ±N.)

X at (0,0): row0 = 1, col0 = 1, diag = 1
X at (0,1): row0 = 2, col1 = 1
X at (0,2): row0 = 3  → X WINS. We only looked at 4 numbers.
```
**Why it works:** a line reaches ±N only if *every* cell in it has the same mark, because a single O in a row of X's pulls the score down.
- Only the **last move** can create a win, so only its 4 lines need checking.
- "Board full" is just `moves == N × N`.

### Who does what
```
Board:  the grid + the counters. "Is (r,c) empty? Place this mark. Did it complete a line?"
Game:   whose turn, game over?, winner, validates moves in order, switches turns
Player: name + symbol
```

---

## 2. From story to design

| # | Decision | Rejected | Why |
|---|---|---|---|
| D1 | `Board` (cells, lines) separate from `TicTacToeGame` (turns, state, winner) | one big class | Each class has one reason to change. N×N or K-in-a-row changes `Board`; turn rules change `Game`. |
| D2 | **Row/column/diagonal counters**, so the win check is O(1) | scanning the line (O(N)) or the board (O(N²)) | Constant time, and it generalises to N×N for free. |
| D3 | Only check lines through the **last move** | checking every line | Only the last move can create a win. |
| D4 | `isFull` = `moves == N²` | scanning for empty cells | O(1). |
| D5 | `makeMove` **throws with the reason** | returning a silent `false` | The UI can tell the user *why* ("not your turn" vs "cell taken"). |
| D6 | Validation order: game over → turn → bounds → empty | any order | The most general reason first; an off-board cell is checked before reading the grid (no array crash). |
| D7 | `GameState` enum: IN_PROGRESS / WON / DRAW | booleans `isOver`, `hasWinner` | Three states can't contradict each other the way two booleans can. |
| D8 | `makeMove` `synchronized` | no locking | An online game gets both players' requests (and double-clicks) on server threads. |
| D9 | `Symbol` enum X/O; `Player` = name + symbol | strings `"X"` | Type safety; `opposite()` helper. |

### Class shape
```
TicTacToeGame                       ← the rules
  Board board · Player playerX, playerO · currentPlayer · GameState state · winner
  makeMove(player, row, col) → GameState · reset · getters

Board                               ← the grid
  Symbol[][] grid · int[] rowSum, colSum · diagSum, antiDiagSum · moves
  isInside · isEmpty · placeAndCheckWin (O(1)) · isFull (O(1)) · reset · render

Player { name, Symbol }   Symbol { X, O; opposite() }   GameState { IN_PROGRESS, WON, DRAW }
```

---

## 3. Patterns that earn their place

| Pattern | In the base? | When |
|---|---|---|
| **Strategy** (`MoveStrategy`: human / bot) | No | Q2: "play against the computer". Human and bot players are swappable. |
| **Enum state** (`GameState`) | Yes | 3 states with no per-state behaviour, so an enum is enough. |

**Say:** *"It's small, so the signal is clean responsibilities and the O(1) win check, not patterns. If we add a computer player, its move choice becomes a Strategy."*

**Tempting but wrong:** State pattern for IN_PROGRESS/WON/DRAW (no per-state behaviour), Singleton game (many games at once online), Factory for players.

---

## 4. The 30-minute run

### Clarify (2 min)
> **You:** Two players, X first, 3×3. Should I make the size configurable?
> **Interviewer:** Yes, N×N would be nice.
> **You:** Win = a full row, column or diagonal; draw when full. Invalid moves rejected with a reason?
> **Interviewer:** Yes.
> **You:** Is it played online, with requests possibly arriving at the same time?
> **Interviewer:** Assume yes.

### Timeline
| Min | Do |
|---|---|
| 2–5 | Class shape. Say: *"Board vs Game responsibilities; counters for an O(1) win check."* |
| 5–15 | **`Board`** with counters. |
| 15–23 | **`TicTacToeGame.makeMove`** (validation order, state transitions, turn switch). |
| 23–26 | Enums + `Player`. |
| 26–30 | Dry run + follow-ups. |

### The code ([Board.java](Board.java), [TicTacToeGame.java](TicTacToeGame.java))
```java
public class Board {
    private final int size;
    private final Symbol[][] grid;
    private final int[] rowSum;
    private final int[] colSum;
    private int diagSum;          // cells where row == col
    private int antiDiagSum;      // cells where row + col == size - 1
    private int moves;

    public Board(int size) {
        if (size < 3) throw new IllegalArgumentException("Board size must be >= 3");
        this.size = size;
        this.grid = new Symbol[size][size];
        this.rowSum = new int[size];
        this.colSum = new int[size];
    }

    public boolean isInside(int row, int col) { return row >= 0 && row < size && col >= 0 && col < size; }
    public boolean isEmpty(int row, int col)  { return grid[row][col] == null; }

    // places the mark; returns true if this move completed a line. O(1).
    public boolean placeAndCheckWin(int row, int col, Symbol mark) {
        grid[row][col] = mark;
        moves++;
        int delta = mark == Symbol.X ? 1 : -1;
        rowSum[row] += delta;
        colSum[col] += delta;
        if (row == col) diagSum += delta;
        if (row + col == size - 1) antiDiagSum += delta;

        int target = size * delta;                    // +N for X, -N for O
        return rowSum[row] == target || colSum[col] == target
                || diagSum == target || antiDiagSum == target;
    }

    public boolean isFull() { return moves == size * size; }
    // + reset, getCell, getSize, render
}

public class TicTacToeGame {
    private final Board board;
    private final Player playerX;
    private final Player playerO;
    private Player currentPlayer;
    private GameState state = GameState.IN_PROGRESS;
    private Player winner;

    public TicTacToeGame(String nameX, String nameO, int size) {
        this.board = new Board(size);
        this.playerX = new Player(nameX, Symbol.X);
        this.playerO = new Player(nameO, Symbol.O);
        this.currentPlayer = playerX;                 // X always goes first
    }

    public synchronized GameState makeMove(Player player, int row, int col) {
        if (state != GameState.IN_PROGRESS) throw new IllegalStateException("Game is over: " + state);
        if (player != currentPlayer)        throw new IllegalStateException("Not " + player + "'s turn");
        if (!board.isInside(row, col))      throw new IllegalArgumentException("(" + row + "," + col + ") is off the board");
        if (!board.isEmpty(row, col))       throw new IllegalArgumentException("(" + row + "," + col + ") is taken");

        if (board.placeAndCheckWin(row, col, player.getMark())) {
            state = GameState.WON;
            winner = player;
        } else if (board.isFull()) {
            state = GameState.DRAW;
        } else {
            currentPlayer = (player == playerX) ? playerO : playerX;
        }
        return state;
    }

    public synchronized void reset() {
        board.reset();
        currentPlayer = playerX;
        state = GameState.IN_PROGRESS;
        winner = null;
    }
    // + synchronized getters
}
// enum Symbol { X, O; Symbol opposite() }   enum GameState { IN_PROGRESS, WON, DRAW }   Player { name, mark }
```
**Order matters:** check **win before full**. The 9th move can both fill the board *and* complete a line, and that's a win, not a draw.

### Dry run: O wins on the anti-diagonal
```
X(0,0): row0 +1, col0 +1, diag +1
O(0,2): row0 0,  col2 −1, anti −1          (0+2 == 2 → on the anti-diagonal)
X(1,0): row1 +1, col0 +2
O(1,1): row1 0,  col1 −1, diag 0, anti −2  (centre is on both diagonals)
X(2,2): row2 +1, col2 0,  diag +1
O(2,0): row2 0,  col0 +1, anti −3  → −3 == −N → O WINS
```

---

## 5. Follow-ups: answer out loud first, then open

<details>
<summary><b>Q1. Make it N×N.</b></summary>

Already done: `new TicTacToeGame("A", "B", 4)`. The counters arrays are sized N, and the target is ±N. Nothing else changes; that's why counters beat hard-coded `[0][0]==[0][1]==[0][2]` checks. (The driver has a 4×4 diagonal win.)
</details>

<details>
<summary><b>Q2. Play against the computer.</b></summary>

**Strategy** for choosing a move. The game doesn't care who decides; it just validates `makeMove`.
```java
public interface MoveStrategy {
    int[] chooseMove(Board board, Symbol me);            // {row, col}
}

// win if you can, else block the opponent's win, else centre, else the first free cell
public class SimpleBotStrategy implements MoveStrategy {
    public int[] chooseMove(Board board, Symbol me) {
        int[] win = findWinningCell(board, me);
        if (win != null) return win;
        int[] block = findWinningCell(board, me.opposite());
        if (block != null) return block;
        int mid = board.getSize() / 2;
        if (board.isEmpty(mid, mid)) return new int[] { mid, mid };
        for (int r = 0; r < board.getSize(); r++)
            for (int c = 0; c < board.getSize(); c++)
                if (board.isEmpty(r, c)) return new int[] { r, c };
        throw new IllegalStateException("Board is full");
    }

    // a free cell that would complete a full line for 'mark'
    private int[] findWinningCell(Board board, Symbol mark) {
        int n = board.getSize();
        for (int r = 0; r < n; r++)
            for (int c = 0; c < n; c++)
                if (board.isEmpty(r, c) && completesLine(board, r, c, mark)) return new int[] { r, c };
        return null;
    }

    private boolean completesLine(Board b, int row, int col, Symbol mark) {
        int n = b.getSize();
        boolean rowOk = true, colOk = true, diagOk = row == col, antiOk = row + col == n - 1;
        for (int i = 0; i < n; i++) {
            if (i != col && b.getCell(row, i) != mark) rowOk = false;
            if (i != row && b.getCell(i, col) != mark) colOk = false;
            if (diagOk && i != row && b.getCell(i, i) != mark) diagOk = false;
            if (antiOk && i != row && b.getCell(i, n - 1 - i) != mark) antiOk = false;
        }
        return rowOk || colOk || diagOk || antiOk;
    }
}
// game loop: int[] m = bot.chooseMove(game.getBoard(), Symbol.O); game.makeMove(game.getPlayerO(), m[0], m[1]);
```
A `HumanStrategy` reads the move from the UI. An unbeatable bot = **minimax** (try every move recursively, assume the opponent plays perfectly). That's fine for 3×3, but too slow for big boards without pruning.
</details>

<details>
<summary><b>Q3. Undo the last move.</b></summary>

Keep a stack of moves. Undo = pop, clear the cell, **subtract** the same delta from the same counters, `moves--`, give the turn back, and set the state back to IN_PROGRESS:
```java
private final Deque<int[]> history = new ArrayDeque<>();    // {row, col}; push in makeMove

public synchronized void undo() {
    if (history.isEmpty()) throw new IllegalStateException("Nothing to undo");
    int[] last = history.pop();
    Symbol mark = board.getCell(last[0], last[1]);
    board.clear(last[0], last[1], mark);                    // grid = null; counters −= delta; moves--
    currentPlayer = mark == Symbol.X ? playerX : playerO;   // that player moves again
    state = GameState.IN_PROGRESS;
    winner = null;
}
```
Counters are reversible, which is another reason to prefer them over a cached "winner found" flag.
</details>

<details>
<summary><b>Q4. Gomoku: 5 in a row on a 15×15 board.</b></summary>

Counters only detect *full* lines. For K-in-a-row, walk out from the last move in 4 directions and count matching cells. That's O(K) per move:
```java
static boolean wins(Board b, int row, int col, Symbol mark, int k) {
    int[][] directions = { {0, 1}, {1, 0}, {1, 1}, {1, -1} };    // →  ↓  ↘  ↙
    for (int[] d : directions) {
        int count = 1 + countFrom(b, row, col, d[0], d[1], mark) + countFrom(b, row, col, -d[0], -d[1], mark);
        if (count >= k) return true;
    }
    return false;
}

private static int countFrom(Board b, int row, int col, int dr, int dc, Symbol mark) {
    int count = 0;
    int r = row + dr, c = col + dc;
    while (b.isInside(r, c) && b.getCell(r, c) == mark) { count++; r += dr; c += dc; }
    return count;
}
```
Put it behind a `WinRule` interface (full-line vs K-in-a-row) if both variants must exist.
</details>

<details>
<summary><b>Q5. Online multiplayer: many games, two browsers per game.</b></summary>

`Map<gameId, TicTacToeGame>` in a `GameService` (a `ConcurrentHashMap`). Each request carries the gameId + playerId, and the server maps playerId → Player (never trust the client to say "I'm X"). `makeMove` is synchronized **per game**, so a double-click is rejected as "Not X's turn" (the driver fires 20 simultaneous moves: 1 accepted). After each move, push the new board to both players over a WebSocket.
</details>

<details>
<summary><b>Q6. Frontend angle: how would you model the UI state?</b></summary>

The server's game is the **source of truth**. The UI holds `{board, currentPlayer, state, winner}` from the last response. On click, send the move; render the response, or show the error message (which is why `makeMove` throws with a reason). Optionally update the board instantly and roll back if the server rejects the move. Disable clicks when it's not your turn or the game is over, but **still validate on the server**.
</details>

<details>
<summary><b>Q7. How do you test it?</b></summary>

Each winning line type (row, column, diagonal, anti-diagonal), a draw, the 9th move that wins (it must be WON, not DRAW), each rejection reason, N = 4, reset, and 20 concurrent moves → 1 accepted. All are in the driver.
</details>

---

## 6. Traps
1. Checking **full before win**: the 9th winning move is reported as a draw.
2. Scanning the whole board after every move.
3. Hard-coded 3×3 checks (`grid[0][0] == grid[0][1] == ...`).
4. Silent `false` returns with no reason.
5. Reading `grid[row][col]` before the bounds check.
6. Forgetting the centre (and, for odd N, the middle cell) is on **both** diagonals.

## 7. Recall check
1. Explain the counter trick in two sentences. Why can't one O hide in a winning X line?
2. Which counters does a move at (1,1) on 3×3 update?
3. Why win-check before full-check?
4. N×N: what changes in the code?
5. Bot: what are the 4 priorities of the simple strategy?

**Rebuild in 8 minutes:** `Board` with `rowSum/colSum/diag/anti/moves` + `placeAndCheckWin` · `makeMove` (4 checks → place → won? full? switch turn).

---

**Files:** `TicTacToeGame` · `Board` · `model/` (`Player`, `Symbol`, `GameState`) · `TicTacToeDriver` (row win, anti-diagonal win, draw, 4 rejections, 4×4, reset, 20-thread double-click)
```bash
mvn -q compile exec:java -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.games.tictactoe.TicTacToeDriver
```
