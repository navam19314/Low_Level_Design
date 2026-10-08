package com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model;

// The order lifecycle as an enum state machine: each state lists where it may go next.
//
//   PLACED → ACCEPTED → READY → PICKED_UP → DELIVERED
//      ↘         ↘         ↘
//       CANCELLED (only before the rider picks the food up)
public enum OrderStatus {
    PLACED, ACCEPTED, READY, PICKED_UP, DELIVERED, CANCELLED;

    public boolean canMoveTo(OrderStatus next) {
        switch (this) {
            case PLACED:    return next == ACCEPTED || next == CANCELLED;   // restaurant accepts or rejects
            case ACCEPTED:  return next == READY || next == CANCELLED;
            case READY:     return next == PICKED_UP || next == CANCELLED;
            case PICKED_UP: return next == DELIVERED;                        // food is on the road: no cancel
            default:        return false;                                    // DELIVERED, CANCELLED are final
        }
    }
}
