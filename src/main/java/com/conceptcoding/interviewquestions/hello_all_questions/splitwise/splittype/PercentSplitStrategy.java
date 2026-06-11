package com.conceptcoding.interviewquestions.hello_all_questions.splitwise.splittype;

import com.conceptcoding.interviewquestions.hello_all_questions.splitwise.model.Split;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Values are basis points (10000 = 100%) — no float drift.
// 33.33% → 3333 bps.  Sum must equal 10000.
public class PercentSplitStrategy implements SplitStrategy {

    private static final long TOTAL_BPS = 10_000L;

    @Override
    public List<Split> calculate(long totalAmountCents, Map<String, Long> participantInputs) {
        long sumBps = participantInputs.values().stream().mapToLong(Long::longValue).sum();
        if (sumBps != TOTAL_BPS) {
            throw new IllegalArgumentException(
                    "PERCENT shares sum to " + sumBps + " bps, must equal " + TOTAL_BPS);
        }

        List<String> users = new ArrayList<>(participantInputs.keySet());
        List<Split> splits = new ArrayList<>();
        long accumulated = 0L;
        for (int i = 0; i < users.size() - 1; i++) {
            long bps    = participantInputs.get(users.get(i));
            long amount = (totalAmountCents * bps) / TOTAL_BPS;  // floor — last absorbs remainder
            splits.add(new Split(users.get(i), amount));
            accumulated += amount;
        }
        // last participant absorbs cent-level remainder so sum == total exactly
        splits.add(new Split(users.get(users.size() - 1), totalAmountCents - accumulated));
        return splits;
    }
}
