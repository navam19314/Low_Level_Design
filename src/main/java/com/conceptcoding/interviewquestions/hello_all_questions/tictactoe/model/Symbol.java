package com.conceptcoding.interviewquestions.hello_all_questions.tictactoe.model;

public enum Symbol {
    X, O;

    public Symbol opposite() {
        return this == X ? O : X;
    }
}
