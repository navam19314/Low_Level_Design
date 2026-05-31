package com.conceptcoding.interviewquestions.hello_all_questions.meetingscheduler.allocation;

import com.conceptcoding.interviewquestions.hello_all_questions.meetingscheduler.model.Room;

import java.util.List;

/** First-fit — return the candidates in insertion order. The simplest baseline. */
public class FirstFitStrategy implements RoomAllocationStrategy {

    @Override
    public List<Room> orderCandidates(List<Room> capacityFiltered) {
        return capacityFiltered;       // already in scheduler's registration order
    }
}
