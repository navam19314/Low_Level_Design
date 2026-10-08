package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.application;

import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.model.Policy;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.model.Question;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.model.Questionnaire;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.model.QuestionnaireStep;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.model.Quote;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.model.UnderwritingDecision;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// The context object of the State pattern. Public actions just delegate to the current
// state; the state decides whether the action is legal and what happens.
// synchronized: a double-clicked "Issue policy" button must not issue two policies.
public class Application {

    private final String id;
    private final String applicantName;
    private final Questionnaire questionnaire;
    private final Map<String, String> answers = new LinkedHashMap<>();   // questionId → answer
    private ApplicationState state = new DraftState();
    private Quote quote;
    private List<String> rejectionReasons = new ArrayList<>();
    private Policy policy;

    public Application(String id, String applicantName, Questionnaire questionnaire) {
        this.id = id;
        this.applicantName = applicantName;
        this.questionnaire = questionnaire;
    }

    // ---- actions: delegate to the state ----
    public synchronized void answer(String questionId, String value)     { state.answer(this, questionId, value); }
    public synchronized void submit()                                    { state.submit(this); }
    public synchronized void startUnderwriting()                         { state.startUnderwriting(this); }
    public synchronized void recordDecision(UnderwritingDecision d)      { state.recordDecision(this, d); }
    public synchronized Policy issue()                                   { return state.issue(this); }
    public synchronized ApplicationStatus getStatus()                    { return state.status(); }

    // ---- multi-step form progress ----
    // The first step that still has an unanswered question, or null when the form is complete.
    public synchronized QuestionnaireStep getNextIncompleteStep() {
        for (QuestionnaireStep step : questionnaire.getSteps()) {
            for (Question q : step.getQuestions()) {
                if (!answers.containsKey(q.getId())) return step;
            }
        }
        return null;
    }

    public synchronized List<String> getMissingQuestionIds() {
        List<String> missing = new ArrayList<>();
        for (Question q : questionnaire.getAllQuestions()) {
            if (!answers.containsKey(q.getId())) missing.add(q.getId());
        }
        return missing;
    }

    // ---- typed reads for underwriting rules ----
    public synchronized long getNumber(String questionId) {
        return Long.parseLong(answers.get(questionId));
    }

    public synchronized String getText(String questionId) {
        return answers.get(questionId);
    }

    public synchronized boolean getYesNo(String questionId) {
        return "yes".equalsIgnoreCase(answers.get(questionId));
    }

    // ---- used only by the state classes (package-private) ----
    void setState(ApplicationState state)            { this.state = state; }
    void putAnswer(String questionId, String value)  { answers.put(questionId, value); }
    void setQuote(Quote quote)                       { this.quote = quote; }
    void setRejectionReasons(List<String> reasons)   { this.rejectionReasons = new ArrayList<>(reasons); }
    void setPolicy(Policy policy)                    { this.policy = policy; }

    public String        getId()               { return id; }
    public String        getApplicantName()    { return applicantName; }
    public Questionnaire getQuestionnaire()    { return questionnaire; }
    public synchronized Quote        getQuote()            { return quote; }
    public synchronized Policy       getPolicy()           { return policy; }
    public synchronized List<String> getRejectionReasons() { return new ArrayList<>(rejectionReasons); }
}
