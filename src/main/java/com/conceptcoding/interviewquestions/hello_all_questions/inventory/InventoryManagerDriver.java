package com.conceptcoding.interviewquestions.hello_all_questions.inventory;

import com.conceptcoding.interviewquestions.hello_all_questions.inventory.model.AlertListener;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class InventoryManagerDriver {

    public static void main(String[] args) throws Exception {
        scenarioBasics();
        scenarioAlertCrossing();
        scenarioRejectNegative();
        scenarioConcurrentRemoval();
        scenarioConcurrentBidirectionalTransfer();
    }

    // ---- 1. Basic CRUD + availability across warehouses --------
    private static void scenarioBasics() {
        System.out.println("=== Scenario 1: basic CRUD + getWarehousesWithAvailability ===");
        InventoryManager mgr = new InventoryManager(List.of("EAST", "WEST", "NORTH"));

        mgr.addStock("EAST",  "WIDGET", 50);
        mgr.addStock("WEST",  "WIDGET", 30);
        mgr.addStock("NORTH", "WIDGET",  5);

        System.out.println("  Warehouses with >= 25 WIDGETs: " +
                mgr.getWarehousesWithAvailability("WIDGET", 25) + "  (expect [EAST, WEST])");

        System.out.println("  Remove 20 from EAST: " + mgr.removeStock("EAST", "WIDGET", 20)
                + " → EAST now has " + mgr.getStock("EAST", "WIDGET") + " (expect 30)");
        System.out.println();
    }

    // ---- 2. Threshold-crossing alert behavior (the core observer test) --------
    private static void scenarioAlertCrossing() {
        System.out.println("=== Scenario 2: alert fires ONCE per downward crossing; resets on recovery ===");
        InventoryManager mgr = new InventoryManager(List.of("EAST"));
        CountingAlertListener counter = new CountingAlertListener();
        mgr.setLowStockAlert("EAST", "WIDGET", /* threshold */ 10, counter);

        mgr.addStock("EAST", "WIDGET", 15);     // 0 → 15  : no fire (never above threshold? actually was below; check below)
        System.out.println("  After +15 (0→15): fires=" + counter.fires.get() + " (expect 0 — went UP across, no downward crossing)");

        mgr.removeStock("EAST", "WIDGET", 6);   // 15 → 9 : CROSS downward → fire
        System.out.println("  After -6  (15→9): fires=" + counter.fires.get() + " (expect 1 — first downward crossing)");

        mgr.removeStock("EAST", "WIDGET", 2);   // 9 → 7  : already below, no NEW crossing
        System.out.println("  After -2  (9→7):  fires=" + counter.fires.get() + " (expect 1 — no duplicate)");

        mgr.addStock("EAST", "WIDGET", 15);     // 7 → 22 : recovery, no downward crossing
        System.out.println("  After +15 (7→22): fires=" + counter.fires.get() + " (expect 1 — still no upward fire)");

        mgr.removeStock("EAST", "WIDGET", 13);  // 22 → 9 : CROSS downward AGAIN → fire
        System.out.println("  After -13 (22→9): fires=" + counter.fires.get() + " (expect 2 — natural reset on recovery)");
        System.out.println();
    }

    // ---- 3. Reject operations that would violate "no negative inventory" --------
    private static void scenarioRejectNegative() {
        System.out.println("=== Scenario 3: reject removes / transfers that would go negative ===");
        InventoryManager mgr = new InventoryManager(List.of("EAST", "WEST"));
        mgr.addStock("EAST", "WIDGET", 2);

        boolean tooBig = mgr.removeStock("EAST", "WIDGET", 10);
        System.out.println("  Remove 10 from EAST (has 2): allowed=" + tooBig
                + " → EAST still " + mgr.getStock("EAST", "WIDGET") + " (expect 2, unchanged)");

        boolean tooBigTransfer = mgr.transfer("WIDGET", "EAST", "WEST", 10);
        System.out.println("  Transfer 10 EAST→WEST (has 2): allowed=" + tooBigTransfer
                + " → EAST=" + mgr.getStock("EAST", "WIDGET") + " WEST=" + mgr.getStock("WEST", "WIDGET")
                + " (expect 2, 0 — atomic rollback)");
        System.out.println();
    }

    // ---- 4. Concurrent removal — exactly stock-count succeed --------
    private static void scenarioConcurrentRemoval() throws Exception {
        System.out.println("=== Scenario 4: 50 threads race to remove from a warehouse with 20 stock ===");
        InventoryManager mgr = new InventoryManager(List.of("EAST"));
        mgr.addStock("EAST", "WIDGET", 20);

        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch fire  = new CountDownLatch(1);
        AtomicInteger ok = new AtomicInteger(), denied = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try { fire.await(); } catch (InterruptedException e) { return; }
                if (mgr.removeStock("EAST", "WIDGET", 1)) ok.incrementAndGet();
                else                                      denied.incrementAndGet();
            });
        }
        ready.await();
        fire.countDown();
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        System.out.println("  ok=" + ok.get() + " denied=" + denied.get()
                + " stock-left=" + mgr.getStock("EAST", "WIDGET"));
        System.out.println("  expect: ok=20, denied=30, stock-left=0     (per-warehouse lock holds)");
        System.out.println(ok.get() == 20 && denied.get() == 30 && mgr.getStock("EAST", "WIDGET") == 0
                ? "  ✓ atomicity preserved" : "  ✗ RACE — counts don't match!");
        System.out.println();
    }

    // ---- 5. Bidirectional concurrent transfers — would deadlock WITHOUT lock ordering --------
    private static void scenarioConcurrentBidirectionalTransfer() throws Exception {
        System.out.println("=== Scenario 5: 100 concurrent transfers A↔B (would deadlock without ordering) ===");
        InventoryManager mgr = new InventoryManager(List.of("A", "B"));
        mgr.addStock("A", "WIDGET", 1000);
        mgr.addStock("B", "WIDGET", 1000);

        int threads = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch fire  = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            final int n = i;
            pool.submit(() -> {
                ready.countDown();
                try { fire.await(); } catch (InterruptedException e) { return; }
                String from = (n % 2 == 0) ? "A" : "B";     // 50 threads each direction
                String to   = (n % 2 == 0) ? "B" : "A";
                if (mgr.transfer("WIDGET", from, to, 1)) successes.incrementAndGet();
            });
        }
        ready.await();
        fire.countDown();
        pool.shutdown();
        boolean finished = pool.awaitTermination(5, TimeUnit.SECONDS);

        int aStock = mgr.getStock("A", "WIDGET");
        int bStock = mgr.getStock("B", "WIDGET");

        System.out.println("  finished in time: " + finished + "  (true → no deadlock)");
        System.out.println("  successful transfers: " + successes.get() + "  (expect 100)");
        System.out.println("  A=" + aStock + "  B=" + bStock + "  total=" + (aStock + bStock)
                + "  (total expect 2000 — no inventory created or destroyed)");
        System.out.println(finished && successes.get() == 100 && (aStock + bStock) == 2000
                ? "  ✓ ordered lock acquisition prevented deadlock + preserved invariant"
                : "  ✗ something raced or deadlocked");
    }

    /** Thread-safe counter for the alert-crossing test. */
    static class CountingAlertListener implements AlertListener {
        final AtomicInteger fires = new AtomicInteger();
        final List<String> history = Collections.synchronizedList(new CopyOnWriteArrayList<>());

        @Override
        public void onLowStock(String warehouseId, String productId, int currentQuantity) {
            fires.incrementAndGet();
            history.add(warehouseId + "/" + productId + "=" + currentQuantity);
        }
    }
}
