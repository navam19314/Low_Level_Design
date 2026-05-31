package com.conceptcoding.interviewquestions.hello_all_questions.elevator.strategy;

import com.conceptcoding.interviewquestions.hello_all_questions.elevator.Elevator;
import com.conceptcoding.interviewquestions.hello_all_questions.elevator.model.Direction;
import com.conceptcoding.interviewquestions.hello_all_questions.elevator.model.Request;
import com.conceptcoding.interviewquestions.hello_all_questions.elevator.model.RequestType;

import java.util.List;

/**
 * The production-grade default. Three priority tiers:
 *   1. an elevator already moving in the requested direction AND still approaching this floor;
 *   2. otherwise the nearest IDLE elevator;
 *   3. otherwise the nearest elevator overall (it'll service after its current run).
 *
 * Beats naive nearest-elevator: a car at floor 7 going UP is "nearest" to a PICKUP_DOWN at 5,
 * but it's the wrong choice — passenger has to wait until it finishes UP and comes back.
 */
public class DirectionAwareDispatchStrategy implements DispatchStrategy {

    @Override
    public Elevator select(List<Elevator> elevators, Request request) {
        Elevator best = findMovingToward(elevators, request);
        if (best != null) return best;

        best = findNearestIdle(elevators, request.getFloor());
        if (best != null) return best;

        return findNearest(elevators, request.getFloor());
    }

    private Elevator findMovingToward(List<Elevator> elevators, Request request) {
        int floor = request.getFloor();
        Direction wanted = (request.getType() == RequestType.PICKUP_UP) ? Direction.UP : Direction.DOWN;

        Elevator best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Elevator e : elevators) {
            if (e.getDirection() != wanted) continue;
            // Must be APPROACHING the floor, not past it.
            if (wanted == Direction.UP   && e.getCurrentFloor() > floor) continue;
            if (wanted == Direction.DOWN && e.getCurrentFloor() < floor) continue;

            int d = Math.abs(e.getCurrentFloor() - floor);
            if (d < bestDistance ||
                    (d == bestDistance && (best == null || e.getCurrentFloor() < best.getCurrentFloor()))) {
                bestDistance = d;
                best = e;
            }
        }
        return best;
    }

    private Elevator findNearestIdle(List<Elevator> elevators, int floor) {
        Elevator best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Elevator e : elevators) {
            if (e.getDirection() != Direction.IDLE) continue;
            int d = Math.abs(e.getCurrentFloor() - floor);
            if (d < bestDistance) { bestDistance = d; best = e; }
        }
        return best;
    }

    private Elevator findNearest(List<Elevator> elevators, int floor) {
        Elevator best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Elevator e : elevators) {
            int d = Math.abs(e.getCurrentFloor() - floor);
            if (d < bestDistance) { bestDistance = d; best = e; }
        }
        return best;
    }
}
