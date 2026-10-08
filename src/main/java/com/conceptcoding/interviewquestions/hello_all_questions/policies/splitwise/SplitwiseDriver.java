package com.conceptcoding.interviewquestions.hello_all_questions.policies.splitwise;

import com.conceptcoding.interviewquestions.hello_all_questions.policies.splitwise.model.Expense;
import com.conceptcoding.interviewquestions.hello_all_questions.policies.splitwise.model.Settlement;
import com.conceptcoding.interviewquestions.hello_all_questions.policies.splitwise.model.Split;
import com.conceptcoding.interviewquestions.hello_all_questions.policies.splitwise.model.User;
import com.conceptcoding.interviewquestions.hello_all_questions.policies.splitwise.splittype.SplitType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class SplitwiseDriver {

    public static void main(String[] args) throws Exception {
        ExpenseManager mgr = newManager();
        mgr.createGroup("goa", "Goa trip", List.of("alice", "bob", "carol"));
        mgr.createGroup("flat", "Flat 4B", List.of("alice", "dave"));

        System.out.println("=== EQUAL in 'goa': Alice pays ₹300 for A/B/C ===");
        Expense e1 = mgr.addGroupExpense("goa", "alice", 300, SplitType.EQUAL, of("alice", 0, "bob", 0, "carol", 0), "Dinner");
        print(e1);
        System.out.println("  goa alice↔bob = " + mgr.getGroupBalance("goa", "alice", "bob") + " (expect +100)");

        System.out.println("\n=== EQUAL with a remainder: ₹100 among 3 ===");
        print(mgr.addGroupExpense("goa", "carol", 100, SplitType.EQUAL, of("alice", 0, "bob", 0, "carol", 0), "Chai"));

        System.out.println("\n=== EXACT in 'goa': Bob pays ₹240 as 120/80/40 ===");
        print(mgr.addGroupExpense("goa", "bob", 240, SplitType.EXACT, of("alice", 120, "bob", 80, "carol", 40), "Groceries"));

        System.out.println("\n=== PERCENT in 'flat': Dave pays the ₹1000 electricity bill, 60% / 40% ===");
        print(mgr.addGroupExpense("flat", "dave", 1000, SplitType.PERCENT, of("alice", 6000, "dave", 4000), "Electricity"));

        System.out.println("\n=== Group vs overall balances for Alice ===");
        System.out.println("  goa nets  " + mgr.getGroupNetBalances("goa"));
        System.out.println("  flat nets " + mgr.getGroupNetBalances("flat"));
        System.out.println("  alice overall net = " + mgr.getNetBalance("alice") + " (goa + flat combined)");

        System.out.println("\n=== Simplify 'goa' ===");
        List<Settlement> plan = mgr.simplifyGroupDebts("goa");
        for (Settlement s : plan) System.out.println("  " + s.getDebtorId() + " pays " + s.getCreditorId() + " ₹" + s.getAmount());

        System.out.println("\n=== Everyone pays as suggested → goa is settled ===");
        for (Settlement s : plan) mgr.recordPayment("goa", s.getDebtorId(), s.getCreditorId(), s.getAmount());
        System.out.println("  goa nets after payments " + mgr.getGroupNetBalances("goa") + " (expect {})");

        System.out.println("\n=== Chain collapses: B→A, C→B, D→C ₹100 each = one payment ===");
        ExpenseManager chain = newManager();
        chain.addExpense("bob", 100, SplitType.EXACT, of("alice", 100), "x");
        chain.addExpense("carol", 100, SplitType.EXACT, of("bob", 100), "y");
        chain.addExpense("dave", 100, SplitType.EXACT, of("carol", 100), "z");
        for (Settlement s : chain.simplifyDebts()) System.out.println("  " + s.getDebtorId() + " pays " + s.getCreditorId() + " ₹" + s.getAmount());

        System.out.println("\n=== Validation ===");
        tryIt("amount 0", () -> mgr.addExpense("alice", 0, SplitType.EQUAL, of("bob", 0), "x"));
        tryIt("no participants", () -> mgr.addExpense("alice", 100, SplitType.EQUAL, new LinkedHashMap<>(), "x"));
        tryIt("unknown user", () -> mgr.addExpense("alice", 100, SplitType.EQUAL, of("zed", 0), "x"));
        tryIt("dave is not in goa", () -> mgr.addGroupExpense("goa", "alice", 100, SplitType.EQUAL, of("dave", 0), "x"));
        tryIt("EXACT sums to 90, not 100", () -> mgr.addExpense("alice", 100, SplitType.EXACT, of("bob", 50, "carol", 40), "x"));
        tryIt("EXACT negative share", () -> mgr.addExpense("alice", 1000, SplitType.EXACT, of("bob", 1500, "carol", -500), "x"));
        tryIt("PERCENT sums to 90%", () -> mgr.addExpense("alice", 100, SplitType.PERCENT, of("bob", 5000, "carol", 4000), "x"));
        tryIt("pay yourself", () -> mgr.recordPayment(null, "bob", "bob", 10));

        concurrent();
    }

    // 100 threads each add a ₹10 expense (alice pays, split with bob) at once: bob must owe exactly ₹500.
    private static void concurrent() throws Exception {
        System.out.println("\n=== Concurrency: 100 simultaneous ₹10 expenses ===");
        ExpenseManager mgr = newManager();
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < 100; i++) {
            pool.submit(() -> {
                start.await();
                mgr.addExpense("alice", 10, SplitType.EQUAL, of("alice", 0, "bob", 0), "coffee");
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println("  alice↔bob = " + mgr.getBalance("alice", "bob") + " (expect 500)");
    }

    private static ExpenseManager newManager() {
        ExpenseManager mgr = new ExpenseManager();
        for (String u : List.of("alice", "bob", "carol", "dave")) mgr.addUser(new User(u, u, u + "@x.com"));
        return mgr;
    }

    // insertion-ordered map: of("alice", 100, "bob", 50)
    private static Map<String, Long> of(Object... kv) {
        Map<String, Long> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], ((Number) kv[i + 1]).longValue());
        return m;
    }

    private static void print(Expense e) {
        StringBuilder sb = new StringBuilder("  " + e.getDescription() + " ₹" + e.getTotalAmount() + " →");
        for (Split s : e.getSplits()) sb.append(" ").append(s.getUserId()).append("=").append(s.getAmount());
        System.out.println(sb);
    }

    private static void tryIt(String label, Runnable action) {
        try {
            action.run();
            System.out.println("  " + label + ": ALLOWED ✗");
        } catch (RuntimeException e) {
            System.out.println("  " + label + ": rejected → " + e.getMessage());
        }
    }
}
