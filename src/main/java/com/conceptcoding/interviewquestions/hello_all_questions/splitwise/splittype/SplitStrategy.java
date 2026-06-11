package com.conceptcoding.interviewquestions.hello_all_questions.splitwise.splittype;

import com.conceptcoding.interviewquestions.hello_all_questions.splitwise.model.Split;

import java.util.List;
import java.util.Map;

public interface SplitStrategy {
    // participantInputs: userId → strategy-specific value (ignored for EQUAL, cents for EXACT, bps for PERCENT)
    // returned splits must sum EXACTLY to totalAmountCents
    List<Split> calculate(long totalAmountCents, Map<String, Long> participantInputs);
}
