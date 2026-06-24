package com.conceptcoding.interviewquestions.hello_all_questions.elevator;

import com.conceptcoding.interviewquestions.hello_all_questions.elevator.model.Direction;

import java.util.Comparator;
import java.util.TreeSet;

/*
 * Elevator — one cab. Runs the SCAN algorithm (disk-scheduling).
 *
 * Internal queues:
 *   upQueue   — floors above current, served ascending  (TreeSet natural order)
 *   downQueue — floors below current, served descending (TreeSet reverse order)
 *
 * SCAN: exhaust upQueue going UP, flip DOWN and exhaust downQueue, repeat.
 * TreeSet gives sorted order for free — no manual sorting, no index tracking.
 *
 * moveToNextStop() jumps to the next queued stop (one stop per tick).
 * Returns the floor stopped at, or -1 when idle.
 */
public class Elevator {

    private final int id;
    private int currentFloor;
    private Direction direction ;

    private final TreeSet<Integer> upQueue   = new TreeSet<>();
    private final TreeSet<Integer> downQueue = new TreeSet<>(Comparator.reverseOrder());

    public Elevator(int id) {
        this.id           = id;
        this.currentFloor = 0;
        this.direction    = Direction.IDLE;
    }

    // Queue a stop. Floors above go to upQueue, floors below to downQueue.
    // Same floor is a no-op — doors would open immediately.
    public void addStop(int floor) {
        if      (floor > currentFloor) upQueue.add(floor);
        else if (floor < currentFloor) downQueue.add(floor);
    }

    // Move to the next pending stop in the current direction (SCAN).
    // Returns floor reached, or -1 if idle.
    public int moveToNextStop() {
        if (direction == Direction.IDLE) {
            if      (!upQueue.isEmpty())   direction = Direction.UP;
            else if (!downQueue.isEmpty()) direction = Direction.DOWN;
            else return -1;
        }

        if (direction == Direction.UP) {
            if (!upQueue.isEmpty()) {
                currentFloor = upQueue.pollFirst();          // lowest floor above
            } else if (!downQueue.isEmpty()) {
                direction    = Direction.DOWN;
                currentFloor = downQueue.pollFirst();        // highest floor below
            } else {
                direction = Direction.IDLE;
                return -1;
            }
        } else {  // DOWN
            if (!downQueue.isEmpty()) {
                currentFloor = downQueue.pollFirst();        // highest floor below
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

    public int       getId()           { return id; }
    public int       getCurrentFloor() { return currentFloor; }
    public Direction getDirection()    { return direction; }
    public int       getPendingCount() { return upQueue.size() + downQueue.size(); }

    @Override
    public String toString() {
        return "Elevator-" + id + "[floor=" + currentFloor + ", dir=" + direction
                + ", up=" + upQueue + ", down=" + downQueue + "]";
    }
}
