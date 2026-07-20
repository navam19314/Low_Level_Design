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

// Orchestrator + facade. Owns users, the expense ledger, the balance graph, and
// the Strategy map (one SplitStrategy per SplitType). Everything the caller touches
// goes through this class — addUser, addExpense, getBalance, simplifyDebts.
//
// All amounts are in rupees (long, never a float/double — avoids rounding drift).
public class ExpenseManager {

    private final Map<String, User>              users      = new HashMap<>();
    private final List<Expense>                  expenses   = new ArrayList<>();

    // balances[creditor][debtor] = amount the debtor owes the creditor (in rupees).
    // We NEVER store both balances[A][B] > 0 AND balances[B][A] > 0 at once — see addOwed().
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

    // Record a new expense and update the balance graph.
    //
    // Example: Alice pays ₹300 for dinner, split equally among Alice, Bob, Carol.
    //   addExpense("alice", 300, EQUAL, {"alice":0, "bob":0, "carol":0}, "Dinner")
    //     1. strategy.calculate(300, {...}) → [Split(alice,100), Split(bob,100), Split(carol,100)]
    //     2. Expense EXP-1 is appended to the ledger (immutable, never rewritten).
    //     3. For every split where userId != payer: addOwed(payer, userId, amount)
    //        → addOwed("alice", "bob", 100)    (bob now owes alice ₹100)
    //        → addOwed("alice", "carol", 100)  (carol now owes alice ₹100)
    //        (alice's own split of ₹100 is skipped — she doesn't owe herself)
    public synchronized Expense addExpense(String paidById, long totalAmount,
                                           SplitType splitType,
                                           Map<String, Long> participantInputs,
                                           String description) {
        if (totalAmount <= 0)
            throw new IllegalArgumentException("Amount must be > 0");
        if (!users.containsKey(paidById))
            throw new IllegalArgumentException("Unknown payer: " + paidById);
        for (String uid : participantInputs.keySet())
            if (!users.containsKey(uid))
                throw new IllegalArgumentException("Unknown participant: " + uid);

        // Validate BEFORE mutating anything — delegate math to strategy.
        // May throw (e.g. EXACT shares don't sum to total) — if it does, nothing
        // has been recorded yet, so the ledger and balances stay untouched.
        List<Split> splits = strategies.get(splitType).calculate(totalAmount, participantInputs);

        String expenseId = "EXP-" + (++expenseCounter);
        Expense expense = new Expense(expenseId, paidById, totalAmount,
                                      splits, description, LocalDateTime.now());
        expenses.add(expense);

        // Every non-payer owes the payer their computed share.
        // (The payer's own split, if present, is excluded — you can't owe yourself.)
        for (Split s : splits) {
            if (!s.getUserId().equals(paidById)) {
                addOwed(paidById, s.getUserId(), s.getAmount());
            }
        }
        return expense;
    }

    // Net balance BETWEEN exactly two users (in rupees).
    //   positive → u1 is owed by u2 (u2 owes u1)
    //   negative → u1 owes u2
    //   zero     → settled
    //
    // Example: bob owes alice ₹100 (balances["alice"]["bob"] = 100, nothing the other way)
    //   getBalance("alice", "bob") = getStored("alice","bob") - getStored("bob","alice")
    //                              = 100 - 0 = 100    (alice is owed ₹100 by bob)
    //   getBalance("bob", "alice") = 0 - 100 = -100    (bob owes alice ₹100 — same fact, other side)
    public synchronized long getBalance(String u1, String u2) {
        return getStored(u1, u2) - getStored(u2, u1);
    }

    // Sum of this user's balance against EVERYONE else.
    // Positive = overall a creditor (owed money). Negative = overall a debtor.
    //
    // Example: bob owes alice ₹100, and carol owes bob ₹50.
    //   getNetBalance("bob") = getBalance(bob,alice) + getBalance(bob,carol)
    //                        =        -100           +        50           = -50
    //   Bob is a net debtor of ₹50 once you net out what carol owes him.
    public synchronized long getNetBalance(String userId) {
        long net = 0;
        for (User other : users.values()) {
            if (!other.getId().equals(userId))
                net += getBalance(userId, other.getId());
        }
        return net;
    }

    // Greedy debt simplification — collapses everyone's net balance into the
    // MINIMUM number of payment instructions (at most N-1 settlements for N users).
    //
    // Algorithm:
    //   1. Compute each user's net balance. Skip users who are already settled (net == 0).
    //   2. Split into creditors (net > 0) and debtors (net < 0).
    //   3. Repeatedly match the BIGGEST creditor with the BIGGEST debtor (two heaps),
    //      settle the smaller of the two amounts, and push back whoever still has
    //      a nonzero balance.
    //
    // Worked example — a chain of IOUs:
    //   B pays for A (₹100), C pays for B (₹100), D pays for C (₹100)
    //   → net[A] = -100, net[B] = 0, net[C] = 0, net[D] = +100
    //   (B and C net out to zero — they both owe and are owed the same ₹100)
    //   creditors = [D(+100)], debtors = [A(-100)]
    //   pop D and A → settle = min(100, 100) = 100
    //   → Settlement(debtor=A, creditor=D, amount=100)   "A pays D ₹100 directly"
    //   Both heaps now empty → DONE. One settlement instead of three separate IOUs.
    //
    // NOT optimal in the strict sense (true minimum-transaction is NP-hard — it's
    // subset-sum). Greedy is the right call for interview scope; name the tradeoff.
    public synchronized List<Settlement> simplifyDebts() {
        // Step 1: net balance per user, skip anyone already settled.
        Map<String, Long> net = new HashMap<>();
        for (String uid : users.keySet()) {
            long n = getNetBalance(uid);
            if (n != 0) net.put(uid, n);
        }

        // Step 2: two heaps — max-heap of creditors (biggest owed first),
        // min-heap of debtors (most negative — i.e. owes the most — first).
        PriorityQueue<String> creditors = new PriorityQueue<>((a, b) -> Long.compare(net.get(b), net.get(a)));
        PriorityQueue<String> debtors   = new PriorityQueue<>((a, b) -> Long.compare(net.get(a), net.get(b)));
        for (Map.Entry<String, Long> e : net.entrySet()) {
            if      (e.getValue() > 0) creditors.offer(e.getKey());
            else if (e.getValue() < 0) debtors.offer(e.getKey());
        }

        // Step 3: greedily match the top of each heap until one heap empties.
        List<Settlement> result = new ArrayList<>();
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            String c = creditors.poll();
            String d = debtors.poll();
            long settle = Math.min(net.get(c), -net.get(d));   // smaller of "owed" vs "owes"
            result.add(new Settlement(d, c, settle));

            long newC = net.get(c) - settle;
            long newD = net.get(d) + settle;
            net.put(c, newC);
            net.put(d, newD);
            // Whoever still has a nonzero balance goes back in — they'll be
            // matched again against the NEXT biggest creditor/debtor.
            if (newC > 0) creditors.offer(c);
            if (newD < 0) debtors.offer(d);
        }
        return result;
    }

    public synchronized List<Expense> getExpenses() {
        return new ArrayList<>(expenses);
    }

    // ── internals ────────────────────────────────────────────────────────────────

    // Record that debtor owes creditor `amount` more — but FIRST cancel out any
    // existing debt running the OPPOSITE direction. This keeps the invariant:
    // balances[A][B] > 0 and balances[B][A] > 0 are NEVER both true at once.
    //
    // Why this matters: without cancellation, if alice already owes bob ₹50 and then
    // bob incurs a new ₹100 debt to alice, you'd store BOTH "alice owes bob ₹50" AND
    // "bob owes alice ₹100" — two facts describing the same relationship, and every
    // reader (getBalance) would have to remember to net them out itself.
    //
    // Worked example: bob currently owes alice ₹50 (balances["alice"]["bob"] = 50).
    // Now alice incurs a NEW debt to bob of ₹80 → addOwed("bob", "alice", 80):
    //   opposing = getStored("alice", "bob") = 50       (the existing debt, reversed)
    //   opposing(50) < amount(80), so:
    //     setStored("alice", "bob", 0)                   — wipe the old ₹50 debt
    //     setStored("bob", "alice", 0 + (80 - 50))        — store only the NET: ₹30
    //   Result: alice now owes bob exactly ₹30 net. One fact, not two.
    private void addOwed(String creditorId, String debtorId, long amount) {
        long opposing = getStored(debtorId, creditorId);
        if (opposing >= amount) {
            // The new debt is fully absorbed by the existing opposing debt —
            // just shrink it. No new debt in the other direction needed.
            setStored(debtorId, creditorId, opposing - amount);
        } else {
            setStored(debtorId, creditorId, 0);
            setStored(creditorId, debtorId, getStored(creditorId, debtorId) + (amount - opposing));
        }
    }

    // Raw lookup: how much does `debtor` owe `creditor`? 0 if no entry exists yet.
    private long getStored(String creditor, String debtor) {
        return balances.getOrDefault(creditor, Map.of()).getOrDefault(debtor, 0L);
    }

    // Raw write: set exactly how much `debtor` owes `creditor`, creating the
    // creditor's row in the map on first use.
    private void setStored(String creditor, String debtor, long amount) {
        balances.computeIfAbsent(creditor, k -> new HashMap<>()).put(debtor, amount);
    }
}
