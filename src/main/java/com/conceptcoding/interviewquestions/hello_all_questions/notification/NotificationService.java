package com.conceptcoding.interviewquestions.hello_all_questions.notification;

import com.conceptcoding.interviewquestions.hello_all_questions.notification.listener.NotificationListener;
import com.conceptcoding.interviewquestions.hello_all_questions.notification.model.DeliveryResult;
import com.conceptcoding.interviewquestions.hello_all_questions.notification.model.Notification;
import com.conceptcoding.interviewquestions.hello_all_questions.notification.model.NotificationChannel;
import com.conceptcoding.interviewquestions.hello_all_questions.notification.sender.NotificationSender;

import java.time.Clock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Orchestrator + facade. Three load-bearing concerns:
 *
 * <ol>
 *   <li><b>Strategy</b> per channel — one Sender impl per {@link NotificationChannel};
 *       service routes via an EnumMap keyed by channel.</li>
 *   <li><b>Observer</b> on outcomes — pluggable {@link NotificationListener}s receive
 *       per-delivery results. Listeners observe, never block delivery.</li>
 *   <li><b>Failure isolation at TWO layers:</b>
 *       (a) a throwing sender is converted to a FAILED DeliveryResult — never crashes
 *           the other channels for the SAME notification;
 *       (b) a throwing listener is logged-and-skipped — never crashes the other
 *           listeners or the calling thread.</li>
 * </ol>
 *
 * <p>Thread-safety: the data structures (EnumMap built once at ctor and made
 * immutable, ConcurrentHashMap for preferences, CopyOnWriteArrayList for listeners)
 * give us correct concurrent access without an outer service lock. Senders themselves
 * are stateless / thread-safe per the Sender contract.
 */
public class NotificationService {

    private final Map<NotificationChannel, NotificationSender> sendersByChannel;
    private final Map<String, Set<NotificationChannel>> preferences = new ConcurrentHashMap<>();
    private final List<NotificationListener> listeners = new CopyOnWriteArrayList<>();
    private final Clock clock;

    public NotificationService(List<NotificationSender> senders) {
        this(senders, Clock.systemUTC());
    }

    public NotificationService(List<NotificationSender> senders, Clock clock) {
        this.clock = clock;
        // Build the strategy map ONCE at construction; immutable thereafter.
        EnumMap<NotificationChannel, NotificationSender> map = new EnumMap<>(NotificationChannel.class);
        for (NotificationSender s : senders) {
            map.put(s.channel(), s);
        }
        // EnumMap → unmodifiable view; can't be mutated after ctor.
        this.sendersByChannel = java.util.Collections.unmodifiableMap(map);
    }

    /** Per-user channel preferences. Unset users default to "all configured channels" at send time. */
    public void setPreferences(String userId, Set<NotificationChannel> channels) {
        preferences.put(userId, Set.copyOf(channels));
    }

    public void addListener(NotificationListener listener) {
        listeners.add(listener);
    }

    public void removeListener(NotificationListener listener) {
        listeners.remove(listener);
    }

    /**
     * Send a notification through every channel the recipient has opted into
     * (or all configured channels if no preferences are set).
     *
     * <p>Returns one {@link DeliveryResult} per attempted channel — both successes
     * and failures appear in the list. Listeners are fired AFTER the sender attempt
     * for each channel (outside any sender lock).
     */
    public List<DeliveryResult> send(Notification notification) {
        Set<NotificationChannel> channels = preferences.getOrDefault(
                notification.recipientId(), sendersByChannel.keySet());

        List<DeliveryResult> results = new ArrayList<>(channels.size());
        for (NotificationChannel channel : channels) {
            DeliveryResult result = deliverTo(notification, channel);
            results.add(result);
            fireListeners(result);
        }
        return results;
    }

    // ----- internals -----

    /** Catch around the sender so one channel's throw never escapes — failure isolation layer 1. */
    private DeliveryResult deliverTo(Notification notification, NotificationChannel channel) {
        NotificationSender sender = sendersByChannel.get(channel);
        if (sender == null) {
            return DeliveryResult.failed(notification.id(), channel,
                    "no sender configured for " + channel, clock.instant());
        }
        try {
            sender.send(notification);
            return DeliveryResult.sent(notification.id(), channel, clock.instant());
        } catch (Throwable t) {
            return DeliveryResult.failed(notification.id(), channel, t.getMessage(), clock.instant());
        }
    }

    /** Catch around each listener — failure isolation layer 2. */
    private void fireListeners(DeliveryResult result) {
        for (NotificationListener listener : listeners) {
            try {
                if (result.isSent()) listener.onDelivered(result);
                else                 listener.onFailed(result);
            } catch (Throwable t) {
                System.err.println("notification: listener threw — " + t.getMessage());
            }
        }
    }
}
