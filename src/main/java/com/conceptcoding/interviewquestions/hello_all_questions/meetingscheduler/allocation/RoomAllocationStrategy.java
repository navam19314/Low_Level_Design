package com.conceptcoding.interviewquestions.hello_all_questions.meetingscheduler.allocation;

import com.conceptcoding.interviewquestions.hello_all_questions.meetingscheduler.model.Room;

import java.util.List;

/**
 * Strategy interface — given the rooms that satisfy capacity + amenity filters,
 * decides which ORDER the scheduler should try them. The scheduler walks the
 * returned list and books the FIRST room that has no time conflict.
 *
 * <p>Different policies (smallest-fit / first-fit / largest-fit / energy-aware)
 * are different orderings. Adding a new policy is one new class + injection at
 * construction. The scheduler code is unchanged.
 */
public interface RoomAllocationStrategy {

    /** Order candidate rooms by preference. First in list = tried first. */
    List<Room> orderCandidates(List<Room> capacityFiltered);
}
