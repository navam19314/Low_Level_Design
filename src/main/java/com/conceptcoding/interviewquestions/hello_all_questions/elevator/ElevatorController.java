package com.conceptcoding.interviewquestions.hello_all_questions.elevator;

import com.conceptcoding.interviewquestions.hello_all_questions.elevator.model.Request;
import com.conceptcoding.interviewquestions.hello_all_questions.elevator.model.RequestType;
import com.conceptcoding.interviewquestions.hello_all_questions.elevator.strategy.DirectionAwareDispatchStrategy;
import com.conceptcoding.interviewquestions.hello_all_questions.elevator.strategy.DispatchStrategy;

import java.util.List;

/**
 * Single-threaded by contract: one caller invokes {@link #step()} and {@link #requestElevator}.
 *
 * <p>For multi-threaded use (hall calls arriving on a separate thread from the tick loop) the
 * minimal fix is method-level {@code synchronized} on both. The higher-throughput option is the
 * inbox pattern: hall calls enqueue {@code Request}s onto a {@code ConcurrentLinkedQueue} per
 * elevator; the step thread drains the inbox at the top of each {@code step()} and runs SCAN
 * against state only it mutates — no locks needed.
 */
public class ElevatorController {

    private final List<Elevator> elevators;
    private final int minFloor;
    private final int maxFloor;
    private final DispatchStrategy dispatchStrategy;

    public ElevatorController(List<Elevator> elevators, int minFloor, int maxFloor) {
        this(elevators, minFloor, maxFloor, new DirectionAwareDispatchStrategy());
    }

    public ElevatorController(List<Elevator> elevators, int minFloor, int maxFloor,
                              DispatchStrategy dispatchStrategy) {
        this.elevators = elevators;
        this.minFloor = minFloor;
        this.maxFloor = maxFloor;
        this.dispatchStrategy = dispatchStrategy;
    }

    public List<Elevator> getElevators() {
        return elevators;
    }

    // Hall-call entry point. Validates floor range and that the type is a HALL call
    // (DESTINATION belongs inside the cab via elevator.addRequest, not here).
    public boolean requestElevator(int floor, RequestType type) {
        if (floor < minFloor || floor > maxFloor) return false;
        if (type == RequestType.DESTINATION)      return false;

        Request request = new Request(floor, type);
        Elevator best = dispatchStrategy.select(elevators, request);
        if (best == null) return false;
        return best.addRequest(request);
    }

    // Tick the whole system. Each elevator runs SCAN independently.
    public void step() {
        for (Elevator e : elevators) {
            e.step();
        }
    }
}
