package com.conceptcoding.interviewquestions.hello_all_questions.splitwise;

import com.conceptcoding.interviewquestions.hello_all_questions.splitwise.model.Expense;
import com.conceptcoding.interviewquestions.hello_all_questions.splitwise.model.Settlement;
import com.conceptcoding.interviewquestions.hello_all_questions.splitwise.model.Split;
import com.conceptcoding.interviewquestions.hello_all_questions.splitwise.model.User;
import com.conceptcoding.interviewquestions.hello_all_questions.splitwise.splittype.EqualSplitStrategy;
import com.conceptcoding.interviewquestions.hello_all_questions.splitwise.splittype.ExactSplitStrategy;
import com.conceptcoding.interviewquestions.hello_all_questions.splitwise.splittype.PercentSplitStrategy;
import com.conceptcoding.interviewquestions.hello_all_questions.splitwise.splittype.SplitStrategy;
import com.conceptcoding.interviewquestions.hello_all_questions.splitwise.splittype.SplitType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public class ExpenseManager {

    private final Map<String, User>              users      = new HashMap<>();
    private final List<Expense>                  expenses   = new ArrayList<>();
    private final Map<String, Map<String, Long>> balances   = new HashMap<>();
    private final Map<SplitType, SplitStrategy>  strategies = Map.of(
            SplitType.EQUAL,   new EqualSplitStrategy(),
            SplitType.EXACT,   new ExactSplitStrategy(),
            SplitType.PERCENT, new PercentSplitStrategy()
    );
    private int expenseCounter = 0;   // simple sequential ID: EXP-1, EXP-2, ...

    public synchronized void addUser(User user) {
        users.put(user.getId(), user);
    }

    public synchronized Expense addExpense(String paidById, long totalAmountCents,
                                           SplitType splitType,
                                           Map<String, Long> participantInputs,
                                           String description) {
        if (totalAmountCents <= 0)
            throw new IllegalArgumentException("Amount must be > 0");
        if (!users.containsKey(paidById))
            throw new IllegalArgumentException("Unknown payer: " + paidById);
        for (String uid : participantInputs.keySet())
            if (!users.containsKey(uid))
                throw new IllegalArgumentException("Unknown participant: " + uid);

        // delegate math to strategy — may throw if inputs invalid (e.g. exact sum mismatch)
        List<Split> splits = strategies.get(splitType).calculate(totalAmountCents, participantInputs);

        String expenseId = "EXP-" + (++expenseCounter);
        Expense expense = new Expense(expenseId, paidById, totalAmountCents,
                                      splits, description, LocalDateTime.now());
        expenses.add(expense);

        // every non-payer owes the payer their computed share
        for (Split s : splits) {
            if (!s.getUserId().equals(paidById)) {
                addOwed(paidById, s.getUserId(), s.getAmountCents());
            }
        }
        return expense;
    }

    // positive: u1 is owed by u2.  negative: u1 owes u2.  zero: settled.
    public synchronized long getBalance(String u1, String u2) {
        return getStored(u1, u2) - getStored(u2, u1);
    }

    public synchronized long getNetBalance(String userId) {
        long net = 0;
        for (User other : users.values()) {
            if (!other.getId().equals(userId))
                net += getBalance(userId, other.getId());
        }
        return net;
    }

    // Greedy simplification — max-heap of creditors, min-heap of debtors; match greedily.
    // Produces at most N-1 settlements. Greedy, not optimal (optimal is NP-hard).
    public synchronized List<Settlement> simplifyDebts() {
        Map<String, Long> net = new HashMap<>();
        for (String uid : users.keySet()) {
            long n = getNetBalance(uid);
            if (n != 0) net.put(uid, n);
        }

        PriorityQueue<String> creditors = new PriorityQueue<>((a, b) -> Long.compare(net.get(b), net.get(a)));
        PriorityQueue<String> debtors   = new PriorityQueue<>((a, b) -> Long.compare(net.get(a), net.get(b)));
        for (Map.Entry<String, Long> e : net.entrySet()) {
            if      (e.getValue() > 0) creditors.offer(e.getKey());
            else if (e.getValue() < 0) debtors.offer(e.getKey());
        }

        List<Settlement> result = new ArrayList<>();
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            String c = creditors.poll();
            String d = debtors.poll();
            long settle = Math.min(net.get(c), -net.get(d));
            result.add(new Settlement(d, c, settle));

            long newC = net.get(c) - settle;
            long newD = net.get(d) + settle;
            net.put(c, newC);
            net.put(d, newD);
            if (newC > 0) creditors.offer(c);
            if (newD < 0) debtors.offer(d);
        }
        return result;
    }

    public synchronized List<Expense> getExpenses() {
        return new ArrayList<>(expenses);
    }

    // ── internals ────────────────────────────────────────────────────────────────

    // Cancel any opposing balance first — never store both directions positive.
    private void addOwed(String creditorId, String debtorId, long amount) {
        long opposing = getStored(debtorId, creditorId);
        if (opposing >= amount) {
            setStored(debtorId, creditorId, opposing - amount);
        } else {
            setStored(debtorId, creditorId, 0);
            setStored(creditorId, debtorId, getStored(creditorId, debtorId) + (amount - opposing));
        }
    }

    private long getStored(String creditor, String debtor) {
        return balances.getOrDefault(creditor, Map.of()).getOrDefault(debtor, 0L);
    }

    private void setStored(String creditor, String debtor, long amount) {
        balances.computeIfAbsent(creditor, k -> new HashMap<>()).put(debtor, amount);
    }
}
