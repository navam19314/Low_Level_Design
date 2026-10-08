package com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot.allocation;

import com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot.model.ParkingSpot;
import com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot.model.VehicleType;

import java.util.List;

// Strategy: which free spot should this vehicle get?
// Returns candidates best-first; the lot claims the first one nobody else grabbed meanwhile.
public interface SpotAllocationStrategy {
    List<ParkingSpot> rank(List<ParkingSpot> freeFittingSpots, VehicleType vehicle);
}
