package com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model;

//   PLACED → ACCEPTED → PICKED_UP → DELIVERED
//      └────────┴──→ CANCELLED      (only before the rider picks the food up)
public enum OrderStatus {
    PLACED, ACCEPTED, PICKED_UP, DELIVERED, CANCELLED;

    public boolean canMoveTo(OrderStatus next) {
        switch (this) {
            case PLACED:    return next == ACCEPTED || next == CANCELLED;
            case ACCEPTED:  return next == PICKED_UP || next == CANCELLED;
            case PICKED_UP: return next == DELIVERED;                 // food is on the road: no cancel
            default:        return false;                             // DELIVERED, CANCELLED are final
        }
    }
}
