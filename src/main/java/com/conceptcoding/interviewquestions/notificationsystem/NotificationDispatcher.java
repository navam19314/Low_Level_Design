package com.conceptcoding.interviewquestions.notificationsystem;

import java.util.Set;

public class NotificationDispatcher {

    private final UserPreferenceService preferenceService;

    public NotificationDispatcher(UserPreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    public void dispatch(Notification notification) {
        UserPreference preference = preferenceService.getPreference(notification.getUserId());
        Set<ChannelType> channels = preference.getPreferredChannels();

        for (ChannelType channelType : channels) {
            NotificationChannel channel = NotificationChannelFactory.getChannel(channelType);
            channel.send(notification);
        }
    }
}
