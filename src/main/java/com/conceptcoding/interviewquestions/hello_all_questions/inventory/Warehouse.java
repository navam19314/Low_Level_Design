package com.conceptcoding.interviewquestions.hello_all_questions.inventory;

import com.conceptcoding.interviewquestions.hello_all_questions.inventory.model.AlertConfig;
import com.conceptcoding.interviewquestions.hello_all_questions.inventory.model.AlertListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// One storage location. Owns:
//   - a productId → quantity map (no negative stock invariant)
//   - a productId → List<AlertConfig> map (multiple thresholds per product)
//   - the per-warehouse lock (coarse-grained synchronized(this))
//
// Concurrency: every public mutator AND reader is synchronized so readers see
// a consistent snapshot. Two threads on the SAME warehouse serialize; threads on
// DIFFERENT warehouses never block each other — exactly what we want.
//
// Alert protocol: state is captured under lock, then listeners are invoked
// AFTER lock release. Prevents two pathologies:
//   1. A slow listener (network I/O) holding the warehouse lock for seconds.
//   2. A listener calling back into the same warehouse → reentrant deadlock-equivalent.
public class Warehouse {

    private final String id;
    private final Map<String, Integer> inventory = new HashMap<>();
    private final Map<String, List<AlertConfig>> alertConfigs = new HashMap<>();

    public Warehouse(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    // Always succeeds (you can always receive more stock); fires any threshold-crossing alerts.
    public void addStock(String productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be > 0");
        }
        List<PendingAlert> toFire;
        synchronized (this) {
            int prev = inventory.getOrDefault(productId, 0);
            int next = prev + quantity;
            inventory.put(productId, next);
            // Collect under lock (immutable view of which alerts crossed) — fire outside.
            toFire = collectAlertsToFire(productId, prev, next);
        }
        fireAll(toFire);
    }

    // Returns false (without mutating) if insufficient stock — enforces the no-negative-inventory invariant.
    public boolean removeStock(String productId, int quantity) {
        if (quantity <= 0) return false;
        List<PendingAlert> toFire;
        synchronized (this) {
            int prev = inventory.getOrDefault(productId, 0);
            if (prev < quantity) {
                return false;          // ← all-or-nothing, no partial state change
            }
            int next = prev - quantity;
            inventory.put(productId, next);
            toFire = collectAlertsToFire(productId, prev, next);
        }
        fireAll(toFire);
        return true;
    }

    public synchronized int getStock(String productId) {
        return inventory.getOrDefault(productId, 0);
    }

    public synchronized boolean checkAvailability(String productId, int quantity) {
        if (quantity <= 0) return false;
        return inventory.getOrDefault(productId, 0) >= quantity;
    }

    public synchronized void setLowStockAlert(String productId, int threshold, AlertListener listener) {
        AlertConfig config = new AlertConfig(threshold, listener);
        alertConfigs.computeIfAbsent(productId, k -> new ArrayList<>()).add(config);
    }

    // ----- internals -----

    // Threshold-CROSSING check: alert fires only on the transition from
    // "at-or-above threshold" → "below threshold". Naturally handles:
    //   - no duplicates while stock stays below the threshold
    //   - no spurious fires on stock increases (additions can never cross downward)
    //   - automatic "reset" if stock recovers above the threshold and drops again
    // No mutable state on AlertConfig required.
    private List<PendingAlert> collectAlertsToFire(String productId, int prev, int next) {
        List<AlertConfig> configs = alertConfigs.get(productId);
        if (configs == null) return List.of();
        List<PendingAlert> result = new ArrayList<>();
        for (AlertConfig cfg : configs) {
            if (prev >= cfg.threshold() && next < cfg.threshold()) {
                result.add(new PendingAlert(cfg.listener(), productId, next));
            }
        }
        return result;
    }

    private void fireAll(List<PendingAlert> alerts) {
        for (PendingAlert a : alerts) {
            try {
                a.listener.onLowStock(id, a.productId, a.currentQuantity);
            } catch (Exception e) {
                // A misbehaving listener must not corrupt the warehouse or kill the caller.
                // Catch Exception, NOT Throwable — never swallow JVM Errors (OOM/StackOverflow).
                System.err.println("Warehouse " + id + ": alert listener threw — " + e.getMessage());
            }
        }
    }

    // Internal struct so collection-under-lock and firing-outside-lock are decoupled.
    private static final class PendingAlert {
        final AlertListener listener;
        final String productId;
        final int currentQuantity;

        PendingAlert(AlertListener listener, String productId, int currentQuantity) {
            this.listener = listener;
            this.productId = productId;
            this.currentQuantity = currentQuantity;
        }
    }
}
