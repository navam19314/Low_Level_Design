package com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot.allocation;

import com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot.model.ParkingSpot;
import com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot.model.VehicleType;

import java.util.List;

// Strategy: which free spot should this vehicle get? Smallest-fit today; nearest-to-lift,
// lowest-floor, EV-first tomorrow, each one new class.
public interface SpotAllocationStrategy {
    // returns the chosen spot, or null if no free spot fits this vehicle
    ParkingSpot choose(List<ParkingSpot> freeSpots, VehicleType vehicle);
}
