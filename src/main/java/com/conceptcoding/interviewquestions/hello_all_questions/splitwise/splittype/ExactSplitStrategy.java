package com.conceptcoding.interviewquestions.hello_all_questions.splitwise.splittype;

import com.conceptcoding.interviewquestions.hello_all_questions.splitwise.model.Split;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Caller specifies the exact amount in cents each participant owes.
 * Validated to sum to totalAmountCents EXACTLY — no silent rounding.
 */
public class ExactSplitStrategy implements SplitStrategy {

    @Override
    public List<Split> calculate(long totalAmountCents, Map<String, Long> participantInputs) {
        if (participantInputs == null || participantInputs.isEmpty()) {
            throw new IllegalArgumentException("at least one participant required");
        }
        long sum = 0L;
        List<Split> splits = new ArrayList<>();
        for (Map.Entry<String, Long> e : participantInputs.entrySet()) {
            long amount = e.getValue();
            if (amount < 0) {
                throw new IllegalArgumentException("negative exact amount for " + e.getKey());
            }
            sum += amount;
            splits.add(new Split(e.getKey(), amount));
        }
        if (sum != totalAmountCents) {
            throw new IllegalArgumentException(
                    "exact shares sum to " + sum + " but expense total is " + totalAmountCents);
        }
        return splits;
    }
}
