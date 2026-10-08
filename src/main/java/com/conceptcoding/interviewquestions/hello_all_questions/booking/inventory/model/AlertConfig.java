package com.conceptcoding.interviewquestions.hello_all_questions.booking.inventory.model;

import java.util.Objects;

// Immutable value object pairing a threshold with the listener to notify.
// Lives in a List<AlertConfig> per product on each warehouse so multiple
// thresholds (e.g., warn at 20, critical at 5) can be registered independently.
//
// No state on the config itself — the threshold-CROSSING check in
// Warehouse.collectAlertsToFire handles "fire once, reset on recovery" naturally.
//
// Normal class (not a record): matches the deck convention — easier to write
// fluently on a whiteboard, and some interviewers frown on records. Immutability
// is kept by hand: final fields, validated in the constructor, no setters.
public class AlertConfig {

    private final int threshold;
    private final AlertListener listener;

    public AlertConfig(int threshold, AlertListener listener) {
        if (threshold <= 0) throw new IllegalArgumentException("threshold must be > 0");
        this.threshold = threshold;
        this.listener = Objects.requireNonNull(listener, "listener must not be null");
    }

    public int threshold() {
        return threshold;
    }

    public AlertListener listener() {
        return listener;
    }
}
