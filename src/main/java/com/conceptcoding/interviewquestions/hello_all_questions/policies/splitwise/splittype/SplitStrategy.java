package com.conceptcoding.interviewquestions.hello_all_questions.policies.splitwise.splittype;

import java.util.Map;

// Strategy: how is one bill divided? Each split object carries its own inputs
// (who is in it, or how much each person owes) and returns person → share.
// Contract: the shares add up EXACTLY to the amount.
public interface SplitStrategy {
    Map<String, Long> shares(long amount);
}
