package com.conceptcoding.interviewquestions.hello_all_questions.splitwise.splittype;

import com.conceptcoding.interviewquestions.hello_all_questions.splitwise.model.Split;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ExactSplitStrategy implements SplitStrategy {

    @Override
    public List<Split> calculate(long totalAmountCents, Map<String, Long> participantInputs) {
        long sum = 0L;
        List<Split> splits = new ArrayList<>();
        for (Map.Entry<String, Long> e : participantInputs.entrySet()) {
            splits.add(new Split(e.getKey(), e.getValue()));
            sum += e.getValue();
        }
        if (sum != totalAmountCents) {
            throw new IllegalArgumentException(
                    "EXACT shares sum to " + sum + ", expense total is " + totalAmountCents);
        }
        return splits;
    }
}
