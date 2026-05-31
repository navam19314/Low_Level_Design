package com.conceptcoding.interviewquestions.hello_all_questions.inventory;

import com.conceptcoding.interviewquestions.hello_all_questions.inventory.model.AlertListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Orchestrator + facade. Owns warehouses (fixed at startup) and routes operations
 * to them. The interesting method is {@link #transfer} — coordinates two warehouses
 * atomically with ORDERED LOCK ACQUISITION to prevent deadlock.
 *
 * <p>All other methods are thin delegation: look up warehouse(s), call the right
 * method, return the result. Validation happens at the boundary (null warehouse,
 * non-positive quantity).
 */
public class InventoryManager {

    private final Map<String, Warehouse> warehouses;

    public InventoryManager(List<String> warehouseIds) {
        this.warehouses = new HashMap<>();
        for (String id : warehouseIds) {
            warehouses.put(id, new Warehouse(id));
        }
    }

    public void addStock(String warehouseId, String productId, int quantity) {
        warehouseOrThrow(warehouseId).addStock(productId, quantity);
    }

    public boolean removeStock(String warehouseId, String productId, int quantity) {
        Warehouse w = warehouses.get(warehouseId);
        return w != null && w.removeStock(productId, quantity);
    }

    public int getStock(String warehouseId, String productId) {
        return warehouseOrThrow(warehouseId).getStock(productId);
    }

    public List<String> getWarehousesWithAvailability(String productId, int quantity) {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, Warehouse> e : warehouses.entrySet()) {
            if (e.getValue().checkAvailability(productId, quantity)) {
                result.add(e.getKey());
            }
        }
        return result;
    }

    public void setLowStockAlert(String warehouseId, String productId, int threshold, AlertListener listener) {
        warehouseOrThrow(warehouseId).setLowStockAlert(productId, threshold, listener);
    }

    /**
     * Atomically move stock from {@code fromId} to {@code toId}.
     *
     * <p>Why ORDERED lock acquisition: thread A doing A→B and thread B doing B→A
     * would otherwise deadlock — A holds(A) and waits for B; B holds(B) and waits
     * for A. Acquiring locks in a globally consistent order (compareTo) breaks the
     * cycle: both threads acquire the alphabetically-first warehouse first.
     *
     * <p>Java's {@code synchronized} is reentrant, so calls into
     * {@code removeStock}/{@code addStock} (which themselves use {@code synchronized(this)})
     * don't re-block — they re-enter the lock the current thread already holds.
     */
    public boolean transfer(String productId, String fromId, String toId, int quantity) {
        if (quantity <= 0)        return false;
        if (fromId.equals(toId))  return false;

        Warehouse from = warehouses.get(fromId);
        Warehouse to   = warehouses.get(toId);
        if (from == null || to == null) return false;

        // Order locks by warehouse-id string to break the deadlock cycle.
        Warehouse first  = fromId.compareTo(toId) < 0 ? from : to;
        Warehouse second = (first == from) ? to : from;

        synchronized (first) {
            synchronized (second) {
                if (!from.removeStock(productId, quantity)) {
                    return false;             // ← insufficient stock → no state change
                }
                to.addStock(productId, quantity);
                return true;
            }
        }
    }

    private Warehouse warehouseOrThrow(String warehouseId) {
        Warehouse w = warehouses.get(warehouseId);
        if (w == null) throw new NoSuchElementException("Unknown warehouse: " + warehouseId);
        return w;
    }
}
