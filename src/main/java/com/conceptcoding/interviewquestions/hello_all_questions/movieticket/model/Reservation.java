package com.conceptcoding.interviewquestions.hello_all_questions.movieticket.model;

import java.util.ArrayList;
import java.util.List;

/**
 * User's booking reference. Immutable data record with a back-reference to its
 * Showtime so cancellation can route to the right place without scanning.
 *
 * <p>Notice there's no cancel() method here — cancellation modifies the Showtime's
 * reservations list, so it lives on Showtime where the state lives (Tell, Don't Ask).
 */
public class Reservation {

    private final String confirmationId;
    private final Showtime showtime;
    private final List<String> seatIds;

    public Reservation(String confirmationId, Showtime showtime, List<String> seatIds) {
        this.confirmationId = confirmationId;
        this.showtime = showtime;
        this.seatIds = new ArrayList<>(seatIds);   // defensive copy in
    }

    public String       getConfirmationId() { return confirmationId; }
    public Showtime     getShowtime()       { return showtime; }
    public List<String> getSeatIds()        { return new ArrayList<>(seatIds); }  // defensive copy out
}
