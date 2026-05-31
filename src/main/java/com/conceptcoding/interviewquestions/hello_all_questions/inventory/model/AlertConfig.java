package com.conceptcoding.interviewquestions.hello_all_questions.inventory.model;

import java.util.Objects;

/**
 * Immutable value object pairing a threshold with the listener to notify.
 * Lives in a {@code List<AlertConfig>} per product on each warehouse so multiple
 * thresholds (e.g., warn at 20, critical at 5) can be registered independently.
 *
 * <p>No state on the config itself — the threshold-CROSSING check in
 * Warehouse.getAlertsToFire handles "fire once, reset on recovery" naturally.
 */
public record AlertConfig(int threshold, AlertListener listener) {

    public AlertConfig {
        if (threshold <= 0) throw new IllegalArgumentException("threshold must be > 0");
        Objects.requireNonNull(listener, "listener must not be null");
    }
}
