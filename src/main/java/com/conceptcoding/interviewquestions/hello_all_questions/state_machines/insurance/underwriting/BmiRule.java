package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.underwriting;

import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.application.Application;

// BMI = weight (kg) / height (m)^2. Over 35: decline. 30-35: +25%.
public class BmiRule implements UnderwritingRule {

    @Override
    public RuleResult evaluate(Application app) {
        double heightM = app.getNumber("heightCm") / 100.0;
        double bmi = app.getNumber("weightKg") / (heightM * heightM);
        if (bmi > 35) return RuleResult.decline(String.format("BMI %.1f is above 35", bmi));
        if (bmi >= 30) return RuleResult.load(25, String.format("BMI %.1f +25%%", bmi));
        return RuleResult.accept();
    }
}
