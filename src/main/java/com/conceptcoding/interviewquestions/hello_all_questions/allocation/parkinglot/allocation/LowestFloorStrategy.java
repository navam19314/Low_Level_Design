package com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot.allocation;

import com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot.model.ParkingSpot;
import com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot.model.VehicleType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// Closest to the entrance (ground floor first), regardless of spot size.
public class LowestFloorStrategy implements SpotAllocationStrategy {

    @Override
    public List<ParkingSpot> rank(List<ParkingSpot> freeFittingSpots, VehicleType vehicle) {
        List<ParkingSpot> ranked = new ArrayList<>(freeFittingSpots);
        ranked.sort(Comparator.comparingInt(ParkingSpot::getFloor));
        return ranked;
    }
}
