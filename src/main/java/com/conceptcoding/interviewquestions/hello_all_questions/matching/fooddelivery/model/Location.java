package com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model;

// A point on a simple km grid. Real system: lat/lng + haversine distance + a geo index.
public class Location {

    private final double x;
    private final double y;

    public Location(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public double distanceTo(Location other) {
        return Math.hypot(x - other.x, y - other.y);
    }

    @Override
    public String toString() { return "(" + x + "," + y + ")"; }
}
