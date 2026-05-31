package com.conceptcoding.interviewquestions.hello_all_questions.connectfour.model;

public class Player {

    private final String name;
    private final DiscColor color;

    public Player(String name, DiscColor color) {
        this.name = name;
        this.color = color;
    }

    public String getName() {
        return name;
    }

    public DiscColor getColor() {
        return color;
    }
}
