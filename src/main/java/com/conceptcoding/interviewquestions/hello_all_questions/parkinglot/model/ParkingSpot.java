package com.conceptcoding.interviewquestions.hello_all_questions.parkinglot.model;

public class ParkingSpot {

    private final String id;
    private final SpotType spotType;

    public ParkingSpot(String id, SpotType spotType) {
        this.id = id;
        this.spotType = spotType;
    }

    public String getId() {
        return id;
    }

    public SpotType getSpotType() {
        return spotType;
    }
}
