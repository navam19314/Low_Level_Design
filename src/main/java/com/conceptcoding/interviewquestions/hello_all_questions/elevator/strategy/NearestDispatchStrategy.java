package com.conceptcoding.interviewquestions.hello_all_questions.elevator.strategy;

import com.conceptcoding.interviewquestions.hello_all_questions.elevator.Elevator;
import com.conceptcoding.interviewquestions.hello_all_questions.elevator.model.Direction;

import java.util.List;

/*
 * Three-tier selection (mention in interview, code in this order):
 *   1. Elevator already moving toward the floor in the requested direction → best pick
 *   2. Nearest idle elevator                                              → second choice
 *   3. Nearest elevator overall                                           → fallback
 *
 * Beats naive "nearest car": a car at floor 8 heading UP is "nearest" to a
 * DOWN request at floor 6, but it will pass floor 6 and come back — wrong choice.
 */
public class NearestDispatchStrategy implements DispatchStrategy {

    @Override
    public Elevator select(List<Elevator> elevators, int floor, Direction direction) {
        // Tier 1: already heading the right way and hasn't passed the floor yet
        Elevator best = null;
        int bestDist = Integer.MAX_VALUE;
        for (Elevator e : elevators) {
            if (e.getDirection() != direction) continue;
            if (direction == Direction.UP   && e.getCurrentFloor() > floor) continue;
            if (direction == Direction.DOWN && e.getCurrentFloor() < floor) continue;
            int d = Math.abs(e.getCurrentFloor() - floor);
            if (d < bestDist) { bestDist = d; best = e; }
        }
        if (best != null) return best;

        // Tier 2: nearest idle elevator
        bestDist = Integer.MAX_VALUE;
        for (Elevator e : elevators) {
            if (e.getDirection() != Direction.IDLE) continue;
            int d = Math.abs(e.getCurrentFloor() - floor);
            if (d < bestDist) { bestDist = d; best = e; }
        }
        if (best != null) return best;

        // Tier 3: nearest overall (will service after its current run)
        bestDist = Integer.MAX_VALUE;
        for (Elevator e : elevators) {
            int d = Math.abs(e.getCurrentFloor() - floor);
            if (d < bestDist) { bestDist = d; best = e; }
        }
        return best;
    }
}
