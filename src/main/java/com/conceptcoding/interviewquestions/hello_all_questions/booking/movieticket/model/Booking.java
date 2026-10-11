package com.conceptcoding.interviewquestions.hello_all_questions.booking.movieticket.model;

import java.util.ArrayList;
import java.util.List;

// A confirmed booking. Immutable.
public class Booking {

    private final String id;
    private final String showtimeId;
    private final List<Integer> seats;

    public Booking(String id, String showtimeId, List<Integer> seats) {
        this.id = id;
        this.showtimeId = showtimeId;
        this.seats = new ArrayList<>(seats);
    }

    public String        getId()         { return id; }
    public String        getShowtimeId() { return showtimeId; }
    public List<Integer> getSeats()      { return new ArrayList<>(seats); }
}
