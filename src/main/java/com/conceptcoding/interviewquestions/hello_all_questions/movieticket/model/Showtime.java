package com.conceptcoding.interviewquestions.hello_all_questions.movieticket.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// A specific screening. THE class where the interesting design lives:
//   - bookings list is the SINGLE source of truth for booked seats.
//     No separate bookedSeats set to keep in sync.
//   - book is synchronized so the check-then-act sequence is atomic.
//
// Per-showtime locking is the right default: short critical sections, no
// deadlock risk. For opening-night-of-Marvel scale you'd push down to per-seat
// locking — but only when the simple approach is a MEASURED bottleneck.
public class Showtime {

    // Fixed seat layout — rows A..Z, seats 0..20 → 546 seats per showtime.
    private static final char MIN_ROW  = 'A';
    private static final char MAX_ROW  = 'Z';
    private static final int  MIN_SEAT = 0;
    private static final int  MAX_SEAT = 20;

    private final String id;
    private final Theater theater;
    private final Movie movie;
    private final LocalDateTime datetime;
    private final String screenLabel;
    private final List<Booking> bookings;

    public Showtime(String id, Theater theater, Movie movie, LocalDateTime datetime, String screenLabel) {
        this.id = id;
        this.theater = theater;
        this.movie = movie;
        this.datetime = datetime;
        this.screenLabel = screenLabel;
        this.bookings = new ArrayList<>();
    }

    public String        getId()          { return id; }
    public Theater       getTheater()     { return theater; }
    public Movie         getMovie()       { return movie; }
    public LocalDateTime getDatetime()    { return datetime; }
    public String        getScreenLabel() { return screenLabel; }

    // Defensive copy — callers can't mutate our internal list.
    public List<Booking> getBookings() {
        return new ArrayList<>(bookings);
    }

    // A seat is available iff no existing booking claims it.
    public boolean isAvailable(String seatId) {
        for (Booking b : bookings) {
            if (b.getSeatIds().contains(seatId)) return false;
        }
        return true;
    }

    // Layout minus booked seats.
    public List<String> getAvailableSeats() {
        Set<String> booked = new HashSet<>();
        for (Booking b : bookings) {
            booked.addAll(b.getSeatIds());
        }
        List<String> available = new ArrayList<>();
        for (char row = MIN_ROW; row <= MAX_ROW; row++) {
            for (int num = MIN_SEAT; num <= MAX_SEAT; num++) {
                String seatId = "" + row + num;
                if (!booked.contains(seatId)) available.add(seatId);
            }
        }
        return available;
    }

    // SYNCHRONIZED — the check-then-act must be atomic. Without the lock two threads
    // could both pass isAvailable() for the same seat and both add their booking
    // (silent double-booking). All-or-nothing: a single unavailable seat aborts the
    // whole booking with no state change.
    public synchronized void book(Booking booking) {
        List<String> seatIds = booking.getSeatIds();
        if (seatIds == null || seatIds.isEmpty()) {
            throw new IllegalArgumentException("Must select at least one seat");
        }
        // Validate seat-id format first — fail fast, no allocation cost.
        for (String seatId : seatIds) {
            if (!isValidSeatId(seatId)) {
                throw new IllegalArgumentException("Invalid seat: " + seatId);
            }
        }
        // Check availability — STILL inside the lock so no one can sneak in.
        for (String seatId : seatIds) {
            if (!isAvailable(seatId)) {
                throw new IllegalStateException("Seat unavailable: " + seatId);
            }
        }
        bookings.add(booking);
    }

    private static boolean isValidSeatId(String seatId) {
        if (seatId == null || seatId.length() < 2) return false;
        char row = seatId.charAt(0);
        if (row < MIN_ROW || row > MAX_ROW) return false;
        try {
            int num = Integer.parseInt(seatId.substring(1));
            return num >= MIN_SEAT && num <= MAX_SEAT;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
