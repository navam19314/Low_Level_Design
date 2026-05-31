package com.conceptcoding.interviewquestions.hello_all_questions.amazonlocker.model;

import java.time.Clock;
import java.time.Instant;

public class AccessToken {

    private final String code;
    private final Instant expiresAt;
    private final Compartment compartment;

    public AccessToken(String code, Instant expiresAt, Compartment compartment) {
        this.code = code;
        this.expiresAt = expiresAt;
        this.compartment = compartment;
    }

    public String getCode() {
        return code;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Compartment getCompartment() {
        return compartment;
    }

    // Expired the instant the clock reaches expiresAt (now >= expiresAt).
    // Clock is injected so tests can advance time deterministically.
    public boolean isExpired(Clock clock) {
        return !clock.instant().isBefore(expiresAt);
    }
}
