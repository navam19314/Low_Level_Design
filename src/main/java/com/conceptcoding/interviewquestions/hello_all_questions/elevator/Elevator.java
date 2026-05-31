package com.conceptcoding.interviewquestions.hello_all_questions.elevator;

import com.conceptcoding.interviewquestions.hello_all_questions.elevator.model.Direction;
import com.conceptcoding.interviewquestions.hello_all_questions.elevator.model.Request;
import com.conceptcoding.interviewquestions.hello_all_questions.elevator.model.RequestType;

import java.util.HashSet;
import java.util.Set;

public class Elevator {

    private final String id;
    private final int minFloor;
    private final int maxFloor;

    private int currentFloor;
    private Direction direction;
    private final Set<Request> requests;

    public Elevator(String id, int minFloor, int maxFloor) {
        this.id = id;
        this.minFloor = minFloor;
        this.maxFloor = maxFloor;
        this.currentFloor = minFloor;
        this.direction = Direction.IDLE;
        this.requests = new HashSet<>();
    }

    public String getId()                  { return id; }
    public int getCurrentFloor()           { return currentFloor; }
    public Direction getDirection()        { return direction; }
    public Set<Request> getRequests()      { return requests; }

    // Returns true if the request was accepted. Same-floor requests are a no-op
    // success (already here); out-of-bounds floors are rejected.
    public boolean addRequest(Request request) {
        int f = request.getFloor();
        if (f < minFloor || f > maxFloor) {
            return false;
        }
        if (f == currentFloor) {
            return true; // already here — no-op
        }
        return requests.add(request);
    }

    // SCAN algorithm — one floor per tick. The order of these branches matters:
    //   1. empty -> go IDLE
    //   2. IDLE  -> pick a direction by nearest pending request
    //   3. should-stop here? -> consume matching requests, do NOT move this tick
    //   4. anything ahead in current direction? -> if no, reverse, no move this tick
    //   5. move one floor in direction
    public void step() {
        // 1. Nothing to do.
        if (requests.isEmpty()) {
            direction = Direction.IDLE;
            return;
        }

        // 2. Idle with work: pick direction by nearest request.
        if (direction == Direction.IDLE) {
            Request nearest = findNearestRequest();
            direction = (nearest.getFloor() > currentFloor) ? Direction.UP : Direction.DOWN;
        }

        // 3. Stop here? Consume any matching pickup-in-direction and any destination.
        RequestType matchingPickup =
                (direction == Direction.UP) ? RequestType.PICKUP_UP : RequestType.PICKUP_DOWN;
        Request pickupHere      = new Request(currentFloor, matchingPickup);
        Request destinationHere = new Request(currentFloor, RequestType.DESTINATION);
        // Bitwise `|` not `||` — both removes MUST execute; short-circuiting would let
        // a matching pickup block consumption of a destination at the same floor.
        boolean stopped = requests.remove(pickupHere) | requests.remove(destinationHere);
        if (stopped) {
            if (requests.isEmpty()) {
                direction = Direction.IDLE;
            }
            return; // door cycle counts as this tick
        }

        // 4. Nothing ahead in this direction -> reverse, no move this tick.
        if (!hasRequestsAhead(direction)) {
            direction = (direction == Direction.UP) ? Direction.DOWN : Direction.UP;
            return;
        }

        // 5. Move one floor.
        currentFloor += (direction == Direction.UP) ? 1 : -1;
    }

    private Request findNearestRequest() {
        Request best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Request r : requests) {
            int d = Math.abs(r.getFloor() - currentFloor);
            // Tie-break: lower floor wins so behavior is deterministic.
            if (d < bestDistance || (d == bestDistance && (best == null || r.getFloor() < best.getFloor()))) {
                bestDistance = d;
                best = r;
            }
        }
        return best;
    }

    public boolean hasRequestsAhead(Direction dir) {
        for (Request r : requests) {
            if (dir == Direction.UP   && r.getFloor() > currentFloor) return true;
            if (dir == Direction.DOWN && r.getFloor() < currentFloor) return true;
        }
        return false;
    }
}
