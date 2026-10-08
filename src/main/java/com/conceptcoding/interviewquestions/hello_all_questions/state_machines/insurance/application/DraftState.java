package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.application;

import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.model.Question;

import java.util.List;

// DRAFT: the applicant fills the form, step by step, in any order, and can change answers.
public class DraftState implements ApplicationState {

    @Override
    public ApplicationStatus status() { return ApplicationStatus.DRAFT; }

    @Override
    public void answer(Application app, String questionId, String value) {
        Question q = app.getQuestionnaire().getQuestion(questionId);       // throws if unknown
        if (!q.getType().isValid(value)) {
            throw new IllegalArgumentException("Invalid answer for '" + q.getText() + "': " + value);
        }
        app.putAnswer(questionId, value.trim());
    }

    @Override
    public void submit(Application app) {
        List<String> missing = app.getMissingQuestionIds();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Cannot submit, unanswered questions: " + missing);
        }
        app.setState(new SubmittedState());
    }
}
