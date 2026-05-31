package com.conceptcoding.interviewquestions.hello_all_questions.notification.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable notification record — the payload sent across all channels.
 *
 * <p>Channel-specific rendering (HTML vs plain text vs short SMS) is the job of
 * each Sender, not the Notification — that's how the same content reaches every
 * channel without N×M renderer classes.
 */
public record Notification(
        String id,
        String recipientId,
        String subject,
        String body,
        Map<String, String> metadata,
        Instant createdAt) {

    public Notification {
        Objects.requireNonNull(id,          "id required");
        Objects.requireNonNull(recipientId, "recipientId required");
        Objects.requireNonNull(subject,     "subject required");
        Objects.requireNonNull(body,        "body required");
        // Defensive immutable copy — callers can't mutate our metadata after construction.
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
