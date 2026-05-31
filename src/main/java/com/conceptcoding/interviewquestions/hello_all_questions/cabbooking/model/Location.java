package com.conceptcoding.interviewquestions.hello_all_questions.cabbooking.model;

/**
 * A geographic point. Latitude / longitude are doubles for clarity here —
 * in production we'd use a fixed-precision integer (microdegrees) to avoid
 * floating-point comparison surprises.
 *
 * <p>{@link #distanceKm(Location)} returns a <i>flat-Earth</i> approximation
 * (Euclidean × ~111 km/deg). For LLD scope this is plenty; the walkthrough
 * calls out Haversine + spatial indexing as the production upgrade.
 */
public record Location(double lat, double lng) {

    /** Approx. km between this location and another. Cheap, good enough for matching. */
    public double distanceKm(Location other) {
        double dLat = this.lat - other.lat;
        double dLng = this.lng - other.lng;
        // ~111 km per degree of latitude; longitude scaling ignored at LLD scope.
        return Math.sqrt(dLat * dLat + dLng * dLng) * 111.0;
    }
}
