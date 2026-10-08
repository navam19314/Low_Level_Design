package com.conceptcoding.interviewquestions.hello_all_questions.games.chess.piece;

import com.conceptcoding.interviewquestions.hello_all_questions.games.chess.Board;
import com.conceptcoding.interviewquestions.hello_all_questions.games.chess.model.Color;
import com.conceptcoding.interviewquestions.hello_all_questions.games.chess.model.Move;

// Abstract base for every chess piece. Each subclass overrides
// isValidMove with that piece's movement rules. Polymorphism handles
// dispatch — the Board never branches on piece type.
//
// Pieces are mutable on hasMoved (used for pawn's initial
// two-step move, and would be used for castling if we added it).
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

    // Whether this piece can legally move from move.from() to move.to()
    // on the given board.
    //
    // Validation checked HERE: piece-specific movement geometry, path blockers,
    // and "destination not occupied by own color".
    // NOT checked here: turn order, leaving own king in check — those are
    // Board / ChessGame concerns.
    public abstract boolean isValidMove(Move move, Board board);

    @Override public String toString() {
        return (color == Color.WHITE ? "w" : "b") + symbol();
    }
}
