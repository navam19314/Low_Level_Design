package com.conceptcoding.interviewquestions.hello_all_questions.splitwise.model;

import java.time.Instant;
import java.util.List;

/**
 * Immutable record of one expense: who paid, how much, who owes what.
 * The {@code splits} list is the COMPUTED result of applying a SplitStrategy to
 * raw user inputs — sums to totalAmountCents (invariant enforced by ExpenseManager).
 */
public record Expense(
        String id,
        String paidById,
        long totalAmountCents,
        List<Split> splits,
        String description,
        Instant createdAt) {

    public Expense {
        splits = List.copyOf(splits);  // defensive immutable copy
    }
}
