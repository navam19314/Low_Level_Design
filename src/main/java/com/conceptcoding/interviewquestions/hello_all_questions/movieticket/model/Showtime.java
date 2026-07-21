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

    // Fixed seat layout — flat numbered seats "1".."100". No row/column math needed;
    // a real system would vary this per-Screen (Step 5).
    private static final int TOTAL_SEATS = 100;

    private final String id;
    private final Screen screen;
    private final Movie movie;
    private final LocalDateTime datetime;
    private final List<Booking> bookings;

    public Showtime(String id, Screen screen, Movie movie, LocalDateTime datetime) {
        this.id = id;
        this.screen = screen;
        this.movie = movie;
        this.datetime = datetime;
        this.bookings = new ArrayList<>();
    }

    public String        getId()       { return id; }
    public Screen        getScreen()   { return screen; }
    public Movie         getMovie()    { return movie; }
    public LocalDateTime getDatetime() { return datetime; }

    // Defensive copy — callers can't mutate our internal list.
    public synchronized List<Booking> getBookings() {
        return new ArrayList<>(bookings);
    }

    // A seat is available iff no existing booking claims it.
    // SYNCHRONIZED — reads must serialize with book()'s writes to the same `bookings`
    // ArrayList. Without this, a reader here can race book()'s `bookings.add(...)` and
    // throw ConcurrentModificationException (or see a torn view) even though book()
    // itself holds the lock — ArrayList iteration isn't safe against an UNSYNCHRONIZED
    // concurrent writer.
    public synchronized boolean isAvailable(String seatId) {
        for (Booking b : bookings) {
            if (b.getSeatIds().contains(seatId)) return false;
        }
        return true;
    }

    // Layout minus booked seats. Synchronized for the same reason as isAvailable.
    public synchronized List<String> getAvailableSeats() {
        Set<String> booked = new HashSet<>();
        for (Booking b : bookings) {
            booked.addAll(b.getSeatIds());
        }
        List<String> available = new ArrayList<>();
        for (int num = 1; num <= TOTAL_SEATS; num++) {
            String seatId = String.valueOf(num);
            if (!booked.contains(seatId)) available.add(seatId);
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
        try {
            int num = Integer.parseInt(seatId);
            return num >= 1 && num <= TOTAL_SEATS;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
