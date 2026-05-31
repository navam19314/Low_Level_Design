package com.conceptcoding.interviewquestions.hello_all_questions.chess;

import com.conceptcoding.interviewquestions.hello_all_questions.chess.model.Color;
import com.conceptcoding.interviewquestions.hello_all_questions.chess.model.Move;
import com.conceptcoding.interviewquestions.hello_all_questions.chess.model.Position;
import com.conceptcoding.interviewquestions.hello_all_questions.chess.piece.Bishop;
import com.conceptcoding.interviewquestions.hello_all_questions.chess.piece.King;
import com.conceptcoding.interviewquestions.hello_all_questions.chess.piece.Knight;
import com.conceptcoding.interviewquestions.hello_all_questions.chess.piece.Pawn;
import com.conceptcoding.interviewquestions.hello_all_questions.chess.piece.Piece;
import com.conceptcoding.interviewquestions.hello_all_questions.chess.piece.Queen;
import com.conceptcoding.interviewquestions.hello_all_questions.chess.piece.Rook;

/**
 * 8×8 chess board. Owns the piece array; exposes {@code pieceAt} (pure read),
 * {@code applyMove} (mutates), and the {@code isSquareAttackedBy} helper used
 * by check detection.
 *
 * <p>Movement RULES live on each Piece subclass (polymorphism — Piece.isValidMove).
 * GAME RULES (turn order, leaving own king in check) live on ChessGame.
 * Board's responsibility: physical state + a few read helpers.
 */
public class Board {

    private final Piece[][] squares = new Piece[8][8];

    /** Setup the standard starting position. */
    public void setupStandard() {
        for (int r = 0; r < 8; r++) for (int c = 0; c < 8; c++) squares[r][c] = null;

        // Pawns
        for (int c = 0; c < 8; c++) {
            squares[1][c] = new Pawn(Color.WHITE);
            squares[6][c] = new Pawn(Color.BLACK);
        }
        // Back ranks — same layout for both colors, mirrored
        Piece[] whiteBack = { new Rook(Color.WHITE), new Knight(Color.WHITE), new Bishop(Color.WHITE),
                              new Queen(Color.WHITE), new King(Color.WHITE),
                              new Bishop(Color.WHITE), new Knight(Color.WHITE), new Rook(Color.WHITE) };
        Piece[] blackBack = { new Rook(Color.BLACK), new Knight(Color.BLACK), new Bishop(Color.BLACK),
                              new Queen(Color.BLACK), new King(Color.BLACK),
                              new Bishop(Color.BLACK), new Knight(Color.BLACK), new Rook(Color.BLACK) };
        for (int c = 0; c < 8; c++) {
            squares[0][c] = whiteBack[c];
            squares[7][c] = blackBack[c];
        }
    }

    public Piece pieceAt(Position p) {
        return squares[p.row()][p.col()];
    }

    /** Place / replace a piece at p. Used by setup helpers + tests. */
    public void setPieceAt(Position p, Piece piece) {
        squares[p.row()][p.col()] = piece;
    }

    /**
     * Apply a move WITHOUT validation — caller (ChessGame) must have already
     * checked piece-level + game-level legality. Captured piece is returned
     * so ChessGame can record it (or null if empty).
     */
    public Piece applyMove(Move move) {
        Piece moving   = squares[move.from().row()][move.from().col()];
        Piece captured = squares[move.to().row()][move.to().col()];
        squares[move.to().row()][move.to().col()] = moving;
        squares[move.from().row()][move.from().col()] = null;
        if (moving != null) moving.markMoved();
        return captured;
    }

    /** Reverse of applyMove — used by ChessGame's "would this leave my king in check?" peek. */
    public void undoMove(Move move, Piece captured) {
        Piece moving = squares[move.to().row()][move.to().col()];
        squares[move.from().row()][move.from().col()] = moving;
        squares[move.to().row()][move.to().col()] = captured;
        // Note: hasMoved flip-back is intentionally NOT done — peek/undo is for check-detection only,
        // never user-visible. If we ever exposed real undo we'd track the previous flag separately.
    }

    /** Find the king of a color. Linear scan is fine — 64 squares. */
    public Position kingPosition(Color color) {
        for (int r = 0; r < 8; r++)
            for (int c = 0; c < 8; c++) {
                Piece p = squares[r][c];
                if (p instanceof King && p.getColor() == color) return new Position(r, c);
            }
        throw new IllegalStateException("No " + color + " king on board");
    }

    /**
     * Is {@code target} attacked by any piece of {@code attackerColor}?
     * Iterate every attacker piece on the board and ask "could you legally
     * move to target?". Used by ChessGame for check detection.
     */
    public boolean isSquareAttackedBy(Position target, Color attackerColor) {
        for (int r = 0; r < 8; r++)
            for (int c = 0; c < 8; c++) {
                Piece p = squares[r][c];
                if (p == null || p.getColor() != attackerColor) continue;
                if (p.isValidMove(new Move(new Position(r, c), target), this)) return true;
            }
        return false;
    }

    /** Compact text rendering for debugging. */
    public String render() {
        StringBuilder sb = new StringBuilder();
        for (int r = 7; r >= 0; r--) {
            sb.append(r + 1).append(' ');
            for (int c = 0; c < 8; c++) {
                Piece p = squares[r][c];
                sb.append(p == null ? ". " : p + " ");
            }
            sb.append('\n');
        }
        sb.append("  a  b  c  d  e  f  g  h\n");
        return sb.toString();
    }
}
