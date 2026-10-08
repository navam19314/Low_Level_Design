package com.conceptcoding.interviewquestions.hello_all_questions.pipelines.notification;

import com.conceptcoding.interviewquestions.hello_all_questions.pipelines.notification.event.Event;
import com.conceptcoding.interviewquestions.hello_all_questions.pipelines.notification.event.EventSubscriber;
import com.conceptcoding.interviewquestions.hello_all_questions.pipelines.notification.model.DeliveryResult;
import com.conceptcoding.interviewquestions.hello_all_questions.pipelines.notification.model.Notification;
import com.conceptcoding.interviewquestions.hello_all_questions.pipelines.notification.model.NotificationChannel;
import com.conceptcoding.interviewquestions.hello_all_questions.pipelines.notification.sender.NotificationSender;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Two jobs:
//   1. Observer: subscribes to business events on the EventBus and turns each into a
//      notification using a template ("Your order {orderId} has shipped").
//   2. Fan-out: sends a notification on every channel the user wants, one sender
//      (Strategy) per channel, and isolates failures: a dead SMS provider never
//      stops the email from going out.
public class NotificationService implements EventSubscriber {

    private final Map<NotificationChannel, NotificationSender> sendersByChannel = new EnumMap<>(NotificationChannel.class);
    private final Map<String, Set<NotificationChannel>> preferences = new ConcurrentHashMap<>();   // userId → channels
    private final Map<String, String[]> templates = new ConcurrentHashMap<>();                      // eventType → {subject, body}
    private final List<DeliveryResult> deliveryLog = Collections.synchronizedList(new ArrayList<>());

    public NotificationService(List<NotificationSender> senders) {
        for (NotificationSender s : senders) sendersByChannel.put(s.channel(), s);   // never changed after this
    }

    public void setPreferences(String userId, Set<NotificationChannel> channels) {
        preferences.put(userId, Set.copyOf(channels));
    }

    // "{name}" placeholders are filled from the event's data
    public void addTemplate(String eventType, String subject, String body) {
        templates.put(eventType, new String[] { subject, body });
    }

    // ---- Observer callback ----
    @Override
    public void onEvent(Event event) {
        String[] template = templates.get(event.getType());
        if (template == null) return;                                // no template: we don't notify for this event
        Notification n = new Notification(UUID.randomUUID().toString(), event.getUserId(),
                render(template[0], event.getData()), render(template[1], event.getData()));
        send(n);
    }

    // ---- fan-out ----
    public List<DeliveryResult> send(Notification notification) {
        Set<NotificationChannel> channels = preferences.getOrDefault(
                notification.getRecipientId(), sendersByChannel.keySet());    // no preference = every channel
        List<DeliveryResult> results = new ArrayList<>();
        for (NotificationChannel channel : channels) {
            DeliveryResult r = deliverTo(notification, channel);
            results.add(r);
            deliveryLog.add(r);
        }
        return results;
    }

    // failure isolation: one channel's exception becomes a FAILED result and never escapes
    private DeliveryResult deliverTo(Notification notification, NotificationChannel channel) {
        NotificationSender sender = sendersByChannel.get(channel);
        if (sender == null) return DeliveryResult.failed(notification.getId(), channel, "no sender for " + channel);
        try {
            sender.send(notification);              // the RetryingSender inside already retried transient errors
            return DeliveryResult.sent(notification.getId(), channel);
        } catch (Exception e) {                     // Exception, not Throwable: never swallow JVM errors
            return DeliveryResult.failed(notification.getId(), channel, e.getMessage());
        }
    }

    public List<DeliveryResult> getDeliveryLog() {
        synchronized (deliveryLog) { return new ArrayList<>(deliveryLog); }
    }

    private static String render(String text, Map<String, String> data) {
        String out = text;
        for (Map.Entry<String, String> e : data.entrySet()) out = out.replace("{" + e.getKey() + "}", e.getValue());
        return out;
    }
}
