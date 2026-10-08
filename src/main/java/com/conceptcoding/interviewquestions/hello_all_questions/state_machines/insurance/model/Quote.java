package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.model;

import java.util.ArrayList;
import java.util.List;

// The price offered after underwriting. Money in whole rupees (long).
public class Quote {

    private final long coverage;
    private final long basePremium;           // per year, before loadings
    private final int loadingPercent;         // extra % for risk factors (smoker, high BMI)
    private final long annualPremium;         // basePremium × (100 + loading) / 100
    private final List<String> loadingReasons;

    public Quote(long coverage, long basePremium, int loadingPercent, long annualPremium, List<String> loadingReasons) {
        this.coverage = coverage;
        this.basePremium = basePremium;
        this.loadingPercent = loadingPercent;
        this.annualPremium = annualPremium;
        this.loadingReasons = new ArrayList<>(loadingReasons);
    }

    public long         getCoverage()       { return coverage; }
    public long         getBasePremium()    { return basePremium; }
    public int          getLoadingPercent() { return loadingPercent; }
    public long         getAnnualPremium()  { return annualPremium; }
    public List<String> getLoadingReasons() { return new ArrayList<>(loadingReasons); }

    @Override
    public String toString() {
        return "Quote{cover ₹" + coverage + ", base ₹" + basePremium + "/yr, +" + loadingPercent
                + "% " + loadingReasons + " = ₹" + annualPremium + "/yr}";
    }
}
