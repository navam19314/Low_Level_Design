package com.conceptcoding.interviewquestions.hello_all_questions.matching.cabbooking.model;

//  MATCHED → IN_PROGRESS → COMPLETED
//     ↘
//      CANCELLED            (only before the trip starts)
public enum RideStatus {
    MATCHED, IN_PROGRESS, COMPLETED, CANCELLED;

    public boolean canMoveTo(RideStatus next) {
        switch (this) {
            case MATCHED:     return next == IN_PROGRESS || next == CANCELLED;
            case IN_PROGRESS: return next == COMPLETED;
            default:          return false;                // COMPLETED, CANCELLED are final
        }
    }
}
