package com.conceptcoding.interviewquestions.hello_all_questions.cabbooking.matching;

import com.conceptcoding.interviewquestions.hello_all_questions.cabbooking.model.Driver;
import com.conceptcoding.interviewquestions.hello_all_questions.cabbooking.model.Location;

import java.util.List;

/**
 * "Given a pickup location and a pool of candidate drivers, return an ORDERED
 * list of drivers to try, best first."
 *
 * <p>Returns a list — not a single driver — because the top choice might lose
 * the AVAILABLE → ON_TRIP race; the service then tries the next candidate.
 *
 * <p>Implementations:
 *   - {@link NearestDriverStrategy} — purely distance-based
 *   - (future) HighestRatedStrategy, SurgeAwareStrategy, RegionedStrategy, ...
 *
 * <p>This is THE design seam of the system. New matching policies (e.g.
 * "prefer drivers heading toward the rider's destination") slot in as new
 * implementations without touching {@link com.conceptcoding.interviewquestions.hello_all_questions.cabbooking.CabBookingService}.
 */
public interface DriverMatchingStrategy {
    List<Driver> rankCandidates(Location pickup, List<Driver> availableDrivers);
}
