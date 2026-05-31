package com.conceptcoding.interviewquestions.hello_all_questions.chess.piece;

import com.conceptcoding.interviewquestions.hello_all_questions.chess.Board;
import com.conceptcoding.interviewquestions.hello_all_questions.chess.model.Color;
import com.conceptcoding.interviewquestions.hello_all_questions.chess.model.Move;

/**
 * King — exactly one square in any direction (8 neighbors).
 * Out of scope (Step 5): castling.
 */
public class King extends Piece {

    public King(Color color) { super(color); }
    @Override public String symbol() { return "♔"; }

    @Override
    public boolean isValidMove(Move move, Board board) {
        int dRow = Math.abs(move.to().row() - move.from().row());
        int dCol = Math.abs(move.to().col() - move.from().col());

        // Must be at most 1 in each axis, and at least 1 in total (no null move).
        if (dRow > 1 || dCol > 1)                 return false;
        if (dRow == 0 && dCol == 0)                return false;

        Piece target = board.pieceAt(move.to());
        return target == null || target.getColor() != getColor();
    }
}
