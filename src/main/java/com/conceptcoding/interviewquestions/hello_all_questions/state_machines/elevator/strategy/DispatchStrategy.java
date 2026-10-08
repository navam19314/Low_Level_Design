package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.elevator.strategy;

import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.elevator.Elevator;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.elevator.model.Direction;

import java.util.List;

// Strategy: which cab answers a hall call? Nearest today; least-busy, zoned
// (cabs 1–2 serve floors 0–20) or energy-saving tomorrow.
public interface DispatchStrategy {
    Elevator select(List<Elevator> elevators, int floor, Direction direction);
}
