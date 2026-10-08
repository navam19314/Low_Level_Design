package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance;

import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.application.Application;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.model.Policy;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.model.Question;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.model.QuestionType;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.model.Questionnaire;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.model.QuestionnaireStep;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.underwriting.AgeRule;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.underwriting.BmiRule;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.underwriting.CoverageToIncomeRule;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.underwriting.PremiumCalculator;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.underwriting.SmokerRule;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.underwriting.Underwriter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class InsuranceDriver {

    public static void main(String[] args) throws Exception {
        InsuranceService service = newService();

        System.out.println("=== 1. Healthy 28-year-old, step by step ===");
        Application a = service.startApplication("Priya");
        System.out.println("  status " + a.getStatus() + ", first step: " + service.nextStep(a.getId()).getName());
        answerAll(service, a.getId(), Map.of("age", "28", "annualIncome", "1500000"));
        System.out.println("  after Personal, next step: " + service.nextStep(a.getId()).getName());
        answerAll(service, a.getId(), Map.of("coverage", "10000000", "termYears", "30",
                                             "heightCm", "165", "weightKg", "60", "smoker", "no"));
        System.out.println("  form complete? " + (service.nextStep(a.getId()) == null));
        service.submit(a.getId());
        System.out.println("  " + service.underwrite(a.getId()) + " " + a.getQuote() + "  (expect ₹10000/yr)");
        Policy policy = service.issuePolicy(a.getId());
        System.out.println("  " + a.getStatus() + " " + policy);

        System.out.println("\n=== 2. Smoker with BMI 31 → approved with loadings ===");
        Application b = service.startApplication("Rahul");
        answerAll(service, b.getId(), Map.of("age", "28", "annualIncome", "1500000", "coverage", "10000000",
                "termYears", "30", "heightCm", "170", "weightKg", "90", "smoker", "yes"));
        service.submit(b.getId());
        System.out.println("  " + service.underwrite(b.getId()) + " " + b.getQuote() + "  (expect +75% = ₹17500/yr)");

        System.out.println("\n=== 3. Age 70 asking for 50x income → rejected with ALL reasons ===");
        Application c = service.startApplication("Mohan");
        answerAll(service, c.getId(), Map.of("age", "70", "annualIncome", "200000", "coverage", "10000000",
                "termYears", "10", "heightCm", "170", "weightKg", "70", "smoker", "no"));
        service.submit(c.getId());
        System.out.println("  " + service.underwrite(c.getId()) + " " + c.getRejectionReasons());

        System.out.println("\n=== 4. Illegal actions are refused by the state ===");
        Application d = service.startApplication("Sana");
        tryIt("answer 'abc' for age", () -> service.answer(d.getId(), "age", "abc"));
        tryIt("submit with missing answers", () -> service.submit(d.getId()));
        tryIt("issue while DRAFT", () -> service.issuePolicy(d.getId()));
        tryIt("change an answer after approval", () -> service.answer(b.getId(), "smoker", "no"));
        tryIt("underwrite twice", () -> service.underwrite(b.getId()));
        tryIt("issue a REJECTED application", () -> service.issuePolicy(c.getId()));
        tryIt("issue an already ISSUED application", () -> service.issuePolicy(a.getId()));

        doubleClickIssue();
    }

    // 10 threads press "Issue policy" on the same approved application at once: exactly 1 policy.
    private static void doubleClickIssue() throws Exception {
        System.out.println("\n=== 5. Concurrency: 10 simultaneous 'issue' clicks ===");
        InsuranceService service = newService();
        Application app = service.startApplication("Kiran");
        answerAll(service, app.getId(), Map.of("age", "35", "annualIncome", "1000000", "coverage", "5000000",
                "termYears", "20", "heightCm", "175", "weightKg", "70", "smoker", "no"));
        service.submit(app.getId());
        service.underwrite(app.getId());

        AtomicInteger issued = new AtomicInteger();
        AtomicInteger refused = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < 10; i++) {
            pool.submit(() -> {
                start.await();
                try {
                    service.issuePolicy(app.getId());
                    issued.incrementAndGet();
                } catch (IllegalStateException e) {
                    refused.incrementAndGet();
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println("  issued = " + issued.get() + " (expect 1), refused = " + refused.get() + " (expect 9)");
    }

    private static InsuranceService newService() {
        Questionnaire form = new Questionnaire(List.of(
                new QuestionnaireStep("Personal", List.of(
                        new Question("age", "Your age", QuestionType.NUMBER),
                        new Question("annualIncome", "Annual income (₹)", QuestionType.NUMBER))),
                new QuestionnaireStep("Coverage", List.of(
                        new Question("coverage", "Cover amount (₹)", QuestionType.NUMBER),
                        new Question("termYears", "Policy term (years)", QuestionType.NUMBER))),
                new QuestionnaireStep("Health", List.of(
                        new Question("heightCm", "Height (cm)", QuestionType.NUMBER),
                        new Question("weightKg", "Weight (kg)", QuestionType.NUMBER),
                        new Question("smoker", "Do you smoke?", QuestionType.YES_NO)))));

        Underwriter underwriter = new Underwriter(
                List.of(new AgeRule(18, 65), new CoverageToIncomeRule(20), new BmiRule(), new SmokerRule()),
                new PremiumCalculator());
        return new InsuranceService(form, underwriter);
    }

    private static void answerAll(InsuranceService service, String appId, Map<String, String> answers) {
        answers.forEach((q, v) -> service.answer(appId, q, v));
    }

    private static void tryIt(String label, Runnable action) {
        try {
            action.run();
            System.out.println("  " + label + ": ALLOWED ✗");
        } catch (RuntimeException e) {
            System.out.println("  " + label + ": refused → " + e.getMessage());
        }
    }
}
