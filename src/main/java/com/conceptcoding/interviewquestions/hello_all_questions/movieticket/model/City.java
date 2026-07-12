package com.conceptcoding.interviewquestions.hello_all_questions.movieticket.model;

import java.util.ArrayList;
import java.util.List;

// A city groups theaters. BookMyShow-style: users pick a city, then browse
// or search within it.
public class City {

    private final String id;
    private final String name;
    private final List<Theater> theaters;

    public City(String id, String name) {
        this.id = id;
        this.name = name;
        this.theaters = new ArrayList<>();
    }

    public String        getId()       { return id; }
    public String        getName()     { return name; }
    public List<Theater> getTheaters() { return theaters; }

    public void addTheater(Theater theater) { theaters.add(theater); }

    // Flatten all showtimes across theaters. Theater does its own flatten across screens.
    public List<Showtime> getAllShowtimes() {
        List<Showtime> all = new ArrayList<>();
        for (Theater theater : theaters) {
            all.addAll(theater.getShowtimes());
        }
        return all;
    }

    // Search: delegate to each theater. City never reaches through theaters into screens.
    public List<Showtime> findShowtimesByTitle(String query) {
        List<Showtime> result = new ArrayList<>();
        for (Theater theater : theaters) {
            result.addAll(theater.findShowtimesByTitle(query));
        }
        return result;
    }
}
