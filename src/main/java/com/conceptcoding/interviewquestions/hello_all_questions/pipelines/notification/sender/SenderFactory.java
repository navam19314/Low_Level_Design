package com.conceptcoding.interviewquestions.hello_all_questions.pipelines.notification.sender;

import com.conceptcoding.interviewquestions.hello_all_questions.pipelines.notification.model.NotificationChannel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

// Factory: channel → the right sender, already wrapped with retries.
// Adding WhatsApp = one new sender class + one case here; nothing else changes.
public class SenderFactory {

    private final RetryPolicy retryPolicy;
    private final Sleeper sleeper;

    public SenderFactory(RetryPolicy retryPolicy, Sleeper sleeper) {
        this.retryPolicy = retryPolicy;
        this.sleeper = sleeper;
    }

    public NotificationSender create(NotificationChannel channel) {
        NotificationSender base;
        switch (channel) {
            case EMAIL: base = new EmailSender(); break;
            case SMS:   base = new SmsSender();   break;
            case PUSH:  base = new PushSender();  break;
            default:    throw new IllegalArgumentException("No sender for channel " + channel);
        }
        return new RetryingSender(base, retryPolicy, sleeper);
    }

    public List<NotificationSender> createAll(Collection<NotificationChannel> channels) {
        List<NotificationSender> senders = new ArrayList<>();
        for (NotificationChannel c : channels) senders.add(create(c));
        return senders;
    }
}
