package com.conceptcoding.interviewquestions.hello_all_questions.cabbooking;

import com.conceptcoding.interviewquestions.hello_all_questions.cabbooking.matching.DriverMatchingStrategy;
import com.conceptcoding.interviewquestions.hello_all_questions.cabbooking.model.Driver;
import com.conceptcoding.interviewquestions.hello_all_questions.cabbooking.model.DriverStatus;
import com.conceptcoding.interviewquestions.hello_all_questions.cabbooking.model.Location;
import com.conceptcoding.interviewquestions.hello_all_questions.cabbooking.model.Ride;
import com.conceptcoding.interviewquestions.hello_all_questions.cabbooking.model.Rider;
import com.conceptcoding.interviewquestions.hello_all_questions.cabbooking.pricing.PricingStrategy;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Facade. Holds registries + injected strategies; orchestrates the ride lifecycle:
 *
 *   1. {@link #requestRide} — finds an AVAILABLE driver via the matching
 *      strategy, atomically reserves them (AVAILABLE → ON_TRIP), creates
 *      a Ride in MATCHED.
 *   2. {@link #startRide}    — driver picked rider up; Ride MATCHED → IN_PROGRESS.
 *   3. {@link #completeRide} — drop-off; calculates fare, releases driver,
 *      Ride IN_PROGRESS → COMPLETED.
 *   4. {@link #cancelRide}   — rider cancelled before/after match; if matched,
 *      driver is released; Ride → CANCELLED.
 *
 * <p><b>Concurrency model:</b>
 *   - Registries are {@link ConcurrentHashMap} so multiple requests can be
 *     served in parallel without coarse locking.
 *   - Driver status mutation is gated by {@code Driver.tryReserve()} which
 *     is internally {@code synchronized(this)} — the AVAILABLE → ON_TRIP
 *     transition is atomic per driver.
 *   - The matching strategy returns a RANKED LIST; we iterate and try to
 *     reserve each until one wins. This is the classic "optimistic match"
 *     loop — no global lock, just per-driver CAS-style ordering.
 *   - Ride state transitions are gated by {@link Ride#transitionTo} under
 *     {@code synchronized(ride)} — concurrent complete/cancel on the same
 *     ride can't corrupt state.
 *
 * <p><b>Surge:</b> {@code currentSurgeBasisPoints} is a simple field updated
 * by some external demand-monitor (out of scope here). The pricing strategy
 * receives it per call.
 */
public class CabBookingService {

    private final ConcurrentMap<String, Driver> drivers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Rider>  riders  = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Ride>   rides   = new ConcurrentHashMap<>();
    private final AtomicLong                    rideSeq = new AtomicLong();

    private final DriverMatchingStrategy matchingStrategy;
    private final PricingStrategy        pricingStrategy;
    private final Clock                  clock;

    private volatile int currentSurgeBasisPoints = 10_000;   // 1.0× default

    public CabBookingService(DriverMatchingStrategy matchingStrategy,
                             PricingStrategy        pricingStrategy,
                             Clock                  clock) {
        this.matchingStrategy = matchingStrategy;
        this.pricingStrategy  = pricingStrategy;
        this.clock            = clock;
    }

    // ---- registries ----

    public void registerDriver(Driver d) { drivers.put(d.getId(), d); }
    public void registerRider(Rider r)   { riders.put(r.getId(), r); }

    public Optional<Driver> getDriver(String id) { return Optional.ofNullable(drivers.get(id)); }
    public Optional<Ride>   getRide(String id)   { return Optional.ofNullable(rides.get(id)); }

    public void setSurgeBasisPoints(int bps) {
        if (bps < 10_000) throw new IllegalArgumentException("Surge cannot reduce price below 1.0×");
        this.currentSurgeBasisPoints = bps;
    }

    // ---- ride lifecycle ----

    /**
     * Try to book a ride. On success returns the Ride in MATCHED state with
     * a Driver assigned. Throws if no driver could be reserved.
     */
    public Ride requestRide(Rider rider, Location pickup, Location dropoff) {
        // 1) snapshot the AVAILABLE pool. Snapshot — not live view — so the
        //    matching strategy operates on a stable list.
        List<Driver> pool = new ArrayList<>();
        for (Driver d : drivers.values()) {
            if (d.getStatus() == DriverStatus.AVAILABLE) pool.add(d);
        }

        // 2) ask strategy for ranked candidates
        List<Driver> ranked = matchingStrategy.rankCandidates(pickup, pool);

        // 3) optimistic match — try each in order. First to win the AVAILABLE → ON_TRIP race takes the ride.
        for (Driver candidate : ranked) {
            if (candidate.tryReserve()) {
                String id   = "ride-" + rideSeq.incrementAndGet();
                Ride   ride = new Ride(id, rider, pickup, dropoff, clock);
                ride.match(candidate);
                rides.put(id, ride);
                return ride;
            }
            // lost the race — candidate was just snatched; loop continues
        }
        throw new IllegalStateException("No drivers available near pickup");
    }

    /** Driver arrived, rider got in — start the trip. */
    public void startRide(String rideId) {
        Ride r = required(rideId);
        r.start();
    }

    /** Drop-off — calculate fare, release driver, mark COMPLETED. */
    public long completeRide(String rideId) {
        Ride r = required(rideId);
        long fare = pricingStrategy.calculateFareCents(
                r.getSource(), r.getDestination(), currentSurgeBasisPoints);
        r.complete(fare);
        r.getDriver().releaseFromTrip(r.getDestination());
        return fare;
    }

    /**
     * Rider cancels. Allowed from REQUESTED or MATCHED. If MATCHED, the
     * driver is released (returns to AVAILABLE at their current location).
     */
    public void cancelRide(String rideId) {
        Ride r = required(rideId);
        Driver d = r.getDriver();   // may be null if cancellation hits before match
        r.cancel();
        if (d != null) d.releaseFromTrip(d.getCurrentLocation());
    }

    private Ride required(String rideId) {
        Ride r = rides.get(rideId);
        if (r == null) throw new IllegalArgumentException("Unknown ride " + rideId);
        return r;
    }
}
