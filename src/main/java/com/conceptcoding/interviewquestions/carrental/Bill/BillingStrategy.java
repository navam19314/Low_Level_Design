package com.conceptcoding.interviewquestions.carrental.Bill;
import com.conceptcoding.interviewquestions.carrental.reservation.Reservation;

public interface BillingStrategy {

    Bill generateBill(Reservation reservation);
}
