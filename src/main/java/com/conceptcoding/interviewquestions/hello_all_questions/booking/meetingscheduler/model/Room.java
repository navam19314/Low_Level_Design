package com.conceptcoding.interviewquestions.hello_all_questions.booking.meetingscheduler.model;

import java.util.Objects;
import java.util.Set;

// Immutable room. capacity drives allocation; amenities ("projector", "whiteboard")
// are tags for amenity-aware allocation (a follow-up).
public class Room {

    private final String id;
    private final String name;
    private final int capacity;
    private final Set<String> amenities;

    public Room(String id, String name, int capacity, Set<String> amenities) {
        this.id = Objects.requireNonNull(id, "id required");
        this.name = Objects.requireNonNull(name, "name required");
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        this.capacity = capacity;
        this.amenities = amenities == null ? Set.of() : Set.copyOf(amenities);
    }

    public String      getId()        { return id; }
    public String      getName()      { return name; }
    public int         getCapacity()  { return capacity; }
    public Set<String> getAmenities() { return amenities; }

    @Override
    public String toString() { return name + "(" + capacity + ")"; }
}
