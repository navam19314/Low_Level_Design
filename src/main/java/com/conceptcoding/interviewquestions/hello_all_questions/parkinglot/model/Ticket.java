package com.conceptcoding.interviewquestions.hello_all_questions.parkinglot.model;

public class Ticket {

    private final String id;
    private final String spotId;
    private final VehicleType vehicleType;
    private final long entryTimeMs;

    public Ticket(String id, String spotId, VehicleType vehicleType, long entryTimeMs) {
        this.id = id;
        this.spotId = spotId;
        this.vehicleType = vehicleType;
        this.entryTimeMs = entryTimeMs;
    }

    public String getId() {
        return id;
    }

    public String getSpotId() {
        return spotId;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }

    public long getEntryTimeMs() {
        return entryTimeMs;
    }
}
