package com.conceptcoding.interviewquestions.hello_all_questions.paymentgateway.model;

import java.time.Instant;

/**
 * Immutable response. {@code errorCode} / {@code errorMessage} are populated only
 * for failures so callers can branch on status without parsing strings.
 *
 * <p>Returned BY VALUE (it's a record) so two callers receiving the cached result
 * via idempotency cannot mutate each other's state.
 */
public record PaymentResult(
        String paymentId,
        String idempotencyKey,
        PaymentStatus status,
        String errorCode,
        String errorMessage,
        Instant processedAt) {

    public static PaymentResult success(String paymentId, String idempotencyKey, Instant at) {
        return new PaymentResult(paymentId, idempotencyKey, PaymentStatus.SUCCESS, null, null, at);
    }

    public static PaymentResult failed(String paymentId, String idempotencyKey,
                                       String code, String message, Instant at) {
        return new PaymentResult(paymentId, idempotencyKey, PaymentStatus.FAILED, code, message, at);
    }
}
