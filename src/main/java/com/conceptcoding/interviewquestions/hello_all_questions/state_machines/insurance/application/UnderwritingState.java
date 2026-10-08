package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.application;

import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.model.UnderwritingDecision;

// UNDERWRITING: rules are being evaluated (in real life this can take days: medical
// reports, manual review). The only legal action is recording the decision.
public class UnderwritingState implements ApplicationState {

    @Override
    public ApplicationStatus status() { return ApplicationStatus.UNDERWRITING; }

    @Override
    public void recordDecision(Application app, UnderwritingDecision decision) {
        if (decision.isApproved()) {
            app.setQuote(decision.getQuote());
            app.setState(new ApprovedState());
        } else {
            app.setRejectionReasons(decision.getRejectionReasons());
            app.setState(new FinalState(ApplicationStatus.REJECTED));
        }
    }
}
