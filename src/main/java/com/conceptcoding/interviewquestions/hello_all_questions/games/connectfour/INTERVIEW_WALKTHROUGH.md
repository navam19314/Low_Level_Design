# Connect Four

> **Why it's in the deck:** a board game one step up from [Tic-Tac-Toe](../tictactoe/INTERVIEW_WALKTHROUGH.md): **gravity** (discs fall to the lowest empty row) and **four in a row** on a 6×7 board. A 25–30 minute question; the prep strategy rates it low-frequency, but the K-in-a-row check transfers to Gomoku-style follow-ups.
>
> **The crux (what's really being tested):**
> 1. **Gravity:** the player picks a **column**; the board finds the row.
> 2. **The win check through the last disc only:** 4 directions, counting outward both ways (O(K), not O(board)).
> 3. **Clean split:** `Board` (cells, gravity, lines) vs `ConnectFourGame` (turns, state, validation with reasons).
>
> **Family:** F6 Board / turn game. See [foundations](../../00_AMAZON_LLD_FOUNDATIONS.md).

---

## 1. Plain-language picture

### Dropping discs
```
 0 1 2 3 4 5 6        Red drops in column 3 → it falls to the bottom row.
| | | | | | | |       Yellow drops in column 3 → it lands on top of Red.
| | | | | | | |       A column with 6 discs is full: no more drops there.
| | | |Y| | | |
| | | |R| | | |
```
The player says **which column**; the board works out the row by scanning up from the bottom for the first empty cell.

### Did the last disc win?
Only the disc just dropped can create a new line of four. From it, look along 4 directions: horizontal, vertical and the two diagonals. For each, count matching discs going one way, plus the other way, plus the disc itself. If ≥ 4, it's a win.
```
direction "/":  count down-left (2) + up-right (1) + itself (1) = 4 → WIN
```
That's at most ~24 cells looked at, whatever the board size.

### Tic-Tac-Toe's counter trick doesn't work here
Counters detect **full** lines (all 3 cells of a row). Connect Four needs **4 consecutive** cells in a 7-wide row, and `R R Y R R R` has 5 reds but no four in a row. So: count outward from the last disc instead.

---

## 2. From story to design

| # | Decision | Rejected | Why |
|---|---|---|---|
| D1 | `makeMove(player, column)`; `Board.placeDisc(col)` returns the row it landed in | the caller choosing the row | Gravity is a board rule. |
| D2 | `canPlace(col)` = top cell empty | scanning the column | A column is full exactly when its top cell is filled. |
| D3 | Win check: 4 directions, count both ways from the last disc | scanning the whole board each move | O(K) per move instead of O(rows × cols × K). |
| D4 | `isFull()` = every top cell filled | scanning all cells | If the top is full, everything below is too (gravity). |
| D5 | Check **win before full** | full first | The last disc can both fill the board and win, and that's a win. |
| D6 | `makeMove` throws with the reason (not your turn / off the board / column full / game over) | silent `false` | The UI can show why; the same convention as Tic-Tac-Toe. |
| D7 | Board size configurable `(rows, cols)` | hard-coded 6×7 | Variants, and small boards for testing draws. |
| D8 | `makeMove` synchronized | no locking | Online play: both players' requests and double-clicks. |

### Class shape
```
ConnectFourGame                     ← turns, state, validation
  Board · playerRed · playerYellow · currentPlayer · GameState · winner
  makeMove(player, column) → row

Board                               ← cells + gravity + lines
  DiscColor[rows][cols] · DIRECTIONS {→, ↓, ↘, ↗}
  canPlace(col) · placeDisc(col, color) → row · checkWin(row, col, color) · isFull · printBoard

Player { name, DiscColor }   DiscColor { RED, YELLOW }   GameState { IN_PROGRESS, WON, DRAW }
```

---

## 3. Patterns that earn their place

| Pattern | When |
|---|---|
| **Strategy** (`MoveStrategy`: human / bot) | Q2: play against the computer. |
| **Enum state** (`GameState`) | 3 states, no per-state behaviour. |

**Say:** *"Board owns gravity and the line check through the last disc, 4 directions counted both ways. Game owns turns and validation. A bot would be a Strategy."*

**Tempting but wrong:** a `Cell`/`Column` class per cell (no behaviour), the State pattern, a Singleton game.

---

## 4. The 30-minute run

### Clarify (2 min)
> **You:** 6 rows × 7 columns, two players, Red first. You pick a column and the disc falls. Four in a row in any direction wins; full board = draw?
> **Interviewer:** Yes.
> **You:** Configurable size? Invalid moves with reasons?
> **Interviewer:** Nice to have; yes.

### Timeline
| Min | Do |
|---|---|
| 2–5 | Picture gravity and the 4-direction check. |
| 5–15 | **`Board`**: `canPlace`, `placeDisc`, `checkWin` + `countInDirection`, `isFull`. |
| 15–22 | **`ConnectFourGame.makeMove`** (checks with reasons → place → win? full? switch). |
| 22–25 | Enums + `Player`. |
| 25–30 | Dry run a diagonal win; follow-ups. |

### The code ([model/Board.java](model/Board.java), [ConnectFourGame.java](ConnectFourGame.java))
```java
public class Board {
    private static final int CONNECT = 4;
    private static final int[][] DIRECTIONS = { {0, 1}, {1, 0}, {1, 1}, {-1, 1} };   // →  ↓  ↘  ↗

    private final int rows, cols;
    private final DiscColor[][] grid;

    public Board(int rows, int cols) { this.rows = rows; this.cols = cols; this.grid = new DiscColor[rows][cols]; }

    public boolean canPlace(int col) { return col >= 0 && col < cols && grid[0][col] == null; }   // top empty = room

    public int placeDisc(int col, DiscColor color) {          // gravity: lowest empty row
        if (!canPlace(col)) return -1;
        for (int row = rows - 1; row >= 0; row--) {
            if (grid[row][col] == null) { grid[row][col] = color; return row; }
        }
        return -1;
    }

    public boolean isFull() {                                  // tops full ⇒ everything full
        for (int c = 0; c < cols; c++) if (grid[0][c] == null) return false;
        return true;
    }

    public boolean checkWin(int row, int col, DiscColor color) {
        for (int[] d : DIRECTIONS) {
            int count = 1 + countInDirection(row, col, d[0], d[1], color)
                          + countInDirection(row, col, -d[0], -d[1], color);    // both ways + the disc itself
            if (count >= CONNECT) return true;
        }
        return false;
    }

    private int countInDirection(int row, int col, int dr, int dc, DiscColor color) {
        int count = 0;
        int r = row + dr, c = col + dc;
        while (r >= 0 && r < rows && c >= 0 && c < cols && grid[r][c] == color) {
            count++;
            r += dr;
            c += dc;
        }
        return count;
    }
    // + getCell, getRows, getCols, printBoard
}

public class ConnectFourGame {
    private final Board board;
    private final Player playerRed, playerYellow;
    private Player currentPlayer;
    private GameState state = GameState.IN_PROGRESS;
    private Player winner;

    public ConnectFourGame(Player red, Player yellow, int rows, int cols) {
        if (red.getColor() != DiscColor.RED || yellow.getColor() != DiscColor.YELLOW) {
            throw new IllegalArgumentException("Players must be assigned RED and YELLOW respectively");
        }
        this.board = new Board(rows, cols);
        this.playerRed = red;
        this.playerYellow = yellow;
        this.currentPlayer = red;                              // Red starts
    }

    public synchronized int makeMove(Player player, int column) {
        if (state != GameState.IN_PROGRESS)           throw new IllegalStateException("Game is over: " + state);
        if (player != currentPlayer)                  throw new IllegalStateException("Not " + player.getName() + "'s turn");
        if (column < 0 || column >= board.getCols())  throw new IllegalArgumentException("Column " + column + " is off the board");
        if (!board.canPlace(column))                  throw new IllegalArgumentException("Column " + column + " is full");

        int row = board.placeDisc(column, player.getColor());
        if (board.checkWin(row, column, player.getColor())) {  // win BEFORE full
            state = GameState.WON;
            winner = player;
        } else if (board.isFull()) {
            state = GameState.DRAW;
        } else {
            currentPlayer = (player == playerRed) ? playerYellow : playerRed;
        }
        return row;
    }
    // + 6×7 convenience constructor, synchronized getters
}
```
**Shape to remember:** `makeMove` = 4 checks → `placeDisc` (gravity) → `checkWin` (4 directions × both ways) → full? → switch turn.

### Dry run: a "/" diagonal win
```
Columns R0 Y1 R1 Y2 R2 Y3 R2 Y3 R3 Y6 R3  (bottom row = 5)
Red ends with discs at (5,0) (4,1) (3,2) (2,3)
Last disc (2,3): direction ↗ means (-1,+1) → none; opposite ↙ (+1,-1) → (3,2) (4,1) (5,0) = 3
1 + 0 + 3 = 4 → WIN
```

---

## 5. Follow-ups: answer out loud first, then open

<details>
<summary><b>Q1. Connect K on an N×M board (e.g. Connect 5 on 9×9).</b></summary>

Make `CONNECT` a constructor parameter. The 4-direction check already works for any K, at O(K) per move. That's why counting outward beats hard-coded `[r][c] == [r][c+1] == ...` comparisons.
</details>

<details>
<summary><b>Q2. Play against the computer.</b></summary>

`MoveStrategy.chooseColumn(board, myColor)`. A simple bot: win if a column wins now; else block the opponent's winning column; else prefer the centre columns (more lines pass through them). Try each column by placing a disc, checking `checkWin`, then removing it again (this needs a `removeTop(col)` on the board). A strong bot uses **minimax with alpha-beta pruning** and depth limits, since Connect Four is too big to search fully in an interview.
</details>

<details>
<summary><b>Q3. Undo.</b></summary>

Keep a stack of played columns. Undo = pop the column, clear its **top** occupied cell (gravity means the last disc in a column is always the topmost), set the turn back, and reset the state to IN_PROGRESS. Much simpler than chess undo: there are no captures and no hidden flags.
</details>

<details>
<summary><b>Q4. Online play.</b></summary>

The same as Tic-Tac-Toe Q5: a `GameService` with `Map<gameId, game>`; the server maps the session to a player; `makeMove` is synchronized per game (the driver fires 20 simultaneous moves: 1 accepted); push each move to both players over a WebSocket.
</details>

<details>
<summary><b>Q5. How do you test it?</b></summary>

All four win directions (vertical, horizontal, `/`, `\`), each rejection (wrong turn, off the board, full column), a draw on a tiny 2×4 board, and 20 concurrent moves → 1 accepted. All are in `ConnectFourDriver`. The game has no I/O, so tests are plain method calls; `PlayGame` is the interactive console version.
</details>

---

## 6. Traps
1. Letting the player choose the row (forgetting gravity).
2. Scanning the whole board for a winner after every move.
3. Using Tic-Tac-Toe's full-line counters (they can't detect 4 consecutive in a 7-wide row).
4. Counting only one direction from the last disc (misses wins where it lands in the middle).
5. Checking full before win.

## 7. Recall check
1. How does `placeDisc` find the row? How do you know a column is full?
2. Write `checkWin` + `countInDirection` from memory.
3. Why doesn't Tic-Tac-Toe's counter trick work here?
4. Why must you count both ways in each direction?

**Rebuild in 8 minutes:** `Board` (`canPlace`, `placeDisc`, `checkWin`, `countInDirection`, `isFull`) · `ConnectFourGame.makeMove`.

---

**Files:** `ConnectFourGame` · `model/` (`Board`, `Player`, `DiscColor`, `GameState`) · `ConnectFourDriver` (4 win directions, rejections, draw, 20-thread double-click) · `PlayGame` (interactive console game)
```bash
mvn -q compile exec:java -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.games.connectfour.ConnectFourDriver
```
