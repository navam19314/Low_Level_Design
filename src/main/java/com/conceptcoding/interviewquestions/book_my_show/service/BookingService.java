package com.conceptcoding.interviewquestions.book_my_show.service;

import com.conceptcoding.interviewquestions.book_my_show.entities.Booking;
import com.conceptcoding.interviewquestions.book_my_show.entities.Payment;
import com.conceptcoding.interviewquestions.book_my_show.entities.Show;
import com.conceptcoding.interviewquestions.book_my_show.entities.User;
import com.conceptcoding.interviewquestions.book_my_show.enums.PaymentStatus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BookingService {

    private final Map<UUID, Booking> bookings = new HashMap<>();


    public Booking book(User user, Show show, List<Integer> seats) {
        // Step 1: Lock the seats
        boolean seatsLocked = show.lockSeats(seats);
        if (!seatsLocked) {
            throw new RuntimeException("Seat unavailable");
        }

        // Step 2: Process payment (simulated - can invoke Payment Controller)
        Payment payment = new Payment(PaymentStatus.SUCCESS);

        // Step 3: Confirm booking if payment succeeds
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            show.confirmSeats(seats);
            Booking booking = new Booking(user, show, seats, payment);
            bookings.put(booking.getBookingId(), booking);
            return booking;
        } else {
            // Step 4: Release seats if payment fails
            show.releaseSeats(seats);
            throw new RuntimeException("Payment failed");
        }
    }

    public Booking getBooking(UUID bookingId) {
        return bookings.get(bookingId);
    }

    public List<Booking> getBookingsForUser(User user) {
        List<Booking> userBookings = new ArrayList<>();
        for (Booking booking : bookings.values()) {
            if (booking.getUser().equals(user)) {
                userBookings.add(booking);
            }
        }
        return userBookings;
    }
}


