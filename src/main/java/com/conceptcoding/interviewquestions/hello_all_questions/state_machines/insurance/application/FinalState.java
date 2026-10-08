package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.application;

// REJECTED and ISSUED: nothing more can happen, so this overrides nothing
// and every action throws. One class serves both end states.
public class FinalState implements ApplicationState {

    private final ApplicationStatus status;

    public FinalState(ApplicationStatus status) { this.status = status; }

    @Override
    public ApplicationStatus status() { return status; }
}
