package com.conceptcoding.interviewquestions.hello_all_questions.meetingscheduler.model;

import java.util.Objects;
import java.util.Set;

/**
 * Immutable room record. {@code capacity} drives allocation (must fit the
 * required attendee count); {@code amenities} are filterable tags
 * ("projector", "whiteboard") for future amenity-aware allocation.
 */
public record Room(String id, String name, int capacity, Set<String> amenities) {

    public Room {
        Objects.requireNonNull(id, "id required");
        Objects.requireNonNull(name, "name required");
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be > 0");
        amenities = amenities == null ? Set.of() : Set.copyOf(amenities);   // defensive immutable copy
    }
}
