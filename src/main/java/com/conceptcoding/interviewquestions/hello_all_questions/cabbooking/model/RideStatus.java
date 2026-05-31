package com.conceptcoding.interviewquestions.hello_all_questions.cabbooking.model;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Ride lifecycle — encoded as a small state machine with explicit allowed
 * transitions. {@link #canTransitionTo(RideStatus)} is what {@link Ride}
 * consults before mutating its own state — invalid transitions throw.
 *
 * <pre>
 *   REQUESTED ──match──► MATCHED ──start──► IN_PROGRESS ──complete──► COMPLETED
 *       │                   │
 *       └──cancel──┬────────┴──cancel──► CANCELLED
 * </pre>
 *
 * <p>We chose the enum-state-machine here (not the class-per-state GoF State
 * pattern) — most transitions are 1-line bookkeeping, no per-state behavior
 * worth its own class. Compare with VendingMachine, where each state has
 * distinct dispense/insert behavior and earns its own class.
 */
public enum RideStatus {
    REQUESTED, MATCHED, IN_PROGRESS, COMPLETED, CANCELLED;

    private static final Map<RideStatus, Set<RideStatus>> ALLOWED = new EnumMap<>(RideStatus.class);

    static {
        ALLOWED.put(REQUESTED,   EnumSet.of(MATCHED, CANCELLED));
        ALLOWED.put(MATCHED,     EnumSet.of(IN_PROGRESS, CANCELLED));
        ALLOWED.put(IN_PROGRESS, EnumSet.of(COMPLETED));
        ALLOWED.put(COMPLETED,   EnumSet.noneOf(RideStatus.class));
        ALLOWED.put(CANCELLED,   EnumSet.noneOf(RideStatus.class));
    }

    public boolean canTransitionTo(RideStatus next) {
        return ALLOWED.get(this).contains(next);
    }
}
