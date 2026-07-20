package com.conceptcoding.interviewquestions.hello_all_questions.splitwise.model;

import java.time.LocalDateTime;
import java.util.List;

// Immutable ledger entry. Created once by ExpenseManager and never mutated.
public class Expense {

    private final String        id;
    private final String        paidById;
    private final long          totalAmount;   // rupees
    private final List<Split>   splits;
    private final String        description;
    private final LocalDateTime createdAt;

    public Expense(String id, String paidById, long totalAmount,
                   List<Split> splits, String description, LocalDateTime createdAt) {
        this.id          = id;
        this.paidById    = paidById;
        this.totalAmount = totalAmount;
        this.splits      = splits;
        this.description = description;
        this.createdAt   = createdAt;
    }

    public String        getId()          { return id; }
    public String        getPaidById()    { return paidById; }
    public long          getTotalAmount() { return totalAmount; }
    public List<Split>   getSplits()      { return splits; }
    public String        getDescription() { return description; }
    public LocalDateTime getCreatedAt()   { return createdAt; }
}
