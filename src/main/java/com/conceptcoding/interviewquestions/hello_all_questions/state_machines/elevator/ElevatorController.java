package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.elevator;

import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.elevator.model.Direction;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.elevator.strategy.DispatchStrategy;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.elevator.strategy.NearestDispatchStrategy;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

// The building's brain. Two kinds of button:
//   callElevator(floor, direction) — HALL call: someone on a floor presses UP/DOWN.
//                                     The controller chooses WHICH cab (DispatchStrategy).
//   selectFloor(elevatorId, floor) — CAB call: a passenger inside presses a floor.
//                                     It goes to THAT cab, no choice to make.
// advance() is one tick of time: every cab moves to its next stop.
public class ElevatorController {

    private final List<Elevator> elevators;
    private final int topFloor;                  // floors are 0..topFloor
    private final DispatchStrategy dispatchStrategy;

    public ElevatorController(List<Elevator> elevators, int topFloor) {
        this(elevators, topFloor, new NearestDispatchStrategy());
    }

    public ElevatorController(List<Elevator> elevators, int topFloor, DispatchStrategy dispatchStrategy) {
        if (elevators.isEmpty()) throw new IllegalArgumentException("Need at least one elevator");
        this.elevators = new ArrayList<>(elevators);
        this.topFloor = topFloor;
        this.dispatchStrategy = dispatchStrategy;
    }

    // returns the id of the cab that will come
    public int callElevator(int floor, Direction direction) {
        checkFloor(floor);
        if (direction == Direction.IDLE) throw new IllegalArgumentException("A hall call is UP or DOWN");
        if (floor == topFloor && direction == Direction.UP)  throw new IllegalArgumentException("No UP button on the top floor");
        if (floor == 0 && direction == Direction.DOWN)       throw new IllegalArgumentException("No DOWN button on the ground floor");
        Elevator best = dispatchStrategy.select(elevators, floor, direction);
        best.addStop(floor);
        return best.getId();
    }

    public void selectFloor(int elevatorId, int floor) {
        checkFloor(floor);
        for (Elevator e : elevators) {
            if (e.getId() == elevatorId) {
                e.addStop(floor);
                return;
            }
        }
        throw new NoSuchElementException("No elevator " + elevatorId);
    }

    // one tick: every cab moves to its next stop. Returns a line per cab that stopped.
    public List<String> advance() {
        List<String> stops = new ArrayList<>();
        for (Elevator e : elevators) {
            int stoppedAt = e.moveToNextStop();
            if (stoppedAt != -1) stops.add("Elevator-" + e.getId() + " → floor " + stoppedAt + " [" + e.getDirection() + "]");
        }
        return stops;
    }

    public List<Elevator> getElevators() { return new ArrayList<>(elevators); }

    private void checkFloor(int floor) {
        if (floor < 0 || floor > topFloor) throw new IllegalArgumentException("Floor " + floor + " is outside 0.." + topFloor);
    }
}
