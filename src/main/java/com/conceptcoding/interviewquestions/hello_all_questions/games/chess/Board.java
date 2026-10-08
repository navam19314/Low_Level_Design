package com.conceptcoding.interviewquestions.hello_all_questions.games.chess;

import com.conceptcoding.interviewquestions.hello_all_questions.games.chess.model.Color;
import com.conceptcoding.interviewquestions.hello_all_questions.games.chess.model.Move;
import com.conceptcoding.interviewquestions.hello_all_questions.games.chess.model.Position;
import com.conceptcoding.interviewquestions.hello_all_questions.games.chess.piece.Bishop;
import com.conceptcoding.interviewquestions.hello_all_questions.games.chess.piece.King;
import com.conceptcoding.interviewquestions.hello_all_questions.games.chess.piece.Knight;
import com.conceptcoding.interviewquestions.hello_all_questions.games.chess.piece.Pawn;
import com.conceptcoding.interviewquestions.hello_all_questions.games.chess.piece.Piece;
import com.conceptcoding.interviewquestions.hello_all_questions.games.chess.piece.Queen;
import com.conceptcoding.interviewquestions.hello_all_questions.games.chess.piece.Rook;

// 8×8 chess board. Owns the piece array; exposes pieceAt (pure read),
// applyMove (mutates), and the isSquareAttackedBy helper used
// by check detection.
//
// Movement RULES live on each Piece subclass (polymorphism — Piece.isValidMove).
// GAME RULES (turn order, leaving own king in check) live on ChessGame.
// Board's responsibility: physical state + a few read helpers.
public class Board {

    private final Piece[][] squares = new Piece[8][8];

    // Setup the standard starting position.
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

    // Place / replace a piece at p. Used by setup helpers + tests.
    public void setPieceAt(Position p, Piece piece) {
        squares[p.row()][p.col()] = piece;
    }

    // Apply a move WITHOUT validation — caller (ChessGame) must have already
    // checked piece-level + game-level legality. Captured piece is returned
    // so ChessGame can record it (or null if empty).
    public Piece applyMove(Move move) {
        Piece moving   = squares[move.from().row()][move.from().col()];
        Piece captured = squares[move.to().row()][move.to().col()];
        squares[move.to().row()][move.to().col()] = moving;
        squares[move.from().row()][move.from().col()] = null;
        // NOT marking the piece as moved here: ChessGame also try-moves pieces to look for check,
        // and those experiments must leave no trace. ChessGame marks moved only on a committed move.
        return captured;
    }

    // Reverse of applyMove — used by ChessGame's "would this leave my king in check?" peek.
    public void undoMove(Move move, Piece captured) {
        Piece moving = squares[move.to().row()][move.to().col()];
        squares[move.from().row()][move.from().col()] = moving;
        squares[move.to().row()][move.to().col()] = captured;
    }

    // Find the king of a color. Linear scan is fine — 64 squares.
    public Position kingPosition(Color color) {
        for (int r = 0; r < 8; r++)
            for (int c = 0; c < 8; c++) {
                Piece p = squares[r][c];
                if (p instanceof King && p.getColor() == color) return new Position(r, c);
            }
        throw new IllegalStateException("No " + color + " king on board");
    }

    // Is target attacked by any piece of attackerColor?
    // Iterate every attacker piece on the board and ask "could you legally
    // move to target?". Used by ChessGame for check detection.
    public boolean isSquareAttackedBy(Position target, Color attackerColor) {
        for (int r = 0; r < 8; r++)
            for (int c = 0; c < 8; c++) {
                Piece p = squares[r][c];
                if (p == null || p.getColor() != attackerColor) continue;
                if (r == target.row() && c == target.col()) continue;     // a Move can't start and end on one square
                if (p.isValidMove(new Move(new Position(r, c), target), this)) return true;
            }
        return false;
    }

    // Compact text rendering for debugging.
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
