package com.conceptcoding.interviewquestions.hello_all_questions.splitwise.splittype;

import com.conceptcoding.interviewquestions.hello_all_questions.splitwise.model.Split;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Caller specifies each participant's share in BASIS POINTS (10000 = 100%).
 * Basis points let us express 33.33% as 3333 with integer arithmetic — no float
 * drift. Shares must sum to 10000 exactly.
 *
 * <p>Rounding: floor for first N-1 participants; last absorbs the remainder so
 * the result sums to totalAmountCents exactly.
 */
public class PercentSplitStrategy implements SplitStrategy {

    private static final long TOTAL_BASIS_POINTS = 10_000L;

    @Override
    public List<Split> calculate(long totalAmountCents, Map<String, Long> participantInputs) {
        if (participantInputs == null || participantInputs.isEmpty()) {
            throw new IllegalArgumentException("at least one participant required");
        }
        long sumBps = 0L;
        for (Long bps : participantInputs.values()) {
            if (bps == null || bps < 0) throw new IllegalArgumentException("invalid basis points");
            sumBps += bps;
        }
        if (sumBps != TOTAL_BASIS_POINTS) {
            throw new IllegalArgumentException(
                    "percent shares sum to " + sumBps + " bps; must equal " + TOTAL_BASIS_POINTS);
        }

        List<String> users = new ArrayList<>(participantInputs.keySet());
        List<Split> splits = new ArrayList<>();
        long accumulated = 0L;
        for (int i = 0; i < users.size() - 1; i++) {
            long bps = participantInputs.get(users.get(i));
            // floor(total * bps / 10000) — multiplication first to avoid early rounding
            long amount = (totalAmountCents * bps) / TOTAL_BASIS_POINTS;
            splits.add(new Split(users.get(i), amount));
            accumulated += amount;
        }
        // Last participant absorbs the remainder — sums EXACTLY to total.
        splits.add(new Split(users.get(users.size() - 1), totalAmountCents - accumulated));
        return splits;
    }
}
