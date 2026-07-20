package com.conceptcoding.interviewquestions.hello_all_questions.splitwise.splittype;

import com.conceptcoding.interviewquestions.hello_all_questions.splitwise.model.Split;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Values are basis points (10000 = 100%) — no float drift.
// 33.33% → 3333 bps.  Sum must equal 10000.
public class PercentSplitStrategy implements SplitStrategy {

    private static final long TOTAL_BPS = 10_000L;

    // Given a bill and each participant's percentage (as basis points),
    // return how much each person owes in rupees.
    //
    // Example: totalAmount = ₹1000, participants = {A: 3333, B: 3333, C: 3334}
    //   A owes (1000 * 3333) / 10000 = 333
    //   B owes (1000 * 3333) / 10000 = 333
    //   C absorbs the remainder = 1000 - 333 - 333 = 334  → total = ₹1000 ✓
    //
    // Why bps instead of doubles? Money math with double leaks precision (0.1 + 0.2 = 0.30000000000000004).
    // Using integers everywhere means totals ALWAYS reconcile exactly.
    @Override
    public List<Split> calculate(long totalAmount, Map<String, Long> participantInputs) {
        // Validate that percentages sum to exactly 100% before touching any money.
        long sumBps = 0L;
        for (long bps : participantInputs.values()) sumBps += bps;
        if (sumBps != TOTAL_BPS) {
            throw new IllegalArgumentException(
                    "PERCENT shares sum to " + sumBps + " bps, must equal " + TOTAL_BPS);
        }

        List<String> users = new ArrayList<>(participantInputs.keySet());
        List<Split> splits = new ArrayList<>();
        long accumulated = 0L;

        // Assign amounts to everyone EXCEPT the last person.
        // Integer division floors — e.g. (1000 * 3333) / 10000 truncates instead of rounding up.
        // That means we may be a few rupees short of the total; the last person picks up the slack.
        for (int i = 0; i < users.size() - 1; i++) {
            long bps    = participantInputs.get(users.get(i));
            long amount = (totalAmount * bps) / TOTAL_BPS;
            splits.add(new Split(users.get(i), amount));
            accumulated += amount;
        }

        // Last participant absorbs the remainder so splits sum EXACTLY to total.
        splits.add(new Split(users.get(users.size() - 1), totalAmount - accumulated));
        return splits;
    }
}
