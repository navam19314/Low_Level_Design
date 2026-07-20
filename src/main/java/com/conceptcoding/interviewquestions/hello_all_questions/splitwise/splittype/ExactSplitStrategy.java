package com.conceptcoding.interviewquestions.hello_all_questions.splitwise.splittype;

import com.conceptcoding.interviewquestions.hello_all_questions.splitwise.model.Split;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ExactSplitStrategy implements SplitStrategy {

    // The caller has already decided how much each person owes (in rupees).
    // Our only job is to validate that the shares add up to the total.
    //
    // Example: ₹1000 dinner, A ordered ₹600 worth, B ordered ₹400 worth
    //   participantInputs = {A: 600, B: 400}, totalAmount = 1000
    //   sum = 600 + 400 = 1000 ✓
    //
    // No remainder trick needed — the caller supplied exact rupee values, so if they add up,
    // we're done. If they don't add up, that's a bad request and we throw.
    @Override
    public List<Split> calculate(long totalAmount, Map<String, Long> participantInputs) {
        long sum = 0L;
        List<Split> splits = new ArrayList<>();
        for (Map.Entry<String, Long> e : participantInputs.entrySet()) {
            splits.add(new Split(e.getKey(), e.getValue()));
            sum += e.getValue();
        }
        if (sum != totalAmount) {
            throw new IllegalArgumentException(
                    "EXACT shares sum to " + sum + ", expense total is " + totalAmount);
        }
        return splits;
    }
}
