package com.conceptcoding.interviewquestions.hello_all_questions.notification;

import com.conceptcoding.interviewquestions.hello_all_questions.notification.listener.NotificationListener;
import com.conceptcoding.interviewquestions.hello_all_questions.notification.model.DeliveryResult;
import com.conceptcoding.interviewquestions.hello_all_questions.notification.model.Notification;
import com.conceptcoding.interviewquestions.hello_all_questions.notification.model.NotificationChannel;
import com.conceptcoding.interviewquestions.hello_all_questions.notification.sender.NotificationSender;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

// Orchestrator + facade. Three load-bearing concerns:
//   1. Strategy per channel — one Sender impl per NotificationChannel; routed via an EnumMap.
//   2. Observer on outcomes — pluggable NotificationListeners get per-delivery results.
//   3. Failure isolation at TWO layers:
//        (a) a throwing sender becomes a FAILED DeliveryResult — never breaks the other channels.
//        (b) a throwing listener is logged-and-skipped — never breaks the other listeners.
//
// Thread-safety: EnumMap is built once and never mutated (safe for concurrent reads);
// ConcurrentHashMap for preferences; CopyOnWriteArrayList for listeners. No outer lock needed.
public class NotificationService {

    private final Map<NotificationChannel, NotificationSender> sendersByChannel;
    private final Map<String, Set<NotificationChannel>> preferences = new ConcurrentHashMap<>();
    private final List<NotificationListener> listeners = new CopyOnWriteArrayList<>();

    public NotificationService(List<NotificationSender> senders) {
        EnumMap<NotificationChannel, NotificationSender> map = new EnumMap<>(NotificationChannel.class);
        for (NotificationSender s : senders) {
            map.put(s.channel(), s);
        }
        this.sendersByChannel = map;   // never mutated after ctor
    }

    // Per-user channel preferences. Unset users default to "all configured channels".
    public void setPreferences(String userId, Set<NotificationChannel> channels) {
        preferences.put(userId, Set.copyOf(channels));
    }

    public void addListener(NotificationListener listener)    { listeners.add(listener); }
    public void removeListener(NotificationListener listener) { listeners.remove(listener); }

    // Send through every channel the recipient opted into (or all configured channels
    // if no preferences set). Returns one DeliveryResult per attempted channel —
    // successes AND failures. Listeners fire after each channel's attempt.
    public List<DeliveryResult> send(Notification notification) {
        Set<NotificationChannel> channels = preferences.getOrDefault(
                notification.getRecipientId(), sendersByChannel.keySet());

        List<DeliveryResult> results = new ArrayList<>(channels.size());
        for (NotificationChannel channel : channels) {
            DeliveryResult result = deliverTo(notification, channel);
            results.add(result);
            fireListeners(result);
        }
        return results;
    }

    // Failure isolation layer 1 — one channel's throw becomes a FAILED result, never escapes.
    private DeliveryResult deliverTo(Notification notification, NotificationChannel channel) {
        NotificationSender sender = sendersByChannel.get(channel);
        if (sender == null) {
            return DeliveryResult.failed(notification.getId(), channel, "no sender for " + channel);
        }
        try {
            sender.send(notification);
            return DeliveryResult.sent(notification.getId(), channel);
        } catch (Exception e) {
            return DeliveryResult.failed(notification.getId(), channel, e.getMessage());
        }
    }

    // Failure isolation layer 2 — a throwing listener can't break the others.
    private void fireListeners(DeliveryResult result) {
        for (NotificationListener listener : listeners) {
            try {
                if (result.isSent()) listener.onDelivered(result);
                else                 listener.onFailed(result);
            } catch (Exception e) {
                System.err.println("notification: listener threw — " + e.getMessage());
            }
        }
    }
}
