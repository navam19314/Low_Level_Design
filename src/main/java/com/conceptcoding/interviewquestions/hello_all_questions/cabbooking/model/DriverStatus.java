package com.conceptcoding.interviewquestions.hello_all_questions.cabbooking.model;

/**
 * Driver availability lifecycle.
 *
 * <pre>
 *   OFFLINE  ──goOnline──►  AVAILABLE  ──reservedByMatch──►  ON_TRIP
 *      ▲                       │                                │
 *      └──goOffline────────────┴───────completeTrip─────────────┘
 * </pre>
 *
 * <p>The AVAILABLE → ON_TRIP transition is the contention point — two riders
 * may try to match the same driver. {@code CabBookingService} uses an atomic
 * compareAndSet (under {@code synchronized(driver)}) to make exactly one win.
 */
public enum DriverStatus {
    OFFLINE,
    AVAILABLE,
    ON_TRIP
}
