package com.conceptcoding.interviewquestions.hello_all_questions.notification.sender;

import com.conceptcoding.interviewquestions.hello_all_questions.notification.model.Notification;
import com.conceptcoding.interviewquestions.hello_all_questions.notification.model.NotificationChannel;

/** Simulated SMS sender. Real impl would wrap Twilio / MSG91. */
public class SmsSender implements NotificationSender {

    private static final int SMS_MAX_LEN = 160;

    @Override
    public NotificationChannel channel() { return NotificationChannel.SMS; }

    @Override
    public void send(Notification notification) {
        String body = notification.getBody();
        String truncated = body.length() <= SMS_MAX_LEN ? body : body.substring(0, SMS_MAX_LEN) + "…";
        System.out.printf("  [sms]    → %s : %s%n", notification.getRecipientId(), truncated);
    }
}
