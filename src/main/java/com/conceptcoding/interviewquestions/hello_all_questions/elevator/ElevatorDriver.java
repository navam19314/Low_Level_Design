package com.conceptcoding.interviewquestions.hello_all_questions.elevator;

import com.conceptcoding.interviewquestions.hello_all_questions.elevator.model.Direction;

import java.util.List;

public class ElevatorDriver {

    public static void main(String[] args) {
        scenarioScanOrder();
        scenarioTwoElevators();
        scenarioInCabAndHallMixed();
    }

    // Start at 0, go to 5 first, then requests above (8) and below (3).
    // SCAN: serves 8 UP, then flips DOWN to 3.
    private static void scenarioScanOrder() {
        System.out.println("=== SCAN order: UP to 5, then 8, then DOWN to 3 ===");
        Elevator e = new Elevator(1);

        e.addStop(5);
        e.moveToNextStop();      // now at floor 5, direction UP
        e.moveToNextStop();      // both queues empty → direction flips to IDLE

        e.addStop(8);            // 8 > 5 → upQueue
        e.addStop(3);            // 3 < 5 → downQueue
        System.out.println("At floor 5, queued: " + e);

        for (int tick = 1; tick <= 3; tick++) {
            int f = e.moveToNextStop();
            System.out.println("  tick " + tick + " → floor " + f + "  [" + e.getDirection() + "]");
        }
        System.out.println();
    }

    // E1 at floor 0, E2 idle at floor 8.
    // Hall call at floor 6 UP → E2 (dist=2) wins over E1 (dist=6) via tier-2 nearest-idle.
    private static void scenarioTwoElevators() {
        System.out.println("=== Two elevators — nearest idle dispatch ===");
        Elevator e1 = new Elevator(1);  // at floor 0
        Elevator e2 = new Elevator(2);
        e2.addStop(8); e2.moveToNextStop(); e2.moveToNextStop(); // advance to 8, drain to IDLE

        System.out.println("E1 at floor " + e1.getCurrentFloor() + " [" + e1.getDirection() + "]"
                + ",  E2 at floor " + e2.getCurrentFloor() + " [" + e2.getDirection() + "]");

        ElevatorController ctrl = new ElevatorController(List.of(e1, e2));

        // E2 at 8 (dist=2) is nearer than E1 at 0 (dist=6) — dispatch picks E2
        ctrl.callElevator(6, Direction.UP);
        System.out.println("Hall call floor 6 UP  →  dispatched to Elevator-"
                + ctrl.getElevators().stream()
                      .filter(e -> e.getPendingCount() > 0)
                      .mapToInt(Elevator::getId).findFirst().orElse(-1)
                + "  (expect E2 — nearest idle)");

        System.out.println("Steps:");
        ctrl.advance();
        System.out.println("E1=" + e1.getCurrentFloor() + ", E2=" + e2.getCurrentFloor());
        System.out.println();
    }

    // Hall calls + in-cab buttons mixed
    private static void scenarioInCabAndHallMixed() {
        System.out.println("=== Hall + in-cab mixed ===");
        Elevator e1 = new Elevator(1);
        Elevator e2 = new Elevator(2);
        ElevatorController ctrl = new ElevatorController(List.of(e1, e2));

        ctrl.callElevator(3, Direction.UP);    // hall: floor 3 UP  → E1 (nearest idle)
        ctrl.callElevator(7, Direction.UP);    // hall: floor 7 UP  → E1 or E2
        ctrl.selectFloor(1, 5);               // in-cab: passenger in E1 presses 5
        ctrl.selectFloor(1, 9);               // in-cab: passenger in E1 presses 9

        System.out.println("After requests — E1: " + e1 + "\n                E2: " + e2);
        System.out.println("Steps:");
        for (int t = 1; t <= 5; t++) {
            System.out.print("  tick " + t + ": ");
            ctrl.advance();
            if (e1.getPendingCount() == 0 && e2.getPendingCount() == 0) {
                System.out.println("  (all idle)");
                break;
            }
        }
    }
}
