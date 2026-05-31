package com.conceptcoding.interviewquestions.hello_all_questions.notification.sender;

import com.conceptcoding.interviewquestions.hello_all_questions.notification.model.Notification;
import com.conceptcoding.interviewquestions.hello_all_questions.notification.model.NotificationChannel;

/**
 * Strategy interface — one impl per channel. The service routes each
 * notification to the matching sender via {@link #channel()}.
 *
 * <p>Implementations represent the I/O boundary with an external delivery
 * system (SMTP, Twilio, FCM, Slack web hooks). Real impls would be Adapters
 * around vendor SDKs; here we simulate them for tests.
 *
 * <p>{@code send} MAY throw — the service catches and converts to a failed
 * {@link com.conceptcoding.interviewquestions.hello_all_questions.notification.model.DeliveryResult}.
 * A throwing sender CANNOT take down the other channels (failure isolation).
 */
public interface NotificationSender {

    /** Which channel this sender handles. Used to route notifications. */
    NotificationChannel channel();

    /** Deliver the notification. Throws on failure; the service handles it. */
    void send(Notification notification);
}
