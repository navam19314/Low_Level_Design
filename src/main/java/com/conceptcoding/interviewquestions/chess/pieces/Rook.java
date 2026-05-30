package com.conceptcoding.interviewquestions.chess.pieces;

import com.conceptcoding.interviewquestions.chess.Board;
import com.conceptcoding.interviewquestions.chess.Cell;
import com.conceptcoding.interviewquestions.chess.enums.PieceColour;
import com.conceptcoding.interviewquestions.chess.enums.PieceType;

public class Rook extends Piece {

    public Rook(PieceColour colour) {
        super(colour, PieceType.ROOK);
    }

    @Override
    public boolean isValidMove(Board board, Cell start, Cell end) {
        if (start.equals(end)) return false;

        // If the target cell has piece of same colour, then it is invalid move
        if (end.getPiece() != null && end.getPiece().getColour() == start.getPiece().getColour()) return false;

        // If the target cell is not horizontal or vertical to the start cell, then it is invalid move
        int rowDiff = Math.abs(end.getPosition().getRow() - start.getPosition().getRow());
        int colDiff = Math.abs(end.getPosition().getCol() - start.getPosition().getCol());

        if ((rowDiff == 0 || colDiff == 0) && isPathClear(board, start, end)) {
            Piece targetPiece = board.getCell(end.getPosition()).getPiece();
            return targetPiece == null || targetPiece.getColour() != start.getPiece().getColour();
        }
        return false;
    }
}