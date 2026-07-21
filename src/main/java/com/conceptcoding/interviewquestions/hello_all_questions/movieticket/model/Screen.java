package com.conceptcoding.interviewquestions.hello_all_questions.movieticket.model;

import java.util.ArrayList;
import java.util.List;

// A physical screen inside a theater. Multiple shows run on the same screen
// through the day (10 AM, 1 PM, 4 PM). Uniform seat layout for v1 —
// variable per-screen layouts is a Step-5 extension (rows/cols move here).
public class Screen {

    private final String id;
    private final String name;
    private final List<Showtime> showtimes;

    public Screen(String id, String name) {
        this.id = id;
        this.name = name;
        this.showtimes = new ArrayList<>();
    }

    public String         getId()        { return id; }
    public String         getName()      { return name; }
    public List<Showtime> getShowtimes() { return showtimes; }

    public void addShowtime(Showtime showtime) { showtimes.add(showtime); }
}
