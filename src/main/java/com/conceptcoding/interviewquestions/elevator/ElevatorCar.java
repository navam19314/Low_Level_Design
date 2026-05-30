package com.conceptcoding.interviewquestions.elevator;

import com.conceptcoding.interviewquestions.elevator.enums.ElevatorDirection;

public class ElevatorCar {

    private final int id;
    private int currentFloor;
    private ElevatorDirection direction;

    public ElevatorCar(int id) {
        this.id = id;
        this.currentFloor = 0;
        this.direction = ElevatorDirection.IDLE;
    }

    public int getId() { return id; }
    public int getCurrentFloor() { return currentFloor; }
    public ElevatorDirection getDirection() { return direction; }
    public void setDirection(ElevatorDirection direction) { this.direction = direction; }

    public void moveElevator(int destinationFloor) {
        if (currentFloor == destinationFloor) {
            System.out.println("Elevator " + id + ": doors open at floor " + currentFloor);
            return;
        }

        System.out.println("Elevator " + id + ": doors closing");
        direction = destinationFloor > currentFloor ? ElevatorDirection.UP : ElevatorDirection.DOWN;
        int step = direction == ElevatorDirection.UP ? 1 : -1;

        while (currentFloor != destinationFloor) {
            sleep(10);
            currentFloor += step;
            System.out.println("Elevator " + id + ": floor " + currentFloor + " [" + direction + "]");
        }

        System.out.println("Elevator " + id + ": doors open at floor " + currentFloor);
    }

    private void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
