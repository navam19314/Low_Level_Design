package com.conceptcoding.interviewquestions.hello_all_questions.movieticket;

import com.conceptcoding.interviewquestions.hello_all_questions.movieticket.model.Booking;
import com.conceptcoding.interviewquestions.hello_all_questions.movieticket.model.City;
import com.conceptcoding.interviewquestions.hello_all_questions.movieticket.model.Showtime;
import com.conceptcoding.interviewquestions.hello_all_questions.movieticket.model.Theater;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

// Orchestrator + facade. Owns cities (each holding theaters, each holding showtimes)
// and ONE index — showtimesById — so book() resolves a showtime id in O(1).
// Search scans showtimes within one city and filters by title.
//
// The concurrency-interesting code lives on Showtime. BookingSystem just creates
// the Booking and hands it off. Cancellation is a Step-5 extension.
public class BookingSystem {

    private final Map<String, City>      citiesById;
    private final Map<String, Showtime>  showtimesById;

    public BookingSystem(List<City> cities) {
        this.citiesById    = new HashMap<>();
        this.showtimesById = new HashMap<>();
        for (City city : cities) {
            citiesById.put(city.getId(), city);
            // Ask the city for its showtimes — don't walk the hierarchy from here.
            for (Showtime showtime : city.getAllShowtimes()) {
                showtimesById.put(showtime.getId(), showtime);
            }
        }
    }

    // Search movies by title within a specific city (BookMyShow-style — city first).
    // Just resolve the city and delegate — City walks its own hierarchy. LoD-clean.
    public List<Showtime> searchMovies(String cityId, String title) {
        if (title == null || title.isEmpty()) return new ArrayList<>();
        City city = citiesById.get(cityId);
        return city == null ? new ArrayList<>() : city.findShowtimesByTitle(title);
    }

    public List<Showtime> getShowtimesAtTheater(Theater theater) {
        if (theater == null) return new ArrayList<>();
        return new ArrayList<>(theater.getShowtimes());
    }

    // Create the Booking up front (just a data object, no state change yet),
    // then hand it to Showtime for atomic check+store. If unavailable, the exception
    // propagates and no state changes anywhere.
    public Booking book(String showtimeId, List<String> seatIds) {
        if (showtimeId == null || seatIds == null || seatIds.isEmpty()) {
            throw new IllegalArgumentException("Invalid booking request");
        }
        Showtime showtime = showtimesById.get(showtimeId);
        if (showtime == null) {
            throw new NoSuchElementException("Showtime not found: " + showtimeId);
        }
        Booking booking = new Booking(UUID.randomUUID().toString(), seatIds);
        showtime.book(booking);                          // atomic — may throw
        return booking;
    }
}
