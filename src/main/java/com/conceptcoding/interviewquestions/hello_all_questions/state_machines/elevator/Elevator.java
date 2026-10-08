package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.elevator;

import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.elevator.model.Direction;

import java.util.Comparator;
import java.util.TreeSet;

// One cab, running SCAN (like a disk head): keep going one way, serving every stop
// on the way, then turn around.
//
//   upQueue   — stops ABOVE the cab, served lowest first   (TreeSet ascending)
//   downQueue — stops BELOW the cab, served highest first  (TreeSet descending)
//
// TreeSet keeps the stops sorted and drops duplicates (two people pressing 7 = one stop).
// moveToNextStop() moves to the next stop in one tick; returns the floor, or -1 when idle.
//
// synchronized: hall calls arrive on request threads while a ticking thread moves the cab;
// TreeSet is not thread-safe.
public class Elevator {

    private final int id;
    private int currentFloor;
    private Direction direction = Direction.IDLE;

    private final TreeSet<Integer> upQueue   = new TreeSet<>();
    private final TreeSet<Integer> downQueue = new TreeSet<>(Comparator.reverseOrder());

    public Elevator(int id) {
        this.id = id;
        this.currentFloor = 0;
    }

    // Above goes up the queue, below goes down. Same floor: doors just open, no stop needed.
    public synchronized void addStop(int floor) {
        if      (floor > currentFloor) upQueue.add(floor);
        else if (floor < currentFloor) downQueue.add(floor);
    }

    public synchronized int moveToNextStop() {
        if (direction == Direction.IDLE) {
            if      (!upQueue.isEmpty())   direction = Direction.UP;
            else if (!downQueue.isEmpty()) direction = Direction.DOWN;
            else return -1;
        }

        if (direction == Direction.UP) {
            if (!upQueue.isEmpty()) {
                currentFloor = upQueue.pollFirst();          // lowest stop above
            } else if (!downQueue.isEmpty()) {               // nothing left above: turn around
                direction    = Direction.DOWN;
                currentFloor = downQueue.pollFirst();        // highest stop below
            } else {
                direction = Direction.IDLE;
                return -1;
            }
        } else {  // DOWN
            if (!downQueue.isEmpty()) {
                currentFloor = downQueue.pollFirst();
            } else if (!upQueue.isEmpty()) {
                direction    = Direction.UP;
                currentFloor = upQueue.pollFirst();
            } else {
                direction = Direction.IDLE;
                return -1;
            }
        }
        return currentFloor;
    }

    public int getId() { return id; }
    public synchronized int       getCurrentFloor() { return currentFloor; }
    public synchronized Direction getDirection()    { return direction; }
    public synchronized int       getPendingCount() { return upQueue.size() + downQueue.size(); }

    @Override
    public synchronized String toString() {
        return "Elevator-" + id + "[floor=" + currentFloor + ", dir=" + direction
                + ", up=" + upQueue + ", down=" + downQueue + "]";
    }
}
