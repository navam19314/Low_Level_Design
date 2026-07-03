package com.conceptcoding.interviewquestions.hello_all_questions.splitwise.splittype;

import com.conceptcoding.interviewquestions.hello_all_questions.splitwise.model.Split;

import java.util.List;
import java.util.Map;

// Strategy pattern — each split algorithm implements this same interface.
// ExpenseManager doesn't know or care which one it's holding.
public interface SplitStrategy {

    // Compute how much each participant owes for an expense.
    //
    // Parameters:
    //   totalAmountCents  — the bill in cents (integer to avoid float rounding on money)
    //   participantInputs — userId → strategy-specific number. The MEANING of the value depends on the strategy:
    //     EQUAL   → value is IGNORED (we only use the keys — who's participating)
    //     EXACT   → value is that user's share IN CENTS
    //     PERCENT → value is that user's share IN BASIS POINTS (10000 = 100%)
    //
    // Contract:
    //   The returned splits must sum EXACTLY to totalAmountCents. Every strategy is responsible
    //   for handling cent-level remainders internally so the caller can trust the invariant.
    List<Split> calculate(long totalAmountCents, Map<String, Long> participantInputs);
}
