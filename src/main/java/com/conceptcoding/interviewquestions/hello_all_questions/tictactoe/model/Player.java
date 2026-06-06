package com.conceptcoding.interviewquestions.hello_all_questions.tictactoe.model;

public record Player(String name, Symbol mark) {

    @Override public String toString() {
        return name + "(" + mark + ")";
    }
}
