package com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot.allocation;

import com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot.model.ParkingSpot;
import com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot.model.VehicleType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// Smallest spot that fits, then lowest floor. Keeps big spots free for big vehicles:
// a bike only takes a car spot when every bike spot is full.
public class SmallestFitStrategy implements SpotAllocationStrategy {

    @Override
    public List<ParkingSpot> rank(List<ParkingSpot> freeFittingSpots, VehicleType vehicle) {
        List<ParkingSpot> ranked = new ArrayList<>(freeFittingSpots);
        ranked.sort(Comparator.comparingInt((ParkingSpot s) -> s.getType().getSize())
                              .thenComparingInt(ParkingSpot::getFloor));
        return ranked;
    }
}
