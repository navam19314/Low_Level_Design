package com.conceptcoding.interviewquestions.hello_all_questions.movieticket.model;

import java.util.ArrayList;
import java.util.List;

// Named location that owns its screens. Each screen hosts multiple showtimes
// through the day. Showtimes are attached to Screens, not Theaters directly.
public class Theater {

    private final String id;
    private final String name;
    private final List<Screen> screens;

    public Theater(String id, String name) {
        this.id = id;
        this.name = name;
        this.screens = new ArrayList<>();
    }

    public String       getId()      { return id; }
    public String       getName()    { return name; }
    public List<Screen> getScreens() { return screens; }

    public void addScreen(Screen screen) { screens.add(screen); }

    // Convenience — flatten all showtimes across all screens.
    public List<Showtime> getShowtimes() {
        List<Showtime> all = new ArrayList<>();
        for (Screen screen : screens) {
            all.addAll(screen.getShowtimes());
        }
        return all;
    }
}
