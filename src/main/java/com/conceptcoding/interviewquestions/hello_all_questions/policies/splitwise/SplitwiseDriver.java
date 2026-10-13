package com.conceptcoding.interviewquestions.hello_all_questions.policies.splitwise;

import com.conceptcoding.interviewquestions.hello_all_questions.policies.splitwise.splittype.EqualSplit;
import com.conceptcoding.interviewquestions.hello_all_questions.policies.splitwise.splittype.ExactSplit;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class SplitwiseDriver {

    public static void main(String[] args) throws Exception {
        ExpenseManager app = new ExpenseManager();

        System.out.println("=== Alice pays ₹300 dinner, equal among alice, bob, carol ===");
        app.addExpense("Dinner", "alice", 300, new EqualSplit(List.of("alice", "bob", "carol")));
        System.out.println("  bob owes alice " + app.getBalance("alice", "bob") + ", carol owes alice " + app.getBalance("alice", "carol") + " (expect 100, 100)");
        System.out.println("  from bob's side: " + app.getBalance("bob", "alice") + " (expect -100: bob owes)");

        System.out.println("\n=== Bob pays ₹120 cab, equal with alice → debts in both directions cancel ===");
        app.addExpense("Cab", "bob", 120, new EqualSplit(List.of("alice", "bob")));
        System.out.println("  bob owes alice " + app.getBalance("alice", "bob") + " (expect 100 - 60 = 40)");

        System.out.println("\n=== Carol pays ₹1000, exact: alice 600, carol 400 ===");
        app.addExpense("Shopping", "carol", 1000, new ExactSplit(Map.of("alice", 600L, "carol", 400L)));
        System.out.println("  alice owes carol " + app.getBalance("carol", "alice") + " (expect 600 - 100 = 500)");
        System.out.println("  alice's balances: " + app.getBalances("alice") + " (expect bob=40, carol=-500)");

        System.out.println("\n=== Leftover rupees: ₹100 among 3 ===");
        System.out.println("  " + new EqualSplit(List.of("a", "b", "c")).shares(100) + " (expect a=33, b=33, c=34)");

        System.out.println("\n=== Settle up: bob pays alice ₹40 ===");
        app.settleUp("bob", "alice", 40);
        System.out.println("  balance " + app.getBalance("alice", "bob") + " (expect 0)");

        System.out.println("\n=== Rejections ===");
        tryIt("exact shares don't add up", () -> app.addExpense("X", "alice", 1000, new ExactSplit(Map.of("bob", 600L, "carol", 300L))));
        tryIt("negative exact share", () -> app.addExpense("X", "alice", 1000, new ExactSplit(Map.of("bob", 1500L, "carol", -500L))));
        tryIt("amount 0", () -> app.addExpense("X", "alice", 0, new EqualSplit(List.of("alice", "bob"))));
        tryIt("same person twice", () -> new EqualSplit(List.of("bob", "bob")));
        tryIt("nobody to split with", () -> new EqualSplit(List.of()));
        tryIt("pay yourself", () -> app.settleUp("bob", "bob", 10));
        System.out.println("  alice's balances unchanged: " + app.getBalances("alice") + " (expect carol=-500)");

        concurrentExpenses();
    }

    // 100 threads each add "alice pays ₹10, equal with bob" at once: bob must owe exactly ₹500.
    private static void concurrentExpenses() throws Exception {
        System.out.println("\n=== Concurrency: 100 expenses at once ===");
        ExpenseManager app = new ExpenseManager();
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch go = new CountDownLatch(1);
        for (int i = 0; i < 100; i++) {
            pool.submit(() -> {
                go.await();
                app.addExpense("Chai", "alice", 10, new EqualSplit(List.of("alice", "bob")));
                return null;
            });
        }
        go.countDown();
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println("  bob owes alice " + app.getBalance("alice", "bob") + " (expect 500), mirror " + app.getBalance("bob", "alice") + " (expect -500)");
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
