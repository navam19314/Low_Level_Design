package com.conceptcoding.interviewquestions.hello_all_questions.booking.movieticket;

import com.conceptcoding.interviewquestions.hello_all_questions.booking.movieticket.model.Booking;
import com.conceptcoding.interviewquestions.hello_all_questions.booking.movieticket.model.Showtime;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

// The service the app calls. It finds the show and lets the SHOW do the booking under its own lock.
// No lock here: a global lock would make every show in the country wait for every other.
public class BookingSystem {

    private final Map<String, Showtime> showtimes = new ConcurrentHashMap<>();   // read by many threads
    private final AtomicInteger nextBookingNumber = new AtomicInteger(1);        // no lock here, so atomic

    public void addShowtime(Showtime showtime) {
        showtimes.put(showtime.getId(), showtime);
    }

    public List<Integer> getAvailableSeats(String showtimeId) {
        return findShowtime(showtimeId).getAvailableSeats();
    }

    public Booking book(String showtimeId, List<Integer> seats) {
        Showtime showtime = findShowtime(showtimeId);
        showtime.book(seats);                                  // atomic; throws if any seat is taken
        return new Booking("B-" + nextBookingNumber.getAndIncrement(), showtimeId, seats);
    }

    private Showtime findShowtime(String showtimeId) {
        Showtime showtime = showtimes.get(showtimeId);
        if (showtime == null) throw new NoSuchElementException("Unknown show " + showtimeId);
        return showtime;
    }
}
