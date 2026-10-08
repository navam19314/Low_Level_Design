package com.conceptcoding.interviewquestions.hello_all_questions.games.chess.model;

import java.util.Objects;

// 0-indexed (row, col) on an 8×8 board. row 0 = rank 1 (white's back rank), col 0 = file 'a'.
// toString() prints algebraic notation ("e4"). equals/hashCode so positions work as map keys.
public final class Position {

    private final int row;
    private final int col;

    public Position(int row, int col) {
        if (row < 0 || row > 7) throw new IllegalArgumentException("row out of bounds: " + row);
        if (col < 0 || col > 7) throw new IllegalArgumentException("col out of bounds: " + col);
        this.row = row;
        this.col = col;
    }

    public int row() { return row; }
    public int col() { return col; }

    public String algebraic() { return "" + (char) ('a' + col) + (1 + row); }

    public static Position of(String algebraic) {
        if (algebraic == null || algebraic.length() != 2) throw new IllegalArgumentException("bad notation: " + algebraic);
        return new Position(algebraic.charAt(1) - '1', algebraic.charAt(0) - 'a');
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Position)) return false;
        Position p = (Position) o;
        return row == p.row && col == p.col;
    }

    @Override public int hashCode()    { return Objects.hash(row, col); }
    @Override public String toString() { return algebraic(); }
}
