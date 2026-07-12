package com.conceptcoding.interviewquestions.hello_all_questions.notification.listener;

import com.conceptcoding.interviewquestions.hello_all_questions.notification.model.DeliveryResult;

// Observer interface — invoked after EACH delivery attempt (one per channel).
// Listeners observe outcomes; they don't influence delivery. A throwing listener
// can't take down the others — the service catches around each invocation.
public interface NotificationListener {

    void onDelivered(DeliveryResult result);   // delivery succeeded on a channel

    void onFailed(DeliveryResult result);      // delivery failed on a channel
}
