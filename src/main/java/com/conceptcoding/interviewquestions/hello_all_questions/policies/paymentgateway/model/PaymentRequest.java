package com.conceptcoding.interviewquestions.hello_all_questions.policies.paymentgateway.model;

import java.util.Objects;

// Immutable request. Two fields matter most:
//   idempotencyKey — the SAME key sent twice must charge ONCE and return the same result
//                    (the client generates it, e.g. per checkout attempt)
//   amountPaise    — smallest currency unit, a long; never double (₹499.00 = 49_900 paise)
public class PaymentRequest {

    private final String idempotencyKey;
    private final String customerId;
    private final long amountPaise;
    private final String currency;
    private final PaymentMethod method;
    private final String description;

    public PaymentRequest(String idempotencyKey, String customerId, long amountPaise, String currency,
                          PaymentMethod method, String description) {
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey required");
        this.customerId = Objects.requireNonNull(customerId, "customerId required");
        this.currency = Objects.requireNonNull(currency, "currency required");
        this.method = Objects.requireNonNull(method, "method required");
        if (amountPaise <= 0) throw new IllegalArgumentException("amountPaise must be > 0");
        this.amountPaise = amountPaise;
        this.description = description;
    }

    // same key + different contents = a client bug; compare these to detect it
    public boolean sameChargeAs(PaymentRequest other) {
        return customerId.equals(other.customerId) && amountPaise == other.amountPaise
                && currency.equals(other.currency) && method == other.method;
    }

    public String        idempotencyKey() { return idempotencyKey; }
    public String        customerId()     { return customerId; }
    public long          amountPaise()    { return amountPaise; }
    public String        currency()       { return currency; }
    public PaymentMethod method()         { return method; }
    public String        description()    { return description; }
}
