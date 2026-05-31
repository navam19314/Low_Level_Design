package com.conceptcoding.interviewquestions.hello_all_questions.meetingscheduler.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable meeting record. The interval is HALF-OPEN: [start, end).
 *
 * <p>Two meetings overlap iff {@code a.start < b.end && b.start < a.end}.
 * Adjacent meetings (a.end == b.start) are NOT considered overlapping —
 * one ends as the next begins. Stick to this convention everywhere.
 */
public record Meeting(
        String id,
        String organizerId,
        List<String> attendeeIds,
        String roomId,
        Instant start,
        Instant end,
        String title) {

    public Meeting {
        Objects.requireNonNull(id, "id required");
        Objects.requireNonNull(roomId, "roomId required");
        Objects.requireNonNull(start, "start required");
        Objects.requireNonNull(end, "end required");
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException("end must be strictly after start (half-open interval)");
        }
        // Defensive immutable copy so caller can't mutate our attendee list.
        attendeeIds = attendeeIds == null ? List.of() : List.copyOf(attendeeIds);
    }

    /** Two intervals overlap iff each starts before the other ends. */
    public boolean overlaps(Instant otherStart, Instant otherEnd) {
        return start.isBefore(otherEnd) && otherStart.isBefore(end);
    }
}
