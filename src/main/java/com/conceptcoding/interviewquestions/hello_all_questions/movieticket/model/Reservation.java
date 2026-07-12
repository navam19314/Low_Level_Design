package com.conceptcoding.interviewquestions.hello_all_questions.movieticket.model;

import java.util.ArrayList;
import java.util.List;

// User's booking reference. Immutable — just the confirmation id and the seats.
// (Cancellation is a Step-5 extension; when added, this grows a back-ref to its
//  Showtime so cancel-by-confirmation-id routes without scanning.)
public class Reservation {

    private final String confirmationId;
    private final List<String> seatIds;

    public Reservation(String confirmationId, List<String> seatIds) {
        this.confirmationId = confirmationId;
        this.seatIds = new ArrayList<>(seatIds);   // defensive copy in
    }

    public String       getConfirmationId() { return confirmationId; }
    public List<String> getSeatIds()        { return new ArrayList<>(seatIds); }  // defensive copy out
}
