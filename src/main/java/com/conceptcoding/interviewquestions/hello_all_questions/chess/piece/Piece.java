package com.conceptcoding.interviewquestions.hello_all_questions.chess.piece;

import com.conceptcoding.interviewquestions.hello_all_questions.chess.Board;
import com.conceptcoding.interviewquestions.hello_all_questions.chess.model.Color;
import com.conceptcoding.interviewquestions.hello_all_questions.chess.model.Move;

/**
 * Abstract base for every chess piece. Each subclass overrides
 * {@link #isValidMove} with that piece's movement rules. Polymorphism handles
 * dispatch — the Board never branches on piece type.
 *
 * <p>Pieces are <b>mutable</b> on {@code hasMoved} (used for pawn's initial
 * two-step move, and would be used for castling if we added it).
 */
public abstract class Piece {

    private final Color color;
    private boolean hasMoved = false;

    protected Piece(Color color) {
        this.color = color;
    }

    public Color   getColor()   { return color; }
    public boolean hasMoved()   { return hasMoved; }
    public void    markMoved()  { this.hasMoved = true; }

    public abstract String symbol();   // for board printing (♙♖♘♗♕♔)

    /**
     * Whether this piece can legally move from {@code move.from()} to {@code move.to()}
     * on the given board.
     *
     * <p>Validation checked HERE: piece-specific movement geometry, path blockers,
     * and "destination not occupied by own color".
     * <p>NOT checked here: turn order, leaving own king in check — those are
     * Board / ChessGame concerns.
     */
    public abstract boolean isValidMove(Move move, Board board);

    @Override public String toString() {
        return (color == Color.WHITE ? "w" : "b") + symbol();
    }
}
