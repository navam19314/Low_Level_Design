package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance;

import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.model.Application;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.model.ApplicationStatus;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.model.Policy;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.rules.UnderwritingRule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

// The service the web app calls.
// Thread safety: every public method is synchronized, one lock for the whole service.
// Simple and correct: a double-clicked "Issue policy" can't create two policies.
public class InsuranceService {

    private final Map<String, Application> applications = new HashMap<>();
    private final List<UnderwritingRule> rules;
    private int nextApplicationNumber = 1;

    public InsuranceService(List<UnderwritingRule> rules) {
        this.rules = new ArrayList<>(rules);
    }

    public synchronized Application apply(String name, int age, boolean smoker, long coverage, long annualIncome) {
        Application app = new Application("APP-" + nextApplicationNumber++, name, age, smoker, coverage, annualIncome);
        applications.put(app.getId(), app);
        return app;
    }

    // Run EVERY rule (not stop-at-first), so a rejection lists all the reasons at once.
    public synchronized ApplicationStatus submit(String applicationId) {
        Application app = findApplication(applicationId);
        List<String> reasons = new ArrayList<>();
        for (UnderwritingRule rule : rules) {
            String reason = rule.check(app);
            if (reason != null) reasons.add(reason);
        }
        if (reasons.isEmpty()) {
            app.moveTo(ApplicationStatus.APPROVED);          // throws if already submitted
            app.setAnnualPremium(premium(app));
        } else {
            app.moveTo(ApplicationStatus.REJECTED);
            app.setRejectionReasons(reasons);
        }
        return app.getStatus();
    }

    public synchronized Policy issuePolicy(String applicationId) {
        Application app = findApplication(applicationId);
        app.moveTo(ApplicationStatus.ISSUED);                // throws unless APPROVED, so it happens only once
        return new Policy("POL-" + app.getId(), app.getId(), app.getAnnualPremium());
    }

    // ₹1 a year for every ₹1,000 of cover; smokers pay double. ₹1 crore → ₹10,000 a year.
    private long premium(Application app) {
        long premium = app.getCoverage() / 1000;
        return app.isSmoker() ? premium * 2 : premium;
    }

    private Application findApplication(String applicationId) {
        Application app = applications.get(applicationId);
        if (app == null) throw new NoSuchElementException("Unknown application " + applicationId);
        return app;
    }
}
