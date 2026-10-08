package com.conceptcoding.interviewquestions.hello_all_questions.booking.meetingscheduler.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

// Immutable meeting. The interval is HALF-OPEN: [start, end).
// Two meetings overlap iff a.start < b.end && b.start < a.end, so a meeting ending at 10:00
// does NOT clash with one starting at 10:00. Use this convention everywhere.
public class Meeting {

    private final String id;
    private final String organizerId;
    private final List<String> attendeeIds;
    private final String roomId;
    private final Instant start;
    private final Instant end;
    private final String title;

    public Meeting(String id, String organizerId, List<String> attendeeIds, String roomId,
                   Instant start, Instant end, String title) {
        this.id = Objects.requireNonNull(id, "id required");
        this.roomId = Objects.requireNonNull(roomId, "roomId required");
        this.start = Objects.requireNonNull(start, "start required");
        this.end = Objects.requireNonNull(end, "end required");
        if (!end.isAfter(start)) throw new IllegalArgumentException("end must be strictly after start");
        this.organizerId = organizerId;
        this.attendeeIds = attendeeIds == null ? List.of() : List.copyOf(attendeeIds);
        this.title = title;
    }

    public boolean overlaps(Instant otherStart, Instant otherEnd) {
        return start.isBefore(otherEnd) && otherStart.isBefore(end);
    }

    public String       getId()          { return id; }
    public String       getOrganizerId() { return organizerId; }
    public List<String> getAttendeeIds() { return attendeeIds; }
    public String       getRoomId()      { return roomId; }
    public Instant      getStart()       { return start; }
    public Instant      getEnd()         { return end; }
    public String       getTitle()       { return title; }
}
