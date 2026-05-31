package com.conceptcoding.interviewquestions.hello_all_questions.elevator.strategy;

import com.conceptcoding.interviewquestions.hello_all_questions.elevator.Elevator;
import com.conceptcoding.interviewquestions.hello_all_questions.elevator.model.Request;

import java.util.List;

/**
 * Naive baseline — picks the closest elevator by absolute floor distance,
 * ignoring direction. Fine when all elevators are idle; suboptimal once any are
 * mid-run because it can assign a car that's heading the wrong way first.
 * Kept as a Strategy alternative for contrast and for explicit configuration.
 */
public class NearestDispatchStrategy implements DispatchStrategy {

    @Override
    public Elevator select(List<Elevator> elevators, Request request) {
        Elevator best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Elevator e : elevators) {
            int d = Math.abs(e.getCurrentFloor() - request.getFloor());
            if (d < bestDistance) { bestDistance = d; best = e; }
        }
        return best;
    }
}
