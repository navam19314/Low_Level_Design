package com.conceptcoding.interviewquestions.hello_all_questions.splitwise.splittype;

import com.conceptcoding.interviewquestions.hello_all_questions.splitwise.model.Split;

import java.util.List;
import java.util.Map;

/**
 * Strategy interface — how to divide an expense across participants.
 *
 * <p>Justified in the BASE design because the problem explicitly enumerates THREE
 * ways to split (equal / exact / percent). Adding a new split type tomorrow
 * (e.g., shares-weighted) is one new class + one new factory case, zero changes
 * to ExpenseManager.
 *
 * <p>Implementations MUST guarantee the returned splits sum EXACTLY to
 * {@code totalAmountCents}. Rounding leftovers go on the LAST participant —
 * never silently round away pennies.
 */
public interface SplitStrategy {

    /**
     * @param totalAmountCents total expense in cents
     * @param participantInputs participants and their strategy-specific input values
     * @return per-user owed amounts; sums to totalAmountCents
     */
    List<Split> calculate(long totalAmountCents, Map<String, Long> participantInputs);
}
