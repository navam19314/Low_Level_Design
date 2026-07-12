package com.conceptcoding.interviewquestions.hello_all_questions.movieticket.model;

import java.util.ArrayList;
import java.util.List;

// Confirmed booking record — immutable. Held by the Showtime that owns these seats.
// (When cancellation is added in Step 5, this grows a back-ref to its Showtime so
//  cancel-by-bookingId routes without scanning.)
public class Booking {

    private final String bookingId;
    private final List<String> seatIds;

    public Booking(String bookingId, List<String> seatIds) {
        this.bookingId = bookingId;
        this.seatIds   = new ArrayList<>(seatIds);   // defensive copy in
    }

    public String       getBookingId() { return bookingId; }
    public List<String> getSeatIds()   { return new ArrayList<>(seatIds); }  // defensive copy out
}
