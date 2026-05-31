package com.conceptcoding.interviewquestions.hello_all_questions.chess.piece;

import com.conceptcoding.interviewquestions.hello_all_questions.chess.Board;
import com.conceptcoding.interviewquestions.hello_all_questions.chess.model.Color;
import com.conceptcoding.interviewquestions.hello_all_questions.chess.model.Move;
import com.conceptcoding.interviewquestions.hello_all_questions.chess.model.Position;

/**
 * Pawn — the trickiest piece because move and capture geometries differ.
 *
 * <ul>
 *   <li>Forward 1 — if destination empty.</li>
 *   <li>Forward 2 from starting rank — if BOTH the intermediate square AND
 *       destination are empty (and the pawn hasn't moved).</li>
 *   <li>Diagonal 1 — only if destination has an enemy piece (capture).</li>
 * </ul>
 *
 * <p>Out of scope (mention in Step 5): en passant, promotion.
 */
public class Pawn extends Piece {

    public Pawn(Color color) { super(color); }
    @Override public String symbol() { return "♙"; }

    @Override
    public boolean isValidMove(Move move, Board board) {
        Position from = move.from(), to = move.to();
        int dir       = (getColor() == Color.WHITE) ? +1 : -1;   // white moves up, black moves down
        int dRow      = to.row() - from.row();
        int dCol      = to.col() - from.col();
        Piece target  = board.pieceAt(to);

        // 1) Forward 1
        if (dCol == 0 && dRow == dir && target == null) return true;

        // 2) Forward 2 from initial rank (hasMoved == false)
        if (dCol == 0 && dRow == 2 * dir && !hasMoved()
                && target == null
                && board.pieceAt(new Position(from.row() + dir, from.col())) == null) {
            return true;
        }

        // 3) Diagonal capture — must be exactly one row in forward dir + one col either side
        if (Math.abs(dCol) == 1 && dRow == dir
                && target != null && target.getColor() != getColor()) {
            return true;
        }

        return false;
    }
}
