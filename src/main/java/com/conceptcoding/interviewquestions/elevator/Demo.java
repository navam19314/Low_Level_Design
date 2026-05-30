package com.conceptcoding.interviewquestions.elevator;

import com.conceptcoding.interviewquestions.elevator.enums.ElevatorDirection;

public class Demo {

    public static void main(String[] args) throws InterruptedException {
        ElevatorSystem system = new ElevatorSystem(2, new NearestElevatorStrategy());

        system.requestElevator(3, ElevatorDirection.UP);
        Thread.sleep(5);

        system.requestElevator(5, ElevatorDirection.DOWN);
        Thread.sleep(5);

        system.selectFloor(1, 4);  // person inside elevator 1 presses 4
        Thread.sleep(5);

        system.selectFloor(1, 5);  // person inside elevator 1 presses 5
        Thread.sleep(5);

        system.requestElevator(1, ElevatorDirection.DOWN);
        Thread.sleep(5);

        system.requestElevator(2, ElevatorDirection.UP);
    }
}
