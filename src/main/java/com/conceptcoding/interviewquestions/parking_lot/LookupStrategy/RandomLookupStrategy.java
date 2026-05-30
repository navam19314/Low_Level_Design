package com.conceptcoding.interviewquestions.parking_lot.LookupStrategy;

import com.conceptcoding.interviewquestions.parking_lot.Entity.ParkingSpot;

import java.util.List;

public class RandomLookupStrategy implements ParkingSpotLookupStrategy {

    @Override
    public ParkingSpot selectSpot(List<ParkingSpot> spots) {
        // Return first available free spot
        for (ParkingSpot spot : spots) {
            if (spot.isSpotFree()) {
                return spot;
            }
        }
        return null; // No free spot found
    }
}


