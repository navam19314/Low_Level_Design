package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance;

import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.application.Application;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.application.ApplicationStatus;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.model.Policy;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.model.Questionnaire;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.model.QuestionnaireStep;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.model.UnderwritingDecision;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.underwriting.Underwriter;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

// The service the web app calls. Thin on purpose: the rules for WHAT is allowed WHEN live
// in the state classes, and the rules for WHO is eligible live in the underwriting rules.
public class InsuranceService {

    private final Map<String, Application> applications = new ConcurrentHashMap<>();
    private final Questionnaire questionnaire;
    private final Underwriter underwriter;
    private final AtomicLong seq = new AtomicLong();

    public InsuranceService(Questionnaire questionnaire, Underwriter underwriter) {
        this.questionnaire = questionnaire;
        this.underwriter = underwriter;
    }

    public Application startApplication(String applicantName) {
        Application app = new Application("APP-" + seq.incrementAndGet(), applicantName, questionnaire);
        applications.put(app.getId(), app);
        return app;
    }

    public void answer(String applicationId, String questionId, String value) {
        get(applicationId).answer(questionId, value);
    }

    public QuestionnaireStep nextStep(String applicationId) {
        return get(applicationId).getNextIncompleteStep();
    }

    public void submit(String applicationId) {
        get(applicationId).submit();
    }

    // SUBMITTED → UNDERWRITING → APPROVED / REJECTED.
    // startUnderwriting() fails if another thread already started, so rules run once.
    // While UNDERWRITING, no answer can change, so the rules read a stable application.
    public ApplicationStatus underwrite(String applicationId) {
        Application app = get(applicationId);
        app.startUnderwriting();
        UnderwritingDecision decision = underwriter.evaluate(app);
        app.recordDecision(decision);
        return app.getStatus();
    }

    public Policy issuePolicy(String applicationId) {
        return get(applicationId).issue();
    }

    public Application get(String applicationId) {
        Application app = applications.get(applicationId);
        if (app == null) throw new NoSuchElementException("Application not found: " + applicationId);
        return app;
    }
}
