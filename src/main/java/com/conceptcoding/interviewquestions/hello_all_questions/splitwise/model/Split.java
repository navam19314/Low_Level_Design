package com.conceptcoding.interviewquestions.hello_all_questions.splitwise.model;

// One participant's share of an expense — output of SplitStrategy.calculate()
public class Split {

    private final String userId;
    private final long   amount;   // rupees

    public Split(String userId, long amount) {
        this.userId = userId;
        this.amount = amount;
    }

    public String getUserId() { return userId; }
    public long   getAmount() { return amount; }
}
