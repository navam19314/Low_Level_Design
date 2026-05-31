package com.conceptcoding.interviewquestions.hello_all_questions.chess.piece;

import com.conceptcoding.interviewquestions.hello_all_questions.chess.Board;
import com.conceptcoding.interviewquestions.hello_all_questions.chess.model.Color;
import com.conceptcoding.interviewquestions.hello_all_questions.chess.model.Move;

/** Bishop — diagonal only, any distance, path must be clear. */
public class Bishop extends Piece {

    public Bishop(Color color) { super(color); }
    @Override public String symbol() { return "♗"; }

    @Override
    public boolean isValidMove(Move move, Board board) {
        int dRow = move.to().row() - move.from().row();
        int dCol = move.to().col() - move.from().col();

        // Must be a true diagonal — |dRow| == |dCol|.
        if (Math.abs(dRow) != Math.abs(dCol)) return false;

        return PieceHelpers.pathIsClearAndTargetCapturable(move, board, this);
    }
}
