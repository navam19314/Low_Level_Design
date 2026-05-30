package com.conceptcoding.interviewquestions.book_my_show.entities;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Screen {

    private final int screenId;
    private final List<Seat> seats;
    private final Map<LocalDate, List<Show>> showsByDate = new HashMap<>();

    public Screen(int screenId, List<Seat> seats) {
        this.screenId = screenId;
        this.seats = seats;
    }

    public List<Seat> getSeats() {
        return seats;
    }

    public void addShow(Show show) {
        LocalDate showDate = show.getShowDate();
        if (!showsByDate.containsKey(showDate)) {
            showsByDate.put(showDate, new ArrayList<>());
        }
        showsByDate.get(showDate).add(show);
    }

    public List<Show> getShows(LocalDate date) {
        return showsByDate.getOrDefault(date, List.of());
    }
}


