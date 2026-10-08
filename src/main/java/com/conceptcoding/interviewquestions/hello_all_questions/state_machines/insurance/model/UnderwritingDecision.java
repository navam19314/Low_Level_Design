package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.model;

import java.util.ArrayList;
import java.util.List;

// The overall result of running every rule: approved with a quote, or rejected with reasons.
public class UnderwritingDecision {

    private final boolean approved;
    private final Quote quote;                 // null when rejected
    private final List<String> rejectionReasons;

    private UnderwritingDecision(boolean approved, Quote quote, List<String> rejectionReasons) {
        this.approved = approved;
        this.quote = quote;
        this.rejectionReasons = new ArrayList<>(rejectionReasons);
    }

    public static UnderwritingDecision approve(Quote quote) {
        return new UnderwritingDecision(true, quote, List.of());
    }

    public static UnderwritingDecision reject(List<String> reasons) {
        return new UnderwritingDecision(false, null, reasons);
    }

    public boolean      isApproved()          { return approved; }
    public Quote        getQuote()            { return quote; }
    public List<String> getRejectionReasons() { return new ArrayList<>(rejectionReasons); }
}
