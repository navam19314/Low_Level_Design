package com.conceptcoding.interviewquestions.parking_lot.Entity;

public class ParkingSpot {

    private final String spotId;
    private boolean isFree = true;

    public ParkingSpot(String spotId) {
        this.spotId = spotId;
    }

    public boolean isSpotFree() {
        return isFree;
    }

    public void occupySpot() {
        isFree = false;
    }

    public void releaseSpot() {
        isFree = true;
    }

    public String getSpotId() {
        return spotId;
    }
}

