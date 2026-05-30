package com.conceptcoding.interviewquestions.book_my_show.entities;

import com.conceptcoding.interviewquestions.book_my_show.enums.SeatStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

public class Show {

    private final Movie movie;
    private final LocalDate showDate;
    private final LocalTime startTime;

    private final Map<Integer, SeatStatus> seatStatusMap = new HashMap<>();
    private final Map<Integer, ReentrantLock> seatLocks = new HashMap<>();

    public Show(Movie movie, Screen screen, LocalDate date, LocalTime time) {
        this.movie = movie;
        this.showDate = date;
        this.startTime = time;

        for (Seat seat : screen.getSeats()) {
            seatStatusMap.put(seat.getSeatId(), SeatStatus.AVAILABLE);
            seatLocks.put(seat.getSeatId(), new ReentrantLock());
        }
    }

    public Movie getMovie() {
        return movie;
    }

    public LocalDate getShowDate() {
        return showDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public boolean lockSeats(List<Integer> seatIds) {
        // Sort seat IDs to avoid deadlock (always acquire locks in same order)
        List<Integer> sortedSeatIds = new ArrayList<>(seatIds);
        Collections.sort(sortedSeatIds);

        List<ReentrantLock> acquiredLocks = new ArrayList<>();

        try {
            // Step 1: Acquire locks for all seats
            for (int seatId : sortedSeatIds) {
                ReentrantLock lock = seatLocks.get(seatId);
                lock.lock();
                acquiredLocks.add(lock);
            }

            // Step 2: Check if all seats are available
            for (int seatId : sortedSeatIds) {
                SeatStatus currentStatus = seatStatusMap.get(seatId);
                if (currentStatus != SeatStatus.AVAILABLE) {
                    return false; // At least one seat is not available
                }
            }

            // Step 3: Mark all seats as LOCKED
            for (int seatId : sortedSeatIds) {
                seatStatusMap.put(seatId, SeatStatus.LOCKED);
            }

            return true;

        } finally {
            // Step 4: Always release all locks
            for (ReentrantLock lock : acquiredLocks) {
                lock.unlock();
            }
        }
    }

    public void confirmSeats(List<Integer> seatIds) {
        for (int seatId : seatIds) {
            seatStatusMap.put(seatId, SeatStatus.BOOKED);
        }
    }

    public void releaseSeats(List<Integer> seatIds) {
        for (int seatId : seatIds) {
            seatStatusMap.put(seatId, SeatStatus.AVAILABLE);
        }
    }
}


