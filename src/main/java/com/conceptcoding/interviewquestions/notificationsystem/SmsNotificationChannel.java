package com.conceptcoding.interviewquestions.notificationsystem;

public class SmsNotificationChannel implements NotificationChannel {

    @Override
    public void send(Notification notification) {
        System.out.println("Sending SMS to user " + notification.getUserId()
                + ": " + notification.getMessage());
    }
}
