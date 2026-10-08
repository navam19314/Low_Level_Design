package com.conceptcoding.interviewquestions.hello_all_questions.matching.cabbooking.model;

import java.util.Objects;

// A geographic point. Latitude / longitude are doubles for clarity here —
// in production we'd use a fixed-precision integer (microdegrees) to avoid
// floating-point comparison surprises.
//
// distanceKm(Location) returns a flat-Earth approximation (Euclidean × ~111
// km/deg). For LLD scope this is plenty; the walkthrough calls out Haversine
// + spatial indexing as the production upgrade.
//
// Normal class, not a record — deck convention (whiteboard-friendly; some
// interviewers frown on records). Immutability kept by hand: final fields,
// no setters.
public class Location {

    private final double lat;
    private final double lng;

    public Location(double lat, double lng) {
        this.lat = lat;
        this.lng = lng;
    }

    public double lat() { return lat; }
    public double lng() { return lng; }

    // Approx. km between this location and another. Cheap, good enough for matching.
    public double distanceKm(Location other) {
        double dLat = this.lat - other.lat;
        double dLng = this.lng - other.lng;
        // ~111 km per degree of latitude; longitude scaling ignored at LLD scope.
        return Math.sqrt(dLat * dLat + dLng * dLng) * 111.0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Location l)) return false;
        return Double.compare(lat, l.lat) == 0 && Double.compare(lng, l.lng) == 0;
    }
    @Override public int hashCode() { return Objects.hash(lat, lng); }
    @Override public String toString() { return "Location(" + lat + "," + lng + ")"; }
}
