package com.conceptcoding.interviewquestions.hello_all_questions.policies.splitwise;

import com.conceptcoding.interviewquestions.hello_all_questions.policies.splitwise.model.Expense;
import com.conceptcoding.interviewquestions.hello_all_questions.policies.splitwise.splittype.SplitStrategy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// The service the app calls: add an expense, settle up, see balances.
// Thread safety: every public method is synchronized: one expense changes several balances,
// and they must all change together.
public class ExpenseManager {

    // balances[a][b] > 0  →  b owes a that much.
    // balances[b][a] is ALWAYS the negative of balances[a][b], so debts in both directions cancel automatically.
    private final Map<String, Map<String, Long>> balances = new HashMap<>();
    private final List<Expense> expenses = new ArrayList<>();
    private int nextExpenseNumber = 1;

    // Example: alice pays ₹300, split equally with bob and carol → bob owes alice 100, carol owes alice 100.
    public synchronized Expense addExpense(String description, String paidBy, long amount, SplitStrategy split) {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
        Map<String, Long> shares = split.shares(amount);        // throws if the shares don't add up
        for (Map.Entry<String, Long> share : shares.entrySet()) {
            if (!share.getKey().equals(paidBy)) {               // you don't owe yourself
                addDebt(paidBy, share.getKey(), share.getValue());
            }
        }
        Expense expense = new Expense("EXP-" + nextExpenseNumber++, description, paidBy, amount, shares);
        expenses.add(expense);
        return expense;
    }

    // "Bob paid Alice ₹100 back": paying back is the reverse of owing.
    public synchronized void settleUp(String from, String to, long amount) {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
        if (from.equals(to)) throw new IllegalArgumentException("Can't pay yourself");
        addDebt(from, to, amount);
    }

    // positive → other owes user;  negative → user owes other;  0 → settled
    public synchronized long getBalance(String user, String other) {
        return get(user, other);
    }

    // everyone user has an open balance with: + they owe user, − user owes them
    public synchronized Map<String, Long> getBalances(String user) {
        Map<String, Long> result = new HashMap<>();
        Map<String, Long> row = balances.get(user);
        if (row == null) return result;
        for (Map.Entry<String, Long> e : row.entrySet()) {
            if (e.getValue() != 0) result.put(e.getKey(), e.getValue());
        }
        return result;
    }

    // debtor now owes creditor amount more: update BOTH directions so they always mirror each other
    private void addDebt(String creditor, String debtor, long amount) {
        put(creditor, debtor, get(creditor, debtor) + amount);
        put(debtor, creditor, get(debtor, creditor) - amount);
    }

    private long get(String a, String b) {
        Map<String, Long> row = balances.get(a);
        return row == null ? 0 : row.getOrDefault(b, 0L);
    }

    private void put(String a, String b, long value) {
        Map<String, Long> row = balances.get(a);
        if (row == null) {
            row = new HashMap<>();
            balances.put(a, row);
        }
        row.put(b, value);
    }
}
