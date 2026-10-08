package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.application;

// SUBMITTED: answers are frozen; waiting for underwriting to pick it up.
public class SubmittedState implements ApplicationState {

    @Override
    public ApplicationStatus status() { return ApplicationStatus.SUBMITTED; }

    @Override
    public void startUnderwriting(Application app) {
        app.setState(new UnderwritingState());
    }
}
