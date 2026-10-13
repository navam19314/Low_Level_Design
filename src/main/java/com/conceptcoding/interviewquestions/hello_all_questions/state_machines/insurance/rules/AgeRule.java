package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.rules;

import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.model.Application;

public class AgeRule implements UnderwritingRule {

    private final int minAge;
    private final int maxAge;

    public AgeRule(int minAge, int maxAge) {
        this.minAge = minAge;
        this.maxAge = maxAge;
    }

    @Override
    public String check(Application app) {
        if (app.getAge() < minAge || app.getAge() > maxAge) {
            return "Age " + app.getAge() + " is outside " + minAge + "-" + maxAge;
        }
        return null;
    }
}
