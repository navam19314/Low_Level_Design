package com.conceptcoding.interviewquestions.hello_all_questions.paymentgateway.model;

import java.util.Objects;

/**
 * Immutable request DTO. Two fields are load-bearing for correctness:
 *   - {@code idempotencyKey} — the SAME key submitted twice MUST return the SAME
 *     PaymentResult (without re-charging). Generated client-side, opaque server-side.
 *   - {@code amountCents} — long, never double. See Splitwise/Parking Lot for the
 *     "no floats for money" discipline.
 */
public record PaymentRequest(
        String idempotencyKey,
        String customerId,
        long amountCents,
        String currency,
        PaymentMethod method,
        String description) {

    public PaymentRequest {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey required");
        Objects.requireNonNull(customerId,     "customerId required");
        Objects.requireNonNull(currency,       "currency required");
        Objects.requireNonNull(method,         "method required");
        if (amountCents <= 0) throw new IllegalArgumentException("amountCents must be > 0");
    }
}
