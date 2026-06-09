package com.conceptcoding.interviewquestions.hello_all_questions.tictactoe.model;

public class Player {

    private final String name;
    private final Symbol mark;

    public Player(String name, Symbol mark) {
        this.name = name;
        this.mark = mark;
    }

    public String getName() { return name; }
    public Symbol getMark() { return mark; }

    @Override
    public String toString() {
        return name + "(" + mark + ")";
    }
}
