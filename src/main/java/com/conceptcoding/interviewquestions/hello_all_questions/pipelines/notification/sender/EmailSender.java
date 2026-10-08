package com.conceptcoding.interviewquestions.hello_all_questions.pipelines.notification.sender;

import com.conceptcoding.interviewquestions.hello_all_questions.pipelines.notification.model.Notification;
import com.conceptcoding.interviewquestions.hello_all_questions.pipelines.notification.model.NotificationChannel;

// Simulated email sender. Real impl would wrap SMTP / SendGrid / SES (an Adapter).
public class EmailSender implements NotificationSender {

    @Override
    public NotificationChannel channel() { return NotificationChannel.EMAIL; }

    @Override
    public void send(Notification notification) {
        System.out.printf("  [email]  → %s : %s%n",
                notification.getRecipientId(), notification.getSubject());
    }
}
