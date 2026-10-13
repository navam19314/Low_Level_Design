package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.rules;

import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.model.Application;

// You can't insure your life for far more than your family would lose: cover ≤ N × yearly income.
public class CoverageToIncomeRule implements UnderwritingRule {

    private final long maxMultiple;

    public CoverageToIncomeRule(long maxMultiple) {
        this.maxMultiple = maxMultiple;
    }

    @Override
    public String check(Application app) {
        if (app.getCoverage() > app.getAnnualIncome() * maxMultiple) {
            return "Cover is more than " + maxMultiple + "x income";
        }
        return null;
    }
}
