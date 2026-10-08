package com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot.model;

// size: a vehicle fits in any spot whose size is >= its own.
public enum VehicleType {
    MOTORCYCLE(1), CAR(2), TRUCK(3);

    private final int size;
    VehicleType(int size) { this.size = size; }
    public int getSize() { return size; }
}
