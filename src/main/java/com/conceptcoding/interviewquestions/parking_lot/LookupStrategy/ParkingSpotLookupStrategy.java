package com.conceptcoding.interviewquestions.parking_lot.LookupStrategy;

import com.conceptcoding.interviewquestions.parking_lot.Entity.ParkingSpot;

import java.util.List;

public interface ParkingSpotLookupStrategy {

    ParkingSpot selectSpot(List<ParkingSpot> spots);
}


