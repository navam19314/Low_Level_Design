package com.conceptcoding.interviewquestions.hello_all_questions.chess.model;

/**
 * 0-indexed (row, col) on an 8×8 board. row=0 is rank 1 (white's back rank);
 * col=0 is file 'a'. Standard internal representation; toString() prints the
 * algebraic notation ("a1", "h8") for readability.
 */
public record Position(int row, int col) {

    public Position {
        if (row < 0 || row > 7) throw new IllegalArgumentException("row out of bounds: " + row);
        if (col < 0 || col > 7) throw new IllegalArgumentException("col out of bounds: " + col);
    }

    /** Algebraic notation: a1..h8 */
    public String algebraic() {
        return "" + (char) ('a' + col) + (1 + row);
    }

    public static Position of(String algebraic) {
        if (algebraic == null || algebraic.length() != 2) {
            throw new IllegalArgumentException("bad notation: " + algebraic);
        }
        int col = algebraic.charAt(0) - 'a';
        int row = algebraic.charAt(1) - '1';
        return new Position(row, col);
    }

    @Override public String toString() { return algebraic(); }
}
