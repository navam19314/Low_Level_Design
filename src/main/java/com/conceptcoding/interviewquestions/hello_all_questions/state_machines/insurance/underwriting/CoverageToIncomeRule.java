package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.underwriting;

import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.application.Application;

// You can't insure your life for far more than you'd lose: cover is capped at N × annual income.
public class CoverageToIncomeRule implements UnderwritingRule {

    private final int maxMultiple;

    public CoverageToIncomeRule(int maxMultiple) { this.maxMultiple = maxMultiple; }

    @Override
    public RuleResult evaluate(Application app) {
        long coverage = app.getNumber("coverage");
        long income = app.getNumber("annualIncome");
        if (coverage > income * maxMultiple) {
            return RuleResult.decline("Cover ₹" + coverage + " is more than " + maxMultiple + "x income");
        }
        return RuleResult.accept();
    }
}
