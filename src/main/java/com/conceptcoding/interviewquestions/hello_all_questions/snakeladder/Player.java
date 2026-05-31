package com.conceptcoding.interviewquestions.hello_all_questions.snakeladder;

/**
 * A player has identity + a position on the board (0 = off-board / start).
 *
 * <p>Mutable on purpose — the position changes every turn. Identity comes from
 * the {@code id} field (used in equals/hashCode if we ever store Players in sets).
 */
public class Player {
    private final String id;
    private final String name;
    private int position;

    public Player(String id, String name) {
        this.id = id;
        this.name = name;
        this.position = 0;
    }

    public String getId()       { return id; }
    public String getName()     { return name; }
    public int    getPosition() { return position; }

    void setPosition(int position) { this.position = position; }

    @Override
    public String toString() {
        return name + "@" + position;
    }
}
