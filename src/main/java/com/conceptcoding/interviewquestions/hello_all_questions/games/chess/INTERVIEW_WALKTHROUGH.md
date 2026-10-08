# Chess

> **Why it's in the deck:** the polymorphism question. Six piece types, each with its own movement rule, and a board that **never** asks "what kind of piece is this?". Plus game rules (turns, check, checkmate). It's big, so the skill is **scoping**: piece moves + turn order + "can't leave your king in check" + checkmate; castling, en passant and promotion as follow-ups.
>
> **The crux (what's really being tested):**
> 1. **Polymorphism:** `piece.isValidMove(move, board)`, one subclass per piece, no type `switch`.
> 2. **Layered rules:** *piece* geometry (on the piece) vs *game* rules (turn, king safety) on the game.
> 3. **Check detection** by trying the move, looking, and undoing, with the undo leaving **no trace**.
>
> **Family:** F6 Board / turn game. See [foundations](../../00_AMAZON_LLD_FOUNDATIONS.md) and the simpler [Tic-Tac-Toe](../tictactoe/INTERVIEW_WALKTHROUGH.md).

---

## 1. Plain-language picture

### Three layers of "is this move allowed?"
```
1. Is it your piece, and your turn?                       → ChessGame
2. Can THIS kind of piece move like that?                  → the piece itself (Knight: L-shape, Rook: straight, path clear)
3. After the move, is your own king safe?                  → ChessGame: try it, look, undo
```
Each layer is owned by the class that knows the rule. A knight knows how knights move; it doesn't know whose turn it is.

### Polymorphism instead of a giant switch
```java
// ✗ the switch every interviewer dreads
switch (piece.type) { case KNIGHT: ...; case ROOK: ...; case BISHOP: ...; ... }

// ✓ ask the piece
if (!piece.isValidMove(move, board)) reject();
```
Add a fairy piece tomorrow (a "Camel" that jumps 3+1)? Write one class. Nothing else changes.

### "Would this leave my king in check?"
You can't tell by looking at the move alone: moving a pinned bishop exposes your king to a rook behind it. So **pretend**: make the move on the board, ask "is my king attacked now?", then **undo** it exactly. The undo must leave **no trace**. This deck had a real bug here: the pretend move marked pawns as "already moved", so Black's a-pawn lost its two-square first move after White's opening move (now fixed and covered by a driver test).

### Checkmate vs stalemate
After your move, does the opponent have **any** legal move?
- No legal move, and in check → **checkmate**.
- No legal move, not in check → **stalemate** (a draw).

---

## 2. From story to design

| # | Decision | Rejected | Why |
|---|---|---|---|
| D1 | Abstract `Piece` with `isValidMove(move, board)`; one subclass per type | a `PieceType` enum + switch | Each piece owns its rule; a new piece is a new class. |
| D2 | Piece checks **geometry + path + not capturing its own colour** only | pieces checking turn and check | Separation: piece rules vs game rules. |
| D3 | `PieceHelpers.pathIsClearAndTargetCapturable` shared by Rook/Bishop/Queen | copy-pasted path loops | Sliding pieces share one walk; Knight jumps; King moves 1 square. |
| D4 | `Board` = 8×8 array + `applyMove` / `undoMove` / `isSquareAttackedBy` | the board knowing chess rules | The board is physical state and a few reads. |
| D5 | King safety: try → check → undo, in `ChessGame` | precomputing pins | Simple and always correct; 8×8 is tiny. |
| D6 | `applyMove` does **not** mark the piece moved; `ChessGame` marks it only on commit | marking inside `applyMove` | Try-moves for check detection must leave no trace (the bug above). |
| D7 | `Move` = two positions, no piece reference | moves holding pieces | Looked up at execution; no stale references. |
| D8 | `Position` immutable with `equals`/`hashCode`, algebraic `of("e4")` | raw int pairs | Readable tests and map keys. |

### Class shape
```
ChessGame                               ← game rules: turn, king safety, check/checkmate/stalemate
  Board · currentTurn · status · moveHistory
  makeMove(move) · hasAnyLegalMove(color)

Board                                   ← physical state
  Piece[8][8]  pieceAt · setPieceAt · applyMove → captured · undoMove · kingPosition · isSquareAttackedBy · render

«abstract» Piece { color, hasMoved }  isValidMove(move, board) · symbol()
  ├── Pawn (forward 1/2, diagonal capture)  ├── Knight (L, jumps)  ├── King (1 square)
  └── Rook / Bishop / Queen  → PieceHelpers.pathIsClearAndTargetCapturable
Move { from, to }   Position { row, col } + of("e4")   Color { WHITE, BLACK; opposite() }
```

---

## 3. Patterns that earn their place

| Pattern | Problem it solves | Without it |
|---|---|---|
| **Polymorphism / Template** (`Piece.isValidMove`) | six movement rules behind one call | a type switch in the board and game |
| **Command** (follow-up: undo) | a move as an object you can reverse | Q3 |

**Say:** *"Each piece subclass owns its movement rule; the board never switches on type. Game rules (turn, king safety) live in ChessGame, which tries the move, checks the king, and undoes."*

**Tempting but wrong:** Strategy for pieces (a piece's rule never changes at runtime, so subclassing fits), a Singleton board, the State pattern for game status (an enum is enough), a `Square` class per cell (no behaviour).

---

## 4. The 45-minute run

### Clarify (min 0–5)
> **You:** Two players, standard board and setup. Validate moves, alternate turns, detect check and checkmate?
> **Interviewer:** Yes.
> **You:** Castling, en passant, promotion, draws by repetition: can those be follow-ups?
> **Interviewer:** Yes, maybe promotion later.
> **You:** No AI, no clocks?
> **Interviewer:** Correct.

```
In scope:  standard setup · per-piece move rules · turn order · can't leave own king in check
           check / checkmate / stalemate
Out:       castling, en passant, promotion, repetition/50-move draws, clocks, AI
```

### Timeline
| Min | Do |
|---|---|
| 5–9 | The three layers. Class shape. Say "polymorphic isValidMove". |
| 9–13 | Code step 1: `Color`, `Position`, `Move`, abstract `Piece`. |
| 13–24 | Code step 2: `Knight`, `King`, `PieceHelpers` + `Rook` (Bishop/Queen the same), **`Pawn`**. |
| 24–30 | Code step 3: `Board` (`applyMove`, `undoMove`, `isSquareAttackedBy`, `kingPosition`). |
| 30–38 | Code step 4: **`ChessGame.makeMove`** + `hasAnyLegalMove`. |
| 38–45 | Dry run Fool's Mate; follow-ups. |

### The code you write, in this order

**Step 1: the piece contract** ([piece/Piece.java](piece/Piece.java))
```java
public abstract class Piece {
    private final Color color;
    private boolean hasMoved = false;                 // pawn double-step (and castling later)

    protected Piece(Color color) { this.color = color; }

    // geometry + path + not capturing your own colour. NOT turn order, NOT king safety.
    public abstract boolean isValidMove(Move move, Board board);
    public abstract String symbol();

    public Color getColor() { return color; }
    public boolean hasMoved() { return hasMoved; }
    public void markMoved() { hasMoved = true; }
}
```

**Step 2: the pieces** ([piece/](piece/))
```java
public class Knight extends Piece {                   // L-shape, jumps: no path check
    public Knight(Color c) { super(c); }
    public String symbol() { return "♘"; }
    public boolean isValidMove(Move move, Board board) {
        int dRow = Math.abs(move.to().row() - move.from().row());
        int dCol = Math.abs(move.to().col() - move.from().col());
        if (!((dRow == 2 && dCol == 1) || (dRow == 1 && dCol == 2))) return false;
        Piece target = board.pieceAt(move.to());
        return target == null || target.getColor() != getColor();
    }
}

final class PieceHelpers {                            // shared by the sliding pieces
    static boolean pathIsClearAndTargetCapturable(Move move, Board board, Piece self) {
        int dRow = Integer.signum(move.to().row() - move.from().row());
        int dCol = Integer.signum(move.to().col() - move.from().col());
        int r = move.from().row() + dRow, c = move.from().col() + dCol;
        while (r != move.to().row() || c != move.to().col()) {      // every square strictly between
            if (board.pieceAt(new Position(r, c)) != null) return false;
            r += dRow;
            c += dCol;
        }
        Piece target = board.pieceAt(move.to());
        return target == null || target.getColor() != self.getColor();
    }
}

public class Rook extends Piece {
    public Rook(Color c) { super(c); }
    public String symbol() { return "♖"; }
    public boolean isValidMove(Move move, Board board) {
        boolean straight = move.from().row() == move.to().row() || move.from().col() == move.to().col();
        return straight && PieceHelpers.pathIsClearAndTargetCapturable(move, board, this);
    }
}
// Bishop: |dRow| == |dCol| + helper.  Queen: straight OR diagonal + helper.  King: max(|dRow|,|dCol|) == 1.

public class Pawn extends Piece {                     // the trickiest: moving ≠ capturing
    public Pawn(Color c) { super(c); }
    public String symbol() { return "♙"; }
    public boolean isValidMove(Move move, Board board) {
        int dir = getColor() == Color.WHITE ? +1 : -1;
        int dRow = move.to().row() - move.from().row();
        int dCol = move.to().col() - move.from().col();
        Piece target = board.pieceAt(move.to());
        if (dCol == 0 && dRow == dir && target == null) return true;                     // forward 1
        if (dCol == 0 && dRow == 2 * dir && !hasMoved() && target == null
                && board.pieceAt(new Position(move.from().row() + dir, move.from().col())) == null) {
            return true;                                                                  // forward 2 from start
        }
        return Math.abs(dCol) == 1 && dRow == dir && target != null && target.getColor() != getColor();  // capture
    }
}
```

**Step 3: the board** ([Board.java](Board.java))
```java
public class Board {
    private final Piece[][] squares = new Piece[8][8];

    public Piece pieceAt(Position p) { return squares[p.row()][p.col()]; }

    // no validation, and does NOT mark the piece moved (try-moves must leave no trace)
    public Piece applyMove(Move move) {
        Piece moving = pieceAt(move.from());
        Piece captured = pieceAt(move.to());
        squares[move.to().row()][move.to().col()] = moving;
        squares[move.from().row()][move.from().col()] = null;
        return captured;
    }

    public void undoMove(Move move, Piece captured) {
        squares[move.from().row()][move.from().col()] = pieceAt(move.to());
        squares[move.to().row()][move.to().col()] = captured;
    }

    public boolean isSquareAttackedBy(Position target, Color attacker) {   // "could any enemy piece move there?"
        for (int r = 0; r < 8; r++)
            for (int c = 0; c < 8; c++) {
                Piece p = squares[r][c];
                if (p != null && p.getColor() == attacker
                        && !(r == target.row() && c == target.col())
                        && p.isValidMove(new Move(new Position(r, c), target), this)) return true;
            }
        return false;
    }
    // + setupStandard, setPieceAt, kingPosition(color), render
}
```

**Step 4: the game** ([ChessGame.java](ChessGame.java))
```java
public class ChessGame {
    public enum GameStatus { IN_PROGRESS, CHECK, CHECKMATE, STALEMATE }

    private final Board board = new Board();
    private final List<Move> moveHistory = new ArrayList<>();
    private Color currentTurn = Color.WHITE;
    private GameStatus status = GameStatus.IN_PROGRESS;

    public boolean makeMove(Move move) {
        if (status == GameStatus.CHECKMATE || status == GameStatus.STALEMATE) throw new IllegalStateException("Game is over");
        Piece moving = board.pieceAt(move.from());
        if (moving == null) throw new IllegalArgumentException("No piece at " + move.from());
        if (moving.getColor() != currentTurn) throw new IllegalArgumentException("Not " + moving.getColor() + "'s turn");
        if (!moving.isValidMove(move, board)) throw new IllegalArgumentException("Illegal " + moving.getClass().getSimpleName() + " move " + move);

        Piece captured = board.applyMove(move);                                  // try it
        if (board.isSquareAttackedBy(board.kingPosition(currentTurn), currentTurn.opposite())) {
            board.undoMove(move, captured);                                       // undo: no trace
            throw new IllegalArgumentException("Move would leave your king in check");
        }
        moving.markMoved();                                                       // committed: now it counts
        moveHistory.add(move);

        Color opponent = currentTurn.opposite();
        boolean inCheck = board.isSquareAttackedBy(board.kingPosition(opponent), currentTurn);
        boolean hasMove = hasAnyLegalMove(opponent);
        if (!hasMove)     status = inCheck ? GameStatus.CHECKMATE : GameStatus.STALEMATE;
        else if (inCheck) status = GameStatus.CHECK;
        else              status = GameStatus.IN_PROGRESS;
        currentTurn = opponent;
        return true;
    }

    private boolean hasAnyLegalMove(Color color) {          // try every move; stop at the first safe one
        for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) {
            Piece p = board.pieceAt(new Position(r, c));
            if (p == null || p.getColor() != color) continue;
            for (int rr = 0; rr < 8; rr++) for (int cc = 0; cc < 8; cc++) {
                if (rr == r && cc == c) continue;
                Move m = new Move(new Position(r, c), new Position(rr, cc));
                if (!p.isValidMove(m, board)) continue;
                Piece captured = board.applyMove(m);
                boolean safe = !board.isSquareAttackedBy(board.kingPosition(color), color.opposite());
                board.undoMove(m, captured);
                if (safe) return true;
            }
        }
        return false;
    }
}
```
**Shape to remember:** validate (game over? your piece? your turn? piece rule?) → try → king attacked? undo + reject : mark moved → opponent in check? any legal move? → status → switch turn.

### Dry run: Fool's Mate
```
1. f2-f3   e7-e5      2. g2-g4   Qd8-h4
After Qh4: white king e1 attacked along h4–e1 (f2 and g3 are empty) → check
hasAnyLegalMove(WHITE): every white move tried + undone; none removes the attack → false
→ in check + no legal move → CHECKMATE
```

---

## 5. Follow-ups: answer out loud first, then open

<details>
<summary><b>Q1. Pawn promotion.</b></summary>

After a committed move, a pawn on the far rank becomes the chosen piece (default: queen):
```java
private void promoteIfNeeded(Move move) {
    Piece moved = board.pieceAt(move.to());
    int lastRank = moved.getColor() == Color.WHITE ? 7 : 0;
    if (moved instanceof Pawn && move.to().row() == lastRank) {
        board.setPieceAt(move.to(), new Queen(moved.getColor()));     // or the player's choice
    }
}
```
Call it in `makeMove` after `markMoved()`, **before** computing the opponent's check status (a new queen can give check). Let `Move` carry an optional `promotionType` so the player can choose a knight.
</details>

<details>
<summary><b>Q2. Castling.</b></summary>

A king moving two squares sideways is a castle. Legal only if: neither the king nor that rook has moved (`hasMoved`, which is why the flag must be accurate), the squares between are empty, the king is **not** in check, and it doesn't pass through or land on an attacked square. Then move the rook too. Put the geometry in `King.isValidMove` and the "not through check" rule in `ChessGame` (it needs `isSquareAttackedBy`).
</details>

<details>
<summary><b>Q3. Undo / takeback.</b></summary>

Store each committed move as a record: `{move, captured piece, moving piece's previous hasMoved, promotion info}` on a stack (the Command pattern). Undo = pop, `undoMove`, restore `hasMoved`, un-promote, switch the turn back, recompute the status. Real undo needs the previous flag; the internal try-moves don't, because they no longer touch it.
</details>

<details>
<summary><b>Q4. En passant.</b></summary>

Remember the last move. If it was an enemy pawn's two-square advance, your pawn on the adjacent square may capture diagonally onto the **skipped** square, removing the enemy pawn from beside it. `Pawn.isValidMove` needs read access to "last move" (pass it via the board or the game), and `applyMove` must remove a piece that isn't on the destination square. That's why it's a follow-up.
</details>

<details>
<summary><b>Q5. `hasAnyLegalMove` tries 64 × 64 moves per piece. Faster?</b></summary>

Let each piece **generate** its candidate moves (`List<Move> candidateMoves(board)`: a knight has at most 8, a rook at most 14) instead of testing every square. Then filter by king safety. The interface stays polymorphic: one more method per piece. For engines: bitboards and incremental attack maps.
</details>

<details>
<summary><b>Q6. Draws: threefold repetition, 50-move rule, insufficient material.</b></summary>

Repetition: hash the position (pieces + turn + castling/en-passant rights) after each move; if a hash count reaches 3 → draw. 50-move: a counter reset on every pawn move or capture; 100 half-moves → draw. All live in `ChessGame`, leaving pieces and board untouched.
</details>

<details>
<summary><b>Q7. Online play.</b></summary>

`Map<gameId, ChessGame>` on the server; each move request carries the gameId + playerId; `makeMove` is synchronized per game; the server validates everything (never trust the client); push the new position to both players over a WebSocket; store moves (PGN) for replay. Add clocks with a server-side timer per player.
</details>

<details>
<summary><b>Q8. How do you test it?</b></summary>

Starting position, pawn single/double/capture, a knight jumping over pieces, a bishop blocked then unblocked, wrong turn, king safety, Fool's Mate → checkmate, and the regression test (a7-a5 still legal after a check scan). All are in the driver. Build custom positions with `setPieceAt` for edge cases such as stalemate and pins.
</details>

---

## 6. Traps
1. A `switch (pieceType)` anywhere.
2. Pieces checking turn order or king safety.
3. Forgetting "can't leave your own king in check" (pinned pieces).
4. Try-move/undo that leaves side effects (the `hasMoved` bug).
5. Coding castling and en passant before the basics work.
6. A sliding piece path check that includes the destination (own-colour vs capture is checked separately).

## 7. Recall check
1. The three layers of move validation, and who owns each.
2. Write `Knight.isValidMove` and `pathIsClearAndTargetCapturable` from memory.
3. Why does `applyMove` not mark the piece moved? What broke when it did?
4. Checkmate vs stalemate in one line each.
5. Where would castling's rules go?

**Rebuild in 15 minutes:** `Piece` · `Knight` · `PieceHelpers` + `Rook` · `Pawn` · `Board.applyMove/undoMove/isSquareAttackedBy` · `ChessGame.makeMove` + `hasAnyLegalMove`.

---

**Files:** `ChessGame` · `Board` · `piece/` (`Piece`, `Pawn`, `Knight`, `Bishop`, `Rook`, `Queen`, `King`, `PieceHelpers`) · `model/` (`Color`, `Position`, `Move`) · `ChessGameDriver` (setup, pawns, knight jump, blocked bishop, wrong turn, king safety, Fool's Mate, check-scan regression)
```bash
mvn -q compile exec:java -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.games.chess.ChessGameDriver
```
