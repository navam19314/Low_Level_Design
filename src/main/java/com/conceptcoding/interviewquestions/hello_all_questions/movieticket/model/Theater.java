package com.conceptcoding.interviewquestions.hello_all_questions.movieticket.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Named location that owns its list of showtimes. Showtimes are added AFTER
 * construction because each Showtime needs a back-reference to its Theater —
 * classic chicken-and-egg solved with two-phase setup.
 */
public class Theater {

    private final String id;
    private final String name;
    private final List<Showtime> showtimes;

    public Theater(String id, String name) {
        this.id = id;
        this.name = name;
        this.showtimes = new ArrayList<>();
    }

    public String getId()   { return id; }
    public String getName() { return name; }

    public List<Showtime> getShowtimes() {
        return showtimes;
    }

    public void addShowtime(Showtime showtime) {
        showtimes.add(showtime);
    }

    public List<Showtime> getShowtimesForMovie(Movie movie) {
        List<Showtime> result = new ArrayList<>();
        for (Showtime s : showtimes) {
            if (s.getMovie().getId().equals(movie.getId())) {
                result.add(s);
            }
        }
        return result;
    }
}
