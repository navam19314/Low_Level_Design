package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.model;

import java.time.Instant;

// The final contract. Immutable once issued.
public class Policy {

    private final String policyNumber;
    private final String applicationId;
    private final String holderName;
    private final long coverage;
    private final long annualPremium;
    private final Instant issuedAt;

    public Policy(String policyNumber, String applicationId, String holderName,
                  long coverage, long annualPremium, Instant issuedAt) {
        this.policyNumber = policyNumber;
        this.applicationId = applicationId;
        this.holderName = holderName;
        this.coverage = coverage;
        this.annualPremium = annualPremium;
        this.issuedAt = issuedAt;
    }

    public String  getPolicyNumber()  { return policyNumber; }
    public String  getApplicationId() { return applicationId; }
    public String  getHolderName()    { return holderName; }
    public long    getCoverage()      { return coverage; }
    public long    getAnnualPremium() { return annualPremium; }
    public Instant getIssuedAt()      { return issuedAt; }

    @Override
    public String toString() {
        return "Policy{" + policyNumber + ", " + holderName + ", cover ₹" + coverage + ", ₹" + annualPremium + "/yr}";
    }
}
