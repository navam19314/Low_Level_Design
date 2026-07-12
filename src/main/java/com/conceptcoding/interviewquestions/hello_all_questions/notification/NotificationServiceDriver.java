package com.conceptcoding.interviewquestions.hello_all_questions.notification;

import com.conceptcoding.interviewquestions.hello_all_questions.notification.listener.NotificationListener;
import com.conceptcoding.interviewquestions.hello_all_questions.notification.model.DeliveryResult;
import com.conceptcoding.interviewquestions.hello_all_questions.notification.model.Notification;
import com.conceptcoding.interviewquestions.hello_all_questions.notification.model.NotificationChannel;
import com.conceptcoding.interviewquestions.hello_all_questions.notification.sender.EmailSender;
import com.conceptcoding.interviewquestions.hello_all_questions.notification.sender.NotificationSender;
import com.conceptcoding.interviewquestions.hello_all_questions.notification.sender.PushSender;
import com.conceptcoding.interviewquestions.hello_all_questions.notification.sender.SmsSender;

import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class NotificationServiceDriver {

    public static void main(String[] args) throws Exception {
        scenarioFanOutToAllChannels();
        scenarioRespectsUserPreferences();
        scenarioSenderFailureIsolation();
        scenarioListenerFailureIsolation();
        scenarioConcurrentBurst();
    }

    // ---- 1. Fan-out to all configured channels ----
    private static void scenarioFanOutToAllChannels() {
        System.out.println("=== Scenario 1: fan-out to all 3 channels (no preferences set) ===");
        NotificationService svc = newService();
        Notification n = note("user-A", "Welcome", "Hi, welcome to the platform!");
        List<DeliveryResult> results = svc.send(n);
        System.out.println("  results.size() = " + results.size() + " (expect 3)");
        for (DeliveryResult r : results) {
            System.out.println("    " + r.getChannel() + " : " + r.getStatus());
        }
        System.out.println();
    }

    // ---- 2. User preferences restrict channels ----
    private static void scenarioRespectsUserPreferences() {
        System.out.println("=== Scenario 2: user prefers EMAIL+PUSH only ===");
        NotificationService svc = newService();
        svc.setPreferences("user-B", EnumSet.of(NotificationChannel.EMAIL, NotificationChannel.PUSH));
        Notification n = note("user-B", "Receipt", "Your order has shipped");
        List<DeliveryResult> results = svc.send(n);
        System.out.println("  results.size() = " + results.size() + " (expect 2)");
        for (DeliveryResult r : results) {
            System.out.println("    " + r.getChannel() + " : " + r.getStatus());
        }
        System.out.println();
    }

    // ---- 3. Sender failure isolation — one channel throws, others succeed ----
    private static void scenarioSenderFailureIsolation() {
        System.out.println("=== Scenario 3: SMS sender throws — EMAIL + PUSH still deliver ===");
        NotificationSender failingSms = new NotificationSender() {
            @Override public NotificationChannel channel() { return NotificationChannel.SMS; }
            @Override public void send(Notification n) {
                throw new RuntimeException("Twilio: rate-limit exceeded");
            }
        };
        NotificationService svc = new NotificationService(
                List.of(new EmailSender(), failingSms, new PushSender()));
        Notification n = note("user-C", "Alert", "Suspicious login");
        List<DeliveryResult> results = svc.send(n);
        for (DeliveryResult r : results) {
            System.out.println("  " + r.getChannel() + " : " + r.getStatus()
                    + (r.getErrorMessage() == null ? "" : " (" + r.getErrorMessage() + ")"));
        }
        long failures = results.stream().filter(r -> !r.isSent()).count();
        long successes = results.stream().filter(DeliveryResult::isSent).count();
        System.out.println("  failures = " + failures + ", successes = " + successes
                + " (expect 1 fail, 2 success)");
        System.out.println();
    }

    // ---- 4. Listener failure isolation — throwing listener doesn't break others ----
    private static void scenarioListenerFailureIsolation() {
        System.out.println("=== Scenario 4: one of two listeners throws — the other still fires ===");
        NotificationService svc = newService();
        CountingListener good = new CountingListener();
        NotificationListener bad = new NotificationListener() {
            @Override public void onDelivered(DeliveryResult r) { throw new RuntimeException("listener bug"); }
            @Override public void onFailed(DeliveryResult r)    { throw new RuntimeException("listener bug"); }
        };
        svc.addListener(bad);
        svc.addListener(good);
        Notification n = note("user-D", "Beep", "boop");
        svc.send(n);
        System.out.println("  good listener.delivered count: " + good.delivered.get()
                + " (expect 3 — fired once per channel despite bad listener throwing)");
        System.out.println();
    }

    // ---- 5. Concurrent burst — 50 notifications × 3 channels = 150 delivery results ----
    private static void scenarioConcurrentBurst() throws Exception {
        System.out.println("=== Scenario 5: 50 threads, 1 notification each, 3 channels — atomicity ===");
        NotificationService svc = newService();
        CountingListener listener = new CountingListener();
        svc.addListener(listener);

        int threads = 50;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch fire = new CountDownLatch(1);
        AtomicInteger totalResults = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            final int n = i;
            pool.submit(() -> {
                ready.countDown();
                try {
                    fire.await();
                    Notification msg = note("user-X-" + n, "burst", "msg " + n);
                    List<DeliveryResult> results = svc.send(msg);
                    totalResults.addAndGet(results.size());
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            });
        }
        ready.await();
        fire.countDown();
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        int expectedDeliveries = threads * 3;     // 3 channels per notification
        System.out.println("  total DeliveryResults returned: " + totalResults.get()
                + " (expect " + expectedDeliveries + ")");
        System.out.println("  listener.delivered count:        " + listener.delivered.get()
                + " (expect " + expectedDeliveries + ")");
        System.out.println(totalResults.get() == expectedDeliveries
                && listener.delivered.get() == expectedDeliveries
                ? "  ✓ no lost results or listener invocations under contention"
                : "  ✗ RACE — count mismatch");
    }

    // ---- helpers ----

    private static NotificationService newService() {
        return new NotificationService(
                List.of(new EmailSender(), new SmsSender(), new PushSender()));
    }

    private static Notification note(String recipient, String subject, String body) {
        return new Notification(UUID.randomUUID().toString(), recipient, subject, body);
    }

    /** Listener that counts delivered + failed invocations across all channels and threads. */
    static class CountingListener implements NotificationListener {
        final AtomicInteger delivered = new AtomicInteger();
        final AtomicInteger failed    = new AtomicInteger();

        @Override public void onDelivered(DeliveryResult r) { delivered.incrementAndGet(); }
        @Override public void onFailed(DeliveryResult r)    { failed.incrementAndGet(); }
    }
}
