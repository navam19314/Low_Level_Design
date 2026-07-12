package com.conceptcoding.interviewquestions.hello_all_questions.notification.sender;

import com.conceptcoding.interviewquestions.hello_all_questions.notification.model.Notification;
import com.conceptcoding.interviewquestions.hello_all_questions.notification.model.NotificationChannel;

// Strategy interface — one impl per channel. The service routes to the matching
// sender via channel(). Real impls are Adapters around vendor SDKs (SMTP, Twilio,
// FCM); here they're simulated. send() MAY throw — the service catches it and
// converts to a FAILED result, so a broken channel can't take down the others.
public interface NotificationSender {

    // Which channel this sender handles — used for routing.
    NotificationChannel channel();

    // Deliver the notification. Throws on failure; the service handles it.
    void send(Notification notification);
}
