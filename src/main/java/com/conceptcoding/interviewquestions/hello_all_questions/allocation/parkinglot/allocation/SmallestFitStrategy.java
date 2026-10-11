package com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot.allocation;

import com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot.model.ParkingSpot;
import com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot.model.VehicleType;

import java.util.List;

// Smallest spot that fits. Keeps big spots free for big vehicles:
// a bike only takes a car spot when every bike spot is full.
public class SmallestFitStrategy implements SpotAllocationStrategy {

    @Override
    public ParkingSpot choose(List<ParkingSpot> freeSpots, VehicleType vehicle) {
        ParkingSpot best = null;
        for (ParkingSpot spot : freeSpots) {
            if (!spot.getType().canFit(vehicle)) continue;                    // too small: skip
            if (best == null || spot.getType().getSize() < best.getType().getSize()) {
                best = spot;                                                  // smallest so far
            }
        }
        return best;                                                          // null = nothing fits
    }
}
