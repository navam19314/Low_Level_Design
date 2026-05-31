package com.conceptcoding.interviewquestions.hello_all_questions.chess.piece;

import com.conceptcoding.interviewquestions.hello_all_questions.chess.Board;
import com.conceptcoding.interviewquestions.hello_all_questions.chess.model.Color;
import com.conceptcoding.interviewquestions.hello_all_questions.chess.model.Move;

/** Knight — L-shape (2+1). Unique piece that JUMPS over others; no path check. */
public class Knight extends Piece {

    public Knight(Color color) { super(color); }
    @Override public String symbol() { return "♘"; }

    @Override
    public boolean isValidMove(Move move, Board board) {
        int dRow = Math.abs(move.to().row() - move.from().row());
        int dCol = Math.abs(move.to().col() - move.from().col());

        // Two-and-one-or-one-and-two: (2,1) or (1,2)
        boolean lShape = (dRow == 2 && dCol == 1) || (dRow == 1 && dCol == 2);
        if (!lShape) return false;

        // No path check — knight jumps. Just enforce "destination not own color".
        Piece target = board.pieceAt(move.to());
        return target == null || target.getColor() != getColor();
    }
}
