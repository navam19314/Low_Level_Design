package com.conceptcoding.interviewquestions.hello_all_questions.pipelines.notification.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

// The Subject in Observer. Business services call publish(); every subscriber of that
// event type is notified. The order service never imports NotificationService.
public class EventBus {

    private final Map<String, List<EventSubscriber>> subscribersByType = new ConcurrentHashMap<>();

    public void subscribe(String eventType, EventSubscriber subscriber) {
        subscribersByType.computeIfAbsent(eventType, t -> new CopyOnWriteArrayList<>()).add(subscriber);
    }

    public void unsubscribe(String eventType, EventSubscriber subscriber) {
        List<EventSubscriber> subs = subscribersByType.get(eventType);
        if (subs != null) subs.remove(subscriber);
    }

    // one broken subscriber must not stop the others, or the publisher
    public void publish(Event event) {
        for (EventSubscriber s : subscribersByType.getOrDefault(event.getType(), List.of())) {
            try {
                s.onEvent(event);
            } catch (Exception e) {
                System.err.println("Subscriber failed on " + event.getType() + ": " + e.getMessage());
            }
        }
    }
}
