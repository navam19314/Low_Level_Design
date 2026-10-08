package com.conceptcoding.interviewquestions.hello_all_questions.pipelines.notification;

import com.conceptcoding.interviewquestions.hello_all_questions.pipelines.notification.event.Event;
import com.conceptcoding.interviewquestions.hello_all_questions.pipelines.notification.event.EventBus;
import com.conceptcoding.interviewquestions.hello_all_questions.pipelines.notification.model.DeliveryResult;
import com.conceptcoding.interviewquestions.hello_all_questions.pipelines.notification.model.Notification;
import com.conceptcoding.interviewquestions.hello_all_questions.pipelines.notification.model.NotificationChannel;
import com.conceptcoding.interviewquestions.hello_all_questions.pipelines.notification.sender.EmailSender;
import com.conceptcoding.interviewquestions.hello_all_questions.pipelines.notification.sender.NotificationSender;
import com.conceptcoding.interviewquestions.hello_all_questions.pipelines.notification.sender.PushSender;
import com.conceptcoding.interviewquestions.hello_all_questions.pipelines.notification.sender.RetryPolicy;
import com.conceptcoding.interviewquestions.hello_all_questions.pipelines.notification.sender.RetryingSender;
import com.conceptcoding.interviewquestions.hello_all_questions.pipelines.notification.sender.SenderFactory;
import com.conceptcoding.interviewquestions.hello_all_questions.pipelines.notification.sender.Sleeper;
import com.conceptcoding.interviewquestions.hello_all_questions.pipelines.notification.sender.TransientDeliveryException;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class NotificationServiceDriver {

    static final RetryPolicy RETRY = new RetryPolicy(4, 100, 2);
    static final List<Long> waits = new ArrayList<>();
    static final Sleeper RECORDING_SLEEPER = waits::add;            // instant "sleep" that records the wait

    public static void main(String[] args) throws Exception {
        System.out.println("=== 1. Observer: the order service publishes an event, nothing else ===");
        SenderFactory factory = new SenderFactory(RETRY, RECORDING_SLEEPER);
        NotificationService notifications = new NotificationService(
                factory.createAll(List.of(NotificationChannel.EMAIL, NotificationChannel.SMS, NotificationChannel.PUSH)));
        notifications.addTemplate("ORDER_SHIPPED", "Order {orderId} shipped", "Your order {orderId} is on the way. ETA {eta}.");

        EventBus bus = new EventBus();
        bus.subscribe("ORDER_SHIPPED", notifications);
        bus.subscribe("ORDER_SHIPPED", e -> System.out.println("  [audit]  " + e.getType() + " for " + e.getUserId()));
        bus.publish(new Event("ORDER_SHIPPED", "user-42", Map.of("orderId", "A17", "eta", "2 days")));

        System.out.println("\n=== 2. Preferences: user-7 wants PUSH only ===");
        notifications.setPreferences("user-7", EnumSet.of(NotificationChannel.PUSH));
        bus.publish(new Event("ORDER_SHIPPED", "user-7", Map.of("orderId", "B9", "eta", "today")));

        System.out.println("\n=== 3. Event with no template: ignored ===");
        bus.publish(new Event("CART_VIEWED", "user-42", Map.of()));
        System.out.println("  (nothing sent)");

        System.out.println("\n=== 4. Retry: SMS fails twice with a timeout, then succeeds ===");
        AtomicInteger smsCalls = new AtomicInteger();
        NotificationSender flakySms = new NotificationSender() {
            public NotificationChannel channel() { return NotificationChannel.SMS; }
            public void send(Notification n) {
                int call = smsCalls.incrementAndGet();
                if (call <= 2) throw new TransientDeliveryException("timeout (attempt " + call + ")");
                System.out.println("  [sms]    → " + n.getRecipientId() + " : " + n.getBody() + "  (attempt " + call + ")");
            }
        };
        waits.clear();
        NotificationService svc = new NotificationService(List.of(new RetryingSender(flakySms, RETRY, RECORDING_SLEEPER)));
        print(svc.send(note("user-1", "OTP", "Your OTP is 4821")));
        System.out.println("  waited " + waits + " ms (expect [100, 200]: exponential backoff)");

        System.out.println("\n=== 5. Retries run out: 4 timeouts → FAILED ===");
        waits.clear();
        NotificationSender deadSms = new NotificationSender() {
            public NotificationChannel channel() { return NotificationChannel.SMS; }
            public void send(Notification n) { throw new TransientDeliveryException("provider down"); }
        };
        svc = new NotificationService(List.of(new EmailSender(), new RetryingSender(deadSms, RETRY, RECORDING_SLEEPER)));
        print(svc.send(note("user-2", "Alert", "Suspicious login")));
        System.out.println("  waited " + waits + " ms (expect [100, 200, 400]); email still delivered");

        System.out.println("\n=== 6. Permanent error is NOT retried ===");
        waits.clear();
        NotificationSender badNumber = new NotificationSender() {
            public NotificationChannel channel() { return NotificationChannel.SMS; }
            public void send(Notification n) { throw new IllegalArgumentException("invalid phone number"); }
        };
        svc = new NotificationService(List.of(new RetryingSender(badNumber, RETRY, RECORDING_SLEEPER), new PushSender()));
        print(svc.send(note("user-3", "Hi", "Welcome")));
        System.out.println("  waited " + waits + " ms (expect []: retrying a bad number is pointless)");

        concurrentPublish();
    }

    // 50 threads publish at once while another thread changes preferences: no lost deliveries, no crash.
    private static void concurrentPublish() throws Exception {
        System.out.println("\n=== 7. Concurrency: 50 events published at once ===");
        AtomicInteger delivered = new AtomicInteger();
        NotificationSender counting = new NotificationSender() {
            public NotificationChannel channel() { return NotificationChannel.PUSH; }
            public void send(Notification n) { delivered.incrementAndGet(); }
        };
        NotificationService svc = new NotificationService(List.of(counting));
        svc.addTemplate("PING", "ping", "ping {i}");
        EventBus bus = new EventBus();
        bus.subscribe("PING", svc);

        ExecutorService pool = Executors.newFixedThreadPool(50);
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < 50; i++) {
            final int n = i;
            pool.submit(() -> {
                start.await();
                bus.publish(new Event("PING", "user-" + n, Map.of("i", String.valueOf(n))));
                svc.setPreferences("user-" + n, EnumSet.of(NotificationChannel.PUSH));
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println("  delivered = " + delivered.get() + " (expect 50), log size = "
                + svc.getDeliveryLog().size() + " (expect 50)");
    }

    private static Notification note(String recipient, String subject, String body) {
        return new Notification(UUID.randomUUID().toString(), recipient, subject, body);
    }

    private static void print(List<DeliveryResult> results) {
        for (DeliveryResult r : results) {
            System.out.println("  " + r.getChannel() + " : " + r.getStatus()
                    + (r.getErrorMessage() == null ? "" : " (" + r.getErrorMessage() + ")"));
        }
    }
}
