package com.conceptcoding.interviewquestions.hello_all_questions.splitwise.splittype;

import com.conceptcoding.interviewquestions.hello_all_questions.splitwise.model.Split;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Divide equally. Input values ignored — only the KEYS (participant ids) matter.
 *
 * <p>Rounding: floor(total/N) to first N-1 participants; last gets the remainder
 * so the sum is EXACTLY the total. $10 across 3 users → $3.33 + $3.33 + $3.34.
 */
public class EqualSplitStrategy implements SplitStrategy {

    @Override
    public List<Split> calculate(long totalAmountCents, Map<String, Long> participantInputs) {
        if (participantInputs == null || participantInputs.isEmpty()) {
            throw new IllegalArgumentException("at least one participant required");
        }
        List<String> users = new ArrayList<>(participantInputs.keySet());
        long n = users.size();
        long share = totalAmountCents / n;
        long remainder = totalAmountCents - share * n;       // 0..n-1 cents

        List<Split> splits = new ArrayList<>();
        for (int i = 0; i < users.size() - 1; i++) {
            splits.add(new Split(users.get(i), share));
        }
        // Last participant absorbs the rounding remainder — sums EXACTLY to total.
        splits.add(new Split(users.get(users.size() - 1), share + remainder));
        return splits;
    }
}
