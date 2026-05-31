package com.conceptcoding.interviewquestions.hello_all_questions.chess;

import com.conceptcoding.interviewquestions.hello_all_questions.chess.model.Color;
import com.conceptcoding.interviewquestions.hello_all_questions.chess.model.Move;
import com.conceptcoding.interviewquestions.hello_all_questions.chess.model.Position;
import com.conceptcoding.interviewquestions.hello_all_questions.chess.piece.Piece;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrator + facade. Enforces:
 *   - turn order (white first, alternating)
 *   - piece-level move legality (delegates to {@code Piece.isValidMove})
 *   - king-safety (a move must not leave your OWN king in check)
 *
 * <p>The piece-class hierarchy already gives us "where can this piece move?";
 * the king-safety check ("does my king end up in check?") needs a try-move +
 * peek-attacked + undo dance. We implement that with {@code Board.applyMove}
 * + {@code Board.undoMove}.
 *
 * <p>Out of scope: castling, en passant, promotion, 50-move rule, threefold
 * repetition, stalemate (only checkmate detected; stalemate would use the same
 * "no legal move available" check + "not currently in check").
 */
public class ChessGame {

    private final Board board = new Board();
    private final List<Move> moveHistory = new ArrayList<>();
    private Color currentTurn = Color.WHITE;
    private GameStatus status = GameStatus.IN_PROGRESS;

    public enum GameStatus { IN_PROGRESS, CHECK, CHECKMATE, STALEMATE }

    public ChessGame() {
        board.setupStandard();
    }

    public Board       getBoard()       { return board; }
    public Color       getCurrentTurn() { return currentTurn; }
    public GameStatus  getStatus()      { return status; }
    public List<Move>  getMoveHistory() { return new ArrayList<>(moveHistory); }

    /**
     * Attempt a move. Returns {@code true} on success; throws with a specific
     * reason on rejection.
     */
    public boolean makeMove(Move move) {
        if (status == GameStatus.CHECKMATE || status == GameStatus.STALEMATE) {
            throw new IllegalStateException("Game is over");
        }
        Piece moving = board.pieceAt(move.from());
        if (moving == null)
            throw new IllegalArgumentException("No piece at " + move.from());
        if (moving.getColor() != currentTurn)
            throw new IllegalArgumentException("Not " + moving.getColor() + "'s turn");
        if (!moving.isValidMove(move, board))
            throw new IllegalArgumentException("Illegal " + moving.getClass().getSimpleName() + " move " + move);

        // King-safety check — temporarily apply, see if our king is attacked, then undo.
        Piece captured = board.applyMove(move);
        Position myKing = board.kingPosition(currentTurn);
        boolean kingInCheck = board.isSquareAttackedBy(myKing, currentTurn.opposite());
        if (kingInCheck) {
            board.undoMove(move, captured);
            throw new IllegalArgumentException("Move would leave your king in check");
        }

        moveHistory.add(move);

        // Compute opponent status — are they in check? Have they any legal moves?
        Color opponent = currentTurn.opposite();
        boolean opponentInCheck = board.isSquareAttackedBy(board.kingPosition(opponent), currentTurn);
        boolean opponentHasMove = hasAnyLegalMove(opponent);

        if (!opponentHasMove && opponentInCheck)        status = GameStatus.CHECKMATE;
        else if (!opponentHasMove && !opponentInCheck)  status = GameStatus.STALEMATE;
        else if (opponentInCheck)                        status = GameStatus.CHECK;
        else                                              status = GameStatus.IN_PROGRESS;

        currentTurn = opponent;
        return true;
    }

    /**
     * Does {@code color} have ANY legal move? Used to detect checkmate (in check + no moves)
     * and stalemate (not in check + no moves). O(64 × 64) worst case — fine for 8×8.
     */
    private boolean hasAnyLegalMove(Color color) {
        for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) {
            Piece p = board.pieceAt(new Position(r, c));
            if (p == null || p.getColor() != color) continue;
            Position from = new Position(r, c);
            for (int rr = 0; rr < 8; rr++) for (int cc = 0; cc < 8; cc++) {
                if (rr == r && cc == c) continue;
                Position to = new Position(rr, cc);
                Move m = new Move(from, to);
                if (!p.isValidMove(m, board)) continue;
                // Try-move + check own-king-safety + undo.
                Piece captured = board.applyMove(m);
                Position myKing = board.kingPosition(color);
                boolean safe = !board.isSquareAttackedBy(myKing, color.opposite());
                board.undoMove(m, captured);
                if (safe) return true;
            }
        }
        return false;
    }
}
