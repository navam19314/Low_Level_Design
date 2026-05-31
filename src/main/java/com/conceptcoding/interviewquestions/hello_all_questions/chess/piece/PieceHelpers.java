package com.conceptcoding.interviewquestions.hello_all_questions.chess.piece;

import com.conceptcoding.interviewquestions.hello_all_questions.chess.Board;
import com.conceptcoding.interviewquestions.hello_all_questions.chess.model.Move;
import com.conceptcoding.interviewquestions.hello_all_questions.chess.model.Position;

/**
 * Shared piece-movement helpers. Used by Rook, Bishop, Queen — pieces that
 * slide along a straight line and need the path-clear + capturable-target check.
 *
 * <p>Knight skips this because it jumps over pieces; King has at most a 1-square
 * step so there's no path to check.
 */
final class PieceHelpers {

    private PieceHelpers() {}

    /**
     * Walk from {@code move.from()} toward {@code move.to()} ONE STEP AT A TIME,
     * stopping just BEFORE the destination. If any intermediate square has a
     * piece, the path is blocked → false. Then check the destination itself —
     * empty or enemy → true; own color → false.
     */
    static boolean pathIsClearAndTargetCapturable(Move move, Board board, Piece self) {
        int dRow = Integer.signum(move.to().row() - move.from().row());
        int dCol = Integer.signum(move.to().col() - move.from().col());

        int curRow = move.from().row() + dRow;
        int curCol = move.from().col() + dCol;
        while (curRow != move.to().row() || curCol != move.to().col()) {
            if (board.pieceAt(new Position(curRow, curCol)) != null) return false;
            curRow += dRow;
            curCol += dCol;
        }

        // Destination square — empty OR enemy (then capture); own color → false
        Piece target = board.pieceAt(move.to());
        return target == null || target.getColor() != self.getColor();
    }
}
