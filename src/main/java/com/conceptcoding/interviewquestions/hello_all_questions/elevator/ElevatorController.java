package com.conceptcoding.interviewquestions.hello_all_questions.elevator;

import com.conceptcoding.interviewquestions.hello_all_questions.elevator.model.Direction;
import com.conceptcoding.interviewquestions.hello_all_questions.elevator.strategy.DispatchStrategy;
import com.conceptcoding.interviewquestions.hello_all_questions.elevator.strategy.NearestDispatchStrategy;

import java.util.List;

/*
 * ElevatorController — facade over all elevators.
 *
 * Two entry points (from old code — useful distinction to mention in interview):
 *   callElevator(floor, direction)  — hall call: someone on a floor pressing UP/DOWN
 *   selectFloor(elevatorId, floor) — in-cab button: passenger already inside
 *
 * advance() advances the whole system by one unit of time (each elevator moves to its next stop).
 */
public class ElevatorController {

    private final List<Elevator>   elevators;
    private final DispatchStrategy dispatchStrategy;

    public ElevatorController(List<Elevator> elevators) {
        this(elevators, new NearestDispatchStrategy());
    }

    public ElevatorController(List<Elevator> elevators, DispatchStrategy dispatchStrategy) {
        this.elevators        = elevators;
        this.dispatchStrategy = dispatchStrategy;
    }

    // Hall call — someone outside pressing UP or DOWN
    public void callElevator(int floor, Direction direction) {
        Elevator best = dispatchStrategy.select(elevators, floor, direction);
        if (best != null) best.addStop(floor);
    }

    // In-cab button — goes directly to the specific elevator
    public void selectFloor(int elevatorId, int floor) {
        for (Elevator e : elevators) {
            if (e.getId() == elevatorId) {
                e.addStop(floor);
                return;
            }
        }
    }

    // Advance all elevators by one unit of time
    public void advance() {
        for (Elevator e : elevators) {
            int stoppedAt = e.moveToNextStop();
            if (stoppedAt != -1) {
                System.out.println("  Elevator-" + e.getId() + " stopped at floor " + stoppedAt
                        + "  [" + e.getDirection() + "]");
            }
        }
    }

    public List<Elevator> getElevators() { return elevators; }
}
