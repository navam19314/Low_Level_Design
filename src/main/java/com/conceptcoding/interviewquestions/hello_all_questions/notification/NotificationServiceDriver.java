package com.conceptcoding.interviewquestions.hello_all_questions.notification;

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

public class NotificationServiceDriver {

    public static void main(String[] args) {
        scenarioFanOutToAllChannels();
        scenarioRespectsUserPreferences();
        scenarioSenderFailureIsolation();
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

    // Concurrent burst (50 threads, ConcurrentHashMap swap) is a Step-5 extension —
    // only write it if the interviewer asks about thread-safety. See INTERVIEW_WALKTHROUGH.md.

    // ---- helpers ----

    private static NotificationService newService() {
        return new NotificationService(
                List.of(new EmailSender(), new SmsSender(), new PushSender()));
    }

    private static Notification note(String recipient, String subject, String body) {
        return new Notification(UUID.randomUUID().toString(), recipient, subject, body);
    }
}
