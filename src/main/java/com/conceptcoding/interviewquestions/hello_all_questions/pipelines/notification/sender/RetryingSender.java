package com.conceptcoding.interviewquestions.hello_all_questions.pipelines.notification.sender;

import com.conceptcoding.interviewquestions.hello_all_questions.pipelines.notification.model.Notification;
import com.conceptcoding.interviewquestions.hello_all_questions.pipelines.notification.model.NotificationChannel;

// Decorator: wraps ANY sender and adds retries with exponential backoff.
// EmailSender / SmsSender stay simple and know nothing about retrying.
public class RetryingSender implements NotificationSender {

    private final NotificationSender inner;
    private final RetryPolicy policy;
    private final Sleeper sleeper;

    public RetryingSender(NotificationSender inner, RetryPolicy policy, Sleeper sleeper) {
        this.inner = inner;
        this.policy = policy;
        this.sleeper = sleeper;
    }

    @Override
    public NotificationChannel channel() { return inner.channel(); }

    @Override
    public void send(Notification notification) {
        long backoff = policy.getInitialBackoffMs();
        for (int attempt = 1; ; attempt++) {
            try {
                inner.send(notification);
                return;
            } catch (TransientDeliveryException e) {
                if (attempt >= policy.getMaxAttempts()) throw e;      // out of attempts: give up
                try {
                    sleeper.sleep(backoff);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
                backoff *= policy.getMultiplier();                    // 100 → 200 → 400 ...
            }
            // any other exception (bad phone number) is permanent: it propagates at once, no retry
        }
    }
}
