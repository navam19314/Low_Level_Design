package com.conceptcoding.interviewquestions.elevator;

import com.conceptcoding.interviewquestions.elevator.enums.ElevatorDirection;

import java.util.List;

public interface ElevatorSelectionStrategy {
    ElevatorController selectElevator(List<ElevatorController> controllers, int floor, ElevatorDirection direction);
}
