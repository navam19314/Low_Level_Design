package com.conceptcoding.interviewquestions.hello_all_questions.splitwise.splittype;

import com.conceptcoding.interviewquestions.hello_all_questions.splitwise.model.Split;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EqualSplitStrategy implements SplitStrategy {

    // Divide the bill equally. If it doesn't divide evenly (in rupees),
    // the last person absorbs the leftover rupees.
    //
    // Example: ₹100 split among 3 people
    //   share     = 100 / 3 = 33
    //   remainder = 100 - 33*3 = 1
    //   A: ₹33, B: ₹33, C: ₹33 + 1 = ₹34  → total = ₹100 ✓
    //
    // Note: participantInputs values are ignored here — we only care about the KEYS (who's paying).
    // Same signature across strategies keeps the polymorphism clean.
    @Override
    public List<Split> calculate(long totalAmount, Map<String, Long> participantInputs) {
        List<String> users = new ArrayList<>(participantInputs.keySet());
        long n         = users.size();
        long share     = totalAmount / n;            // floor division
        long remainder = totalAmount - share * n;    // 0..n-1 rupees left over

        List<Split> splits = new ArrayList<>();
        for (int i = 0; i < users.size() - 1; i++) {
            splits.add(new Split(users.get(i), share));
        }
        // Last participant absorbs remainder so splits sum EXACTLY to total.
        splits.add(new Split(users.get(users.size() - 1), share + remainder));
        return splits;
    }
}
