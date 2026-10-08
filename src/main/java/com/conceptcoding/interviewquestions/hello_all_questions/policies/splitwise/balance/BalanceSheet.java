package com.conceptcoding.interviewquestions.hello_all_questions.policies.splitwise.balance;

import com.conceptcoding.interviewquestions.hello_all_questions.policies.splitwise.model.Settlement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

// Who owes whom, for ONE scope (a group, or everything overall).
// Not thread-safe on its own: ExpenseManager guards every sheet with its lock.
public class BalanceSheet {

    // owes[creditor][debtor] = amount the debtor owes the creditor (rupees).
    // Invariant: owes[A][B] > 0 and owes[B][A] > 0 are NEVER both true (see addOwed).
    private final Map<String, Map<String, Long>> owes = new HashMap<>();

    // Record that debtor owes creditor `amount` more, cancelling any debt in the other direction first.
    //
    // Example: bob owes alice ₹50. Now alice owes bob ₹80 more → addOwed("bob", "alice", 80):
    //   opposing = 50 < 80 → wipe the ₹50, store only the net: alice owes bob ₹30.
    public void addOwed(String creditorId, String debtorId, long amount) {
        long opposing = get(debtorId, creditorId);
        if (opposing >= amount) {
            set(debtorId, creditorId, opposing - amount);       // absorbed by the existing opposite debt
        } else {
            set(debtorId, creditorId, 0);
            set(creditorId, debtorId, get(creditorId, debtorId) + (amount - opposing));
        }
    }

    // Bob pays Alice ₹100 back = the same as Alice now "owing" Bob ₹100, which cancels his debt.
    public void recordPayment(String fromUserId, String toUserId, long amount) {
        addOwed(fromUserId, toUserId, amount);
    }

    // positive → u1 is owed by u2; negative → u1 owes u2; 0 → settled
    public long getBalance(String u1, String u2) {
        return get(u1, u2) - get(u2, u1);
    }

    // every user's overall position in this sheet: + is owed money, − owes money. Sums to 0.
    public Map<String, Long> getNetBalances() {
        Map<String, Long> net = new HashMap<>();
        for (Map.Entry<String, Map<String, Long>> row : owes.entrySet()) {
            String creditor = row.getKey();
            for (Map.Entry<String, Long> cell : row.getValue().entrySet()) {
                net.merge(creditor, cell.getValue(), Long::sum);
                net.merge(cell.getKey(), -cell.getValue(), Long::sum);
            }
        }
        net.values().removeIf(v -> v == 0);
        return net;
    }

    // Greedy: match the biggest creditor with the biggest debtor until everyone is at 0.
    // At most N-1 payments for N people. (The true minimum is NP-hard; greedy is the standard answer.)
    //
    // Example: B paid for A ₹100, C paid for B ₹100, D paid for C ₹100
    //   net: A −100, B 0, C 0, D +100 → one payment: "A pays D ₹100" instead of three.
    public List<Settlement> simplify() {
        Map<String, Long> net = getNetBalances();
        PriorityQueue<String> creditors = new PriorityQueue<>((a, b) -> Long.compare(net.get(b), net.get(a)));
        PriorityQueue<String> debtors   = new PriorityQueue<>((a, b) -> Long.compare(net.get(a), net.get(b)));
        for (Map.Entry<String, Long> e : net.entrySet()) {
            if (e.getValue() > 0) creditors.offer(e.getKey());
            else                  debtors.offer(e.getKey());
        }

        List<Settlement> result = new ArrayList<>();
        while (!creditors.isEmpty() && !debtors.isEmpty()) {
            String c = creditors.poll();
            String d = debtors.poll();
            long settle = Math.min(net.get(c), -net.get(d));
            result.add(new Settlement(d, c, settle));

            net.put(c, net.get(c) - settle);
            net.put(d, net.get(d) + settle);
            if (net.get(c) > 0) creditors.offer(c);       // still owed: match again
            if (net.get(d) < 0) debtors.offer(d);         // still owes: match again
        }
        return result;
    }

    private long get(String creditor, String debtor) {
        return owes.getOrDefault(creditor, Map.of()).getOrDefault(debtor, 0L);
    }

    private void set(String creditor, String debtor, long amount) {
        owes.computeIfAbsent(creditor, k -> new HashMap<>()).put(debtor, amount);
    }
}
