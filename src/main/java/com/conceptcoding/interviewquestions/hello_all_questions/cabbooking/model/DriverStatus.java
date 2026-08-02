package com.conceptcoding.interviewquestions.hello_all_questions.cabbooking.model;

// Driver availability lifecycle.
//
//   OFFLINE  ──goOnline──►  AVAILABLE  ──reservedByMatch──►  ON_TRIP
//      ▲                       │                                │
//      └──goOffline────────────┴───────completeTrip─────────────┘
//
// The AVAILABLE → ON_TRIP transition is the contention point — two riders
// may try to match the same driver. CabBookingService uses an atomic
// compareAndSet (under synchronized(driver)) to make exactly one win.
public enum DriverStatus {
    OFFLINE,
    AVAILABLE,
    ON_TRIP
}
