package com.conceptcoding.interviewquestions.hello_all_questions.policies.paymentgateway.model;

import java.time.Instant;

// Immutable response. errorCode / errorMessage are set only on failure, so callers branch
// on status without parsing strings. Immutable, so callers sharing a cached result can't affect each other.
public class PaymentResult {

    private final String paymentId;
    private final String idempotencyKey;
    private final PaymentStatus status;
    private final String errorCode;
    private final String errorMessage;
    private final Instant processedAt;

    public PaymentResult(String paymentId, String idempotencyKey, PaymentStatus status,
                         String errorCode, String errorMessage, Instant processedAt) {
        this.paymentId = paymentId;
        this.idempotencyKey = idempotencyKey;
        this.status = status;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.processedAt = processedAt;
    }

    public static PaymentResult success(String paymentId, String idempotencyKey, Instant at) {
        return new PaymentResult(paymentId, idempotencyKey, PaymentStatus.SUCCESS, null, null, at);
    }

    public static PaymentResult failed(String paymentId, String idempotencyKey, String code, String message, Instant at) {
        return new PaymentResult(paymentId, idempotencyKey, PaymentStatus.FAILED, code, message, at);
    }

    public String        paymentId()      { return paymentId; }
    public String        idempotencyKey() { return idempotencyKey; }
    public PaymentStatus status()         { return status; }
    public String        errorCode()      { return errorCode; }
    public String        errorMessage()   { return errorMessage; }
    public Instant       processedAt()    { return processedAt; }

    @Override
    public String toString() {
        return "PaymentResult{" + paymentId + ", " + status + (errorCode == null ? "" : ", " + errorCode) + "}";
    }
}
