package com.conceptcoding.interviewquestions.hello_all_questions.inventory.model;

/**
 * Observer-pattern interface. Warehouses notify listeners when stock drops below
 * a configured threshold. The warehouse doesn't know (or care) whether the listener
 * sends email, calls a webhook, logs to disk, or pushes to a queue — that's the
 * listener's problem.
 *
 * <p>Implementations MUST be safe to call from any thread (the warehouse releases
 * its internal lock BEFORE invoking listeners — see Warehouse.addStock).
 *
 * <p>Implementations MUST NOT call back into the same warehouse synchronously —
 * that would re-enter the lock indirectly. If you need to read warehouse state,
 * the alert payload already carries (warehouseId, productId, currentQuantity).
 */
public interface AlertListener {
    void onLowStock(String warehouseId, String productId, int currentQuantity);
}
