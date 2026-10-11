package com.conceptcoding.interviewquestions.hello_all_questions.booking.inventory;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class InventoryManagerDriver {

    public static void main(String[] args) throws Exception {
        InventoryManager inv = new InventoryManager(List.of("BLR", "DEL"));
        inv.addStock("BLR", "iphone", 10);
        inv.addStock("DEL", "iphone", 2);

        System.out.println("=== Remove: never below zero ===");
        System.out.println("  remove 12 from BLR (has 10): " + inv.removeStock("BLR", "iphone", 12) + " (expect false)");
        System.out.println("  BLR still has " + inv.getStock("BLR", "iphone") + " (expect 10)");
        System.out.println("  remove 4 from BLR: " + inv.removeStock("BLR", "iphone", 4) + ", left " + inv.getStock("BLR", "iphone") + " (expect true, 6)");

        System.out.println("\n=== Which warehouses can ship 5? ===");
        System.out.println("  " + inv.warehousesWith("iphone", 5) + " (expect [BLR])");

        System.out.println("\n=== Transfer ===");
        System.out.println("  BLR → DEL 5: " + inv.transfer("iphone", "BLR", "DEL", 5)
                + ", BLR " + inv.getStock("BLR", "iphone") + ", DEL " + inv.getStock("DEL", "iphone") + " (expect true, 1, 7)");
        System.out.println("  BLR → DEL 3 (BLR has 1): " + inv.transfer("iphone", "BLR", "DEL", 3)
                + ", BLR " + inv.getStock("BLR", "iphone") + ", DEL " + inv.getStock("DEL", "iphone") + " (expect false, 1, 7)");

        System.out.println("\n=== Rejections ===");
        tryIt("transfer to the same warehouse", () -> inv.transfer("iphone", "BLR", "BLR", 1));
        tryIt("remove 0", () -> inv.removeStock("BLR", "iphone", 0));
        tryIt("unknown warehouse", () -> inv.addStock("MUM", "iphone", 1));

        lastUnits();
        oppositeTransfers();
    }

    // 50 buyers, 20 units: exactly 20 succeed and stock ends at 0.
    private static void lastUnits() throws Exception {
        System.out.println("\n=== Concurrency: 50 buyers, 20 units ===");
        InventoryManager inv = new InventoryManager(List.of("BLR"));
        inv.addStock("BLR", "ps5", 20);
        AtomicInteger sold = new AtomicInteger();
        runAtOnce(50, i -> { if (inv.removeStock("BLR", "ps5", 1)) sold.incrementAndGet(); });
        System.out.println("  sold = " + sold + " (expect 20), left = " + inv.getStock("BLR", "ps5") + " (expect 0)");
    }

    // 100 transfers at once, half BLR→DEL and half DEL→BLR: no deadlock, and the total never changes.
    private static void oppositeTransfers() throws Exception {
        System.out.println("\n=== Concurrency: 100 opposite transfers ===");
        InventoryManager inv = new InventoryManager(List.of("BLR", "DEL"));
        inv.addStock("BLR", "tv", 500);
        inv.addStock("DEL", "tv", 500);
        boolean finished = runAtOnce(100, i -> {
            if (i % 2 == 0) inv.transfer("tv", "BLR", "DEL", 3);
            else            inv.transfer("tv", "DEL", "BLR", 3);
        });
        int total = inv.getStock("BLR", "tv") + inv.getStock("DEL", "tv");
        System.out.println("  finished in time (no deadlock)? " + finished + ", total = " + total + " (expect true, 1000)");
    }

    interface Task { void run(int i); }

    private static boolean runAtOnce(int threads, Task task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        for (int i = 0; i < threads; i++) {
            final int n = i;
            pool.submit(() -> { go.await(); task.run(n); return null; });
        }
        go.countDown();
        pool.shutdown();
        return pool.awaitTermination(5, TimeUnit.SECONDS);
    }

    private static void tryIt(String label, Runnable action) {
        try {
            action.run();
            System.out.println("  " + label + ": ALLOWED ✗");
        } catch (RuntimeException e) {
            System.out.println("  " + label + ": rejected → " + e.getMessage());
        }
    }
}
