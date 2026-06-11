# Splitwise — 45-min LLD Interview Walkthrough

**Frequency:** Top-5 at Amazon India SDE-2, Top-3 at Adobe Noida. Rare at Microsoft (they prefer Vending Machine / Logger).
**Senior signal:** Three things separate mid from senior here — `long` cents, balance-graph normalization, and greedy debt simplification. Nail all three.

---

## Time budget

| Step | Activity | Time | Clock |
|------|----------|------|-------|
| 1 | Requirements | 5 min | 0–5 |
| 2 | Entities & Relationships | 4 min | 5–9 |
| 3 | Class Design (state + behavior) | 10 min | 9–19 |
| 4 | Code + dry-run | 17 min | 19–36 |
| 5 | Extensibility | 7 min | 36–43 |
| — | Buffer | 2 min | 43–45 |

**Watch the clock at min 9** (start coding) and **min 36** (switch to extensibility). Step 4 is the longest because the dry-run on simplification is what earns the senior tag.

---

## Mental Models — memorize these before you walk in

### M1. What `addExpense` actually does (3-line summary)

```
1. Strategy.calculate(total, participants) → List<Split>   // each split = (userId, amountCents)
2. For every split where userId != payer:
       addOwed(payer, userId, split.amountCents)           // payer is creditor, user is debtor
3. Append Expense to ledger.
```

That's it. Strategy handles the math. `addOwed` handles the graph. Ledger records history.

> **Say in interview:** *"addExpense delegates the math to the Strategy, updates the balance graph via addOwed, and appends to the ledger. Three responsibilities, three lines."*

---

### M2. Money = `long` cents. Always. No exceptions.

**The rule:** Never use `double` or `float` for money.

**Why:** `0.1 + 0.2 = 0.30000000000000004` in floating point. Equal splits over hundreds of transactions drift. Amounts become wrong.

**The fix — 3 rules:**
```
1. Store everything as long cents  (₹10.00 → 1000L)
2. PERCENT split → use basis points (33.33% → 3333,  sum must == 10000)
3. Rounding remainder → last participant absorbs it

   Example: 1000 cents split equally among 3
   share     = 1000 / 3 = 333    (integer division)
   remainder = 1000 − 333×3 = 1
   → first 2 get 333, last gets 334.   Sum = 1000. ✓ Exact.
```

> **Say in interview:** *"Money is `long` cents — never doubles. PERCENT uses basis points so 33.33% is `3333` with no float drift. The rounding remainder goes on the last participant so the sum is always exactly the total — no penny vanishes."*

---

### M3. Balance graph — one invariant, one rule

**The graph:** `Map<String, Map<String, Long>> balances`
- `balances[A][B] = 1000` means B owes A ₹10.

**The invariant:** `balances[A][B] > 0` and `balances[B][A] > 0` can NEVER both be true at the same time.

**The rule — `addOwed(creditor, debtor, amount)`:**
```
opposing = balances[debtor][creditor]    // does creditor currently owe debtor anything?

if opposing >= amount:
    balances[debtor][creditor] -= amount  // just reduce the opposing debt, net is less
else:
    balances[debtor][creditor]  = 0       // wipe opposing
    balances[creditor][debtor] += (amount - opposing)  // only the net goes forward
```

> **Say in interview:** *"The invariant is: we never store debt in both directions at once. `addOwed` cancels any opposing balance first and only stores the net. This keeps `getBalance` a single subtract and the graph storage minimal."*

---

### M4. Greedy simplification — 3 steps, memorize the heap trick

**Goal:** Settle all debts in fewest transactions.

```
Step 1 — Net balance per user:
   net[user] = what others owe them  −  what they owe others
   Positive → creditor.  Negative → debtor.  Zero → skip.

Step 2 — Two heaps:
   MAX-heap of creditors (most owed first)
   MIN-heap of debtors   (most owing first, i.e. most negative)

Step 3 — Greedy match:
   while both heaps non-empty:
       c = pop largest creditor
       d = pop largest debtor
       settle = min(net[c], |net[d]|)
       emit Settlement(debtor=d, creditor=c, amount=settle)
       update net[c] and net[d], push back if non-zero
```

**Two dry-runs to know cold:**

```
Chain:  B pays for A ($10), C pays for B ($10), D pays for C ($10)
   net[A]=-1000, net[B]=0(skip), net[C]=0(skip), net[D]=+1000
   → 1 settlement: A pays D $10.    (vs 3 naïve settlements)

Circular: A→B $10, B→C $10, C→A $10
   net[A]=0, net[B]=0, net[C]=0
   → 0 settlements. Circular debt auto-cancels.
```

> **Say in interview:** *"Greedy gives at most N-1 settlements. The truly optimal version is NP-hard — it reduces to subset-sum. Greedy is the right call for interview scope; I'll name the tradeoff."*

---

## Step 1 — Requirements (5 min)

**Ask the interviewer:**
- Split types needed — equal / exact / percentage?
- Should percentage work for 33.33%? (This forces basis-points answer.)
- Do we need debt simplification / settlement?
- Concurrent adds from multiple users?

**State out loud:**

```
IN SCOPE
1. Add users (id, name, email)
2. Add expense — one payer, N participants, one of 3 split types (EQUAL / EXACT / PERCENT)
3. EQUAL: share = total/N, last absorbs remainder
   EXACT: per-user amounts, must sum to total
   PERCENT: basis points (10000 = 100%), must sum to 10000
4. Pairwise balance query: getBalance(u1, u2) → signed long cents
5. Net balance per user: getNetBalance(userId)
6. simplifyDebts() → minimal list of (debtor, creditor, amount) settlements
7. Thread-safe under concurrent addExpense calls

OUT OF SCOPE
- Groups as a first-class entity
- Multi-currency / FX
- Notifications (Observer — Step 5)
- Persistence
- UI / rendering
```

> **Close with:** *"Three load-bearing requirements: integer-cent money, basis-point percentages, and greedy simplification. That's where the senior signal lives."*

---

## Step 2 — Entities & Relationships (4 min)

**What to draw:**

```
            +----------------------------------+
            |        ExpenseManager            |   ← orchestrator + facade
            |  addUser / addExpense            |
            |  getBalance / simplifyDebts      |
            +----------------------------------+
               |        |         |         |
           users{}   expenses[]  balances{}  strategies{}
           Map<id,    List<       Map<u,      Map<Type,
           User>      Expense>    Map<u,Long>> Strategy>
```

**Entities:**
| Entity | What it is |
|--------|------------|
| `ExpenseManager` | Orchestrator + facade. The only class callers touch. |
| `User` | Immutable value object: id, name, email |
| `Expense` | Immutable ledger entry: id, paidById, total, splits, createdAt |
| `Split` | (userId, amountCents) — output of Strategy |
| `Settlement` | (debtorId, creditorId, amountCents) — output of simplifyDebts |
| `SplitStrategy` | Interface. 3 impls: Equal / Exact / Percent |

**Not entities (say why):**
- `Group` — no group-level invariants in requirements. Participants list is enough.
- `BalanceSheet` — just a Map of Maps. No behavior beyond what `addOwed` already enforces.
- `Money` — `long` cents + single currency is sufficient for v1.

---

## Step 3 — Class Design (10 min)

### Requirements → State mapping

| Requirement | State |
|-------------|-------|
| Multiple users | `Map<String, User> users` |
| Expense history | `List<Expense> expenses` |
| Who owes whom | `Map<String, Map<String, Long>> balances` |
| Split algorithm per type | `Map<SplitType, SplitStrategy> strategies` |

### Behavior derived from requirements

```java
// ExpenseManager — write this outline on the board
public class ExpenseManager {
    private final Map<String, User>                  users      = new HashMap<>();
    private final List<Expense>                      expenses   = new ArrayList<>();
    private final Map<String, Map<String, Long>>     balances   = new HashMap<>();
    private final Map<SplitType, SplitStrategy>      strategies;

    public synchronized void          addUser(User user);
    public synchronized Expense       addExpense(String paidById, long totalCents,
                                                 SplitType type,
                                                 Map<String, Long> inputs,
                                                 String description);
    public synchronized long          getBalance(String u1, String u2);
    public synchronized long          getNetBalance(String userId);
    public synchronized List<Settlement> simplifyDebts();
}
```

### SplitStrategy interface

```java
public interface SplitStrategy {
    List<Split> calculate(long totalAmountCents, Map<String, Long> participantInputs);
}
// EqualSplitStrategy  — keys only; floor division + remainder on last
// ExactSplitStrategy  — values are cents; validate sum == total
// PercentSplitStrategy — values are bps; validate sum == 10000; last absorbs cent-remainder
```

> **Say in interview:** *"Strategy passes the one-sentence test: I need 3 implementations on day 1. The validation rules differ per type — EXACT validates sum, PERCENT validates bps. Each impl owns its own rules. ExpenseManager doesn't grow when a new split type is added."*

---

## Step 4 — Code + dry-run (17 min)

### Code in this order (do it in this sequence)

1. `SplitType` enum, `SplitStrategy` interface — 1 min
2. `EqualSplitStrategy.calculate` — 3 min (shows the rounding trick)
3. `ExpenseManager.addExpense` + `addOwed` — 7 min (core of the problem)
4. `ExpenseManager.simplifyDebts` — 4 min (the algorithm)
5. Dry-run chain-debt scenario out loud — 2 min

### `addExpense` — write this and narrate

```java
public synchronized Expense addExpense(String paidById, long totalCents,
                                       SplitType type, Map<String, Long> inputs,
                                       String description) {
    if (!users.containsKey(paidById))
        throw new IllegalArgumentException("Unknown payer: " + paidById);
    for (String uid : inputs.keySet())
        if (!users.containsKey(uid))
            throw new IllegalArgumentException("Unknown participant: " + uid);

    List<Split> splits = strategies.get(type).calculate(totalCents, inputs); // may throw

    Expense expense = new Expense(UUID.randomUUID().toString(), paidById,
                                  totalCents, splits, description, LocalDateTime.now());
    expenses.add(expense);

    for (Split s : splits)
        if (!s.getUserId().equals(paidById))
            addOwed(paidById, s.getUserId(), s.getAmountCents());

    return expense;
}
```

**Narrate 3 points out loud:**
1. *"Validate before mutating — if strategy throws (bad sum), nothing is recorded."*
2. *"Strategy delegation — one map lookup, one call. New split type = new class, zero changes here."*
3. *"Self-debt guard — payer's own share doesn't generate a debt to themselves."*

### `addOwed` — narrate the invariant

```java
private void addOwed(String creditorId, String debtorId, long amount) {
    long opposing = getStored(debtorId, creditorId);
    if (opposing >= amount) {
        setStored(debtorId, creditorId, opposing - amount);
    } else {
        setStored(debtorId, creditorId, 0);
        setStored(creditorId, debtorId, getStored(creditorId, debtorId) + (amount - opposing));
    }
}
```

> *"Cancel the opposing direction first. We never store both balances[A][B] > 0 and balances[B][A] > 0 — only the net remains. This keeps getBalance a clean single subtract."*

### `getBalance` — one liner

```java
public synchronized long getBalance(String u1, String u2) {
    return getStored(u1, u2) - getStored(u2, u1);
}
// Positive: u1 is owed by u2.  Negative: u1 owes u2.  Zero: settled.
```

### `simplifyDebts` — the algorithm

```java
public synchronized List<Settlement> simplifyDebts() {
    Map<String, Long> net = new HashMap<>();
    for (String uid : users.keySet()) {
        long n = getNetBalance(uid);
        if (n != 0) net.put(uid, n);
    }

    PriorityQueue<String> creditors = new PriorityQueue<>((a, b) -> Long.compare(net.get(b), net.get(a)));
    PriorityQueue<String> debtors   = new PriorityQueue<>((a, b) -> Long.compare(net.get(a), net.get(b)));
    for (Map.Entry<String, Long> e : net.entrySet()) {
        if      (e.getValue() > 0) creditors.offer(e.getKey());
        else if (e.getValue() < 0) debtors.offer(e.getKey());
    }

    List<Settlement> result = new ArrayList<>();
    while (!creditors.isEmpty() && !debtors.isEmpty()) {
        String c = creditors.poll();
        String d = debtors.poll();
        long settle = Math.min(net.get(c), -net.get(d));
        result.add(new Settlement(d, c, settle));
        long newC = net.get(c) - settle;
        long newD = net.get(d) + settle;
        net.put(c, newC); net.put(d, newD);
        if (newC > 0) creditors.offer(c);
        if (newD < 0) debtors.offer(d);
    }
    return result;
}
```

### Dry-run — say this out loud (chain collapse)

```
B pays ₹10 for A  → balances[B][A] = 1000
C pays ₹10 for B  → balances[C][B] = 1000
D pays ₹10 for C  → balances[D][C] = 1000

net[A] = 0 − 1000 = -1000  (debtor)
net[B] = 1000 − 1000 = 0   (skip)
net[C] = 1000 − 1000 = 0   (skip)
net[D] = 1000 − 0 = +1000  (creditor)

Greedy: pop D(+1000) and A(-1000), settle 1000.
Result: 1 settlement — A pays D ₹10.
Naïve would have been 3 settlements. Greedy wins.
```

---

## Step 5 — Extensibility (7 min)

Offer these before the interviewer asks.

| Follow-up | Pattern | One-line answer |
|-----------|---------|-----------------|
| Notify users on expense add | **Observer** | `ExpenseListener` interface; SMS/email/push register independently |
| Multi-currency (USD + INR on a trip) | **Strategy** | `FxRateProvider` interface — historical rate vs. spot rate as two impls |
| Different simplification algorithms | **Strategy** | `SimplificationStrategy` — `GreedyStrategy` default, `OptimalStrategy` NP-hard opt-in |
| Persist across restart | **Repository** | `ExpenseRepository` — write on every add, replay on boot to rebuild balances |
| Groups (Flat, Trip, Office) | Promote `Group` entity | Group owns its own ledger + balance graph; `ExpenseManager` routes by groupId |
| Partial settlement mid-trip | `recordSettlement()` | Treat a payment as a reverse expense — same `addOwed` call with roles swapped |

---

## Questions you WILL get asked in Indian SDE-2 rounds

### "Why `long` and not `double`?"
> *"0.1 + 0.2 = 0.30000000000000004 in floating point. Equal splits over hundreds of transactions drift. Long cents are exact — integer division + rounding remainder on the last participant means the sum equals the total every time."*

### "How does your percent split handle 33.33%?"
> *"33.33% is stored as 3333 basis points — integer, no float. Sum must equal 10000. The last participant absorbs the cent-level rounding remainder so totals are exact."*

### "What's the time complexity of simplifyDebts?"
> *"O(U log U) — one linear pass to compute net balances, then heap operations over at most U users. U is typically small (< 50 in a friend group), so it's effectively O(1) in practice."*

### "Is greedy optimal?"
> *"No — truly optimal minimum-transactions is NP-hard (reduces to subset-sum). Greedy gives at most N-1 settlements which is the upper bound. In practice it collapses chains and cycles aggressively. For interview scope greedy is the right call — and naming the tradeoff is the senior signal."*

### "How is this thread-safe?"
> *"Coarse-grained synchronized on all public methods of ExpenseManager. Correct and simple. If contention became a bottleneck, I'd move to per-user lock pairs with compareTo-ordered acquisition to avoid deadlock — same pattern as a bank transfer."*

### "What if the payer is also a participant?"
> *"Handled by the `userId != paidById` guard in addExpense. The payer's share is computed by the strategy but never creates a self-debt in the graph."*

---

## Top mistakes that lose points

| Mistake | Why it costs |
|---------|-------------|
| `double` for money | Floats drift. Instant red flag. |
| `double` for percent | Same. Basis points fix it. |
| Not absorbing remainder on last participant | Sum ≠ total. Pennies vanish. |
| Storing both `balances[A][B] > 0` and `balances[B][A] > 0` | `getBalance` returns wrong answer without extra subtraction |
| Self-debt on payer | Forgetting the `userId != paidById` guard |
| Validation after mutation | Bad input partly corrupts the graph before throwing |
| Missing `synchronized` | Concurrent adds race on the balance map |
| Adding `Group`, `Money`, `BalanceSheet` to v1 | Over-engineering. No requirement demands them. |
| Skipping the dry-run | Greedy code without a walkthrough looks unverified |

---

## 30-second summary (memorize for closing)

> *"One orchestrator — ExpenseManager — and four immutable value objects: User, Expense, Split, Settlement. Strategy in the base for three split types, justified by the requirements explicitly naming all three. Money is `long` cents — never floats. PERCENT uses basis points so 33.33% is 3333 with integer arithmetic, last participant absorbs rounding remainder so totals are exact. Balance graph normalized: never both directions positive — addOwed cancels opposing first. simplifyDebts uses two priority queues: max-heap of creditors, min-heap of debtors, greedy match. Produces at most N-1 settlements — collapses a chain of 4 into 1, collapses circular debt into 0. Extensions: Observer for notifications, FX-rate Strategy for multi-currency, Repository for persistence."*
