package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.model;

// The final contract. Immutable once issued. Money in whole rupees.
public class Policy {

    private final String policyNumber;
    private final String applicationId;
    private final long annualPremium;

    public Policy(String policyNumber, String applicationId, long annualPremium) {
        this.policyNumber = policyNumber;
        this.applicationId = applicationId;
        this.annualPremium = annualPremium;
    }

    public String getPolicyNumber()  { return policyNumber; }
    public String getApplicationId() { return applicationId; }
    public long   getAnnualPremium() { return annualPremium; }
}
