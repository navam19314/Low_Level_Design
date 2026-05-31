package com.conceptcoding.interviewquestions.hello_all_questions.movieticket;

import com.conceptcoding.interviewquestions.hello_all_questions.movieticket.model.Movie;
import com.conceptcoding.interviewquestions.hello_all_questions.movieticket.model.Reservation;
import com.conceptcoding.interviewquestions.hello_all_questions.movieticket.model.Showtime;
import com.conceptcoding.interviewquestions.hello_all_questions.movieticket.model.Theater;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Orchestrator + facade. Owns theaters; builds three lookup indexes at construction
 * for O(1) routing:
 *   - moviesById            (search)
 *   - showtimesByMovieId    (search → list showtimes for a matched movie)
 *   - showtimesById         (book — find target Showtime from id)
 *   - reservationsById      (cancel — find target Reservation from confirmation id)
 *
 * <p>The interesting concurrency lives on Showtime. BookingSystem just creates the
 * Reservation, hands it off, and (on success) registers it in the routing index.
 * If you wanted thread-safe routing-index mutation, swap the HashMaps for
 * ConcurrentHashMaps — operations are short-lived single-key updates, no contention.
 *
 * <p>Clock is injected for deterministic "future showtime" filtering in tests.
 */
public class BookingSystem {

    private final List<Theater>                theaters;
    private final Map<String, Movie>           moviesById;
    private final Map<String, List<Showtime>>  showtimesByMovieId;
    private final Map<String, Showtime>        showtimesById;
    private final Map<String, Reservation>     reservationsById;
    private final Clock                         clock;

    public BookingSystem(List<Theater> theaters) {
        this(theaters, Clock.systemUTC());
    }

    public BookingSystem(List<Theater> theaters, Clock clock) {
        this.theaters            = theaters;
        this.clock               = clock;
        this.moviesById          = new HashMap<>();
        this.showtimesByMovieId  = new HashMap<>();
        this.showtimesById       = new HashMap<>();
        this.reservationsById    = new HashMap<>();

        // Build all three search/route indexes in one pass.
        for (Theater theater : theaters) {
            for (Showtime showtime : theater.getShowtimes()) {
                indexShowtime(showtime);
            }
        }
    }

    /** Case-insensitive substring match on title; returns future showtimes only. */
    public List<Showtime> searchMovies(String title) {
        if (title == null || title.isEmpty()) return new ArrayList<>();
        String searchLower = title.toLowerCase();
        Instant now = clock.instant();
        List<Showtime> results = new ArrayList<>();

        for (Movie movie : moviesById.values()) {
            if (!movie.getTitle().toLowerCase().contains(searchLower)) continue;
            List<Showtime> forMovie = showtimesByMovieId.get(movie.getId());
            if (forMovie == null) continue;
            for (Showtime s : forMovie) {
                if (s.getDatetime().isAfter(now)) results.add(s);
            }
        }
        return results;
    }

    /** Returns only future showtimes — past ones aren't bookable. */
    public List<Showtime> getShowtimesAtTheater(Theater theater) {
        if (theater == null) return new ArrayList<>();
        Instant now = clock.instant();
        List<Showtime> results = new ArrayList<>();
        for (Showtime s : theater.getShowtimes()) {
            if (s.getDatetime().isAfter(now)) results.add(s);
        }
        return results;
    }

    /**
     * Create the Reservation up front (just a data object, no state change yet),
     * then hand it to Showtime for atomic check+store. If unavailable, the exception
     * propagates and the reservation is never registered in our routing index.
     */
    public Reservation book(String showtimeId, List<String> seatIds) {
        if (showtimeId == null || seatIds == null || seatIds.isEmpty()) {
            throw new IllegalArgumentException("Invalid booking request");
        }
        Showtime showtime = showtimesById.get(showtimeId);
        if (showtime == null) {
            throw new NoSuchElementException("Showtime not found: " + showtimeId);
        }
        Reservation reservation = new Reservation(
                UUID.randomUUID().toString(),
                showtime,
                seatIds);
        showtime.book(reservation);                       // atomic — may throw
        reservationsById.put(reservation.getConfirmationId(), reservation);
        return reservation;
    }

    /**
     * Follow the Reservation's back-reference rather than scanning every showtime.
     * Removing from our index AFTER showtime.cancel succeeds keeps the two consistent
     * even if the cancel ever started throwing.
     */
    public void cancelReservation(String confirmationId) {
        if (confirmationId == null || confirmationId.isEmpty()) {
            throw new IllegalArgumentException("Invalid confirmation ID");
        }
        Reservation reservation = reservationsById.get(confirmationId);
        if (reservation == null) {
            throw new NoSuchElementException("Reservation not found: " + confirmationId);
        }
        reservation.getShowtime().cancel(reservation);
        reservationsById.remove(confirmationId);
    }

    // ----- Internal helpers -----

    private void indexShowtime(Showtime showtime) {
        Movie movie = showtime.getMovie();
        moviesById.put(movie.getId(), movie);
        showtimesById.put(showtime.getId(), showtime);
        showtimesByMovieId
                .computeIfAbsent(movie.getId(), k -> new ArrayList<>())
                .add(showtime);
    }
}
