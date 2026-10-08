package com.conceptcoding.interviewquestions.hello_all_questions.pipelines.notification.event;

// Observer: anything that wants to react to business events.
// NotificationService is one subscriber; an audit log or analytics could be others.
public interface EventSubscriber {
    void onEvent(Event event);
}
