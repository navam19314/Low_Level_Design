package com.conceptcoding.interviewquestions.hello_all_questions.notification.listener;

import com.conceptcoding.interviewquestions.hello_all_questions.notification.model.DeliveryResult;

/**
 * Observer interface — invoked after EACH delivery attempt (one per channel).
 * Listeners observe outcomes; they don't influence delivery.
 *
 * <p>Implementations MUST be safe to call from any thread (service publishes
 * outside its own locks). A listener that throws MUST NOT take down the other
 * listeners or the calling thread — the service catches around each invocation.
 */
public interface NotificationListener {

    /** Called when a delivery attempt succeeded on a specific channel. */
    void onDelivered(DeliveryResult result);

    /** Called when a delivery attempt failed on a specific channel. */
    void onFailed(DeliveryResult result);
}
