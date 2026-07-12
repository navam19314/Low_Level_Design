package com.conceptcoding.interviewquestions.hello_all_questions.notification.model;

import java.time.Instant;

// Per-channel delivery outcome. One send() call produces ONE DeliveryResult
// per attempted channel — fan-out is N-results-out. errorMessage is set only on
// failure, so callers branch on status without parsing strings.
public class DeliveryResult {

    private final String notificationId;
    private final NotificationChannel channel;
    private final DeliveryStatus status;
    private final String errorMessage;   // null when SENT
    private final Instant attemptedAt;

    private DeliveryResult(String notificationId, NotificationChannel channel,
                           DeliveryStatus status, String errorMessage, Instant attemptedAt) {
        this.notificationId = notificationId;
        this.channel        = channel;
        this.status         = status;
        this.errorMessage   = errorMessage;
        this.attemptedAt    = attemptedAt;
    }

    public static DeliveryResult sent(String notificationId, NotificationChannel channel) {
        return new DeliveryResult(notificationId, channel, DeliveryStatus.SENT, null, Instant.now());
    }

    public static DeliveryResult failed(String notificationId, NotificationChannel channel, String errorMessage) {
        return new DeliveryResult(notificationId, channel, DeliveryStatus.FAILED, errorMessage, Instant.now());
    }

    public String              getNotificationId() { return notificationId; }
    public NotificationChannel getChannel()        { return channel; }
    public DeliveryStatus      getStatus()         { return status; }
    public String              getErrorMessage()   { return errorMessage; }
    public Instant             getAttemptedAt()    { return attemptedAt; }

    public boolean isSent() { return status == DeliveryStatus.SENT; }
}
