package com.conceptcoding.interviewquestions.hello_all_questions.matching.cabbooking.model;

// A point on a simple city grid, in km. A real map (lat/lng + haversine) is an extension.
public class Location {

    private final double x;
    private final double y;

    public Location(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public double distanceTo(Location other) {
        return Math.hypot(x - other.x, y - other.y);           // straight-line km
    }
}
