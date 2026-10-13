package com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model;

//   PLACED → ACCEPTED → PICKED_UP → DELIVERED      (forward only: no cancel in the lite version)
public enum OrderStatus {
    PLACED, ACCEPTED, PICKED_UP, DELIVERED;

    public boolean canMoveTo(OrderStatus next) {
        switch (this) {
            case PLACED:    return next == ACCEPTED;
            case ACCEPTED:  return next == PICKED_UP;
            case PICKED_UP: return next == DELIVERED;
            default:        return false;                             // DELIVERED is final
        }
    }
}
