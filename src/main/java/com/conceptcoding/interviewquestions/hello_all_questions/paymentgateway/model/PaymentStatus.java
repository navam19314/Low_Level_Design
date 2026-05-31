package com.conceptcoding.interviewquestions.hello_all_questions.paymentgateway.model;

/**
 * State machine for a payment's lifecycle.
 *
 * <pre>
 *   PENDING ──→ PROCESSING ──→ SUCCESS ──→ REFUND_PENDING ──→ REFUNDED
 *                           ╰→ FAILED  (terminal)
 *
 *   Invalid transitions are rejected by Payment.transitionTo so the status can
 *   never silently regress (e.g., SUCCESS → PENDING).
 * </pre>
 */
public enum PaymentStatus {
    PENDING,
    PROCESSING,
    SUCCESS,
    FAILED,
    REFUND_PENDING,
    REFUNDED;

    public boolean isTerminal() {
        return this == FAILED || this == REFUNDED;
    }
}
