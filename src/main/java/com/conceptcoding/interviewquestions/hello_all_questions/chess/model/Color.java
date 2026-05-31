package com.conceptcoding.interviewquestions.hello_all_questions.chess.model;

public enum Color {
    WHITE,
    BLACK;

    public Color opposite() {
        return this == WHITE ? BLACK : WHITE;
    }
}
