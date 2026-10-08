package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.elevator.strategy;

import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.elevator.Elevator;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.elevator.model.Direction;

import java.util.List;

// Three tiers, best first:
//   1. A cab already moving toward the floor in the requested direction (it will pass by anyway)
//   2. The nearest truly idle cab (stopped AND nothing queued)
//   3. The nearest cab overall (it will come after finishing its current run)
//
// Why not plain "nearest": a cab at floor 8 going UP is 2 floors from a DOWN call at 6,
// but it must go up to the top of its run first and come back. Tier 1 skips it.
public class NearestDispatchStrategy implements DispatchStrategy {

    @Override
    public Elevator select(List<Elevator> elevators, int floor, Direction direction) {
        // Tier 1: heading the right way and hasn't passed the floor yet
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

        // Tier 2: nearest truly idle cab. A cab that just got a stop is still IDLE until its
        // next tick, so also require an empty queue, or every call piles onto the same cab.
        bestDist = Integer.MAX_VALUE;
        for (Elevator e : elevators) {
            if (e.getDirection() != Direction.IDLE || e.getPendingCount() > 0) continue;
            int d = Math.abs(e.getCurrentFloor() - floor);
            if (d < bestDist) { bestDist = d; best = e; }
        }
        if (best != null) return best;

        // Tier 3: nearest overall; on a tie, the cab with fewer stops queued
        bestDist = Integer.MAX_VALUE;
        for (Elevator e : elevators) {
            int d = Math.abs(e.getCurrentFloor() - floor);
            if (d < bestDist || (d == bestDist && e.getPendingCount() < best.getPendingCount())) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }
}
