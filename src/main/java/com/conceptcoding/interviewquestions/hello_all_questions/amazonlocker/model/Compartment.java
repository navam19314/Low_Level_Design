package com.conceptcoding.interviewquestions.hello_all_questions.amazonlocker.model;

public class Compartment {

    private final String id;
    private final Size size;
    private CompartmentStatus status;

    public Compartment(String id, Size size) {
        this.id = id;
        this.size = size;
        this.status = CompartmentStatus.AVAILABLE;
    }

    public String getId() {
        return id;
    }

    public Size getSize() {
        return size;
    }

    public CompartmentStatus getStatus() {
        return status;
    }

    public boolean isAvailable() {
        return status == CompartmentStatus.AVAILABLE;
    }

    public void markOccupied() {
        this.status = CompartmentStatus.OCCUPIED;
    }

    public void markFree() {
        this.status = CompartmentStatus.AVAILABLE;
    }

    public void markOutOfService() {
        this.status = CompartmentStatus.OUT_OF_SERVICE;
    }

    public void open() {
        System.out.println("[hardware] compartment " + id + " (" + size + ") unlocked");
    }
}
