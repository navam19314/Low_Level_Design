package com.conceptcoding.interviewquestions.hello_all_questions.parkinglot.model;

import java.time.LocalDateTime;

public class Ticket {

    private final String id;
    private final String spotId;
    private final VehicleType vehicleType;
    private final LocalDateTime entryTime;

    public Ticket(String id, String spotId, VehicleType vehicleType, LocalDateTime entryTime) {
        this.id = id;
        this.spotId = spotId;
        this.vehicleType = vehicleType;
        this.entryTime = entryTime;
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

    public LocalDateTime getEntryTime() {
        return entryTime;
    }
}
