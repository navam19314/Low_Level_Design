package com.conceptcoding.interviewquestions.hello_all_questions.policies.splitwise.splittype;

import java.util.LinkedHashMap;
import java.util.Map;

// The caller says exactly how much each person owes; they must add up to the bill.
public class ExactSplit implements SplitStrategy {

    private final Map<String, Long> amounts;

    public ExactSplit(Map<String, Long> amounts) {
        this.amounts = new LinkedHashMap<>(amounts);
    }

    @Override
    public Map<String, Long> shares(long amount) {
        long sum = 0;
        for (Map.Entry<String, Long> e : amounts.entrySet()) {
            if (e.getValue() < 0) throw new IllegalArgumentException("Negative share for " + e.getKey());
            sum += e.getValue();
        }
        if (sum != amount) throw new IllegalArgumentException("Shares add up to " + sum + ", not " + amount);
        return new LinkedHashMap<>(amounts);
    }
}
