package com.conceptcoding.interviewquestions.hello_all_questions.policies.splitwise.model;

import java.util.LinkedHashMap;
import java.util.Map;

// One bill: who paid, how much, and each person's share. Immutable.
public class Expense {

    private final String id;
    private final String description;
    private final String paidBy;
    private final long amount;                       // rupees
    private final Map<String, Long> shares;          // person → share

    public Expense(String id, String description, String paidBy, long amount, Map<String, Long> shares) {
        this.id = id;
        this.description = description;
        this.paidBy = paidBy;
        this.amount = amount;
        this.shares = new LinkedHashMap<>(shares);
    }

    public String            getId()          { return id; }
    public String            getDescription() { return description; }
    public String            getPaidBy()      { return paidBy; }
    public long              getAmount()      { return amount; }
    public Map<String, Long> getShares()      { return new LinkedHashMap<>(shares); }
}
