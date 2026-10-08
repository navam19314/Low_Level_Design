package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.application;

import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.model.Policy;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.model.Quote;

import java.time.Instant;

// APPROVED: a quote exists; the applicant accepts it and the policy is issued.
public class ApprovedState implements ApplicationState {

    @Override
    public ApplicationStatus status() { return ApplicationStatus.APPROVED; }

    @Override
    public Policy issue(Application app) {
        Quote quote = app.getQuote();
        Policy policy = new Policy("POL-" + app.getId(), app.getId(), app.getApplicantName(),
                                   quote.getCoverage(), quote.getAnnualPremium(), Instant.now());
        app.setPolicy(policy);
        app.setState(new FinalState(ApplicationStatus.ISSUED));
        return policy;
    }
}
