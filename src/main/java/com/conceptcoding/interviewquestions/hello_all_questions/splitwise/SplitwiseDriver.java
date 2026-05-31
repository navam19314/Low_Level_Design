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

        scenarioEqualSplit(mgr);
        scenarioExactSplit(mgr);
        scenarioPercentSplit(mgr);
        scenarioSimplification();
        scenarioCircularDebt();
        scenarioValidation(mgr);
    }

    // ----- 1. EQUAL split with rounding remainder -----
    private static void scenarioEqualSplit(ExpenseManager mgr) {
        System.out.println("=== Scenario 1: Alice pays $30 dinner, equal split A/B/C ===");
        Map<String, Long> participants = new LinkedHashMap<>();
        participants.put("alice", 0L);
        participants.put("bob",   0L);
        participants.put("carol", 0L);
        Expense e = mgr.addExpense("alice", 3000L, SplitType.EQUAL, participants, "Dinner");
        printSplits(e.splits());
        System.out.printf("  balance alice↔bob:   %d (expect +1000, bob owes alice)%n", mgr.getBalance("alice", "bob"));
        System.out.printf("  balance alice↔carol: %d (expect +1000, carol owes alice)%n", mgr.getBalance("alice", "carol"));
        System.out.println();
    }

    // ----- 2. EXACT split: must sum to total -----
    private static void scenarioExactSplit(ExpenseManager mgr) {
        System.out.println("=== Scenario 2: Bob pays $24 groceries, exact split (12/8/4) ===");
        Map<String, Long> exact = new LinkedHashMap<>();
        exact.put("alice", 1200L);
        exact.put("bob",    800L);
        exact.put("carol",  400L);
        Expense e = mgr.addExpense("bob", 2400L, SplitType.EXACT, exact, "Groceries");
        printSplits(e.splits());
        System.out.printf("  balance alice↔bob:   %d (was +1000; now bob owed less by 1200 → -200)%n",
                mgr.getBalance("alice", "bob"));
        System.out.println();
    }

    // ----- 3. PERCENT split with basis points (no float drift) -----
    private static void scenarioPercentSplit(ExpenseManager mgr) {
        System.out.println("=== Scenario 3: Carol pays $10 snack; 33.33%/33.33%/33.34% ===");
        Map<String, Long> pct = new LinkedHashMap<>();
        pct.put("alice", 3333L);     // basis points: 33.33%
        pct.put("bob",   3333L);
        pct.put("carol", 3334L);     // last absorbs remainder
        Expense e = mgr.addExpense("carol", 1000L, SplitType.PERCENT, pct, "Snacks");
        printSplits(e.splits());
        long sum = e.splits().stream().mapToLong(Split::amountCents).sum();
        System.out.printf("  splits sum: %d (expect exactly 1000 — no float drift)%n", sum);
        System.out.println();
    }

    // ----- 4. Simplification — chain debt collapses -----
    private static void scenarioSimplification() {
        System.out.println("=== Scenario 4: chain A→B→C→D should collapse to A→D (3 → 1 transactions) ===");
        ExpenseManager m = new ExpenseManager();
        m.addUser(new User("A", "A", null));
        m.addUser(new User("B", "B", null));
        m.addUser(new User("C", "C", null));
        m.addUser(new User("D", "D", null));

        // Each link: payer fronts $10; sole participant is the next person → that person owes payer $10
        m.addExpense("B", 1000L, SplitType.EQUAL, Map.of("A", 0L), "B paid for A");   // A owes B 1000
        m.addExpense("C", 1000L, SplitType.EQUAL, Map.of("B", 0L), "C paid for B");   // B owes C 1000
        m.addExpense("D", 1000L, SplitType.EQUAL, Map.of("C", 0L), "D paid for C");   // C owes D 1000

        System.out.println("  Before simplification:");
        System.out.println("    A net:  " + m.getNetBalance("A") + "  (expect -1000)");
        System.out.println("    B net:  " + m.getNetBalance("B") + "  (expect    0 — owes 1000, owed 1000)");
        System.out.println("    C net:  " + m.getNetBalance("C") + "  (expect    0 — owes 1000, owed 1000)");
        System.out.println("    D net:  " + m.getNetBalance("D") + "  (expect +1000)");

        List<Settlement> settlements = m.simplifyDebts();
        System.out.println("  Simplified settlements (" + settlements.size() + " — expect 1):");
        for (Settlement s : settlements) {
            System.out.printf("    %s pays %s  %d%n", s.debtorId(), s.creditorId(), s.amountCents());
        }
        System.out.println();
    }

    // ----- 5. Circular debt: A→B 10, B→C 10, C→A 10 should fully cancel -----
    private static void scenarioCircularDebt() {
        System.out.println("=== Scenario 5: circular debt fully cancels (0 settlements) ===");
        ExpenseManager m = new ExpenseManager();
        m.addUser(new User("A", "A", null));
        m.addUser(new User("B", "B", null));
        m.addUser(new User("C", "C", null));
        m.addExpense("B", 1000L, SplitType.EQUAL, Map.of("A", 0L), "B paid for A");
        m.addExpense("C", 1000L, SplitType.EQUAL, Map.of("B", 0L), "C paid for B");
        m.addExpense("A", 1000L, SplitType.EQUAL, Map.of("C", 0L), "A paid for C");

        System.out.println("  Nets: A=" + m.getNetBalance("A") + ", B=" + m.getNetBalance("B")
                + ", C=" + m.getNetBalance("C") + "  (all expect 0)");
        List<Settlement> settlements = m.simplifyDebts();
        System.out.println("  Simplified settlements: " + settlements.size() + "  (expect 0)");
        System.out.println();
    }

    // ----- 6. Validation — exact shares that don't sum + unknown user -----
    private static void scenarioValidation(ExpenseManager mgr) {
        System.out.println("=== Scenario 6: validation ===");
        try {
            mgr.addExpense("alice", 1000L, SplitType.EXACT,
                    Map.of("alice", 600L, "bob", 300L), "bad exact sum");
        } catch (IllegalArgumentException ex) {
            System.out.println("  rejected (exact sum mismatch): " + ex.getMessage());
        }
        try {
            mgr.addExpense("ghost", 100L, SplitType.EQUAL, Map.of("alice", 0L), "unknown payer");
        } catch (IllegalArgumentException ex) {
            System.out.println("  rejected (unknown payer): " + ex.getMessage());
        }
        try {
            mgr.addExpense("alice", 1000L, SplitType.PERCENT,
                    Map.of("alice", 4000L, "bob", 5000L), "percent != 100%");
        } catch (IllegalArgumentException ex) {
            System.out.println("  rejected (bps sum != 10000): " + ex.getMessage());
        }
    }

    private static void printSplits(List<Split> splits) {
        for (Split s : splits) {
            System.out.printf("    %s owes %d cents%n", s.userId(), s.amountCents());
        }
    }
}
