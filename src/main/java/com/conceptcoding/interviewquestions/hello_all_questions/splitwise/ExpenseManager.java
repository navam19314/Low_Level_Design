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

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.UUID;

/**
 * Orchestrator + facade. Owns the user registry, the expense ledger, the pairwise
 * balance graph, and the Strategy registry. Single public API: add expenses,
 * query balances, simplify debts.
 *
 * <p>Pairwise balance representation:
 * <pre>
 *   balances[A][B] = positive amount A is owed by B.
 *   We never store both balances[A][B] > 0 AND balances[B][A] > 0 — opposing
 *   amounts cancel during {@code updateBalance}.
 * </pre>
 *
 * <p>Thread-safety: every public mutator/reader is {@code synchronized(this)} —
 * coarse-grained per-manager lock. For finer concurrency (per-user locks with
 * ordered acquisition) see the walkthrough §5.
 */
public class ExpenseManager {

    private final Map<String, User> users = new HashMap<>();
    private final List<Expense> expenses = new ArrayList<>();
    private final Map<String, Map<String, Long>> balances = new HashMap<>();
    private final Map<SplitType, SplitStrategy> strategies;
    private final Clock clock;

    public ExpenseManager() { this(Clock.systemUTC()); }

    public ExpenseManager(Clock clock) {
        this.clock = clock;
        this.strategies = Map.of(
                SplitType.EQUAL,   new EqualSplitStrategy(),
                SplitType.EXACT,   new ExactSplitStrategy(),
                SplitType.PERCENT, new PercentSplitStrategy()
        );
    }

    public synchronized void addUser(User user) {
        users.put(user.id(), user);
    }

    /**
     * Add an expense. Resolves the splits via the appropriate strategy, then
     * updates the pairwise balance graph. Returns the immutable record.
     */
    public synchronized Expense addExpense(String paidById,
                                           long totalAmountCents,
                                           SplitType splitType,
                                           Map<String, Long> participantInputs,
                                           String description) {
        if (totalAmountCents <= 0)              throw new IllegalArgumentException("amount must be > 0");
        if (!users.containsKey(paidById))        throw new IllegalArgumentException("unknown payer " + paidById);
        for (String uid : participantInputs.keySet()) {
            if (!users.containsKey(uid))         throw new IllegalArgumentException("unknown participant " + uid);
        }

        SplitStrategy strategy = strategies.get(splitType);
        List<Split> splits = strategy.calculate(totalAmountCents, participantInputs);

        Expense expense = new Expense(
                UUID.randomUUID().toString(),
                paidById,
                totalAmountCents,
                splits,
                description,
                clock.instant());
        expenses.add(expense);

        // Update the balance graph: every non-payer owes the payer their share.
        for (Split s : splits) {
            if (!s.userId().equals(paidById)) {
                addOwed(paidById, s.userId(), s.amountCents());
            }
        }
        return expense;
    }

    /** Positive: u1 is owed money by u2; negative: u1 owes money to u2; zero: settled. */
    public synchronized long getBalance(String u1, String u2) {
        return getStored(u1, u2) - getStored(u2, u1);
    }

    /** Net position of one user: positive → they are net owed; negative → they owe net. */
    public synchronized long getNetBalance(String userId) {
        long net = 0L;
        for (User other : users.values()) {
            if (!other.id().equals(userId)) {
                net += getBalance(userId, other.id());
            }
        }
        return net;
    }

    /**
     * Greedy debt simplification. NP-hard to do optimally; this produces ≤ N-1
     * settlements which is the upper bound — typically much fewer.
     *
     * <p>Algorithm: compute net balance per user. Repeatedly pick the user with
     * the largest CREDIT and the user with the largest DEBT, settle min of the
     * two amounts between them. Repeat until both heaps are empty.
     */
    public synchronized List<Settlement> simplifyDebts() {
        // Step 1: net balance per user (positive = owed; negative = owes).
        Map<String, Long> net = new HashMap<>();
        for (String userId : users.keySet()) {
            long n = getNetBalance(userId);
            if (n != 0) net.put(userId, n);
        }

        // Step 2: max-heap of creditors (largest credit first), min-heap of debtors (most-negative first).
        PriorityQueue<String> creditors = new PriorityQueue<>((a, b) -> Long.compare(net.get(b), net.get(a)));
        PriorityQueue<String> debtors   = new PriorityQueue<>((a, b) -> Long.compare(net.get(a), net.get(b)));
        for (Map.Entry<String, Long> e : net.entrySet()) {
            if      (e.getValue() > 0) creditors.offer(e.getKey());
            else if (e.getValue() < 0) debtors.offer(e.getKey());
        }

        // Step 3: greedy match.
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

    // ----- internals -----

    /**
     * Record that {@code debtorId} owes {@code creditorId} an additional {@code amount}.
     * Cancels any opposing balance first so we never store both directions positive
     * — the graph stays in a normalized form.
     */
    private void addOwed(String creditorId, String debtorId, long amount) {
        long opposing = getStored(debtorId, creditorId);    // amount creditor previously owed debtor
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
