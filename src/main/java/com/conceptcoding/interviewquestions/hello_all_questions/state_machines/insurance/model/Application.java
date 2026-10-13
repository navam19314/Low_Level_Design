package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.model;

import java.util.ArrayList;
import java.util.List;

// One person's application: their details, where it is in the flow, and the result.
public class Application {

    private final String id;
    private final String name;
    private final int age;
    private final boolean smoker;
    private final long coverage;                                   // rupees
    private final long annualIncome;                               // rupees
    private ApplicationStatus status = ApplicationStatus.DRAFT;
    private long annualPremium;                                    // set when approved
    private List<String> rejectionReasons = new ArrayList<>();     // set when rejected

    public Application(String id, String name, int age, boolean smoker, long coverage, long annualIncome) {
        this.id = id;
        this.name = name;
        this.age = age;
        this.smoker = smoker;
        this.coverage = coverage;
        this.annualIncome = annualIncome;
    }

    public void moveTo(ApplicationStatus next) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("Application " + id + ": cannot go from " + status + " to " + next);
        }
        status = next;
    }

    public String            getId()               { return id; }
    public String            getName()             { return name; }
    public int               getAge()              { return age; }
    public boolean           isSmoker()            { return smoker; }
    public long              getCoverage()         { return coverage; }
    public long              getAnnualIncome()     { return annualIncome; }
    public ApplicationStatus getStatus()           { return status; }
    public long              getAnnualPremium()    { return annualPremium; }
    public List<String>      getRejectionReasons() { return new ArrayList<>(rejectionReasons); }
    public void setAnnualPremium(long premium)            { this.annualPremium = premium; }
    public void setRejectionReasons(List<String> reasons) { this.rejectionReasons = new ArrayList<>(reasons); }
}
