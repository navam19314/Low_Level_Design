# Splitwise

> 30 min round. On the Ethos list. Phases: **Requirements (5) → Entities (3) → Class Design (5–10) → Implementation (10) → Extensibility (5)**.

---

## 0. The problem in plain words (read first, don't say it in the interview)

**Picture a Goa trip with friends.** Alice pays ₹300 for dinner for three people, so Bob and Carol each owe her ₹100. Then Bob pays ₹120 for a cab he shared with Alice, so Alice owes him ₹60. Instead of remembering both, the app shows the **net**: Bob owes Alice just ₹40. Later Bob pays Alice ₹40, and they're settled.

**The whole system is 3 actions:**
```
Add a bill   →  addExpense("Dinner", paidBy alice, ₹300, split equally among alice, bob, carol)
Pay back     →  settleUp(bob → alice, ₹40)
See who owes →  getBalance(alice, bob) = 40  ("bob owes alice ₹40")
```

**Real world → code**
| In the real world | In the code |
|---|---|
| A bill | `Expense`: who paid, how much, each person's share |
| "Split equally" / "I'll pay 600, you 400" | `EqualSplit` / `ExactSplit`: both are a `SplitStrategy` |
| The notebook of who owes whom | `balances[a][b]`: a table inside `ExpenseManager` |
| A person | just a name / id (no class) |

**Only 3 things are tricky:**
1. **Split the bill so the shares add up exactly.** ₹100 among 3 is 33 + 33 + 34; the leftover rupee goes to someone. With an exact split, the amounts must add up to the bill.
2. **Debts in both directions cancel.** Bob owes Alice 100 and Alice owes Bob 60, so show 40. The trick: keep `balances[a][b]` and `balances[b][a]` as **exact opposites**, and the cancelling happens by itself.
3. **One bill changes several balances,** so they all change under one lock (`synchronized`).

**Picture this run before you start coding:**
```
alice pays ₹300, equal: alice, bob, carol   → bob owes alice 100, carol owes alice 100
bob pays ₹120, equal: alice, bob            → bob owes alice 100 − 60 = 40
carol pays ₹1000, exact: alice 600, carol 400 → alice owes carol 600 − 100 = 500
bob settles ₹40 with alice                  → 0
exact split 600 + 300 for a ₹1000 bill       → rejected (doesn't add up)
```

---

## 1. Requirements (~5 min)

**Ask:** which split types? Do debts in both directions cancel? Settle up? Groups? Simplify debts?

```
1. addExpense(description, paidBy, amount, split): EQUAL or EXACT split; the shares must add up to the amount
2. getBalance(a, b) and getBalances(user): the NET amount between two people
3. settleUp(from, to, amount): record a payment back
4. Safe with many expenses at once

Out of scope: groups, percent split, simplify debts, edit/delete, currencies
```

---

## 2. Entities & Relationships (~3 min)

```
ExpenseManager
   ├── balances: person → (person → amount)
   ├── Expense (many)        paidBy, amount, shares
   └── uses a SplitStrategy  per expense   ← EqualSplit, ExactSplit
```
- **A person is just an id.** A `User` class has no behaviour here.
- **The split object carries its own inputs:** `new EqualSplit(List.of("alice","bob"))` or `new ExactSplit(Map.of("alice", 600L, "carol", 400L))`. So there's no awkward map whose values are "ignored for EQUAL".

---

## 3. Class Design (~5 min)

```
ExpenseManager   addExpense(desc, paidBy, amount, split) → Expense · settleUp(from, to, amount)
                 getBalance(user, other) · getBalances(user)
                 private addDebt(creditor, debtor, amount)      ← the one place balances change

Expense          id, description, paidBy, amount, shares (person → share)
SplitStrategy    shares(amount) → person → share   (must add up to amount)
  EqualSplit     people              ExactSplit     person → amount
```
- **Thread safety:** every service method is `synchronized`.

### Design patterns (how to say it)
**Strategy for the split.** *Problem:* there are several ways to split (equal, exact, percent, shares), and more get asked for. *Without it:* `if (type == EQUAL) ... else if (type == EXACT) ...` inside `addExpense`. *With it:* each split is a class with `shares(amount)`, so a new split type is a new class and `addExpense` never changes.
**Say:** *"`addExpense` only knows 'ask the split for the shares, then record who owes whom'. Each split also checks its own rule, like exact amounts adding up."*

**Not used:** Factory for splits (the caller builds the split it wants, so there's no type switch left to hide) and Singleton (it's hard to test).

### ACID (if asked)
**Atomic:** the split is calculated and checked **before** any balance changes, so a bad split changes nothing. **Consistent:** `balances[a][b] == -balances[b][a]` always holds, and the shares add up to the bill. **Isolated:** `synchronized`. **Durable:** not in memory; *"in production, the expense and the balance rows go in one DB transaction."*

---

## 4. Implementation (~10 min)

**Write it in this order:** `SplitStrategy` → `EqualSplit`, `ExactSplit` → `ExpenseManager` (`addDebt` is the crux).

```java
public interface SplitStrategy {
    Map<String, Long> shares(long amount);                 // person → share; must add up to amount
}

public class EqualSplit implements SplitStrategy {
    private final List<String> people;
    // constructor: reject an empty list or the same person twice

    public Map<String, Long> shares(long amount) {
        Map<String, Long> shares = new LinkedHashMap<>();
        long each = amount / people.size();
        for (String person : people) shares.put(person, each);
        String last = people.get(people.size() - 1);
        shares.put(last, each + amount % people.size());    // leftover rupees: ₹100 / 3 → 33, 33, 34
        return shares;
    }
}

public class ExactSplit implements SplitStrategy {
    private final Map<String, Long> amounts;
    // constructor copies the map

    public Map<String, Long> shares(long amount) {
        long sum = 0;
        for (Map.Entry<String, Long> e : amounts.entrySet()) {
            if (e.getValue() < 0) throw new IllegalArgumentException("Negative share for " + e.getKey());
            sum += e.getValue();
        }
        if (sum != amount) throw new IllegalArgumentException("Shares add up to " + sum + ", not " + amount);
        return new LinkedHashMap<>(amounts);
    }
}

public class Expense { private final String id, description, paidBy; private final long amount; private final Map<String, Long> shares; /* ctor + getters */ }
```

```java
public class ExpenseManager {
    // balances[a][b] > 0 → b owes a. balances[b][a] is ALWAYS the negative, so opposite debts cancel by themselves.
    private final Map<String, Map<String, Long>> balances = new HashMap<>();
    private final List<Expense> expenses = new ArrayList<>();
    private int nextExpenseNumber = 1;

    public synchronized Expense addExpense(String description, String paidBy, long amount, SplitStrategy split) {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
        Map<String, Long> shares = split.shares(amount);      // throws if they don't add up: nothing changed
        for (Map.Entry<String, Long> share : shares.entrySet()) {
            if (!share.getKey().equals(paidBy)) {             // you don't owe yourself
                addDebt(paidBy, share.getKey(), share.getValue());
            }
        }
        Expense expense = new Expense("EXP-" + nextExpenseNumber++, description, paidBy, amount, shares);
        expenses.add(expense);
        return expense;
    }

    public synchronized void settleUp(String from, String to, long amount) {
        if (amount <= 0) throw new IllegalArgumentException("Amount must be positive");
        if (from.equals(to)) throw new IllegalArgumentException("Can't pay yourself");
        addDebt(from, to, amount);                            // paying back = the reverse of owing
    }

    public synchronized long getBalance(String user, String other) {   // + other owes user, − user owes other
        return get(user, other);
    }
    // getBalances(user): the non-zero entries of balances[user]

    private void addDebt(String creditor, String debtor, long amount) {  // update BOTH directions
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
```

**Verify out loud:** walk through the run from section 0. For the cancelling, say: *"After dinner, `balances[alice][bob] = 100`. The cab calls `addDebt(bob, alice, 60)`: `balances[bob][alice]` goes from −100 to −40, and `balances[alice][bob]` from 100 to 40. There's no if/else, because the mirror does the netting."*

---

## 5. Extensibility (~5 min)

Name **which class changes**, write the few new lines, and don't redesign the rest.

**Quick revision:**

| # | Interviewer asks | One-line answer |
|---|---|---|
| 1 | "Simplify debts: fewest payments" | Net amount per person, then match the biggest creditor with the biggest debtor (greedy) |
| 2 | "Split by percent" | One more `SplitStrategy`: `PercentSplit` |
| 3 | "Groups (the Goa trip, the flat)" | `Group` = members + its own `ExpenseManager`; check everyone is a member |
| 4 | "Edit or delete an expense" | Undo = add the same shares with a **minus** sign |
| 5 | "Activity feed / notify friends" | Observer: listeners hear every expense and payment |
| 6 | "Thousands of groups, one lock is slow" | A lock per group (each group's ledger is independent) |

---

### 1. "Simplify debts: fewest payments"
**Idea:** only each person's **net** matters (+ is owed money, − owes money). Repeatedly let the person who owes the most pay the person owed the most.
```java
public synchronized List<String> simplifyDebts() {
    Map<String, Long> net = new HashMap<>();                     // person → + owed to them, − they owe
    for (Map.Entry<String, Map<String, Long>> row : balances.entrySet()) {
        long total = 0;
        for (long v : row.getValue().values()) total += v;
        if (total != 0) net.put(row.getKey(), total);
    }

    List<String> payments = new ArrayList<>();
    while (!net.isEmpty()) {
        String creditor = null, debtor = null;
        for (String p : net.keySet()) {
            if (creditor == null || net.get(p) > net.get(creditor)) creditor = p;   // owed the most
            if (debtor == null || net.get(p) < net.get(debtor)) debtor = p;         // owes the most
        }
        long amount = Math.min(net.get(creditor), -net.get(debtor));
        payments.add(debtor + " pays " + creditor + " ₹" + amount);
        change(net, creditor, -amount);
        change(net, debtor, amount);
    }
    return payments;
}

private void change(Map<String, Long> net, String person, long by) {   // drop people who reach 0
    long v = net.get(person) + by;
    if (v == 0) net.remove(person); else net.put(person, v);
}
```
**Check:** A owes B 50, B owes C 50, C owes D 50 → net A −50, D +50 → **one** payment: "A pays D ₹50" instead of three.
**Say:** *"Greedy needs at most N−1 payments for N people. The true minimum is NP-hard, so greedy is the standard answer."*

---

### 2. "Split by percent"
```java
public class PercentSplit implements SplitStrategy {
    private final Map<String, Integer> percents;               // person → whole percent
    // constructor copies the map

    public Map<String, Long> shares(long amount) {
        int total = 0;
        for (int p : percents.values()) total += p;
        if (total != 100) throw new IllegalArgumentException("Percents add up to " + total + ", not 100");

        Map<String, Long> shares = new LinkedHashMap<>();
        long given = 0;
        String last = null;
        for (Map.Entry<String, Integer> e : percents.entrySet()) {
            long share = amount * e.getValue() / 100;
            shares.put(e.getKey(), share);
            given += share;
            last = e.getKey();
        }
        shares.put(last, shares.get(last) + amount - given);      // leftover rupees, so it adds up exactly
        return shares;
    }
}
```
**Check:** 33/33/34% of ₹1001 → the shares still add up to ₹1001.
**Say:** *"A new split type is a new class, and `addExpense` doesn't change. For 33.33%, I'd use basis points (10000 = 100%) to stay in integers."*

---

### 3. "Groups: the Goa trip, the flat"
**Idea:** a group is its members plus **its own** ledger, so "settle up the Goa trip" only shows Goa debts.
```java
public class Group {
    private final String id;
    private final Set<String> members;
    private final ExpenseManager ledger = new ExpenseManager();   // this group's own balances

    public Expense addExpense(String description, String paidBy, long amount, SplitStrategy split) {
        if (!members.contains(paidBy)) throw new IllegalArgumentException(paidBy + " is not in " + id);
        for (String person : split.shares(amount).keySet()) {
            if (!members.contains(person)) throw new IllegalArgumentException(person + " is not in " + id);
        }
        return ledger.addExpense(description, paidBy, amount, split);
    }
    // getBalance / settleUp: delegate to ledger
}
```
**Say:** *"Each group reuses the same `ExpenseManager`. For 'what do I owe Bob across everything', I'd also record every expense in one overall ledger."*

---

### 4. "Edit or delete an expense"
**Idea:** undoing an expense means applying its shares **in reverse**. An edit is a delete plus a new add.
```java
public synchronized void deleteExpense(String expenseId) {
    Expense expense = null;
    for (Expense e : expenses) if (e.getId().equals(expenseId)) expense = e;
    if (expense == null) throw new NoSuchElementException("Unknown expense " + expenseId);

    expenses.remove(expense);
    for (Map.Entry<String, Long> share : expense.getShares().entrySet()) {
        if (!share.getKey().equals(expense.getPaidBy())) {
            addDebt(expense.getPaidBy(), share.getKey(), -share.getValue());   // minus = undo
        }
    }
}
```
**Check:** dinner (₹300) + cab (₹120), then delete the dinner → only the cab remains: alice owes bob ₹60, carol is back to 0.
**Say:** *"That's why the Expense keeps its shares: they're exactly what I need to undo it."*

---

### 5. "Activity feed: 'Alice added Dinner ₹300'"
```java
public interface ActivityListener {
    void onExpense(Expense expense);
    void onPayment(String from, String to, long amount);
}

// ExpenseManager
private final List<ActivityListener> listeners = new ArrayList<>();
public synchronized void addListener(ActivityListener l) { listeners.add(l); }

// end of addExpense():  for (ActivityListener l : listeners) l.onExpense(expense);
// end of settleUp():    for (ActivityListener l : listeners) l.onPayment(from, to, amount);
```
**Say:** *"The feed, push notifications and emails are listeners. The manager doesn't know about them. Slow ones go on a queue."*

---

### 6. "Thousands of groups, the single lock is slow"
**First say:** *"Each call holds the lock for microseconds. I'd measure first."*

**Idea:** groups never share balances, so give each group its **own** lock. That's Extension 3's design: each `Group` has its own `ExpenseManager`, and its `synchronized` methods lock only that group's ledger.
```java
private final Map<String, Group> groups = new ConcurrentHashMap<>();

public Expense addGroupExpense(String groupId, String description, String paidBy, long amount, SplitStrategy split) {
    return groups.get(groupId).addExpense(description, paidBy, amount, split);   // locks only that group's ledger
}
```
**Say:** *"The Goa trip and the flat never wait for each other. An overall ledger across groups would need its own lock, or be built from the group ledgers when it's asked for."*

---

```bash
mvn -q compile exec:java -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.policies.splitwise.SplitwiseDriver
```
