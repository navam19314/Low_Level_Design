package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.underwriting;

import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.application.Application;

public class SmokerRule implements UnderwritingRule {

    @Override
    public RuleResult evaluate(Application app) {
        return app.getYesNo("smoker") ? RuleResult.load(50, "smoker +50%") : RuleResult.accept();
    }
}
