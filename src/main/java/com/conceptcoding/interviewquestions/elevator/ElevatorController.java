package com.conceptcoding.interviewquestions.elevator;

import com.conceptcoding.interviewquestions.elevator.enums.ElevatorDirection;

import java.util.concurrent.PriorityBlockingQueue;

public class ElevatorController implements Runnable {

    private final ElevatorCar car;
    private final PriorityBlockingQueue<Integer> upQueue;   // min-heap: lowest floor first
    private final PriorityBlockingQueue<Integer> downQueue; // max-heap: highest floor first
    private final Object lock = new Object();

    public ElevatorController(ElevatorCar car) {
        this.car = car;
        this.upQueue = new PriorityBlockingQueue<>();
        this.downQueue = new PriorityBlockingQueue<>(11, (a, b) -> b - a);
    }

    public int getCurrentFloor() { return car.getCurrentFloor(); }
    public ElevatorDirection getDirection() { return car.getDirection(); }
    public int getPendingCount() { return upQueue.size() + downQueue.size(); }

    public void submitRequest(int floor) {
        System.out.println("Elevator " + car.getId() + " accepted request for floor " + floor);
        if (floor >= car.getCurrentFloor()) {
            if (!upQueue.contains(floor)) upQueue.offer(floor);
        } else {
            if (!downQueue.contains(floor)) downQueue.offer(floor);
        }
        synchronized (lock) { lock.notify(); }
    }

    @Override
    public void run() {
        while (true) {
            synchronized (lock) {
                while (upQueue.isEmpty() && downQueue.isEmpty()) {
                    try {
                        car.setDirection(ElevatorDirection.IDLE);
                        System.out.println("Elevator " + car.getId() + " is idle");
                        lock.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
            while (!upQueue.isEmpty()) car.moveElevator(upQueue.poll());
            while (!downQueue.isEmpty()) car.moveElevator(downQueue.poll());
        }
    }
}
