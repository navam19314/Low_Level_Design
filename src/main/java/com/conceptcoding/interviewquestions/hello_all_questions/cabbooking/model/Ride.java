package com.conceptcoding.interviewquestions.hello_all_questions.cabbooking.model;

import java.time.Clock;
import java.time.Instant;

/**
 * One ride request — joins a rider, an optional driver (assigned at MATCHED),
 * source / destination locations, lifecycle status, fare (cents), and timestamps.
 *
 * <p>State transitions go through {@link #transitionTo(RideStatus)} which
 * consults {@link RideStatus#canTransitionTo} — invalid transitions throw.
 * No setter takes a Status directly; the only way to mutate is via the
 * lifecycle methods ({@link #match}, {@link #start}, {@link #complete},
 * {@link #cancel}) so the state machine can't be bypassed.
 *
 * <p>Fare is {@code long cents} (always — never doubles for money).
 */
public class Ride {

    private final String   id;
    private final Rider    rider;
    private final Location source;
    private final Location destination;
    private final Instant  createdAt;
    private final Clock    clock;

    private Driver     driver;          // null until MATCHED
    private RideStatus status;
    private long       fareCents = -1;  // set on complete()
    private Instant    matchedAt;
    private Instant    startedAt;
    private Instant    completedAt;
    private Instant    cancelledAt;

    public Ride(String id, Rider rider, Location source, Location destination, Clock clock) {
        this.id          = id;
        this.rider       = rider;
        this.source      = source;
        this.destination = destination;
        this.clock       = clock;
        this.createdAt   = Instant.now(clock);
        this.status      = RideStatus.REQUESTED;
    }

    public String     getId()          { return id; }
    public Rider      getRider()       { return rider; }
    public Driver     getDriver()      { return driver; }
    public Location   getSource()      { return source; }
    public Location   getDestination() { return destination; }
    public RideStatus getStatus()      { return status; }
    public long       getFareCents()   { return fareCents; }
    public Instant    getCreatedAt()   { return createdAt; }
    public Instant    getMatchedAt()   { return matchedAt; }
    public Instant    getStartedAt()   { return startedAt; }
    public Instant    getCompletedAt() { return completedAt; }
    public Instant    getCancelledAt() { return cancelledAt; }

    // ---- lifecycle ----

    public synchronized void match(Driver d) {
        transitionTo(RideStatus.MATCHED);
        this.driver    = d;
        this.matchedAt = Instant.now(clock);
    }

    public synchronized void start() {
        transitionTo(RideStatus.IN_PROGRESS);
        this.startedAt = Instant.now(clock);
    }

    public synchronized void complete(long fareCents) {
        transitionTo(RideStatus.COMPLETED);
        this.fareCents   = fareCents;
        this.completedAt = Instant.now(clock);
    }

    public synchronized void cancel() {
        transitionTo(RideStatus.CANCELLED);
        this.cancelledAt = Instant.now(clock);
    }

    private void transitionTo(RideStatus next) {
        if (!status.canTransitionTo(next))
            throw new IllegalStateException("Illegal transition " + status + " → " + next + " on ride " + id);
        this.status = next;
    }

    @Override
    public String toString() {
        return "Ride(" + id + "," + status + (driver != null ? ",driver=" + driver.getId() : "") + ")";
    }
}
