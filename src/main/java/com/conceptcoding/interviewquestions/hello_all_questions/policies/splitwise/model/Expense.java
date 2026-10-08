package com.conceptcoding.interviewquestions.hello_all_questions.policies.splitwise.model;

import java.time.LocalDateTime;
import java.util.List;

// Immutable ledger entry. Created once by ExpenseManager and never mutated.
public class Expense {

    private final String        id;
    private final String        groupId;       // null for a personal (non-group) expense
    private final String        paidById;
    private final long          totalAmount;   // rupees
    private final List<Split>   splits;
    private final String        description;
    private final LocalDateTime createdAt;

    public Expense(String id, String groupId, String paidById, long totalAmount,
                   List<Split> splits, String description, LocalDateTime createdAt) {
        this.id          = id;
        this.groupId     = groupId;
        this.paidById    = paidById;
        this.totalAmount = totalAmount;
        this.splits      = List.copyOf(splits);
        this.description = description;
        this.createdAt   = createdAt;
    }

    public String        getId()          { return id; }
    public String        getGroupId()     { return groupId; }
    public String        getPaidById()    { return paidById; }
    public long          getTotalAmount() { return totalAmount; }
    public List<Split>   getSplits()      { return splits; }
    public String        getDescription() { return description; }
    public LocalDateTime getCreatedAt()   { return createdAt; }
}
