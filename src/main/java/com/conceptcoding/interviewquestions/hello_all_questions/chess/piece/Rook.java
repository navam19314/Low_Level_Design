package com.conceptcoding.interviewquestions.hello_all_questions.chess.piece;

import com.conceptcoding.interviewquestions.hello_all_questions.chess.Board;
import com.conceptcoding.interviewquestions.hello_all_questions.chess.model.Color;
import com.conceptcoding.interviewquestions.hello_all_questions.chess.model.Move;

/** Rook — horizontal and vertical, any distance, path must be clear. */
public class Rook extends Piece {

    public Rook(Color color) { super(color); }
    @Override public String symbol() { return "♖"; }

    @Override
    public boolean isValidMove(Move move, Board board) {
        int dRow = move.to().row() - move.from().row();
        int dCol = move.to().col() - move.from().col();

        // Must move purely horizontally OR purely vertically.
        if (dRow != 0 && dCol != 0) return false;

        // Path must be clear; destination must be empty OR enemy (capture).
        return PieceHelpers.pathIsClearAndTargetCapturable(move, board, this);
    }
}
