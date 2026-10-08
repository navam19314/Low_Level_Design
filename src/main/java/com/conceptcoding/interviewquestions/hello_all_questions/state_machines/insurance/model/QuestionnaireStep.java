package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.model;

import java.util.ArrayList;
import java.util.List;

// One screen of the multi-step form ("Personal details", "Health").
public class QuestionnaireStep {

    private final String name;
    private final List<Question> questions;

    public QuestionnaireStep(String name, List<Question> questions) {
        this.name = name;
        this.questions = new ArrayList<>(questions);
    }

    public String         getName()      { return name; }
    public List<Question> getQuestions() { return new ArrayList<>(questions); }
}
