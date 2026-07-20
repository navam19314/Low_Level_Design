package com.conceptcoding.interviewquestions.hello_all_questions.splitwise;

import com.conceptcoding.interviewquestions.hello_all_questions.splitwise.model.Expense;
import com.conceptcoding.interviewquestions.hello_all_questions.splitwise.model.Settlement;
import com.conceptcoding.interviewquestions.hello_all_questions.splitwise.model.Split;
import com.conceptcoding.interviewquestions.hello_all_questions.splitwise.model.User;
import com.conceptcoding.interviewquestions.hello_all_questions.splitwise.splittype.SplitType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SplitwiseDriver {

    public static void main(String[] args) {
        ExpenseManager mgr = new ExpenseManager();
        mgr.addUser(new User("alice", "Alice", "a@x.com"));
        mgr.addUser(new User("bob",   "Bob",   "b@x.com"));
        mgr.addUser(new User("carol", "Carol", "c@x.com"));
        mgr.addUser(new User("dave",  "Dave",  "d@x.com"));

        equalSplit(mgr);
        exactSplit(mgr);
        percentSplit(mgr);
        chainDebtCollapses();
        circularDebtCancels();
        validationRejected(mgr);
    }

    // Alice pays ₹300, split equally among A/B/C — each owes ₹100
    private static void equalSplit(ExpenseManager mgr) {
        System.out.println("=== EQUAL: Alice pays ₹300, split A/B/C ===");
        Map<String, Long> p = new LinkedHashMap<>();
        p.put("alice", 0L); p.put("bob", 0L); p.put("carol", 0L);
        Expense e = mgr.addExpense("alice", 300L, SplitType.EQUAL, p, "Dinner");
        printSplits(e.getSplits());
        System.out.println("alice↔bob:   " + mgr.getBalance("alice", "bob")   + "  (expect +100)");
        System.out.println("alice↔carol: " + mgr.getBalance("alice", "carol") + "  (expect +100)");
        System.out.println();
    }

    // Bob pays ₹240; exact amounts ₹120/₹80/₹40
    private static void exactSplit(ExpenseManager mgr) {
        System.out.println("=== EXACT: Bob pays ₹240, split ₹120/₹80/₹40 ===");
        Map<String, Long> p = new LinkedHashMap<>();
        p.put("alice", 120L); p.put("bob", 80L); p.put("carol", 40L);
        Expense e = mgr.addExpense("bob", 240L, SplitType.EXACT, p, "Groceries");
        printSplits(e.getSplits());
        System.out.println("alice↔bob: " + mgr.getBalance("alice", "bob") + "  (100-120 → -20, bob now owed)");
        System.out.println();
    }

    // Carol pays ₹100; 33.33%/33.33%/33.34% in basis points — no float drift
    private static void percentSplit(ExpenseManager mgr) {
        System.out.println("=== PERCENT: Carol pays ₹100, 3333/3333/3334 bps ===");
        Map<String, Long> p = new LinkedHashMap<>();
        p.put("alice", 3333L); p.put("bob", 3333L); p.put("carol", 3334L);
        Expense e = mgr.addExpense("carol", 100L, SplitType.PERCENT, p, "Snacks");
        printSplits(e.getSplits());
        long sum = e.getSplits().stream().mapToLong(Split::getAmount).sum();
        System.out.println("splits sum: " + sum + "  (expect exactly 100 — no float drift)");
        System.out.println();
    }

    // Chain A←B←C←D should collapse to 1 settlement: A pays D
    private static void chainDebtCollapses() {
        System.out.println("=== Simplification: chain A←B←C←D → 1 settlement ===");
        ExpenseManager m = new ExpenseManager();
        m.addUser(new User("A", "A", null));
        m.addUser(new User("B", "B", null));
        m.addUser(new User("C", "C", null));
        m.addUser(new User("D", "D", null));

        m.addExpense("B", 100L, SplitType.EQUAL, Map.of("A", 0L), "B paid for A");
        m.addExpense("C", 100L, SplitType.EQUAL, Map.of("B", 0L), "C paid for B");
        m.addExpense("D", 100L, SplitType.EQUAL, Map.of("C", 0L), "D paid for C");

        System.out.println("net A=" + m.getNetBalance("A") + "  B=" + m.getNetBalance("B")
                + "  C=" + m.getNetBalance("C") + "  D=" + m.getNetBalance("D"));

        List<Settlement> s = m.simplifyDebts();
        System.out.println("Settlements: " + s.size() + "  (expect 1)");
        for (Settlement st : s)
            System.out.println("  " + st.getDebtorId() + " pays " + st.getCreditorId()
                    + "  ₹" + st.getAmount());
        System.out.println();
    }

    // Circular A→B→C→A — all nets are 0, expect 0 settlements
    private static void circularDebtCancels() {
        System.out.println("=== Simplification: circular A→B→C→A → 0 settlements ===");
        ExpenseManager m = new ExpenseManager();
        m.addUser(new User("A", "A", null));
        m.addUser(new User("B", "B", null));
        m.addUser(new User("C", "C", null));

        m.addExpense("B", 100L, SplitType.EQUAL, Map.of("A", 0L), "B paid for A");
        m.addExpense("C", 100L, SplitType.EQUAL, Map.of("B", 0L), "C paid for B");
        m.addExpense("A", 100L, SplitType.EQUAL, Map.of("C", 0L), "A paid for C");

        System.out.println("net A=" + m.getNetBalance("A") + "  B=" + m.getNetBalance("B")
                + "  C=" + m.getNetBalance("C") + "  (all expect 0)");
        System.out.println("Settlements: " + m.simplifyDebts().size() + "  (expect 0)");
        System.out.println();
    }

    // Validation: bad inputs must be rejected before any mutation
    private static void validationRejected(ExpenseManager mgr) {
        System.out.println("=== Validation ===");
        try {
            mgr.addExpense("alice", 100L, SplitType.EXACT,
                    Map.of("alice", 60L, "bob", 30L), "bad sum");
        } catch (IllegalArgumentException e) {
            System.out.println("EXACT sum mismatch: " + e.getMessage());
        }
        try {
            mgr.addExpense("ghost", 100L, SplitType.EQUAL, Map.of("alice", 0L), "unknown payer");
        } catch (IllegalArgumentException e) {
            System.out.println("Unknown payer: " + e.getMessage());
        }
        try {
            mgr.addExpense("alice", 100L, SplitType.PERCENT,
                    Map.of("alice", 4000L, "bob", 5000L), "bps != 10000");
        } catch (IllegalArgumentException e) {
            System.out.println("PERCENT bps mismatch: " + e.getMessage());
        }
    }

    private static void printSplits(List<Split> splits) {
        for (Split s : splits)
            System.out.println("  " + s.getUserId() + " owes ₹" + s.getAmount());
    }
}
