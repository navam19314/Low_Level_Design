package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.elevator;

import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.elevator.model.Direction;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ElevatorDriver {

    public static void main(String[] args) throws Exception {
        scenarioScanOrder();
        scenarioNearestIdle();
        scenarioRightDirectionBeatsNearest();
        scenarioHallAndCabMixed();
        scenarioValidation();
        scenarioConcurrentCalls();
    }

    // At floor 5 with stops 8 (above) and 3 (below), going UP: serve 8 first, then turn to 3.
    private static void scenarioScanOrder() {
        System.out.println("=== SCAN: at 5 going up, stops 8 and 3 → 8 first, then turn around to 3 ===");
        Elevator e = new Elevator(1);
        e.addStop(5);
        e.moveToNextStop();                   // at 5, direction UP
        e.addStop(8);
        e.addStop(3);
        for (int tick = 1; tick <= 3; tick++) {
            System.out.println("  tick " + tick + " → floor " + e.moveToNextStop() + " [" + e.getDirection() + "]");
        }
        System.out.println("  (expect 8 UP, 3 DOWN, -1 IDLE)\n");
    }

    private static void scenarioNearestIdle() {
        System.out.println("=== Nearest idle: call at 6, E1 idle at 0, E2 idle at 8 ===");
        Elevator e1 = new Elevator(1);
        Elevator e2 = new Elevator(2);
        e2.addStop(8); e2.moveToNextStop(); e2.moveToNextStop();     // park E2 at 8, idle
        ElevatorController ctrl = new ElevatorController(List.of(e1, e2), 10);
        System.out.println("  dispatched to Elevator-" + ctrl.callElevator(6, Direction.UP) + " (expect 2: 2 floors vs 6)\n");
    }

    // E1 at 8 going UP is nearer to a DOWN call at 6, but it must reach 10 first and come back.
    // E2 at 9 is already going DOWN and will pass 6, so tier 1 picks E2.
    private static void scenarioRightDirectionBeatsNearest() {
        System.out.println("=== Right direction beats nearest: DOWN call at 6 ===");
        Elevator up8 = new Elevator(1);
        up8.addStop(8); up8.moveToNextStop(); up8.addStop(10);            // at 8, heading UP to 10
        Elevator down9 = new Elevator(2);
        down9.addStop(10); down9.moveToNextStop();                        // at 10
        down9.addStop(9); down9.addStop(1); down9.moveToNextStop();       // at 9, heading DOWN to 1
        ElevatorController ctrl = new ElevatorController(List.of(up8, down9), 10);
        System.out.println("  E1 at " + up8.getCurrentFloor() + " " + up8.getDirection()
                + ", E2 at " + down9.getCurrentFloor() + " " + down9.getDirection());
        System.out.println("  dispatched to Elevator-" + ctrl.callElevator(6, Direction.DOWN)
                + " (expect 2: already heading down past 6; E1 is nearer but going up)\n");
    }

    private static void scenarioHallAndCabMixed() {
        System.out.println("=== Hall + cab buttons mixed ===");
        Elevator e1 = new Elevator(1);
        Elevator e2 = new Elevator(2);
        ElevatorController ctrl = new ElevatorController(List.of(e1, e2), 10);
        ctrl.callElevator(3, Direction.UP);
        ctrl.callElevator(7, Direction.UP);
        ctrl.selectFloor(1, 5);
        ctrl.selectFloor(1, 9);
        System.out.println("  " + e1 + "\n  " + e2);
        for (int t = 1; t <= 6; t++) {
            List<String> stops = ctrl.advance();
            if (stops.isEmpty()) { System.out.println("  tick " + t + ": all idle"); break; }
            System.out.println("  tick " + t + ": " + stops);
        }
        System.out.println();
    }

    private static void scenarioValidation() {
        System.out.println("=== Validation ===");
        ElevatorController ctrl = new ElevatorController(List.of(new Elevator(1)), 10);
        tryIt("call floor 15 in a 0..10 building", () -> ctrl.callElevator(15, Direction.UP));
        tryIt("UP on the top floor", () -> ctrl.callElevator(10, Direction.UP));
        tryIt("cab button in elevator 7", () -> ctrl.selectFloor(7, 3));
        System.out.println();
    }

    // 200 hall calls from many threads while another thread keeps ticking: no corruption, all served.
    private static void scenarioConcurrentCalls() throws Exception {
        System.out.println("=== Concurrency: 200 hall calls while the cars are moving ===");
        Elevator e1 = new Elevator(1);
        Elevator e2 = new Elevator(2);
        ElevatorController ctrl = new ElevatorController(List.of(e1, e2), 50);
        ExecutorService pool = Executors.newFixedThreadPool(9);
        CountDownLatch start = new CountDownLatch(1);
        for (int t = 0; t < 8; t++) {
            final int seed = t;
            pool.submit(() -> {
                start.await();
                for (int i = 0; i < 25; i++) ctrl.callElevator(1 + (seed * 25 + i) % 49, Direction.UP);
                return null;
            });
        }
        pool.submit(() -> {                                    // the "tick" thread
            start.await();
            for (int i = 0; i < 500; i++) ctrl.advance();
            return null;
        });
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);
        while (e1.getPendingCount() + e2.getPendingCount() > 0) ctrl.advance();   // drain what's left
        System.out.println("  finished, pending stops = " + (e1.getPendingCount() + e2.getPendingCount()) + " (expect 0)");
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
