# Chess — 45-min LLD Interview Walkthrough

**Target role:** SDE‑2 (Amazon, Adobe, Microsoft, Atlassian, etc.)

> Chess is the **canonical polymorphism-via-piece-subclasses problem**. Three senior signals: (a) **abstract `Piece` base class** with each concrete piece overriding `isValidMove` — the Board NEVER branches on piece type, (b) **separation of piece-level legality (movement rules) from game-level legality (turn order, king-safety)** — different layers, different responsibilities, (c) **check / checkmate detection** via the "try-move + peek-attacked + undo" pattern.

---

## Time budget (45 min)

| Step | Activity                                                                                | Budget   | Cumulative |
| ---- | --------------------------------------------------------------------------------------- | -------- | ---------- |
| 1    | Requirements (SCOPE THIS HARD — full chess is too big)                                  | ~5 min   | 5          |
| 2    | Entities & Relationships                                                                | ~4 min   | 9          |
| 3    | Class Design (Piece hierarchy + Board + ChessGame separation)                           | ~12 min  | 21         |
| 4    | Implementation (Pawn + Knight + sliding pieces + king-safety + dry-run)                 | ~16 min  | 37         |
| 5    | Extensibility (castling, en passant, promotion, time controls, online play)             | ~7 min   | 44         |
| —    | Wrap & questions                                                                        | ~1 min   | 45         |

**Scope discipline matters here more than any other problem.** A full implementation of chess (castling, en passant, promotion, draw conditions, time control, opening books) is a multi-week project. In 45 minutes you implement the CORE — piece movements, check, checkmate — and explicitly defer the rest.

---

## Mental models — internalize these BEFORE you walk in

### M1. Polymorphism instead of type-branching

```
   WRONG (anti-pattern):                  RIGHT (polymorphism):
   ----------------------                  ---------------------
   class Board {                           abstract class Piece {
     boolean isValidMove(Move m) {           abstract boolean isValidMove(Move m, Board b);
       PieceType t = pieceAt(m.from).type;  }
       switch (t) {
         case PAWN:   return validatePawn(m);     class Pawn extends Piece { @Override isValidMove(...) { ... } }
         case ROOK:   return validateRook(m);     class Knight extends Piece { @Override isValidMove(...) { ... } }
         case BISHOP: return validateBishop(m);   ...
         ...
       }                                       }
     }
   }                                       Board just calls:  piece.isValidMove(m, this)
                                                              ↑↑↑ NO type-branching

   Why polymorphism wins:
     - Adding a new piece type (e.g., a "Berolina Pawn" variant) = new class,
       zero changes to Board / ChessGame.
     - Each piece's rules live IN that piece — easy to reason about + test.
     - Open-Closed Principle exemplar — extend by adding, not by switching.
```

**Senior soundbite (memorize):** *"The Piece hierarchy uses polymorphism to dispatch `isValidMove`. Board never branches on piece type — it just calls `piece.isValidMove(move, this)` and lets the piece decide. Adding chess960-style fairy pieces is one new class, zero changes to existing code. This is Open-Closed and Liskov simultaneously."*

### M2. Three layers of "is this move legal?"

```
                  ┌────────────────────────────────────────────────┐
                  │   Layer 3: GAME-LEVEL legality                  │   ← ChessGame
                  │   - is it my turn?                              │
                  │   - does this move leave MY king in check?      │
                  └──────────────────┬─────────────────────────────┘
                                     │ if OK, ask...
                                     ▼
                  ┌────────────────────────────────────────────────┐
                  │   Layer 2: BOARD-LEVEL questions                │   ← Board
                  │   - what's at this square?                      │
                  │   - is this square attacked by the opponent?    │
                  └──────────────────┬─────────────────────────────┘
                                     │ if asked, look up...
                                     ▼
                  ┌────────────────────────────────────────────────┐
                  │   Layer 1: PIECE-LEVEL legality                 │   ← Piece subclass
                  │   - geometric rule for this piece type?         │
                  │   - path blocked? destination capturable?       │
                  └────────────────────────────────────────────────┘

   Each layer has ONE responsibility — Information Expert applied per-layer.

   Layer 1 (Piece):
     "Can I, given my movement geometry and the current board, get from A to B?"
   Layer 2 (Board):
     "What's the physical state? What squares are attacked?"
   Layer 3 (ChessGame):
     "What turn is it? Would this move expose MY king?"
```

### M3. King-safety via try-move + peek-attacked + undo

```
   The hardest correctness invariant in chess: you cannot leave YOUR king in check.

   How to check it cleanly:

     PROPOSED MOVE m
        |
        v
     1. board.applyMove(m)         ← apply the move
     2. position = board.kingPosition(myColor)
     3. inCheck  = board.isSquareAttackedBy(position, opponentColor)
     4. board.undoMove(m, captured) ← undo regardless of result
        |
        v
     if inCheck → REJECT  (would leave own king in check)
     else       → ACCEPT  (real apply)

   This pattern works because:
     - applyMove and undoMove are simple physical state changes (no validation).
     - isSquareAttackedBy reuses Piece.isValidMove polymorphically — iterate every
       enemy piece, ask "can you reach the king's square?", short-circuit on yes.

   Same pattern used for:
     - "Did MY last move put OPPONENT in check?" (after applying for real)
     - "Does opponent have ANY legal move?" (try every candidate; same dance)
       → if not + in check → CHECKMATE
       → if not + not in check → STALEMATE
```

> **The interview rule:** *the try-move/undo pattern is the SAME for check, checkmate, and stalemate detection. You implement it ONCE on Board and reuse everywhere. Don't duplicate the apply/check/undo logic per place — extract it.*

---

## STEP 1 — Requirements (~5 min)

### What to say out loud (opener — SCOPE THIS HARD)
> "Full chess is too big for 45 minutes — castling, en passant, promotion, threefold repetition, 50-move rule, time control all add up. Let me confirm scope: I'll implement piece movements for all 6 piece types, turn enforcement, check detection, and checkmate / stalemate. I'll explicitly defer castling, en passant, promotion, draw conditions, and time control to extensibility."

### Probe the 4 themes

| Theme               | Question to ask                                                                                              |
| ------------------- | ------------------------------------------------------------------------------------------------------------ |
| Primary capabilities| "Two-player 8×8 chess with all 6 piece types? Make moves; query state (whose turn, check, checkmate)?" |
| Rules — IN scope    | "Standard movement rules including captures + path checking? Pawn's initial 2-step? Knight jumps? King-safety enforcement?" |
| Rules — OUT scope   | "Castling, en passant, pawn promotion, 50-move rule, threefold repetition, stalemate by insufficient material — defer to Step 5?" |
| Error handling      | "Illegal moves throw with specific reason? Wrong turn → throws? Move after checkmate → throws?" |

### What to write on the board

```
Functional Requirements
1. 8×8 board, standard starting position.
2. Six piece types: King, Queen, Rook, Bishop, Knight, Pawn.
3. Piece movement rules:
   - King: 1 square any direction
   - Queen: rook + bishop combined
   - Rook: horizontal/vertical, path must be clear
   - Bishop: diagonal, path must be clear
   - Knight: L-shape, JUMPS over pieces (no path check)
   - Pawn: forward 1, initial 2-step, diagonal capture
4. Turn enforcement — white first, then alternating.
5. Cannot capture own color.
6. Cannot leave OWN king in check.
7. Detect: IN_PROGRESS / CHECK / CHECKMATE / STALEMATE.
8. makeMove(Move) returns true on success, throws on illegal with reason.

Out of Scope (defer to Step 5 — call out explicitly!)
- Castling
- En passant
- Pawn promotion (replace at 8th rank)
- 50-move rule
- Threefold repetition
- Insufficient-material draws (K vs K, K+B vs K, etc.)
- Time control / clocks
- Move history notation export (PGN)
- Online / networked play
```

### Close the step
> "Two load-bearing scope decisions: (a) all six piece types with movement geometry, (b) king-safety enforcement via try-move + peek + undo. Defer castling and en passant explicitly — those are extension exercises."

---

## STEP 2 — Entities & Relationships (~4 min)

### What to say out loud
> "Three layers worth of types. Models: **Color**, **Position**, **Move**. Pieces: **Piece** (abstract) + 6 concrete subclasses. Board + Game: **Board** (8×8 physical state) and **ChessGame** (orchestrator — turn, check, checkmate). Plus a `PieceHelpers` package-private utility for shared path-clearing logic across Rook/Bishop/Queen."

### Why the piece hierarchy (not a single Piece class with a `type` enum)
> "The whole problem is that each piece has materially DIFFERENT movement rules. A `type` enum + switch is the anti-pattern — every new piece type requires editing the switch. Polymorphism delegates the rule to the piece subclass; adding a new piece is a new class, zero existing changes. Same Open-Closed argument as Connect Four's Player → HumanPlayer/BotPlayer extension."

### Why ChessGame separate from Board
> "Two different responsibilities. Board owns physical state — what's at each square, applyMove, undoMove. ChessGame owns game-level invariants — whose turn, has my king been exposed, has the opponent been checkmated. Mixing them gives you a single 1000-line class that does everything. Separating them lets each one be reasoned about and tested independently."

### What to write on the board

```
Models
- Color           (enum: WHITE, BLACK, with opposite())
- Position        (record: row, col — 0..7 each; algebraic("e4") helper)
- Move            (record: from, to — both Positions)

Pieces (hierarchy)
- Piece           (abstract — owns color + hasMoved flag; abstract isValidMove(Move, Board))
   ├── Pawn       (forward 1; initial 2-step; diagonal capture)
   ├── Knight     (L-shape; JUMPS — no path check)
   ├── Bishop     (diagonal slide; path-clear required)
   ├── Rook       (orthogonal slide; path-clear required)
   ├── Queen      (Rook + Bishop combined)
   └── King       (1 square any direction)

Board + Game
- Board           (8x8 squares; setupStandard, pieceAt, applyMove, undoMove,
                   kingPosition, isSquareAttackedBy)
- ChessGame       (orchestrator + facade; currentTurn, status, makeMove, hasAnyLegalMove)
- GameStatus      (enum nested in ChessGame: IN_PROGRESS, CHECK, CHECKMATE, STALEMATE)

NOT entities
- PieceType enum  (would lead to switch-on-type → polymorphism anti-pattern)
- Player          (just the Color enum — no player state in v1)
- MoveHistory class (an ArrayList<Move> inside ChessGame is enough)

Relationships
- Board contains a Piece[8][8] grid (positions own at most one piece each).
- ChessGame owns a Board + currentTurn + status + move history list.
- Each Piece queries Board (for path checks, target inspection) via Board.pieceAt.
- Board does NOT call Piece (no type-branching in Board) — it calls polymorphically via Piece.isValidMove.
```

### Diagram — boxes and arrows

```
                  +----------------------------+
                  |          ChessGame         |    <- orchestrator + facade
                  | currentTurn, status        |
                  | makeMove(Move) — enforces  |
                  |   turn, piece legality,    |
                  |   king-safety, checkmate   |
                  +----------------------------+
                              │
                       owns   │
                              ▼
                  +----------------------------+
                  |           Board            |    <- physical state
                  | Piece[8][8] squares        |
                  | pieceAt / applyMove /      |
                  | undoMove / kingPosition /  |
                  | isSquareAttackedBy         |
                  +----------------------------+
                              │
                       8×8    │ contains
                              ▼
                  +-----------------------------+
                  | <<abstract>>   Piece         |    color, hasMoved
                  | abstract isValidMove(Move,   |
                  |                       Board) |
                  +-----------------------------+
                       ▲       ▲       ▲       ▲       ▲       ▲
                       │       │       │       │       │       │
                     Pawn   Knight   Bishop   Rook   Queen   King

   (Models: Color, Position, Move — used by all)
```

---

## STEP 3 — Class Design (~12 min)

### Piece — abstract base

```java
public abstract class Piece {
    private final Color color;
    private boolean hasMoved = false;

    protected Piece(Color color) { this.color = color; }

    public Color getColor()       { return color; }
    public boolean hasMoved()     { return hasMoved; }
    public void markMoved()       { this.hasMoved = true; }

    public abstract String symbol();      // "♙♖♘♗♕♔" for printing
    public abstract boolean isValidMove(Move move, Board board);
}
```

> **Senior callout — `hasMoved`:** *"Tracked per piece. Used today for pawn's initial 2-step (only available on first move). Reusable later for castling (king + relevant rook can only castle if neither has moved). This is a small state addition with a real downstream payoff."*

### Six concrete pieces — outlines

```java
public class Knight extends Piece {
    public boolean isValidMove(Move move, Board board) {
        int dr = abs(to.row - from.row), dc = abs(to.col - from.col);
        boolean lShape = (dr == 2 && dc == 1) || (dr == 1 && dc == 2);
        if (!lShape) return false;
        Piece target = board.pieceAt(move.to());
        return target == null || target.getColor() != getColor();   // no path check — knight jumps
    }
}

public class Rook extends Piece {
    public boolean isValidMove(Move move, Board board) {
        if (dr != 0 && dc != 0) return false;       // must be purely horizontal or vertical
        return PieceHelpers.pathIsClearAndTargetCapturable(move, board, this);
    }
}

public class Bishop extends Piece {
    public boolean isValidMove(Move move, Board board) {
        if (abs(dr) != abs(dc)) return false;       // must be true diagonal
        return PieceHelpers.pathIsClearAndTargetCapturable(move, board, this);
    }
}

public class Queen extends Piece {
    public boolean isValidMove(Move move, Board board) {
        // Either rook-like (dr==0 || dc==0) or bishop-like (|dr|==|dc|)
        if (!rookLike && !bishopLike) return false;
        return PieceHelpers.pathIsClearAndTargetCapturable(move, board, this);
    }
}

public class King extends Piece {
    public boolean isValidMove(Move move, Board board) {
        if (abs(dr) > 1 || abs(dc) > 1) return false;
        if (dr == 0 && dc == 0) return false;       // null move
        Piece target = board.pieceAt(move.to());
        return target == null || target.getColor() != getColor();
    }
}

public class Pawn extends Piece {
    public boolean isValidMove(Move move, Board board) {
        int dir = (getColor() == WHITE) ? +1 : -1;
        // 3 cases — see implementation
    }
}
```

> **Senior callout — `PieceHelpers.pathIsClearAndTargetCapturable`:** *"Rook, Bishop, Queen all slide — they all need: walk from `from` to `to`, fail if any intermediate square has a piece, succeed at destination if empty or enemy. Extract that to a package-private utility so we don't duplicate it three times. Knight skips because it jumps; King skips because there's no path (1-square move)."*

### Pawn — the trickiest piece

```java
public boolean isValidMove(Move move, Board board) {
    int dir  = (getColor() == Color.WHITE) ? +1 : -1;
    int dRow = to.row() - from.row();
    int dCol = to.col() - from.col();
    Piece target = board.pieceAt(to);

    // 1) Forward 1
    if (dCol == 0 && dRow == dir && target == null) return true;

    // 2) Forward 2 from initial rank (hasMoved == false)
    if (dCol == 0 && dRow == 2 * dir && !hasMoved()
            && target == null
            && board.pieceAt(new Position(from.row() + dir, from.col())) == null) {
        return true;
    }

    // 3) Diagonal capture
    if (Math.abs(dCol) == 1 && dRow == dir
            && target != null && target.getColor() != getColor()) {
        return true;
    }

    return false;
}
```

> **Three pawn quirks worth saying:** *"Pawn is the trickiest piece because move and capture geometries DIFFER. Forward = move only (no capture); diagonal = capture only (no move). Initial 2-step requires BOTH squares empty — many implementations forget the intermediate-square check. En passant and promotion are deferred to Step 5."*

### Board — physical state + read helpers

```java
public class Board {
    private final Piece[][] squares = new Piece[8][8];

    public void setupStandard();                                // initial position
    public Piece pieceAt(Position p);
    public void setPieceAt(Position p, Piece piece);            // for testing

    /** Apply WITHOUT validation — caller has already checked legality. */
    public Piece applyMove(Move move);                           // returns captured (or null)
    public void undoMove(Move move, Piece captured);             // peek + restore

    public Position kingPosition(Color color);
    public boolean isSquareAttackedBy(Position target, Color attackerColor);
}
```

> **Senior callout — `applyMove` is unvalidated:** *"Board's `applyMove` is a low-level mutation — it doesn't check legality. The caller (ChessGame) has already done piece-level + king-safety checks. Keeping Board's API thin lets us reuse it for the try-move/undo dance without re-validating in a loop."*

### ChessGame — turn + king-safety + checkmate

```java
public class ChessGame {
    private final Board board = new Board();
    private final List<Move> moveHistory = new ArrayList<>();
    private Color currentTurn = Color.WHITE;
    private GameStatus status = GameStatus.IN_PROGRESS;

    public enum GameStatus { IN_PROGRESS, CHECK, CHECKMATE, STALEMATE }

    public boolean makeMove(Move move) {
        // ... see Step 4 ...
    }
    private boolean hasAnyLegalMove(Color color);    // for checkmate / stalemate
}
```

### The principle to verbalize — Polymorphism + Information Expert + Layered Validation
> "Three layers. Piece subclasses own piece-level legality — that's polymorphism + Information Expert (only the piece knows its own movement rules). Board owns physical state + a few read helpers like `isSquareAttackedBy`. ChessGame owns game-level invariants — turn order, king-safety, checkmate. Each layer has ONE responsibility. The Board never branches on piece type; it calls polymorphically into the piece, which is the whole point."

---

## STEP 4 — Implementation (~16 min)

### Open by asking
> "Real Java or pseudo-code? I'll do the abstract Piece + a slider (Rook) + a non-slider (Knight) + Pawn (the trickiest), then Board's helpers, then ChessGame's makeMove + king-safety, then dry-run Fool's Mate."

### 4.1 `PieceHelpers.pathIsClearAndTargetCapturable` — the slider primitive

```java
static boolean pathIsClearAndTargetCapturable(Move move, Board board, Piece self) {
    int dRow = Integer.signum(move.to().row() - move.from().row());
    int dCol = Integer.signum(move.to().col() - move.from().col());

    int curRow = move.from().row() + dRow;
    int curCol = move.from().col() + dCol;
    while (curRow != move.to().row() || curCol != move.to().col()) {
        if (board.pieceAt(new Position(curRow, curCol)) != null) return false;   // blocked
        curRow += dRow;
        curCol += dCol;
    }

    Piece target = board.pieceAt(move.to());
    return target == null || target.getColor() != self.getColor();
}
```

> **Senior callout:** *"Three lines for the path-walk: signum gives the step direction (-1/0/+1), step toward target, fail on any blocker. Then check the destination — empty or enemy = OK; own color = reject. This single utility powers Rook + Bishop + Queen because their movement geometries are all 'slide along a line'."*

### 4.2 Pawn — the textbook tricky case

(Shown in Step 3.)

> **Three things to verbalize:**
> 1. *"`dir = +1 for white, -1 for black`. The direction encodes 'forward' so the same code handles both colors."*
> 2. *"Initial 2-step needs to check the INTERMEDIATE square too. `board.pieceAt(new Position(from.row() + dir, from.col())) == null`. Forgetting that is the #1 pawn bug."*
> 3. *"Diagonal capture requires `target != null && target.getColor() != getColor()`. Pawn moves and captures are geometrically different — a pawn can move forward only if empty, and capture diagonally only if enemy. Reverse those and you'll let pawns capture forward."*

### 4.3 `Board.isSquareAttackedBy` — uses piece polymorphism reflexively

```java
public boolean isSquareAttackedBy(Position target, Color attackerColor) {
    for (int r = 0; r < 8; r++)
        for (int c = 0; c < 8; c++) {
            Piece p = squares[r][c];
            if (p == null || p.getColor() != attackerColor) continue;
            if (p.isValidMove(new Move(new Position(r, c), target), this)) return true;
        }
    return false;
}
```

> **Senior callout:** *"This is the polymorphism payoff. Asking 'is this square attacked?' just walks every enemy piece and asks each one 'can you reach here?' via `isValidMove`. Zero piece-type-specific code on the Board. Adding a new piece type doesn't change this method."*

> **Pawn-attack subtlety:** *"With our current pawn rules, a pawn correctly threatens diagonal squares — its `isValidMove` returns true to a diagonal target only if the target has an enemy piece. So for `isSquareAttackedBy`, this works UNLESS the target square is empty (a pawn 'attacks' an empty diagonal square in the sense of being able to capture there). For real chess this matters for king-safety: a king can't step to a square that a pawn THREATENS even if currently empty. A production impl adds an `attacks(square)` method separate from `isValidMove`. For interview scope this is a known limitation worth mentioning."*

### 4.4 `ChessGame.makeMove` — the orchestrator

```java
public boolean makeMove(Move move) {
    if (status == GameStatus.CHECKMATE || status == GameStatus.STALEMATE) {
        throw new IllegalStateException("Game is over");
    }
    Piece moving = board.pieceAt(move.from());
    if (moving == null)                              throw new IllegalArgumentException("No piece at " + move.from());
    if (moving.getColor() != currentTurn)            throw new IllegalArgumentException("Not " + moving.getColor() + "'s turn");
    if (!moving.isValidMove(move, board))            throw new IllegalArgumentException("Illegal " + moving.getClass().getSimpleName() + " move");

    // King-safety: try the move, check own-king-attacked, undo if so.
    Piece captured = board.applyMove(move);
    Position myKing = board.kingPosition(currentTurn);
    boolean kingInCheck = board.isSquareAttackedBy(myKing, currentTurn.opposite());
    if (kingInCheck) {
        board.undoMove(move, captured);
        throw new IllegalArgumentException("Move would leave your king in check");
    }

    moveHistory.add(move);

    // Compute opponent status
    Color opponent = currentTurn.opposite();
    boolean opponentInCheck = board.isSquareAttackedBy(board.kingPosition(opponent), currentTurn);
    boolean opponentHasMove = hasAnyLegalMove(opponent);

    if (!opponentHasMove && opponentInCheck)       status = GameStatus.CHECKMATE;
    else if (!opponentHasMove && !opponentInCheck) status = GameStatus.STALEMATE;
    else if (opponentInCheck)                       status = GameStatus.CHECK;
    else                                              status = GameStatus.IN_PROGRESS;

    currentTurn = opponent;
    return true;
}
```

> **Three callouts:**
> 1. *"Validation is in a strict ORDER: game-over → piece-exists → right-turn → piece-level-legal → king-safety. Each layer rejects earlier-with-cheaper-cost. Don't shuffle the order — the more expensive king-safety check should only run after the cheap structural checks pass."*
> 2. *"The king-safety check uses try + peek + undo on Board. Board doesn't know about turns or check — it's a pure physical-state layer. ChessGame composes Board's primitives."*
> 3. *"Checkmate detection is `not in check + no legal moves`? No — that's stalemate. Checkmate is `IN CHECK + no legal moves`. Get this backwards in the interview and you'll print STALEMATE when the opponent is mated. Common bug; double-check the booleans."*

### 4.5 `hasAnyLegalMove` — for checkmate detection

```java
private boolean hasAnyLegalMove(Color color) {
    for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) {
        Piece p = board.pieceAt(new Position(r, c));
        if (p == null || p.getColor() != color) continue;
        Position from = new Position(r, c);
        for (int rr = 0; rr < 8; rr++) for (int cc = 0; cc < 8; cc++) {
            if (rr == r && cc == c) continue;
            Move m = new Move(from, new Position(rr, cc));
            if (!p.isValidMove(m, board)) continue;
            // Same try/peek/undo dance — must not leave own king attacked
            Piece captured = board.applyMove(m);
            boolean safe = !board.isSquareAttackedBy(board.kingPosition(color), color.opposite());
            board.undoMove(m, captured);
            if (safe) return true;
        }
    }
    return false;
}
```

> **Senior callout — complexity:** *"O(64 × 64 × king-safety-check) = O(64² × O(N)) where N is total pieces on the board. For an 8×8 board with at most 32 pieces, this is a bounded constant — a few thousand operations. Cheap enough to run on every move."*

### 4.6 Verification — dry-run Fool's Mate

```
Fool's Mate — the fastest checkmate in chess (2 moves).

  1.  f2-f3      (white pawn forward)
  1...e7-e5      (black pawn forward)
  2.  g2-g4      (white pawn another forward — leaves diagonal h4-e1 OPEN)
  2...d8-h4#     (black queen swoops in to h4)

After 2...Qh4:
   Board state has a black queen on h4 attacking the white king on e1
   via the open diagonal h4-g3-f2-e1.
   (g3 empty, f2 empty because pawn moved to f3, but f3 is white pawn —
    actually the attack line is h4-g3-f2-e1; f2 was vacated by the pawn moving to f3.)

ChessGame.makeMove(d8→h4) calls hasAnyLegalMove(WHITE):
   For every white piece, for every destination square...
   No move can:
     (a) block the diagonal at f2 or g3 — no white piece can move there with legal geometry
     (b) capture the queen on h4 — no white piece reaches h4 with legal geometry
     (c) move the king to a safe square — e1, e2, d1, f1 are all attacked or blocked
   ⇒ hasAnyLegalMove(WHITE) = false.

White is IN CHECK + has NO LEGAL MOVE.
   ⇒ CHECKMATE.

Driver scenario 7 runs this and asserts status == CHECKMATE. ✓
```

> **This is the most-impressive single dry-run in your entire deck.** Knowing Fool's Mate cold and being able to step through it on a chess board demonstrates that you understand both the game AND your implementation's check/checkmate logic.

---

## STEP 5 — Extensibility (~7 min)

### 5.1 "Castling"

> **Problem in current design:** *"King and rook both have `hasMoved` flags but no castling logic. Real chess allows kings-side and queens-side castling under strict conditions."*
>
> **Pattern as the fix:** *"Add a special `Castling` Move subtype (or recognize King moving 2 squares as castling). Conditions: king + relevant rook haven't moved; intermediate squares empty; king not currently in check; king doesn't pass through an attacked square. Implement as a new branch in King.isValidMove + a special Board.applyMove handler that moves both king AND rook. The `hasMoved` plumbing is already there."*

### 5.2 "En passant"

> **Problem in current design:** *"Pawn capture is purely 'diagonal with enemy on target'. En passant lets a pawn capture an enemy pawn that JUST passed it via a two-step move."*
>
> **Pattern as the fix:** *"Track `lastMove` on ChessGame. In Pawn.isValidMove, add a 4th case: if `lastMove` was an enemy pawn's two-step that landed adjacent to me, the destination diagonal-empty-but-enemy-pawn-adjacent is a legal capture. The target square is the diagonal, but the captured piece is the pawn that just moved. Apply move handler must remove that pawn from a non-destination square."*

### 5.3 "Pawn promotion"

> **Problem in current design:** *"Pawn reaches the 8th rank → stays a pawn, can't move further. In real chess it must promote to Queen/Rook/Bishop/Knight."*
>
> **Pattern as the fix:** *"Detect 'pawn moved to last rank' in ChessGame.makeMove after applyMove. Require the caller to specify promotion via an enriched `PromotionMove` subclass (or a separate `chooseProm` callback). Replace the pawn on the destination square with the chosen piece. Tradeoff: forces the API to support promotion choice at move-submit time."*

### 5.4 "Time control / clocks"

> **Problem in current design:** *"No clock. Real chess has per-side time budgets."*
>
> **Pattern as the fix:** *"Add a `Clock` per side that decrements between moves. ChessGame.makeMove checks the side's remaining time before legality; if expired, terminate with a TIMEOUT status. Inject a real `Clock` (java.time.Clock) so tests can fast-forward time. Same Clock injection pattern as Locker / Rate Limiter."*

### 5.5 Other "what-if" answers

| Follow-up                                  | Answer                                                                                              |
| ------------------------------------------ | --------------------------------------------------------------------------------------------------- |
| "Threefold repetition / 50-move rule"      | Track position-hash history + plies-since-last-capture/pawn-move on ChessGame. Check after each move. |
| "PGN move-history export"                  | New formatter class that walks moveHistory + board state at each ply. Format: "1. e4 e5 2. Nf3 ..." |
| "Online multiplayer"                       | Out of scope for LLD; that's networking. Mention serialization of Move + Board snapshot.            |
| "Engine / AI opponent"                     | `Player` interface; `HumanPlayer` reads from stdin; `MinimaxPlayer` runs minimax with alpha-beta. Game just calls `player.chooseMove(board)`. |
| "Variant boards (chess960, 4-player)"      | Promote `Board` to a `BoardLayout` interface; standard + chess960 + 4-player all implement it.    |
| "Persistence / save game"                  | Serialize moveHistory; replay on load. Don't snapshot the board itself — moves are the source of truth. |

---

## Design Patterns — Hello Interview's canonical 8

> **No GoF pattern is required by name in the base.** The senior signals are polymorphism (Piece hierarchy) and the layered-validation architecture — those are principles, not GoF patterns.

### How this maps to Chess specifically

**Already in the BASE design — call out by name:**

- **Polymorphism + Open-Closed** (principle) — Piece hierarchy with subclass-specific `isValidMove`. Board never branches on type.
- **Information Expert** (GRASP) — each piece owns its own movement rules; Board owns physical state; ChessGame owns game-level invariants.
- **State Machine (#3) — lite** — GameStatus enum (IN_PROGRESS / CHECK / CHECKMATE / STALEMATE) with implicit transitions in makeMove. Enum-machine style (like PaymentGateway / JobScheduler) — full GoF State pattern would be overkill.
- **Facade (#8)** — ChessGame is the only class application code touches.

**Reach for these on Step-5 follow-ups:**

| Follow-up                                  | Pattern (HI's 8)             | Your line                                                                                            |
| ------------------------------------------ | ---------------------------- | ---------------------------------------------------------------------------------------------------- |
| "AI opponent / pluggable players"          | **Strategy (#1)** ⭐         | *"`Player` interface; HumanPlayer reads input, MinimaxPlayer runs minimax. ChessGame calls `player.chooseMove(board)` — same Strategy pattern as Connect Four's AI extension."* |
| "Variant boards / fairy pieces"            | (Existing polymorphism + Factory) | *"Polymorphism on Piece already supports new pieces. `BoardLayout` Strategy for non-8×8 boards."* |
| "Move history serialization (PGN)"         | (Builder or visitor)         | *"PGN formatter visits moveHistory + board. Builder pattern if the export format is rich (annotations, comments)."* |
| "Spectator notifications"                  | **Observer (#2)**            | *"ChessGame publishes `MoveExecuted` / `CheckDetected` / `GameEnded` events; subscribers (UI, analytics) register."* |
| "Promotion choice at move submit"          | **Command (#)** (not HI's 8) | *"Move becomes a sealed hierarchy: `BasicMove`, `PromotionMove`, `CastlingMove`, `EnPassantMove`. Each Command knows how to apply itself to the Board."* |

**Patterns to actively refuse:**

- **Singleton on Board or ChessGame** — multiple games at once must be possible.
- **Builder for `new ChessGame()`** — 0-arg ctor. Academic noise.
- **State pattern on GameStatus** (class-per-state) — game-status transitions are simple; enum + setStatus is correct here.
- **Visitor over pieces** — sounds tempting but rarely earns rent in practice; polymorphism on isValidMove already handles "dispatch by piece type". Visitor would only earn its place if you had MANY operations across all piece types (move, threat-analysis, value, capture-priority...).

### One sentence to say at the end of Step 3

> *"No GoF pattern named in the base — the senior signal is polymorphism on Piece + the three-layer separation of piece-level / board-level / game-level legality. Strategy lands in Step 5 for AI players or board variants; Observer if spectator notifications come up."*

---

## Interview deep-dives — the questions you'll definitely get asked

### 1. Complexity (Big-O)

| Operation                                | Time                                              | Space               | Notes                                                                              |
| ---------------------------------------- | ------------------------------------------------- | ------------------- | ---------------------------------------------------------------------------------- |
| `pieceAt(p)`                              | **`O(1)`**                                        | O(1)                | 2D array lookup                                                                     |
| `applyMove / undoMove`                    | **`O(1)`**                                        | O(1)                | Two array writes                                                                    |
| `Piece.isValidMove` (sliders)            | `O(8)` worst case for path scan                   | O(1)                | Max 7 intermediate squares on an 8×8 board                                          |
| `Piece.isValidMove` (knight, king, pawn) | **`O(1)`**                                        | O(1)                | Pure geometry checks                                                                |
| `isSquareAttackedBy`                      | **`O(N × P)`** where N ≤ 32, P = avg piece's isValidMove cost ≈ O(8) | O(1) | Iterate every enemy piece                          |
| `kingPosition`                            | `O(64)` linear scan; could be cached              | O(1)                | Optimization opportunity: maintain kingPosByColor in Board                          |
| `makeMove` (full)                         | **`O(N × P)`** dominated by king-safety check     | O(1)                |                                                                                    |
| `hasAnyLegalMove` (checkmate detect)     | **`O(64 × 64 × N × P)`** ≈ a few thousand ops    | O(1)                | Tight bound; fine for 8×8                                                          |
| Storage                                  | -                                                 | **`O(64)`**         | Board grid; piece count ≤ 32                                                       |

> **Senior callout:** *"All operations are bounded constants because the board is fixed at 8×8 with ≤32 pieces. `hasAnyLegalMove` is the expensive one — up to ~4000 try-undo dances on every move. For 45-min interview scope that's fine; for a tournament-engine, you'd cache the king position and use bitboards (one 64-bit int per piece type per color) for O(1) per-piece move generation. That's optimization territory."*

### 2. Concurrency / thread-safety

| Approach                                | When to use                                  | Cost                                                              |
| --------------------------------------- | -------------------------------------------- | ----------------------------------------------------------------- |
| Single-threaded                          | **Default for v1.** Chess is turn-based.    | None                                                              |
| `synchronized makeMove`                  | Multi-threaded callers (e.g., a UI thread)  | One lock, short critical section, no contention                  |
| Per-game lock                            | Many games at once on the same server       | Different games never block each other                            |

> *"Chess is inherently turn-based — concurrency isn't a correctness problem within ONE game. The only real concern is many concurrent GAMES on a server, which is solved by per-game locking (or per-game ChessGame instances, which is what you'd usually do)."*

### 3. Testing — what to write tests for

| Test category                | Cases to cover                                                                                              |
| ---------------------------- | ----------------------------------------------------------------------------------------------------------- |
| Starting position            | All 32 pieces in correct squares; whose turn (WHITE)                                                        |
| **Per-piece movement**       | Each of 6 pieces: 1 valid + 1 invalid move in each direction                                                |
| Pawn — initial 2-step        | Allowed only on first move; intermediate square must be empty                                              |
| Pawn — capture diagonal      | Allowed only if enemy on diagonal target; rejected if empty                                                |
| Sliders — path blocked       | Rook/Bishop/Queen attempting to move through a piece → rejected                                            |
| Sliders — capture            | Slider attempting to land on enemy → allowed; on own color → rejected                                      |
| Knight — jumps               | Knight from b1 to c3 at game start succeeds (jumps over pawns)                                            |
| **Wrong turn**               | Black moving first → rejected; white moving twice → rejected                                              |
| **King-safety**              | Move that exposes own king → rejected, no state change                                                     |
| **Checkmate (Fool's Mate)**  | The 2-move sequence ends with status == CHECKMATE                                                          |
| Stalemate                    | Construct a position where opponent has no legal move but isn't in check → STALEMATE                       |
| Move after game-over         | Any move attempted after CHECKMATE/STALEMATE → throws                                                      |

```java
@Test
void fools_mate_results_in_checkmate() {
    ChessGame g = new ChessGame();
    g.makeMove(new Move(of("f2"), of("f3")));
    g.makeMove(new Move(of("e7"), of("e5")));
    g.makeMove(new Move(of("g2"), of("g4")));
    g.makeMove(new Move(of("d8"), of("h4")));
    assertEquals(ChessGame.GameStatus.CHECKMATE, g.getStatus());
}
```

### 4. SOLID mapping

| Letter                       | Where it shows up                                                                                          |
| ---------------------------- | ---------------------------------------------------------------------------------------------------------- |
| **S** Single Responsibility  | Piece subclasses = piece-level rules. Board = physical state + read helpers. ChessGame = game-level invariants. Each layer has ONE reason to change. |
| **O** Open/Closed            | New piece type = new Piece subclass. Board and ChessGame are unchanged. (This is the headline win of the polymorphic design.) |
| **L** Liskov Substitution    | Every Piece subclass honors the same `isValidMove(Move, Board)` contract. A `Pawn` is always substitutable for `Piece` — same return semantics. |
| **I** Interface Segregation  | Piece interface is narrow: `isValidMove`, `getColor`, `hasMoved`. Board interface is narrow: physical-state ops + a few read helpers. ChessGame doesn't expose internals. |
| **D** Dependency Inversion   | Board depends on the abstract `Piece`, never on concrete subclasses. ChessGame depends on Board and Piece via the abstractions. |

### 5. "Summarize your design in 30 seconds"

> *"Three layers: **Piece hierarchy** with abstract base + 6 concrete subclasses (Pawn, Knight, Bishop, Rook, Queen, King) — each overrides `isValidMove`. The Board NEVER branches on piece type; it calls polymorphically. **Board** owns physical 8×8 state — `pieceAt`, `applyMove`, `undoMove`, `kingPosition`, `isSquareAttackedBy`. The last one walks every enemy piece and asks 'can you reach this square?' via Piece.isValidMove — pure polymorphism. **ChessGame** is the orchestrator + facade — turn enforcement, king-safety, checkmate/stalemate detection. Validation is strict-order: game-over → piece-exists → right-turn → piece-legal → king-safety. King-safety uses try/peek/undo on Board — apply, check `isSquareAttackedBy(king, opposite)`, undo if attacked. Checkmate = in-check + no legal move; stalemate = not-in-check + no legal move. `hasAnyLegalMove` does the same try/peek/undo dance over every candidate move. The driver verifies all this with Fool's Mate (the fastest checkmate in chess — 2 moves) ending in CHECKMATE status. Extensions: castling/en-passant/promotion as `Move` subtypes, AI as a `Player` Strategy, PGN export, time controls via injected Clock."*

That's ~60 seconds. Hits: polymorphism + 3 layers + the try/undo invariant + Fool's Mate proof.

---

## Closing soundbites (memorize these)

- **Opening:** *"Full chess is a multi-week project — let me scope this tight to piece movements + turn + check/checkmate. Castling, en passant, promotion explicit out of scope."*
- **Why polymorphism on Piece:** *"Each piece has materially DIFFERENT movement rules. An enum + switch is the anti-pattern. Polymorphism delegates rule to the subclass; Board never branches on type. Open-Closed + Liskov simultaneously."*
- **Why three layers:** *"Piece = piece-level legality (geometry). Board = physical state + read helpers. ChessGame = game-level invariants (turn, king-safety). One responsibility each."*
- **Why `pathIsClearAndTargetCapturable`:** *"Rook + Bishop + Queen all slide — same path-walk + destination-check logic. Extract once, reuse three times."*
- **Why try/peek/undo:** *"King-safety = 'would this move leave my king attacked?'. Easiest correct way: apply, check, undo. Board.applyMove and undoMove are pure state mutators — no validation in them — which is what makes this dance cheap."*
- **Why `isSquareAttackedBy` works:** *"It uses Piece.isValidMove reflexively — walks every enemy piece, asks if it could move here. Pure polymorphism payoff."*
- **Checkmate vs stalemate:** *"Checkmate = in-check + no move. Stalemate = NOT in check + no move. Get the boolean order right or you'll declare stalemate when the player is mated."*
- **On extensibility:** *"Castling + en passant + promotion as `Move` subtypes with custom apply logic. AI as a Player Strategy. PGN export as a separate formatter."*

---

## Top mistakes that lose points

- **Single `Piece` class with a `type` enum + switch** — the anti-pattern. Polymorphism is the whole point.
- **Board branches on piece type** (`if (piece instanceof Knight) ...`) — defeats polymorphism.
- **Forgetting the intermediate-square check on pawn's 2-step** — you'll allow a pawn to jump over a blocker.
- **Pawn captures forward / moves diagonally** — flipping the geometries silently breaks captures.
- **Sliding path-walk includes the destination** — should walk UP TO but NOT INCLUDING the destination; destination is checked separately for capturability.
- **Knight's path-clear check** — knight JUMPS. No path check. Skipping this is fine; ADDING it (and then having the knight blocked by its own pawn from b1→c3) is the bug.
- **King-safety with apply-then-keep-checking-the-board** — you must UNDO the move; otherwise consecutive king-safety checks see corrupted state.
- **Checkmate vs stalemate swap** — checkmate is IN check + no move; stalemate is NOT in check + no move. Read each condition carefully.
- **Forgetting to mark `hasMoved`** — pawn 2-step keeps being legal forever; castling extension breaks later.
- **Drift into full feature set in the 45 min** — castling, en passant, promotion all together = too much. Defer explicitly.
- **No bound checks on Position** — `Position(-1, 8)` slipping through silently.

---

## Files in this folder (your reference implementation)

| File                                              | What it shows                                                                            |
| ------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| `model/Color.java`                                | Enum + `opposite()`                                                                       |
| `model/Position.java`                             | Record with bounds-check + algebraic notation helper                                     |
| `model/Move.java`                                 | Record — from + to; rejects from==to                                                     |
| `piece/Piece.java`                                | Abstract base — color + hasMoved + abstract isValidMove                                  |
| `piece/Pawn.java`                                 | Forward 1 / initial 2-step / diagonal capture                                            |
| `piece/Knight.java`                               | L-shape; JUMPS (no path check)                                                            |
| `piece/Bishop.java`                               | Diagonal slider                                                                           |
| `piece/Rook.java`                                 | Orthogonal slider                                                                         |
| `piece/Queen.java`                                | Rook + Bishop combined                                                                    |
| `piece/King.java`                                 | 1 square any direction                                                                    |
| `piece/PieceHelpers.java`                         | Package-private utility — pathIsClearAndTargetCapturable for sliders                     |
| `Board.java`                                      | 8×8 grid + applyMove/undoMove + kingPosition + isSquareAttackedBy                       |
| `ChessGame.java`                                  | **The orchestrator** — turn enforcement + king-safety + checkmate/stalemate detection    |
| `ChessGameDriver.java`                            | 7 scenarios — starting position / pawn / knight / bishop blocked / wrong turn / king-safety / **Fool's Mate → CHECKMATE** |

Run from the project root:

```bash
mvn -q compile exec:java \
  -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.chess.ChessGameDriver
```
