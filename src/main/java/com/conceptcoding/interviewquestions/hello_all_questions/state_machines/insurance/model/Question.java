package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.model;

public class Question {

    private final String id;          // stable key the underwriting rules read, e.g. "age"
    private final String text;
    private final QuestionType type;

    public Question(String id, String text, QuestionType type) {
        this.id = id;
        this.text = text;
        this.type = type;
    }

    public String       getId()   { return id; }
    public String       getText() { return text; }
    public QuestionType getType() { return type; }
}
