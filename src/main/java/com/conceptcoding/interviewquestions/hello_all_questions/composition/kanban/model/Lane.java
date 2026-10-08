package com.conceptcoding.interviewquestions.hello_all_questions.composition.kanban.model;

import java.util.ArrayList;
import java.util.List;

// A column ("To Do", "Doing", "Done"). Cards are kept in display order.
// Package-private mutators: only Board changes a lane, under the board's lock.
public class Lane {

    private final String id;
    private String name;
    private final int wipLimit;                    // max cards allowed; 0 = no limit
    private final List<Card> cards = new ArrayList<>();

    public Lane(String id, String name, int wipLimit) {
        if (wipLimit < 0) throw new IllegalArgumentException("WIP limit must be >= 0");
        this.id = id;
        this.name = name;
        this.wipLimit = wipLimit;
    }

    void insert(Card card, int position) { cards.add(position, card); }
    void remove(Card card)               { cards.remove(card); }
    void rename(String name)             { this.name = name; }

    boolean isFull()            { return wipLimit > 0 && cards.size() >= wipLimit; }
    int     size()              { return cards.size(); }
    int     indexOf(Card card)  { return cards.indexOf(card); }

    public String     getId()       { return id; }
    public String     getName()     { return name; }
    public int        getWipLimit() { return wipLimit; }
    public List<Card> getCards()    { return new ArrayList<>(cards); }
}
