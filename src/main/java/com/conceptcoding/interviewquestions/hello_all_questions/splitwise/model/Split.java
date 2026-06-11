package com.conceptcoding.interviewquestions.hello_all_questions.splitwise.model;

// One participant's share of an expense — output of SplitStrategy.calculate()
public class Split {

    private final String userId;
    private final long   amountCents;

    public Split(String userId, long amountCents) {
        this.userId      = userId;
        this.amountCents = amountCents;
    }

    public String getUserId()      { return userId; }
    public long   getAmountCents() { return amountCents; }
}
