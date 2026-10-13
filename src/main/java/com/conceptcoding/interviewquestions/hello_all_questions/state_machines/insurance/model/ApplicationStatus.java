package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.model;

//  DRAFT ──submit──→ APPROVED ──issue──→ ISSUED
//    └────submit──→ REJECTED
//  REJECTED and ISSUED are final.
public enum ApplicationStatus {
    DRAFT, APPROVED, REJECTED, ISSUED;

    public boolean canMoveTo(ApplicationStatus next) {
        switch (this) {
            case DRAFT:    return next == APPROVED || next == REJECTED;
            case APPROVED: return next == ISSUED;
            default:       return false;                 // REJECTED, ISSUED are final
        }
    }
}
