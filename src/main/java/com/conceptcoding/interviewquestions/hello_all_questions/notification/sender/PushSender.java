package com.conceptcoding.interviewquestions.hello_all_questions.notification.sender;

import com.conceptcoding.interviewquestions.hello_all_questions.notification.model.Notification;
import com.conceptcoding.interviewquestions.hello_all_questions.notification.model.NotificationChannel;

/** Simulated push sender. Real impl would wrap FCM / APNs. */
public class PushSender implements NotificationSender {

    @Override
    public NotificationChannel channel() { return NotificationChannel.PUSH; }

    @Override
    public void send(Notification notification) {
        System.out.printf("  [push]   → %s : %s%n",
                notification.recipientId(), notification.subject());
    }
}
