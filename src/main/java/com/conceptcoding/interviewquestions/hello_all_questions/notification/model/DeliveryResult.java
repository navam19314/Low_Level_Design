package com.conceptcoding.interviewquestions.hello_all_questions.notification.model;

import java.time.Instant;

/**
 * Immutable per-channel delivery outcome. A single {@code send()} call produces
 * ONE DeliveryResult per attempted channel — fan-out is N-results-out.
 *
 * <p>{@code errorMessage} is populated only on failure so callers can branch on
 * status without parsing strings.
 */
public record DeliveryResult(
        String notificationId,
        NotificationChannel channel,
        DeliveryStatus status,
        String errorMessage,
        Instant attemptedAt) {

    public static DeliveryResult sent(String notificationId, NotificationChannel channel, Instant at) {
        return new DeliveryResult(notificationId, channel, DeliveryStatus.SENT, null, at);
    }

    public static DeliveryResult failed(String notificationId, NotificationChannel channel,
                                        String errorMessage, Instant at) {
        return new DeliveryResult(notificationId, channel, DeliveryStatus.FAILED, errorMessage, at);
    }

    public boolean isSent() { return status == DeliveryStatus.SENT; }
}
