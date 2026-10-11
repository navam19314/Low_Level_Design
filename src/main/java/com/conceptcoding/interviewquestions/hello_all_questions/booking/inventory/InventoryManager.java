package com.conceptcoding.interviewquestions.hello_all_questions.booking.inventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

// The service callers use. The warehouses are created once, in the constructor, and the map
// is never changed after that, so reading it from many threads is safe.
// The locks live on each Warehouse; only transfer() needs two of them.
public class InventoryManager {

    private final Map<String, Warehouse> warehouses = new HashMap<>();

    public InventoryManager(List<String> warehouseIds) {
        for (String id : warehouseIds) {
            warehouses.put(id, new Warehouse(id));
        }
    }

    public void addStock(String warehouseId, String productId, int quantity) {
        checkQuantity(quantity);
        findWarehouse(warehouseId).addStock(productId, quantity);
    }

    public boolean removeStock(String warehouseId, String productId, int quantity) {
        checkQuantity(quantity);
        return findWarehouse(warehouseId).removeStock(productId, quantity);
    }

    public int getStock(String warehouseId, String productId) {
        return findWarehouse(warehouseId).getStock(productId);
    }

    // which warehouses can ship this many right now
    public List<String> warehousesWith(String productId, int quantity) {
        List<String> result = new ArrayList<>();
        for (Warehouse w : warehouses.values()) {
            if (w.getStock(productId) >= quantity) result.add(w.getId());
        }
        return result;
    }

    // Move stock in one step: never "taken from A but not yet in B".
    // Both warehouses are locked in id ORDER, so A→B and B→A at the same moment can't deadlock.
    public boolean transfer(String productId, String fromId, String toId, int quantity) {
        checkQuantity(quantity);
        if (fromId.equals(toId)) throw new IllegalArgumentException("Same warehouse: " + fromId);
        Warehouse from = findWarehouse(fromId);
        Warehouse to = findWarehouse(toId);

        Warehouse first = fromId.compareTo(toId) < 0 ? from : to;     // smaller id first, always
        Warehouse second = (first == from) ? to : from;
        synchronized (first) {
            synchronized (second) {
                if (!from.removeStock(productId, quantity)) return false;   // not enough: nothing moved
                to.addStock(productId, quantity);
                return true;
            }
        }
    }

    private void checkQuantity(int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive: " + quantity);
    }

    private Warehouse findWarehouse(String warehouseId) {
        Warehouse w = warehouses.get(warehouseId);
        if (w == null) throw new NoSuchElementException("Unknown warehouse " + warehouseId);
        return w;
    }
}
