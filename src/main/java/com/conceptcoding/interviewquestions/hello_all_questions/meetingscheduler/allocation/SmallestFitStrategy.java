package com.conceptcoding.interviewquestions.hello_all_questions.meetingscheduler.allocation;

import com.conceptcoding.interviewquestions.hello_all_questions.meetingscheduler.model.Room;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Smallest-fit-first — try the room with smallest sufficient capacity first.
 * Conserves large rooms for meetings that actually need them.
 *
 * <p>Tie-break by room id so ordering is deterministic when two rooms have the
 * same capacity (otherwise tests flake on undefined Collections.sort stability).
 */
public class SmallestFitStrategy implements RoomAllocationStrategy {

    @Override
    public List<Room> orderCandidates(List<Room> capacityFiltered) {
        List<Room> ordered = new ArrayList<>(capacityFiltered);
        ordered.sort(Comparator
                .comparingInt(Room::capacity)
                .thenComparing(Room::id));
        return ordered;
    }
}
