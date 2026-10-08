package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.underwriting;

import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.application.Application;

public class AgeRule implements UnderwritingRule {

    private final int minAge;
    private final int maxAge;

    public AgeRule(int minAge, int maxAge) {
        this.minAge = minAge;
        this.maxAge = maxAge;
    }

    @Override
    public RuleResult evaluate(Application app) {
        long age = app.getNumber("age");
        if (age < minAge || age > maxAge) {
            return RuleResult.decline("Age " + age + " is outside " + minAge + "-" + maxAge);
        }
        return RuleResult.accept();
    }
}
