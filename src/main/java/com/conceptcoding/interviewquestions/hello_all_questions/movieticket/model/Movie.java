package com.conceptcoding.interviewquestions.hello_all_questions.movieticket.model;

// Searchable entity. ID + title; immutable once constructed.
// ID exists because two movies might share a title (remakes, re-releases).
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
