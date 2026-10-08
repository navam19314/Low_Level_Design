package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.underwriting;

import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.application.Application;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.model.Quote;

import java.util.List;

// Base premium from age band and cover, then the rules' loadings on top.
// Rate is in paise per ₹1,000 of cover per year: integer math only, no doubles for money.
public class PremiumCalculator {

    public Quote calculate(Application app, int loadingPercent, List<String> loadingReasons) {
        long age = app.getNumber("age");
        long coverage = app.getNumber("coverage");
        long ratePaisePer1000 = age < 30 ? 100 : age < 45 ? 200 : 500;
        long basePremium = coverage / 1000 * ratePaisePer1000 / 100;     // ₹1 crore at age 28 → ₹10,000/yr
        long annualPremium = basePremium * (100 + loadingPercent) / 100;
        return new Quote(coverage, basePremium, loadingPercent, annualPremium, loadingReasons);
    }
}
