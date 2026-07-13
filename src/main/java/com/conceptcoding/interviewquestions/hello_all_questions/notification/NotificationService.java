package com.conceptcoding.interviewquestions.hello_all_questions.notification;

import com.conceptcoding.interviewquestions.hello_all_questions.notification.model.DeliveryResult;
import com.conceptcoding.interviewquestions.hello_all_questions.notification.model.Notification;
import com.conceptcoding.interviewquestions.hello_all_questions.notification.model.NotificationChannel;
import com.conceptcoding.interviewquestions.hello_all_questions.notification.sender.NotificationSender;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Orchestrator + facade. Two load-bearing concerns for the 45-min base build:
//   1. Strategy per channel — one Sender impl per NotificationChannel; routed via an EnumMap.
//   2. Failure isolation — a throwing sender becomes a FAILED DeliveryResult; the other
//      channels for the same notification still get attempted.
//
// Deliberately out of the base: thread-safety (ConcurrentHashMap), pluggable Observer
// listeners, retries, async dispatch, rate limiting — these are Step-5 extensions (see
// INTERVIEW_WALKTHROUGH.md), added only if asked, without changing this class's shape.
public class NotificationService {

    private final Map<NotificationChannel, NotificationSender> sendersByChannel;
    private final Map<String, Set<NotificationChannel>> preferences = new HashMap<>();

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

    // Send through every channel the recipient opted into (or all configured channels
    // if no preferences set). Returns one DeliveryResult per attempted channel —
    // successes AND failures.
    public List<DeliveryResult> send(Notification notification) {
        Set<NotificationChannel> channels = preferences.getOrDefault(
                notification.getRecipientId(), sendersByChannel.keySet());

        List<DeliveryResult> results = new ArrayList<>(channels.size());
        for (NotificationChannel channel : channels) {
            results.add(deliverTo(notification, channel));
        }
        return results;
    }

    // Failure isolation — one channel's throw becomes a FAILED result, never escapes.
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
}
