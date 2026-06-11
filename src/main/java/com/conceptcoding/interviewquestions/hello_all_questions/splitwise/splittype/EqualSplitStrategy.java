package com.conceptcoding.interviewquestions.hello_all_questions.splitwise.splittype;

import com.conceptcoding.interviewquestions.hello_all_questions.splitwise.model.Split;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class EqualSplitStrategy implements SplitStrategy {

    @Override
    public List<Split> calculate(long totalAmountCents, Map<String, Long> participantInputs) {
        List<String> users = new ArrayList<>(participantInputs.keySet());
        long n         = users.size();
        long share     = totalAmountCents / n;
        long remainder = totalAmountCents - share * n;   // 0..n-1 cents

        List<Split> splits = new ArrayList<>();
        for (int i = 0; i < users.size() - 1; i++) {
            splits.add(new Split(users.get(i), share));
        }
        // last participant absorbs remainder so sum == total exactly
        splits.add(new Split(users.get(users.size() - 1), share + remainder));
        return splits;
    }
}
