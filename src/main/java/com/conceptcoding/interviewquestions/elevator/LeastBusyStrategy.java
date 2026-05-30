package com.conceptcoding.interviewquestions.elevator;

import com.conceptcoding.interviewquestions.elevator.enums.ElevatorDirection;

import java.util.List;

public class LeastBusyStrategy implements ElevatorSelectionStrategy {

    @Override
    public ElevatorController selectElevator(List<ElevatorController> controllers, int floor, ElevatorDirection direction) {
        ElevatorController best = controllers.get(0);

        for (ElevatorController c : controllers) {
            if (c.getPendingCount() < best.getPendingCount()) {
                best = c;
            }
        }

        return best;
    }
}

