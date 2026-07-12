package com.conceptcoding.interviewquestions.hello_all_questions.movieticket;

import com.conceptcoding.interviewquestions.hello_all_questions.movieticket.model.Reservation;
import com.conceptcoding.interviewquestions.hello_all_questions.movieticket.model.Showtime;
import com.conceptcoding.interviewquestions.hello_all_questions.movieticket.model.Theater;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

// Orchestrator + facade. Owns theaters and ONE index — showtimesById — so book()
// resolves a showtime id in O(1). Search just scans showtimes and filters by title;
// at interview scale that's plenty. (If search became hot, a title index would help —
// a Step-5 answer.)
//
// The concurrency-interesting code lives on Showtime. BookingSystem just creates
// the Reservation and hands it off. Cancellation is a Step-5 extension.
public class BookingSystem {

    private final List<Theater>          theaters;
    private final Map<String, Showtime>  showtimesById;

    public BookingSystem(List<Theater> theaters) {
        this.theaters      = theaters;
        this.showtimesById = new HashMap<>();
        for (Theater theater : theaters) {
            for (Showtime showtime : theater.getShowtimes()) {
                showtimesById.put(showtime.getId(), showtime);
            }
        }
    }

    // Case-insensitive substring match on title; returns future showtimes only.
    public List<Showtime> searchMovies(String title) {
        if (title == null || title.isEmpty()) return new ArrayList<>();
        String query = title.toLowerCase();
        LocalDateTime now = LocalDateTime.now();
        List<Showtime> results = new ArrayList<>();
        for (Showtime s : showtimesById.values()) {
            if (s.getMovie().getTitle().toLowerCase().contains(query) && s.getDatetime().isAfter(now)) {
                results.add(s);
            }
        }
        return results;
    }

    // Returns only future showtimes — past ones aren't bookable.
    public List<Showtime> getShowtimesAtTheater(Theater theater) {
        if (theater == null) return new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        List<Showtime> results = new ArrayList<>();
        for (Showtime s : theater.getShowtimes()) {
            if (s.getDatetime().isAfter(now)) results.add(s);
        }
        return results;
    }

    // Create the Reservation up front (just a data object, no state change yet),
    // then hand it to Showtime for atomic check+store. If unavailable, the exception
    // propagates and no state changes anywhere.
    public Reservation book(String showtimeId, List<String> seatIds) {
        if (showtimeId == null || seatIds == null || seatIds.isEmpty()) {
            throw new IllegalArgumentException("Invalid booking request");
        }
        Showtime showtime = showtimesById.get(showtimeId);
        if (showtime == null) {
            throw new NoSuchElementException("Showtime not found: " + showtimeId);
        }
        Reservation reservation = new Reservation(UUID.randomUUID().toString(), seatIds);
        showtime.book(reservation);                      // atomic — may throw
        return reservation;
    }
}
