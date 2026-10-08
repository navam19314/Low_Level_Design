package com.conceptcoding.interviewquestions.hello_all_questions.policies.splitwise;

import com.conceptcoding.interviewquestions.hello_all_questions.policies.splitwise.balance.BalanceSheet;
import com.conceptcoding.interviewquestions.hello_all_questions.policies.splitwise.model.Expense;
import com.conceptcoding.interviewquestions.hello_all_questions.policies.splitwise.model.Group;
import com.conceptcoding.interviewquestions.hello_all_questions.policies.splitwise.model.Settlement;
import com.conceptcoding.interviewquestions.hello_all_questions.policies.splitwise.model.Split;
import com.conceptcoding.interviewquestions.hello_all_questions.policies.splitwise.model.User;
import com.conceptcoding.interviewquestions.hello_all_questions.policies.splitwise.splittype.EqualSplitStrategy;
import com.conceptcoding.interviewquestions.hello_all_questions.policies.splitwise.splittype.ExactSplitStrategy;
import com.conceptcoding.interviewquestions.hello_all_questions.policies.splitwise.splittype.PercentSplitStrategy;
import com.conceptcoding.interviewquestions.hello_all_questions.policies.splitwise.splittype.SplitStrategy;
import com.conceptcoding.interviewquestions.hello_all_questions.policies.splitwise.splittype.SplitType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

// The service the app calls. Validates every input, delegates the split math to a
// Strategy, and keeps TWO views of balances up to date:
//   - one BalanceSheet per group  ("settle up the Goa trip")
//   - one overall BalanceSheet    ("what do I owe Bob across everything?")
// synchronized: every expense touches several balances, which must change together.
public class ExpenseManager {

    private final Map<String, User> users = new HashMap<>();
    private final Map<String, Group> groups = new HashMap<>();
    private final Map<String, BalanceSheet> groupSheets = new HashMap<>();   // groupId → sheet
    private final BalanceSheet overall = new BalanceSheet();
    private final List<Expense> expenses = new ArrayList<>();
    private final Map<SplitType, SplitStrategy> strategies = Map.of(
            SplitType.EQUAL,   new EqualSplitStrategy(),
            SplitType.EXACT,   new ExactSplitStrategy(),
            SplitType.PERCENT, new PercentSplitStrategy());
    private int expenseCounter = 0;

    public synchronized void addUser(User user) {
        users.put(user.getId(), user);
    }

    public synchronized Group createGroup(String groupId, String name, List<String> memberIds) {
        if (groups.containsKey(groupId)) throw new IllegalArgumentException("Group exists: " + groupId);
        Group group = new Group(groupId, name);
        for (String uid : memberIds) {
            requireUser(uid);
            group.addMember(uid);
        }
        groups.put(groupId, group);
        groupSheets.put(groupId, new BalanceSheet());
        return group;
    }

    public synchronized void addMember(String groupId, String userId) {
        requireUser(userId);
        requireGroup(groupId).addMember(userId);
    }

    // Personal expense between friends, outside any group.
    public synchronized Expense addExpense(String paidById, long totalAmount, SplitType splitType,
                                           Map<String, Long> participantInputs, String description) {
        return addGroupExpense(null, paidById, totalAmount, splitType, participantInputs, description);
    }

    // Example: Alice pays ₹300 in "goa", EQUAL among alice, bob, carol
    //   → splits 100/100/100 → bob owes alice 100, carol owes alice 100 (alice's own share is skipped)
    //   → recorded in the goa sheet AND the overall sheet
    public synchronized Expense addGroupExpense(String groupId, String paidById, long totalAmount,
                                                SplitType splitType, Map<String, Long> participantInputs,
                                                String description) {
        // 1. validate everything BEFORE changing anything
        if (totalAmount <= 0) throw new IllegalArgumentException("Amount must be > 0");
        if (participantInputs == null || participantInputs.isEmpty()) {
            throw new IllegalArgumentException("An expense needs at least one participant");
        }
        requireUser(paidById);
        for (String uid : participantInputs.keySet()) requireUser(uid);
        Group group = groupId == null ? null : requireGroup(groupId);
        if (group != null) {
            if (!group.isMember(paidById)) throw new IllegalArgumentException(paidById + " is not in " + groupId);
            for (String uid : participantInputs.keySet()) {
                if (!group.isMember(uid)) throw new IllegalArgumentException(uid + " is not in " + groupId);
            }
        }
        // 2. the strategy computes shares and validates its own rules (EXACT sums, PERCENT = 100%)
        List<Split> splits = strategies.get(splitType).calculate(totalAmount, participantInputs);

        // 3. record
        Expense expense = new Expense("EXP-" + (++expenseCounter), groupId, paidById, totalAmount,
                                      splits, description, LocalDateTime.now());
        expenses.add(expense);
        for (Split s : splits) {
            if (s.getUserId().equals(paidById)) continue;              // you can't owe yourself
            overall.addOwed(paidById, s.getUserId(), s.getAmount());
            if (group != null) groupSheets.get(groupId).addOwed(paidById, s.getUserId(), s.getAmount());
        }
        return expense;
    }

    // "Bob paid Alice ₹100 back" (in a group, or personal when groupId is null)
    public synchronized void recordPayment(String groupId, String fromUserId, String toUserId, long amount) {
        if (amount <= 0) throw new IllegalArgumentException("Payment must be > 0");
        if (fromUserId.equals(toUserId)) throw new IllegalArgumentException("Can't pay yourself");
        requireUser(fromUserId);
        requireUser(toUserId);
        if (groupId != null) groupSheet(groupId).recordPayment(fromUserId, toUserId, amount);
        overall.recordPayment(fromUserId, toUserId, amount);
    }

    public synchronized long getBalance(String u1, String u2)        { return overall.getBalance(u1, u2); }
    public synchronized long getGroupBalance(String groupId, String u1, String u2) {
        return groupSheet(groupId).getBalance(u1, u2);
    }
    public synchronized long getNetBalance(String userId) {
        return overall.getNetBalances().getOrDefault(userId, 0L);
    }
    public synchronized Map<String, Long> getGroupNetBalances(String groupId) {
        return groupSheet(groupId).getNetBalances();
    }
    public synchronized List<Settlement> simplifyDebts()                { return overall.simplify(); }
    public synchronized List<Settlement> simplifyGroupDebts(String groupId) { return groupSheet(groupId).simplify(); }

    public synchronized List<Expense> getGroupExpenses(String groupId) {
        List<Expense> result = new ArrayList<>();
        for (Expense e : expenses) if (groupId.equals(e.getGroupId())) result.add(e);
        return result;
    }

    private BalanceSheet groupSheet(String groupId) {
        requireGroup(groupId);
        return groupSheets.get(groupId);
    }

    private User requireUser(String userId) {
        User u = users.get(userId);
        if (u == null) throw new NoSuchElementException("Unknown user: " + userId);
        return u;
    }

    private Group requireGroup(String groupId) {
        Group g = groups.get(groupId);
        if (g == null) throw new NoSuchElementException("Unknown group: " + groupId);
        return g;
    }
}
