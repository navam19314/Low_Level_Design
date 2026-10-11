package com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot.model;

import java.time.Instant;

public class Ticket {

    private final String id;
    private final String spotId;
    private final VehicleType vehicleType;
    private final Instant entryTime;

    public Ticket(String id, String spotId, VehicleType vehicleType, Instant entryTime) {
        this.id = id;
        this.spotId = spotId;
        this.vehicleType = vehicleType;
        this.entryTime = entryTime;
    }

    public String      getId()          { return id; }
    public String      getSpotId()      { return spotId; }
    public VehicleType getVehicleType() { return vehicleType; }
    public Instant     getEntryTime()   { return entryTime; }
}
