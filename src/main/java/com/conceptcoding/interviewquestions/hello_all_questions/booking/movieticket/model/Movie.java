package com.conceptcoding.interviewquestions.hello_all_questions.booking.movieticket.model;

public class Movie {

    private final String id;
    private final String title;

    public Movie(String id, String title) {
        this.id = id;
        this.title = title;
    }

    public String getId()    { return id; }
    public String getTitle() { return title; }
}
