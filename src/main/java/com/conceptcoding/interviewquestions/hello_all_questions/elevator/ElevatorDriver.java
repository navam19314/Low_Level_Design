package com.conceptcoding.interviewquestions.hello_all_questions.elevator;

import com.conceptcoding.interviewquestions.hello_all_questions.elevator.model.Request;
import com.conceptcoding.interviewquestions.hello_all_questions.elevator.model.RequestType;

import java.util.ArrayList;
import java.util.List;

public class ElevatorDriver {

    private static final int MIN_FLOOR = 0;
    private static final int MAX_FLOOR = 9;

    public static void main(String[] args) {
        List<Elevator> elevators = new ArrayList<>();
        elevators.add(new Elevator("E1", MIN_FLOOR, MAX_FLOOR));
        elevators.add(new Elevator("E2", MIN_FLOOR, MAX_FLOOR));
        elevators.add(new Elevator("E3", MIN_FLOOR, MAX_FLOOR));

        ElevatorController controller = new ElevatorController(elevators, MIN_FLOOR, MAX_FLOOR);

        System.out.println("--- Scenario 1: hall call from floor 5 going UP ---");
        controller.requestElevator(5, RequestType.PICKUP_UP);
        runTicks(controller, 10);

        System.out.println("\n--- Scenario 2: passenger inside E1 wants floor 8 ---");
        elevators.get(0).addRequest(new Request(8, RequestType.DESTINATION));
        runTicks(controller, 6);

        System.out.println("\n--- Scenario 3: hall call from 2 going DOWN — dispatch should prefer different elevator ---");
        controller.requestElevator(2, RequestType.PICKUP_DOWN);
        runTicks(controller, 12);

        System.out.println("\n--- Scenario 4: invalid hall call (DESTINATION via controller) ---");
        boolean ok = controller.requestElevator(3, RequestType.DESTINATION);
        System.out.println("requestElevator(3, DESTINATION) accepted? " + ok + " (expected false)");

        System.out.println("\n--- Scenario 5: out-of-bounds floor ---");
        ok = controller.requestElevator(99, RequestType.PICKUP_UP);
        System.out.println("requestElevator(99, PICKUP_UP) accepted? " + ok + " (expected false)");
    }

    private static void runTicks(ElevatorController controller, int ticks) {
        for (int t = 0; t < ticks; t++) {
            controller.step();
            printState(controller, t);
            if (allIdle(controller)) {
                System.out.println("  (all idle — stopping early)");
                break;
            }
        }
    }

    private static void printState(ElevatorController controller, int tick) {
        StringBuilder sb = new StringBuilder("t=").append(tick).append("  ");
        for (Elevator e : controller.getElevators()) {
            sb.append(e.getId()).append("@").append(e.getCurrentFloor())
              .append("(").append(e.getDirection()).append(")");
            sb.append(" reqs=").append(e.getRequests()).append("  ");
        }
        System.out.println(sb);
    }

    private static boolean allIdle(ElevatorController controller) {
        for (Elevator e : controller.getElevators()) {
            if (e.getDirection() != com.conceptcoding.interviewquestions.hello_all_questions.elevator.model.Direction.IDLE) return false;
            if (!e.getRequests().isEmpty()) return false;
        }
        return true;
    }
}
