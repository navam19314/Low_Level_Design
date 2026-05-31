package com.conceptcoding.interviewquestions.hello_all_questions.elevator.model;

import java.util.Objects;

public final class Request {

    private final int floor;
    private final RequestType type;

    public Request(int floor, RequestType type) {
        this.floor = floor;
        this.type = type;
    }

    public int getFloor() {
        return floor;
    }

    public RequestType getType() {
        return type;
    }

    // equals/hashCode by (floor, type) so the Set dedupes — two PICKUP_UPs on the
    // same floor collapse to one stop, but a PICKUP_UP and a DESTINATION on the
    // same floor are still distinct events the elevator must service.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Request)) return false;
        Request other = (Request) o;
        return floor == other.floor && type == other.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(floor, type);
    }

    @Override
    public String toString() {
        return "Request{f=" + floor + ", t=" + type + "}";
    }
}
