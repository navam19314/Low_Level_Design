package com.conceptcoding.interviewquestions.hello_all_questions.cabbooking.matching;

import com.conceptcoding.interviewquestions.hello_all_questions.cabbooking.model.Driver;
import com.conceptcoding.interviewquestions.hello_all_questions.cabbooking.model.Location;

import java.util.Comparator;
import java.util.List;

/**
 * Rank all AVAILABLE drivers by Euclidean distance to the pickup, nearest first.
 *
 * <p>Complexity: O(n log n) on the full available-driver pool. For real
 * city-scale (10k+ drivers, 100s of req/sec) this is replaced by a spatial
 * index lookup (grid, quadtree, H3) — see walkthrough Step 5.
 *
 * <p>An optional {@code maxRadiusKm} caps the search — if the nearest
 * candidate is still beyond it, no driver is returned (rider sees "no cars
 * nearby"). 0 / Double.MAX_VALUE = no cap.
 */
public class NearestDriverStrategy implements DriverMatchingStrategy {

    private final double maxRadiusKm;

    public NearestDriverStrategy()                       { this(Double.MAX_VALUE); }
    public NearestDriverStrategy(double maxRadiusKm)     { this.maxRadiusKm = maxRadiusKm; }

    @Override
    public List<Driver> rankCandidates(Location pickup, List<Driver> available) {
        return available.stream()
                .filter(d -> d.getCurrentLocation().distanceKm(pickup) <= maxRadiusKm)
                .sorted(Comparator.comparingDouble(d -> d.getCurrentLocation().distanceKm(pickup)))
                .toList();
    }
}
