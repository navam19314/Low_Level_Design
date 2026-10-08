package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

// The whole form: ordered steps. Data, not code: adding a question never touches Application.
public class Questionnaire {

    private final List<QuestionnaireStep> steps;
    private final Map<String, Question> questionsById = new LinkedHashMap<>();

    public Questionnaire(List<QuestionnaireStep> steps) {
        this.steps = new ArrayList<>(steps);
        for (QuestionnaireStep step : steps) {
            for (Question q : step.getQuestions()) questionsById.put(q.getId(), q);
        }
    }

    public Question getQuestion(String questionId) {
        Question q = questionsById.get(questionId);
        if (q == null) throw new NoSuchElementException("Unknown question: " + questionId);
        return q;
    }

    public List<QuestionnaireStep> getSteps()        { return new ArrayList<>(steps); }
    public List<Question>          getAllQuestions() { return new ArrayList<>(questionsById.values()); }
}
