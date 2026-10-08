package com.conceptcoding.interviewquestions.hello_all_questions.allocation.amazonlocker.model;

// One locker door. Its status is the contended field: two delivery drivers at the same
// locker may both see it AVAILABLE. tryOccupy() checks and claims under one lock,
// so exactly one of them gets it.
public class Compartment {

    private final String id;
    private final Size size;
    private CompartmentStatus status = CompartmentStatus.AVAILABLE;

    public Compartment(String id, Size size) {
        this.id = id;
        this.size = size;
    }

    public synchronized boolean tryOccupy() {
        if (status != CompartmentStatus.AVAILABLE) return false;
        status = CompartmentStatus.OCCUPIED;
        return true;
    }

    public synchronized void markFree()          { status = CompartmentStatus.AVAILABLE; }
    public synchronized void markOutOfService()  { status = CompartmentStatus.OUT_OF_SERVICE; }
    public synchronized boolean isAvailable()    { return status == CompartmentStatus.AVAILABLE; }
    public synchronized CompartmentStatus getStatus() { return status; }

    public String getId()   { return id; }
    public Size   getSize() { return size; }

    public void open() {
        System.out.println("  [hardware] compartment " + id + " (" + size + ") unlocked");
    }
}
