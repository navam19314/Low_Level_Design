package com.conceptcoding.interviewquestions.hello_all_questions.elevator;

import com.conceptcoding.interviewquestions.hello_all_questions.elevator.model.Direction;
import com.conceptcoding.interviewquestions.hello_all_questions.elevator.strategy.DispatchStrategy;
import com.conceptcoding.interviewquestions.hello_all_questions.elevator.strategy.NearestDispatchStrategy;

import java.util.List;

/*
 * ElevatorController — facade over all elevators.
 *
 * Two entry points (from old code — useful distinction to mention in interview):
 *   requestPickup(floor, direction) — hall call: someone on a floor pressing UP/DOWN
 *   selectFloor(elevatorId, floor)  — in-cab button: passenger already inside
 *
 * step() advances the whole system by one tick (each elevator moves to its next stop).
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

    // Hall call — dispatch to best elevator via strategy
    public void requestPickup(int floor, Direction direction) {
        Elevator best = dispatchStrategy.select(elevators, floor, direction);
        if (best != null) best.addStop(floor);
    }

    // In-cab button — goes directly to the specific elevator (1-indexed id)
    public void selectFloor(int elevatorId, int floor) {
        elevators.stream()
                 .filter(e -> e.getId() == elevatorId)
                 .findFirst()
                 .ifPresent(e -> e.addStop(floor));
    }

    // Advance all elevators by one step
    public void step() {
        for (Elevator e : elevators) {
            int stopped = e.step();
            if (stopped != -1) {
                System.out.println("  Elevator-" + e.getId() + " stopped at floor " + stopped
                        + "  [" + e.getDirection() + "]");
            }
        }
    }

    public List<Elevator> getElevators() { return elevators; }
}
