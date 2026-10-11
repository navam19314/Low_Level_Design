package com.conceptcoding.interviewquestions.hello_all_questions.booking.movieticket.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// One screening: "Inception, 7 PM". The seats belong to the show, not the hall:
// seat 5 at 7 PM and seat 5 at 10 PM are different tickets.
//
// Thread safety: the lock is PER SHOW (synchronized methods). Two people booking the same
// show wait for each other; people booking different shows never do.
public class Showtime {

    private final String id;
    private final Movie movie;
    private final LocalDateTime startTime;
    private final int totalSeats;                              // seats are numbered 1..totalSeats
    private final Set<Integer> bookedSeats = new HashSet<>();

    public Showtime(String id, Movie movie, LocalDateTime startTime, int totalSeats) {
        this.id = id;
        this.movie = movie;
        this.startTime = startTime;
        this.totalSeats = totalSeats;
    }

    public synchronized List<Integer> getAvailableSeats() {
        List<Integer> free = new ArrayList<>();
        for (int seat = 1; seat <= totalSeats; seat++) {
            if (!bookedSeats.contains(seat)) free.add(seat);
        }
        return free;
    }

    // All or nothing: check EVERY seat first, then book them all.
    // Check and book happen under one lock, so nobody can take a seat in between.
    public synchronized void book(List<Integer> seats) {
        if (seats.isEmpty()) throw new IllegalArgumentException("Pick at least one seat");
        if (new HashSet<>(seats).size() != seats.size()) {
            throw new IllegalArgumentException("Same seat twice: " + seats);
        }
        for (int seat : seats) {
            if (seat < 1 || seat > totalSeats) throw new IllegalArgumentException("No such seat: " + seat);
            if (bookedSeats.contains(seat)) throw new IllegalStateException("Seat already booked: " + seat);
        }
        bookedSeats.addAll(seats);
    }

    public String        getId()        { return id; }
    public Movie         getMovie()     { return movie; }
    public LocalDateTime getStartTime() { return startTime; }
}
