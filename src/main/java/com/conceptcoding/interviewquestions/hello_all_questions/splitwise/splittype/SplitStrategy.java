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
    //   totalAmount        — the bill in rupees (whole units — integer, never a float)
    //   participantInputs  — userId → strategy-specific number. The MEANING of the value depends on the strategy:
    //     EQUAL   → value is IGNORED (we only use the keys — who's participating)
    //     EXACT   → value is that user's share IN RUPEES
    //     PERCENT → value is that user's share IN BASIS POINTS (10000 = 100%)
    //
    // Contract:
    //   The returned splits must sum EXACTLY to totalAmount. Every strategy is responsible
    //   for handling rounding remainders internally so the caller can trust the invariant.
    List<Split> calculate(long totalAmount, Map<String, Long> participantInputs);
}
