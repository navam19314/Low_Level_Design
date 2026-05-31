package com.conceptcoding.interviewquestions.hello_all_questions.chess.model;

/**
 * Immutable request: "move the piece at {@code from} to {@code to}".
 * Does NOT carry a piece reference — the Board looks it up at execution time
 * so the Move object stays as a plain coordinate pair (no aliasing concerns).
 */
public record Move(Position from, Position to) {

    public Move {
        if (from.equals(to)) {
            throw new IllegalArgumentException("Move from == to is degenerate");
        }
    }

    @Override public String toString() { return from + "→" + to; }
}
