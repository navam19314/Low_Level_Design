package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance;

import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.model.Application;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.model.Policy;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.rules.AgeRule;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.rules.CoverageToIncomeRule;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class InsuranceDriver {

    static final long LAKH = 100_000, CRORE = 10_000_000;

    public static void main(String[] args) throws Exception {
        InsuranceService service = newService();

        System.out.println("=== Asha: 28, non-smoker, ₹1 crore cover, ₹10 lakh income → approved ===");
        Application asha = service.apply("Asha", 28, false, CRORE, 10 * LAKH);
        System.out.println("  " + service.submit(asha.getId()) + ", ₹" + asha.getAnnualPremium() + "/yr (expect APPROVED, ₹10000)");
        Policy policy = service.issuePolicy(asha.getId());
        System.out.println("  issued " + policy.getPolicyNumber() + " (expect POL-APP-1)");
        tryIt("issue again", () -> service.issuePolicy(asha.getId()));
        tryIt("submit again", () -> service.submit(asha.getId()));

        System.out.println("\n=== Meera: smoker, ₹1 crore → double ===");
        Application meera = service.apply("Meera", 40, true, CRORE, 10 * LAKH);
        service.submit(meera.getId());
        System.out.println("  ₹" + meera.getAnnualPremium() + "/yr (expect ₹20000)");

        System.out.println("\n=== Ravi: 65, ₹5 crore cover, ₹10 lakh income → rejected with BOTH reasons ===");
        Application ravi = service.apply("Ravi", 65, false, 5 * CRORE, 10 * LAKH);
        System.out.println("  " + service.submit(ravi.getId()) + " " + ravi.getRejectionReasons());
        tryIt("issue a rejected application", () -> service.issuePolicy(ravi.getId()));
        tryIt("issue before submitting", () -> service.issuePolicy(service.apply("Kiran", 30, false, CRORE, 10 * LAKH).getId()));
        tryIt("unknown application", () -> service.submit("APP-99"));

        doubleClickIssue();
    }

    // 50 threads press "Issue policy" on the same approved application: exactly 1 policy.
    private static void doubleClickIssue() throws Exception {
        System.out.println("\n=== Concurrency: 50 clicks on Issue policy ===");
        InsuranceService service = newService();
        String id = service.apply("Asha", 28, false, CRORE, 10 * LAKH).getId();
        service.submit(id);
        AtomicInteger issued = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(50);
        CountDownLatch go = new CountDownLatch(1);
        for (int i = 0; i < 50; i++) {
            pool.submit(() -> {
                go.await();
                try { service.issuePolicy(id); issued.incrementAndGet(); }
                catch (IllegalStateException e) { /* already issued */ }
                return null;
            });
        }
        go.countDown();
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println("  policies issued = " + issued + " (expect 1)");
    }

    private static InsuranceService newService() {
        return new InsuranceService(List.of(new AgeRule(18, 60), new CoverageToIncomeRule(20)));
    }

    private static void tryIt(String label, Runnable action) {
        try {
            action.run();
            System.out.println("  " + label + ": ALLOWED ✗");
        } catch (RuntimeException e) {
            System.out.println("  " + label + ": rejected → " + e.getMessage());
        }
    }
}
