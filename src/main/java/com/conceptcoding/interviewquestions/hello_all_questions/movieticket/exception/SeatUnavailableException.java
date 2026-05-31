package com.conceptcoding.interviewquestions.hello_all_questions.movieticket.exception;

/**
 * Thrown by Showtime.book when any requested seat is already taken (by a confirmed
 * reservation). Distinct from IllegalArgumentException so callers can tell apart
 * "you typed an invalid seat id" from "someone else booked it before you".
 */
public class SeatUnavailableException extends RuntimeException {
    public SeatUnavailableException(String message) { super(message); }
}
