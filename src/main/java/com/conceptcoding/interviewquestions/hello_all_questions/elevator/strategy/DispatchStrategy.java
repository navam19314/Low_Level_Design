package com.conceptcoding.interviewquestions.hello_all_questions.elevator.strategy;

import com.conceptcoding.interviewquestions.hello_all_questions.elevator.Elevator;
import com.conceptcoding.interviewquestions.hello_all_questions.elevator.model.Direction;

import java.util.List;

public interface DispatchStrategy {
    // Pick the best elevator for a hall call at floor in the given direction.
    // Returns null if no elevator is available.
    Elevator select(List<Elevator> elevators, int floor, Direction direction);
}
