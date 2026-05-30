package com.conceptcoding.interviewquestions.notificationsystem;

public class EmailNotificationChannel implements NotificationChannel {

    @Override
    public void send(Notification notification) {
        System.out.println("Sending EMAIL to user " + notification.getUserId()
                + ": " + notification.getMessage());
    }
}
