# Splitwise

> **Why it's asked:** users, groups, three split types and debt simplification. Interviewers use it to see **how you model and validate data**: money that always adds up, inputs that can't be nonsense, and balances that never contradict themselves.
>
> **The crux (what's really being tested):**
> 1. **Split types as Strategy:** EQUAL / EXACT / PERCENT, each validating its own input and always summing to the exact total.
> 2. **The balance graph:** one number per pair of people, never "A owes B" *and* "B owes A" at once.
> 3. **Simplify debts:** net balances + greedy matching of the biggest creditor with the biggest debtor.
>
> **Family:** F5 Swappable policy + careful data modelling. See [foundations](../../00_AMAZON_LLD_FOUNDATIONS.md).

---

## 1. Plain-language picture

### A Goa trip
```
Alice pays ₹300 for dinner, split equally among Alice, Bob, Carol → each share ₹100
   Bob owes Alice ₹100 · Carol owes Alice ₹100 · (Alice doesn't owe herself)
Bob pays ₹240 for groceries: Alice ₹120, Bob ₹80, Carol ₹40 (EXACT)
   Alice owes Bob ₹120 — but Bob already owes Alice ₹100 → net: Alice owes Bob ₹20
```
That last line is the key modelling idea: between two people there is **one** number. Store "Alice owes Bob ₹20", **not** "Bob owes Alice ₹100 and Alice owes Bob ₹120". Two numbers for one relationship is two sources of truth.

### Three ways to split
| Type | Input | ₹100 among A, B, C |
|---|---|---|
| EQUAL | just who's in | 33 / 33 / **34** (the last person absorbs the rounding) |
| EXACT | rupees each, must sum to the total | 50 / 30 / 20 |
| PERCENT | basis points (10000 = 100%), must sum to 10000 | 3333 / 3333 / 3334 bps → 33 / 33 / 34 |

**The golden rule:** shares **always** add up to the exact total, in whole rupees. No `double`, ever.

### Settling up: "who pays whom, in the fewest payments?"
```
B paid ₹100 for A, C paid ₹100 for B, D paid ₹100 for C
Naive:   A→B ₹100, B→C ₹100, C→D ₹100        (3 payments)
Net:     A −100, B 0, C 0, D +100
Smart:   A→D ₹100                             (1 payment)
```
Compute everyone's **net** (what they're owed minus what they owe). Then repeatedly match the person owed the most with the person who owes the most. That's two heaps.

### Groups
A group (the Goa trip, your flat) is a set of members with its **own** balance sheet, so you can settle up one trip without touching the others. There's also an **overall** sheet for "what do I owe Bob across everything?". Every expense updates both.

---

## 2. From story to design

| # | Decision | Rejected | Why |
|---|---|---|---|
| D1 | `SplitStrategy.calculate(total, inputs) → List<Split>`, one class per type | a `switch(splitType)` in the manager | Each type has its own math **and** its own validation. A new type (SHARES) is a new class. |
| D2 | Every strategy guarantees **splits sum exactly to total** (the last person absorbs the rounding) | letting rounding leak | ₹100 / 3 = 33.33… In integers, someone must take the extra rupee. Pick a rule and state it. |
| D3 | PERCENT in **basis points** (10000 = 100%) | `double` percentages | 33.33% is 3333 bps, which is exact. Doubles drift. |
| D4 | `BalanceSheet` with `owes[creditor][debtor]`, **netting** on every write (`addOwed`) | storing both directions | One number per pair. `getBalance` never has to "remember to net". |
| D5 | One sheet **per group** + one **overall** sheet | recomputing balances from all expenses on each query | O(1) balance lookups. Group settle-up is independent of other groups. |
| D6 | A payment is just a reverse debt: `recordPayment(from, to, x)` = `addOwed(from, to, x)` | a separate payments ledger affecting balances | Reuses the same netting logic, so the same invariant holds automatically. |
| D7 | **Validate everything before changing anything** | validating mid-way | A rejected expense must leave zero trace: no ledger entry, no balance change. |
| D8 | Group expenses: payer and all participants must be members | trusting the client | Garbage in a group's balances is very hard to clean up later. |
| D9 | `ExpenseManager` methods `synchronized` | per-user locks | One expense updates several balances in two sheets, and they must change together. Fine for one app instance. |
| D10 | Expenses are **immutable** ledger entries | editing in place | Edit = undo + re-add (Q5). The ledger stays an honest history. |

### Class shape
```
ExpenseManager                                    ← the service the app calls
  users, groups, expenses
  Map<groupId, BalanceSheet> groupSheets + BalanceSheet overall
  Map<SplitType, SplitStrategy> strategies
  createGroup · addGroupExpense · addExpense · recordPayment
  getBalance · getGroupBalance · getNetBalance · simplifyGroupDebts · simplifyDebts

BalanceSheet                                      ← the crux of the data model
  Map<creditor, Map<debtor, Long>> owes           (never both directions > 0)
  addOwed · recordPayment · getBalance · getNetBalances · simplify

«interface» SplitStrategy  calculate(total, inputs) → List<Split>
  ├── EqualSplitStrategy  ├── ExactSplitStrategy  └── PercentSplitStrategy
SplitType enum { EQUAL, EXACT, PERCENT }

User { id, name, email }   Group { id, name, memberIds }
Expense { id, groupId, paidBy, total, List<Split>, description }   (immutable)
Split { userId, amount }   Settlement { debtor, creditor, amount }
```

---

## 3. Patterns that earn their place

| Pattern | In the base? | Problem it solves | Without it |
|---|---|---|---|
| **Strategy** (`SplitStrategy`) | **Yes** | 3 split types on day 1, each with its own math and validation | one method with a `switch` and 3 validation blocks; SHARES or ADJUSTMENT types edit it again |
| **Observer** (activity feed) | No | "Alice added Dinner ₹300" notifications | Q8 |

`ExpenseManager` is the Facade (the service class). No need to name it.

**Say:** *"Strategy for split types, because each has different math and different validation. The rest of the design is about the data model: one balance per pair, integer money, validate before mutating."*

**Tempting but wrong here:**
- **Factory for strategies:** a `Map<SplitType, SplitStrategy>` already maps type → object in one line.
- **Observer for balances:** balances are updated directly in the same transaction. They aren't independent reactions.
- **A `Balance` class per pair:** a nested map already holds one number per pair. A class adds nothing.

---

## 4. The 45-minute run

### Clarify (min 0–5)
> **You:** Users create groups and add expenses. One person pays and it's split among some participants. Right?
> **Interviewer:** Yes.
> **You:** Split types: equal, exact amounts, percentage?
> **Interviewer:** Those three.
> **You:** Can an expense be outside any group, just between two friends?
> **Interviewer:** Yes.
> **You:** Balances per group, and overall per pair of users?
> **Interviewer:** Both.
> **You:** "Simplify debts": the fewest payments to settle a group?
> **Interviewer:** Yes. And recording a payment when someone pays back.
> **You:** Money in whole rupees? Who takes the extra rupee when it doesn't divide?
> **Interviewer:** Rupees. Your choice, just be consistent.
> **You:** Currencies, editing expenses, notifications: out of scope?
> **Interviewer:** For now.

```
In scope:  users, groups (members), expenses in or outside a group
           EQUAL / EXACT / PERCENT with validation; shares always sum to the total
           group + overall balances · record payment · simplify debts
Out:       currencies, edit/delete expense, notifications, persistence
```

### Timeline
| Min | Do |
|---|---|
| 5–9 | Class shape. Say the crux: *"Strategy per split type; one balance per pair; greedy simplify."* |
| 9–17 | Code step 1: `Split`, `SplitStrategy`, the 3 strategies with validation. |
| 17–27 | Code step 2: **`BalanceSheet`**: `addOwed` (netting), `getBalance`, `getNetBalances`, `simplify`. |
| 27–35 | Code step 3: `ExpenseManager.addGroupExpense` (validate → split → record), `recordPayment`, `createGroup`. Thin models. |
| 35–40 | Dry run: Goa trip, then simplify. |
| 40–45 | Follow-ups. |

### The code you write, in this order

**Step 1: split strategies** ([splittype/](splittype/))
```java
public enum SplitType { EQUAL, EXACT, PERCENT }

public interface SplitStrategy {
    // inputs: EQUAL → values ignored; EXACT → rupees each; PERCENT → basis points (10000 = 100%)
    // contract: returned splits sum EXACTLY to totalAmount
    List<Split> calculate(long totalAmount, Map<String, Long> participantInputs);
}

public class EqualSplitStrategy implements SplitStrategy {
    @Override
    public List<Split> calculate(long totalAmount, Map<String, Long> participantInputs) {
        List<String> users = new ArrayList<>(participantInputs.keySet());
        long share = totalAmount / users.size();
        long remainder = totalAmount - share * users.size();
        List<Split> splits = new ArrayList<>();
        for (int i = 0; i < users.size() - 1; i++) splits.add(new Split(users.get(i), share));
        splits.add(new Split(users.get(users.size() - 1), share + remainder));   // ₹100/3 → 33, 33, 34
        return splits;
    }
}

public class ExactSplitStrategy implements SplitStrategy {
    @Override
    public List<Split> calculate(long totalAmount, Map<String, Long> participantInputs) {
        long sum = 0;
        List<Split> splits = new ArrayList<>();
        for (Map.Entry<String, Long> e : participantInputs.entrySet()) {
            // {A: 1500, B: -500} sums to 1000 but is nonsense: reject negative shares
            if (e.getValue() < 0) throw new IllegalArgumentException("Negative share for " + e.getKey());
            splits.add(new Split(e.getKey(), e.getValue()));
            sum += e.getValue();
        }
        if (sum != totalAmount) {
            throw new IllegalArgumentException("EXACT shares sum to " + sum + ", expense total is " + totalAmount);
        }
        return splits;
    }
}

public class PercentSplitStrategy implements SplitStrategy {
    private static final long TOTAL_BPS = 10_000L;

    @Override
    public List<Split> calculate(long totalAmount, Map<String, Long> participantInputs) {
        long sumBps = 0;
        for (Map.Entry<String, Long> e : participantInputs.entrySet()) {
            if (e.getValue() < 0) throw new IllegalArgumentException("Negative percentage for " + e.getKey());
            sumBps += e.getValue();
        }
        if (sumBps != TOTAL_BPS) {
            throw new IllegalArgumentException("PERCENT shares sum to " + sumBps + " bps, must equal " + TOTAL_BPS);
        }
        List<String> users = new ArrayList<>(participantInputs.keySet());
        List<Split> splits = new ArrayList<>();
        long accumulated = 0;
        for (int i = 0; i < users.size() - 1; i++) {
            long amount = totalAmount * participantInputs.get(users.get(i)) / TOTAL_BPS;   // floors
            splits.add(new Split(users.get(i), amount));
            accumulated += amount;
        }
        splits.add(new Split(users.get(users.size() - 1), totalAmount - accumulated));   // absorbs the rounding
        return splits;
    }
}
```

**Step 2: the balance sheet, the crux** ([balance/BalanceSheet.java](balance/BalanceSheet.java))
```java
public class BalanceSheet {

    // owes[creditor][debtor] = what debtor owes creditor. Never both directions > 0.
    private final Map<String, Map<String, Long>> owes = new HashMap<>();

    // record "debtor owes creditor amount more", cancelling any opposite debt first
    public void addOwed(String creditorId, String debtorId, long amount) {
        long opposing = get(debtorId, creditorId);
        if (opposing >= amount) {
            set(debtorId, creditorId, opposing - amount);        // fully absorbed by the opposite debt
        } else {
            set(debtorId, creditorId, 0);
            set(creditorId, debtorId, get(creditorId, debtorId) + (amount - opposing));
        }
    }

    // Bob pays Alice back ₹100 = as if Alice now "owes" Bob ₹100, which cancels his debt
    public void recordPayment(String fromUserId, String toUserId, long amount) {
        addOwed(fromUserId, toUserId, amount);
    }

    public long getBalance(String u1, String u2) {            // + u1 is owed; − u1 owes
        return get(u1, u2) - get(u2, u1);
    }

    public Map<String, Long> getNetBalances() {               // + owed money, − owes money; sums to 0
        Map<String, Long> net = new HashMap<>();
        for (Map.Entry<String, Map<String, Long>> row : owes.entrySet()) {
            for (Map.Entry<String, Long> cell : row.getValue().entrySet()) {
                net.merge(row.getKey(), cell.getValue(), Long::sum);
                net.merge(cell.getKey(), -cell.getValue(), Long::sum);
            }
        }
        net.values().removeIf(v -> v == 0);
        return net;
    }

    // greedy: biggest creditor ↔ biggest debtor until all are 0 (at most N−1 payments)
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
            if (net.get(c) > 0) creditors.offer(c);           // still owed: match again
            if (net.get(d) < 0) debtors.offer(d);             // still owes: match again
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
```
**Shape to remember:** `addOwed` = look at the opposite direction first; absorb it, or zero it and store the remainder. `simplify` = nets → two heaps → match the tops → push back leftovers.

**Step 3: the manager** ([ExpenseManager.java](ExpenseManager.java))
```java
public class ExpenseManager {

    private final Map<String, User> users = new HashMap<>();
    private final Map<String, Group> groups = new HashMap<>();
    private final Map<String, BalanceSheet> groupSheets = new HashMap<>();
    private final BalanceSheet overall = new BalanceSheet();
    private final List<Expense> expenses = new ArrayList<>();
    private final Map<SplitType, SplitStrategy> strategies = Map.of(
            SplitType.EQUAL,   new EqualSplitStrategy(),
            SplitType.EXACT,   new ExactSplitStrategy(),
            SplitType.PERCENT, new PercentSplitStrategy());
    private int expenseCounter = 0;

    public synchronized Group createGroup(String groupId, String name, List<String> memberIds) {
        if (groups.containsKey(groupId)) throw new IllegalArgumentException("Group exists: " + groupId);
        Group group = new Group(groupId, name);
        for (String uid : memberIds) { requireUser(uid); group.addMember(uid); }
        groups.put(groupId, group);
        groupSheets.put(groupId, new BalanceSheet());
        return group;
    }

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
        // 2. the strategy computes shares and checks its own rules
        List<Split> splits = strategies.get(splitType).calculate(totalAmount, participantInputs);

        // 3. record in the ledger, the overall sheet and the group sheet
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

    public synchronized Expense addExpense(String paidById, long totalAmount, SplitType splitType,
                                           Map<String, Long> participantInputs, String description) {
        return addGroupExpense(null, paidById, totalAmount, splitType, participantInputs, description);
    }

    public synchronized void recordPayment(String groupId, String fromUserId, String toUserId, long amount) {
        if (amount <= 0) throw new IllegalArgumentException("Payment must be > 0");
        if (fromUserId.equals(toUserId)) throw new IllegalArgumentException("Can't pay yourself");
        requireUser(fromUserId);
        requireUser(toUserId);
        if (groupId != null) groupSheet(groupId).recordPayment(fromUserId, toUserId, amount);
        overall.recordPayment(fromUserId, toUserId, amount);
    }

    public synchronized List<Settlement> simplifyGroupDebts(String groupId) { return groupSheet(groupId).simplify(); }
    // + addUser, addMember, getBalance, getGroupBalance, getNetBalance, getGroupNetBalances, simplifyDebts,
    //   requireUser / requireGroup (throw NoSuchElementException)
}
// Group { id, name, Set<memberIds>, isMember }   Expense (immutable, List.copyOf(splits))
// Split { userId, amount }   Settlement { debtorId, creditorId, amount }   User { id, name, email }
```

### Dry run (the Goa group)
```
Dinner:    Alice pays 300 EQUAL A/B/C  → addOwed(A,B,100), addOwed(A,C,100)
Chai:      Carol pays 100 EQUAL        → 33/33/34 → addOwed(C,A,33), addOwed(C,B,33)
             C→A: A already is owed 100 by C → opposing 100 ≥ 33 → owes[A][C] = 67
             C→B: no opposite → owes[C][B] = 33
Groceries: Bob pays 240 EXACT 120/80/40 → addOwed(B,A,120), addOwed(B,C,40)
             B→A: opposing owes[A][B] = 100 < 120 → owes[A][B] = 0, owes[B][A] = 20
             B→C: opposing owes[C][B] = 33 < 40  → owes[C][B] = 0, owes[B][C] = 7

nets:      Alice +47 (owed 67 by C, owes 20 to B) · Bob +27 (owed 20 + 7) · Carol −74   → sum 0 ✓
simplify:  Carol pays Alice ₹47, Carol pays Bob ₹27   (2 payments)
```

---

## 5. Follow-ups: answer out loud first, then open

<details>
<summary><b>Q1. Why net the two directions on every write? Why not store everything and compute later?</b></summary>

Storing "A owes B 100" and "B owes A 120" means **every** reader must remember to subtract. One missed subtraction and a screen shows the wrong balance. Netting on write keeps the invariant *"at most one direction is non-zero"*, so `getBalance` is a single subtraction and is always correct. It's the same principle as one source of truth: one fact, one number.
</details>

<details>
<summary><b>Q2. Is greedy simplification optimal?</b></summary>

It always settles everyone with **at most N−1** payments, but not always the true minimum. Finding the minimum number of payments is NP-hard: it means finding subsets of people whose nets sum to zero, which is subset-sum. Example: nets +5, +5, −5, −5 is settled by greedy in 2 payments, which is optimal. With some mixes, a clever grouping saves one payment that greedy misses. **Say:** *"Greedy is O(N log N) and good enough; the exact minimum is exponential and not worth it for a 10-person group."*
</details>

<details>
<summary><b>Q3. Add a SHARES split: "2 shares for the couple, 1 for each single".</b></summary>

A new strategy class + one enum value + one map entry. Nothing else changes:
```java
// ₹1200 with {A: 2, B: 1, C: 1} → 600 / 300 / 300
public class SharesSplitStrategy implements SplitStrategy {
    @Override
    public List<Split> calculate(long totalAmount, Map<String, Long> participantInputs) {
        long totalShares = 0;
        for (Map.Entry<String, Long> e : participantInputs.entrySet()) {
            if (e.getValue() <= 0) throw new IllegalArgumentException("Shares must be > 0 for " + e.getKey());
            totalShares += e.getValue();
        }
        List<String> users = new ArrayList<>(participantInputs.keySet());
        List<Split> splits = new ArrayList<>();
        long assigned = 0;
        for (int i = 0; i < users.size() - 1; i++) {
            long amount = totalAmount * participantInputs.get(users.get(i)) / totalShares;
            splits.add(new Split(users.get(i), amount));
            assigned += amount;
        }
        splits.add(new Split(users.get(users.size() - 1), totalAmount - assigned));   // last absorbs rounding
        return splits;
    }
}
// SplitType.SHARES + strategies.put(SplitType.SHARES, new SharesSplitStrategy())
```
</details>

<details>
<summary><b>Q4. Thousands of groups: one lock for everything is a bottleneck.</b></summary>

Lock **per group**: each group's expenses only touch that group's sheet. But the **overall** sheet is shared across groups, so either:
- make the overall sheet **derived**: compute "Alice ↔ Bob overall" by summing that pair across their groups (+ the personal sheet) on read. Then writes only lock one group; or
- lock the group sheet, then the overall sheet, **always in that order** (so no deadlock), holding the overall lock only for the few `addOwed` calls.

In a DB: one transaction per expense, updating the `balance` rows for (group, creditor, debtor) with row locks.
</details>

<details>
<summary><b>Q5. Edit or delete an expense.</b></summary>

Expenses are immutable, so **delete = apply the reverse**, and **edit = delete + add the new version**. Reversing reuses `addOwed` in the opposite direction, which cancels exactly:
```java
// every "debtor owes payer" becomes "payer owes debtor", which cancels it
private void undo(BalanceSheet sheet, Expense expense) {
    for (Split s : expense.getSplits()) {
        if (s.getUserId().equals(expense.getPaidById())) continue;
        sheet.addOwed(s.getUserId(), expense.getPaidById(), s.getAmount());
    }
}

public synchronized void deleteExpense(String expenseId) {
    Expense e = findExpense(expenseId);
    undo(overall, e);
    if (e.getGroupId() != null) undo(groupSheets.get(e.getGroupId()), e);
    expenses.remove(e);                    // or mark it deleted, to keep the history
}
```
Keep a `deleted` flag rather than removing, so the activity feed can show "Alice deleted Dinner".
</details>

<details>
<summary><b>Q6. Remove a member from a group.</b></summary>

Allowed only if their **group net is 0** (`getGroupNetBalances(groupId).getOrDefault(user, 0L) == 0`); otherwise they'd walk away owing money, or being owed it. Otherwise ask them to settle first. Past expenses keep their id, since the ledger is history.
</details>

<details>
<summary><b>Q7. Multiple currencies (the Goa trip in ₹, the Bangkok trip in ฿).</b></summary>

Store a currency on each expense and keep balances **per currency** (`Map<Currency, BalanceSheet>` per group). Never convert silently: rates change daily. At settle-up time, either settle each currency separately, or convert at **today's** rate with the user's consent, recorded as an explicit conversion entry.
</details>

<details>
<summary><b>Q8. Activity feed: "Alice added Dinner ₹300", "Bob paid you ₹100".</b></summary>

**Observer**: `ExpenseListener.onExpenseAdded(expense)` / `onPayment(...)`. The feed, push notifications and email digests subscribe. Notify **after** the balances are updated, with each listener in a try/catch. In production, publish to a queue.
</details>

<details>
<summary><b>Q9. How would you store this in a database?</b></summary>

```sql
expense(id, group_id NULL, paid_by, total, split_type, description, created_at, deleted)
expense_split(expense_id, user_id, amount)                  -- the computed shares
balance(group_id, creditor_id, debtor_id, amount,           -- materialised, one row per pair
        PRIMARY KEY (group_id, creditor_id, debtor_id))
payment(id, group_id NULL, from_user, to_user, amount, created_at)
```
`addExpense` = one transaction: insert the expense + splits, upsert the balance rows. The `expense_split` table lets you **rebuild** the balances from scratch, which is useful to verify that the materialised balances haven't drifted.
</details>

<details>
<summary><b>Q10. Who should absorb the extra rupee in an equal split?</b></summary>

Any rule works if it's consistent and the total is exact. Options: the last participant (the current code), the **payer** (fair: they chose the amount), or rotate it by expense. Say the rule out loud. Interviewers mostly check that you *noticed* ₹100/3 and didn't use `double`.
</details>

<details>
<summary><b>Q11. How would you know it's working in production?</b></summary>

- **Invariant check:** in every sheet, **net balances sum to exactly 0**. Run it as a periodic job; any non-zero is a bug and should page someone.
- **Rebuild check:** recompute balances from `expense_split` + `payment` and compare them with the materialised `balance` rows.
- **Metrics:** expenses/day, validation-failure rate by reason (UX problems), settle-up rate.
</details>

<details>
<summary><b>Q12. How do you test it?</b></summary>

- **Each strategy:** exact sums (₹100/3 → 33/33/34), every validation error (sum mismatch, negative, empty).
- **Netting:** A owes B 100, then B owes A 120 → only one direction is stored: B owes A 20.
- **Simplify:** the chain case (B→A, C→B, D→C) → exactly 1 payment; after paying the suggested settlements, nets are `{}`.
- **Invariant:** after any random sequence of expenses, nets sum to 0.
- **Concurrency:** 100 threads add ₹10 expenses at once → balance exactly ₹500 (in the driver).
</details>

---

## 6. Traps that cost points
1. `double` for money or percentages.
2. Shares that don't sum to the total (₹33.33 × 3 = ₹99.99).
3. Storing both directions of a debt.
4. Validating halfway through: a rejected expense leaves partial balance changes.
5. EXACT accepting negative numbers, or EQUAL with zero participants (a divide-by-zero crash).
6. A `switch(splitType)` in the manager instead of strategies.
7. Forgetting the payer's own share is skipped (you don't owe yourself).

---

## 7. Recall check (next day, no peeking)
1. ₹100 split EQUAL among 3, and 33.33%/33.33%/33.34% of ₹100: write the exact splits.
2. Bob owes Alice ₹50. Alice incurs ₹80 to Bob. Walk through `addOwed`. What's stored afterwards?
3. Why is a payment just `addOwed(from, to, amount)`?
4. Simplify: nets A +40, B +10, C −30, D −20. Which payments come out?
5. Why both group sheets and an overall sheet?
6. How do you delete an expense without recomputing everything?

<details><summary>Answer to 4</summary>

Biggest creditor A(40) vs biggest debtor C(−30) → C pays A 30 (A now +10). Next: A(10) vs D(−20) → D pays A 10 (D now −10). Then B(10) vs D(−10) → D pays B 10. Three payments.
</details>

**Rebuild in 10 minutes:** `SplitStrategy` + EQUAL (remainder to last) + EXACT (sum and non-negative check) · `BalanceSheet.addOwed` (netting) + `getNetBalances` + `simplify` (two heaps) · `ExpenseManager.addGroupExpense` (validate → strategy → record in both sheets).

---

**Files:** `ExpenseManager` (service) · `balance/BalanceSheet` · `splittype/` (`SplitType`, `SplitStrategy`, `EqualSplitStrategy`, `ExactSplitStrategy`, `PercentSplitStrategy`) · `model/` (`User`, `Group`, `Expense`, `Split`, `Settlement`) · `SplitwiseDriver` (3 split types, rounding, group vs overall, simplify + pay back, chain collapse, 8 validation errors, 100-thread test)
```bash
mvn -q compile exec:java -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.policies.splitwise.SplitwiseDriver
```
