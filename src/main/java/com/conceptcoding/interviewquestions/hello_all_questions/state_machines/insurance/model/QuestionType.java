package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.model;

// Each question type knows how to validate its own answers.
public enum QuestionType {
    NUMBER, YES_NO, TEXT;

    public boolean isValid(String value) {
        if (value == null || value.isBlank()) return false;
        switch (this) {
            case NUMBER:
                try {
                    return Long.parseLong(value.trim()) >= 0;
                } catch (NumberFormatException e) {
                    return false;
                }
            case YES_NO:
                return value.equalsIgnoreCase("yes") || value.equalsIgnoreCase("no");
            default:
                return true;
        }
    }
}
