package com.conceptcoding.interviewquestions.hello_all_questions.chess.piece;

import com.conceptcoding.interviewquestions.hello_all_questions.chess.Board;
import com.conceptcoding.interviewquestions.hello_all_questions.chess.model.Color;
import com.conceptcoding.interviewquestions.hello_all_questions.chess.model.Move;

/** Queen — Rook + Bishop combined. Implemented by delegating to both checks. */
public class Queen extends Piece {

    public Queen(Color color) { super(color); }
    @Override public String symbol() { return "♕"; }

    @Override
    public boolean isValidMove(Move move, Board board) {
        int dRow = move.to().row() - move.from().row();
        int dCol = move.to().col() - move.from().col();

        // Either rook-like (pure horizontal/vertical) or bishop-like (true diagonal).
        boolean rookLike   = (dRow == 0) || (dCol == 0);
        boolean bishopLike = Math.abs(dRow) == Math.abs(dCol);
        if (!rookLike && !bishopLike) return false;

        return PieceHelpers.pathIsClearAndTargetCapturable(move, board, this);
    }
}
