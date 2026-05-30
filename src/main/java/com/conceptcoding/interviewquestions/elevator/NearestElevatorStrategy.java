package com.conceptcoding.interviewquestions.elevator;

import com.conceptcoding.interviewquestions.elevator.enums.ElevatorDirection;

import java.util.List;

public class NearestElevatorStrategy implements ElevatorSelectionStrategy {

    @Override
    public ElevatorController selectElevator(List<ElevatorController> controllers, int floor, ElevatorDirection direction) {
        ElevatorController best = null;
        int minDistance = Integer.MAX_VALUE;

        for (ElevatorController c : controllers) {
            boolean sameDirection = c.getDirection() == direction;
            boolean hasNotPassed = (direction == ElevatorDirection.UP && c.getCurrentFloor() <= floor)
                    || (direction == ElevatorDirection.DOWN && c.getCurrentFloor() >= floor);

            if (sameDirection && hasNotPassed) {
                int dist = Math.abs(c.getCurrentFloor() - floor);
                if (dist < minDistance) {
                    minDistance = dist;
                    best = c;
                }
            }
        }

        if (best != null) return best;

        for (ElevatorController c : controllers) {
            if (c.getDirection() == ElevatorDirection.IDLE) return c;
        }

        return controllers.get(0);
    }
}
