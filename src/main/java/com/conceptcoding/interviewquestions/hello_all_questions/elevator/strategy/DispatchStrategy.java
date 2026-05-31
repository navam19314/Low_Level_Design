package com.conceptcoding.interviewquestions.hello_all_questions.elevator.strategy;

import com.conceptcoding.interviewquestions.hello_all_questions.elevator.Elevator;
import com.conceptcoding.interviewquestions.hello_all_questions.elevator.model.Request;

import java.util.List;

/**
 * How to pick which elevator services an inbound hall call.
 * Pluggable so dispatch can range from naive nearest-elevator (good only when
 * everyone is idle) to direction-aware (production default) to least-busy or
 * future ML-driven approaches — without touching the Elevator or Controller.
 */
public interface DispatchStrategy {

    /** Returns the elevator that should service the request, or null if none can. */
    Elevator select(List<Elevator> elevators, Request request);
}
