package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.application;

import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.model.Policy;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.model.UnderwritingDecision;

// State pattern. Every action is refused by default; each state overrides ONLY the
// actions that are legal in it. So DraftState overrides answer() and submit(),
// ApprovedState overrides issue(), and so on. An illegal action can't slip through
// because there's no "if (status == ...)" anywhere to forget.
public interface ApplicationState {

    ApplicationStatus status();

    default void answer(Application app, String questionId, String value) { throw notAllowed("answer questions"); }
    default void submit(Application app)                                  { throw notAllowed("submit"); }
    default void startUnderwriting(Application app)                       { throw notAllowed("start underwriting"); }
    default void recordDecision(Application app, UnderwritingDecision d)  { throw notAllowed("record a decision"); }
    default Policy issue(Application app)                                 { throw notAllowed("issue a policy"); }

    private IllegalStateException notAllowed(String action) {
        return new IllegalStateException("Cannot " + action + " when the application is " + status());
    }
}
