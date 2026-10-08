package com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot.model;

import java.time.Instant;

public class Ticket {

    private final String id;
    private final String spotId;
    private final Vehicle vehicle;
    private final Instant entryTime;

    public Ticket(String id, String spotId, Vehicle vehicle, Instant entryTime) {
        this.id = id;
        this.spotId = spotId;
        this.vehicle = vehicle;
        this.entryTime = entryTime;
    }

    public String  getId()        { return id; }
    public String  getSpotId()    { return spotId; }
    public Vehicle getVehicle()   { return vehicle; }
    public Instant getEntryTime() { return entryTime; }
}
