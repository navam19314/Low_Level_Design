package com.conceptcoding.interviewquestions.hello_all_questions.games.chess.model;

// Immutable request: "move whatever is on 'from' to 'to'". Carries no piece reference:
// the board looks the piece up when the move is made.
public final class Move {

    private final Position from;
    private final Position to;

    public Move(Position from, Position to) {
        if (from.equals(to)) throw new IllegalArgumentException("Move from == to is degenerate");
        this.from = from;
        this.to = to;
    }

    public Position from() { return from; }
    public Position to()   { return to; }

    @Override public String toString() { return from + "→" + to; }
}
