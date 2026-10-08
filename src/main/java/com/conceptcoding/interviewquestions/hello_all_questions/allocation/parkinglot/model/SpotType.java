package com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot.model;

public enum SpotType {
    SMALL(1), MEDIUM(2), LARGE(3);

    private final int size;
    SpotType(int size) { this.size = size; }
    public int getSize() { return size; }

    // a bike fits in a car spot; a truck doesn't fit in a bike spot
    public boolean canFit(VehicleType vehicle) { return size >= vehicle.getSize(); }
}
