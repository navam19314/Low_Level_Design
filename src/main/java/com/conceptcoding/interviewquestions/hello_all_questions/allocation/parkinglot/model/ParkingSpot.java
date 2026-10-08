package com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot.model;

// A physical spot. Immutable: whether it's free lives in ONE place, the lot's occupied set,
// so there is no "spot says free, lot says taken" disagreement.
public class ParkingSpot {

    private final String id;
    private final int floor;
    private final SpotType type;

    public ParkingSpot(String id, int floor, SpotType type) {
        this.id = id;
        this.floor = floor;
        this.type = type;
    }

    public String   getId()    { return id; }
    public int      getFloor() { return floor; }
    public SpotType getType()  { return type; }
}
