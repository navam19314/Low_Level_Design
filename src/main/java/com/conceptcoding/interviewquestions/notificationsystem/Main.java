package com.conceptcoding.interviewquestions.notificationsystem;

import java.util.Set;

public class Main {

    public static void main(String[] args) {

        UserPreferenceService preferenceService = new UserPreferenceService();

        preferenceService.savePreference(
                new UserPreference("user123", Set.of(ChannelType.EMAIL, ChannelType.SMS))
        );

        NotificationDispatcher dispatcher = new NotificationDispatcher(preferenceService);

        NotificationService syncService  = new NotificationService(dispatcher);
        AsyncNotificationService asyncService = new AsyncNotificationService(dispatcher);

        Notification notification = new Notification("user123", "Your order has been shipped!");

        System.out.println("--- Sync ---");
        syncService.sendNotification(notification);

        System.out.println("--- Async ---");
        asyncService.sendNotification(notification);
    }
}
