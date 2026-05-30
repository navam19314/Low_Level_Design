package com.conceptcoding.interviewquestions.elevator;

import com.conceptcoding.interviewquestions.elevator.enums.ElevatorDirection;

import java.util.ArrayList;
import java.util.List;

public class ElevatorSystem {

    private final List<ElevatorController> controllers = new ArrayList<>();
    private ElevatorSelectionStrategy strategy;

    public ElevatorSystem(int totalElevators, ElevatorSelectionStrategy strategy) {
        this.strategy = strategy;
        for (int i = 1; i <= totalElevators; i++) {
            ElevatorController controller = new ElevatorController(new ElevatorCar(i));
            controllers.add(controller);
//            Thread constructor takes in a runnable object as first arguement, second is the name of the thread in string which you wnat to see in logs
//           .start() => Tells the JVM to create a new OS thread and begin executing controller.run() on it — concurrently with everything else.
//
//Important distinction:
//
//.start() → spawns a new thread, runs run() on it
//.run() directly → no new thread, runs on the current thread (wrong)
            new Thread(controller, "Elevator-" + i).start();
        }
    }

    public void setStrategy(ElevatorSelectionStrategy strategy) {
        this.strategy = strategy;
    }

    // External request — hall button pressed on a floor
    public void requestElevator(int floor, ElevatorDirection direction) {
        strategy.selectElevator(controllers, floor, direction).submitRequest(floor);
    }

    // Internal request — button pressed inside an elevator
    public void selectFloor(int elevatorId, int floor) {
        controllers.get(elevatorId - 1).submitRequest(floor);
    }
}
