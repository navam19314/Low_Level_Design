package com.conceptcoding.interviewquestions.hello_all_questions.splitwise.splittype;

import com.conceptcoding.interviewquestions.hello_all_questions.splitwise.model.Split;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EqualSplitStrategy implements SplitStrategy {

    // Divide the bill equally. If it doesn't divide evenly (in cents),
    // the last person absorbs the leftover cents.
    //
    // Example: $10.00 (1000 cents) split among 3 people
    //   share     = 1000 / 3 = 333
    //   remainder = 1000 - 333*3 = 1 cent
    //   A: 333, B: 333, C: 333 + 1 = 334  → total = 1000 ✓
    //
    // Note: participantInputs values are ignored here — we only care about the KEYS (who's paying).
    // Same signature across strategies keeps the polymorphism clean.
    @Override
    public List<Split> calculate(long totalAmountCents, Map<String, Long> participantInputs) {
        List<String> users = new ArrayList<>(participantInputs.keySet());
        long n         = users.size();
        long share     = totalAmountCents / n;            // floor division
        long remainder = totalAmountCents - share * n;    // 0..n-1 cents left over

        List<Split> splits = new ArrayList<>();
        for (int i = 0; i < users.size() - 1; i++) {
            splits.add(new Split(users.get(i), share));
        }
        // Last participant absorbs remainder so splits sum EXACTLY to total.
        splits.add(new Split(users.get(users.size() - 1), share + remainder));
        return splits;
    }
}
