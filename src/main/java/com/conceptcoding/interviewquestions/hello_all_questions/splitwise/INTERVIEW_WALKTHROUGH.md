# Splitwise — 45-min LLD Interview Walkthrough

**Target role:** SDE‑2 (Amazon, Adobe, Microsoft, Atlassian, etc.)
**Source method:** Same Delivery Framework as the HI deck, applied to Splitwise (not in HI's curriculum but a top-frequency Indian SDE‑2 question).

> Splitwise is the **canonical "Strategy + graph state + algorithmic senior signal" problem**. Three signals separate senior from mid: (a) Strategy in the base for split-types (justified by R3's explicit enumeration), (b) money as **`long` cents** with explicit remainder-on-last-participant rounding, and (c) the **greedy debt-simplification algorithm** that collapses N-1 chained debts into a minimal-settlement set.

---

## Time budget (45 min)

| Step | Activity                                                                                | Budget   | Cumulative |
| ---- | --------------------------------------------------------------------------------------- | -------- | ---------- |
| 1    | Requirements                                                                            | ~5 min   | 5          |
| 2    | Entities & Relationships                                                                | ~4 min   | 9          |
| 3    | Class Design (Strategy + the balance-graph representation)                              | ~10 min  | 19         |
| 4    | Implementation (`addExpense` + balance update + **greedy simplification** + dry-run)    | ~17 min  | 36         |
| 5    | Extensibility (notifications, currencies, groups, optimal simplification)               | ~8 min   | 44         |
| —    | Wrap & questions                                                                        | ~1 min   | 45         |

Step 4 is the longest because the simplification algorithm is the senior signal — don't shortchange the dry-run.

Watch the clock at minute **5** (Step 1 done), minute **19** (start coding), minute **36** (extensibility).

---

## Mental models — internalize these BEFORE you walk in

### M1. The expense → balance-graph update flow

```
   addExpense(payer = Alice, $30 dinner, EQUAL split among A/B/C)
        |
        v
   +-----------------------------------------+
   | strategy = strategies.get(EQUAL)        |   <-- Strategy lookup
   | splits = strategy.calculate(3000, {A,B,C}) |   = [(A,1000),(B,1000),(C,1000)]
   +-----------------------------------------+
        |
        v
   +-----------------------------------------+
   | For each split where userId != payer:   |
   |   addOwed(creditor=payer, debtor=user,  |
   |           amount=split.amountCents)     |
   +-----------------------------------------+
        |
        v
   balances[Alice][Bob]   += 1000   (Bob owes Alice 10.00)
   balances[Alice][Carol] += 1000

   Invariant: we never have BOTH balances[A][B] > 0 AND balances[B][A] > 0.
              addOwed cancels opposing balances first; only the net remains.
```

### M2. Money as `long` cents + rounding on the last participant

```
   Splitting $10.00 (= 1000 cents) equally among 3 users.
                                                   
   share     = 1000 / 3 = 333          (integer division, floors)
   remainder = 1000 - 333*3 = 1            (cents left over from rounding)
                                                   
   First N-1 participants: 333 each.       
   Last participant:       333 + 1 = 334.      ← absorbs the remainder.
                                                   
   Sum: 333 + 333 + 334 = 1000  ✓     EXACTLY the total. No penny vanishes.

   The same trick for PERCENT splits:
     each share = floor(total × bps / 10000)
     last absorbs (total − sum of first N-1)

   The same trick for EXACT splits:
     validate inputs sum to total; reject otherwise — never silently pad.
```

**Senior soundbite (memorize):** *"All amounts are `long` cents. Floats can't represent 0.10 exactly, so equal splits would drift over hundreds of transactions. Integer cents + put the rounding remainder on the LAST participant means the sum is EXACTLY the total every time — no penny vanishes, no penny is created."*

### M3. The greedy simplification algorithm

```
   Goal: settle all debts in the FEWEST transactions.

   Step 1: compute the NET balance per user.
           Sum across all balance-graph edges:
             net[user] = sum(balance[user][*]) - sum(balance[*][user])
           Positive  → user is net owed money (creditor).
           Negative  → user owes money net    (debtor).
           Zero      → user is settled — skip them.

   Step 2: put creditors in a MAX heap, debtors in a MIN heap (most-negative).

   Step 3: pop the largest creditor and the largest debtor; settle
           min(|debt|, credit) between them; push the remainder back
           onto whichever heap still has work; repeat until both empty.

   Result: at most N-1 settlements (one per debtor in the worst case).


   Example — chain A→B→C→D (each owes next $10):

      net[A] = -10   debtor
      net[B] =   0   skip
      net[C] =   0   skip
      net[D] = +10   creditor

   Greedy match: A pays D $10.   1 settlement. Done.

   Without simplification: 3 settlements (A→B $10, B→C $10, C→D $10).
   With simplification:    1 settlement  (A → D $10).
```

> **Caveat to verbalize:** *"This is greedy — produces at most N-1 settlements but not always optimal (NP-hard in general). For interview scope, greedy is the right call; mention that the truly optimal problem reduces to subset-sum-like."*

---

## STEP 1 — Requirements (~5 min)

### What to say out loud (opener)
> "Splitwise has two correctness traps everyone falls into — money as float and naïve debt graphs that grow without simplification. Let me clarify scope on both."

### Probe the 4 themes

| Theme               | Question to ask                                                                                              |
| ------------------- | ------------------------------------------------------------------------------------------------------------ |
| Primary capabilities| "Add user, add expense (single payer, multiple participants), query balances, simplify debts?"                |
| Rules / completion  | "Split types: equal / exact / percent. Percent in basis points so 33.33% works without float drift?"          |
| Error handling      | "Reject exact-shares that don't sum to total. Reject percent shares that don't sum to 100%. Unknown user → throw?" |
| Concurrency         | "Multi-user concurrent add — exact pairwise balances stay consistent (no lost-update on the balance graph)?" |
| Scope boundaries    | "Out: groups (light support OK), comments, attachments, currency conversion, persistence, distributed?"      |

### What to write on the board

```
Functional Requirements
1. Users with id + name + email; user registry on the manager.
2. Expenses: one payer pays a total amount; expense is split among N participants.
3. Three split types — EQUAL / EXACT / PERCENT. EXACT must sum to total. PERCENT
   given in BASIS POINTS (10000 = 100%) so we never use floats.
4. Pairwise balances queryable: getBalance(u1, u2) → signed long cents.
5. Net balance queryable per user.
6. simplifyDebts() returns the MINIMAL-ish list of (debtor → creditor, amount)
   settlements that bring every user's net balance to zero.
7. Thread-safe under concurrent adds.

Out of Scope
- Group as a first-class entity (just use the manager's user registry)
- Multi-currency / FX
- Comments / receipts / attachments
- Notifications (Step 5 — Observer)
- Persistence
- Distributed across multiple processes
```

### Close the step
> "Three load-bearing requirements: integer-cent money, basis-points percentages, and debt simplification. Those three are where the senior signal lives in this problem."

---

## STEP 2 — Entities & Relationships (~4 min)

### What to say out loud
> "Six types, in two packages. **ExpenseManager** is the orchestrator + facade. **User**, **Expense**, **Split**, **Settlement** are immutable records — the data shape of the system. **SplitStrategy** is the Strategy interface with three implementations — `EqualSplitStrategy`, `ExactSplitStrategy`, `PercentSplitStrategy`. The pairwise balance graph lives as a private `Map<String, Map<String, Long>>` inside ExpenseManager — no need to promote it to its own class."

### Why no `Group` class
> "The problem doesn't require group-level invariants — adding an expense to a 'group' is just choosing the participants. The manager's user registry is sufficient. If groups gain real behavior later (admin permissions, group-level alerts), they earn a class then."

### Why no `Balance` or `BalanceSheet` class
> "It's just a map of maps. The invariants — non-negative stored amounts, no opposing balances — are enforced by the `addOwed` helper on ExpenseManager, where they belong. A `BalanceSheet` class with three getter methods would be pure ceremony."

### What to write on the board

```
Entities
- ExpenseManager     (orchestrator + facade: addUser/addExpense/getBalance/simplifyDebts)
- User               (immutable record: id + name + email)
- Expense            (immutable record: id + paidById + total + splits + description + createdAt)
- Split              (immutable record: userId + amountCents)        ← computed by Strategy
- Settlement         (immutable record: debtorId + creditorId + amountCents)
- SplitStrategy      (interface; 3 impls: Equal / Exact / Percent)   ← Strategy

NOT entities
- Group              (no group-level invariants in the requirements)
- Balance / BalanceSheet  (just Map<String, Map<String, Long>> inside the manager)
- Currency           (out of scope — all amounts in cents)

Relationships
- ExpenseManager owns: Map<String, User>          (registry)
                       List<Expense>              (ledger)
                       Map<String, Map<String, Long>> balances     (pairwise graph)
                       Map<SplitType, SplitStrategy>               (Strategy registry)
- Expense        refs: User via paidById (string id, Law of Demeter)
- Split / Settlement  refs: User via userId
```

### Diagram — boxes and arrows

```
                  +--------------------------------------+
                  |          ExpenseManager              |   <- orchestrator + facade
                  |  addExpense / getBalance / simplify  |
                  +--------------------------------------+
                       |         |          |          |
                       | owns    | owns     | owns     | owns
                       v         v          v          v
                  Map<id,User>  List<Exp>  balances    Map<Type,Strategy>
                                            graph
                                              (Map<u1, Map<u2, Long>>)
                                              positive: u1 is owed by u2;
                                              never both directions positive.

  Strategies (Strategy pattern):
    +--------------------+
    | <<interface>>      |       Implementations:
    |  SplitStrategy     |   --   EqualSplitStrategy
    +--------------------+        ExactSplitStrategy
    | + calculate(...)   |        PercentSplitStrategy
    +--------------------+

  Records:
    +--------+   +---------+   +----------+   +------------+
    |  User  |   | Expense |   |  Split   |   | Settlement |
    +--------+   +---------+   +----------+   +------------+
```

---

## STEP 3 — Class Design (~10 min)

### Work top-down: ExpenseManager → SplitStrategy + impls → records.

### ExpenseManager — state ↔ requirement table

| Requirement                              | State ExpenseManager must own                              |
| ---------------------------------------- | ---------------------------------------------------------- |
| Multiple users                           | `Map<String, User> users`                                  |
| Ledger of all expenses                   | `List<Expense> expenses`                                   |
| Pairwise debt graph                      | `Map<String, Map<String, Long>> balances`                  |
| Split strategy per type                  | `Map<SplitType, SplitStrategy> strategies`                 |
| Time injection (for testing)             | `Clock clock`                                              |

### ExpenseManager — behavior table

| Need from requirements              | Method                                                  |
| ----------------------------------- | ------------------------------------------------------- |
| Add a user                          | `void addUser(User user)`                                |
| Record an expense                   | `Expense addExpense(paidById, totalCents, type, inputs, desc)` |
| Pairwise balance query              | `long getBalance(String u1, String u2)`                  |
| Net balance per user                | `long getNetBalance(String userId)`                      |
| Minimize settlements                | `List<Settlement> simplifyDebts()`                       |
| Ledger access                       | `List<Expense> getExpenses()`                            |

### ExpenseManager — class outline (write on the board)

```java
public class ExpenseManager {
    private final Map<String, User>                      users;
    private final List<Expense>                          expenses;
    private final Map<String, Map<String, Long>>         balances;
    private final Map<SplitType, SplitStrategy>          strategies;
    private final Clock                                  clock;

    public ExpenseManager(Clock clock) {
        // build strategy registry — Map.of(EQUAL, ..., EXACT, ..., PERCENT, ...)
    }

    public synchronized void    addUser(User user);
    public synchronized Expense addExpense(String paidById, long total, SplitType type,
                                           Map<String, Long> inputs, String description);
    public synchronized long    getBalance(String u1, String u2);
    public synchronized long    getNetBalance(String userId);
    public synchronized List<Settlement> simplifyDebts();
}
```

### SplitStrategy interface + 3 implementations

```java
public interface SplitStrategy {
    List<Split> calculate(long totalAmountCents, Map<String, Long> participantInputs);
}
```

- **EqualSplitStrategy** — keys-only input. share = total / N; last absorbs remainder.
- **ExactSplitStrategy** — values are cents per user. Validate sum == total.
- **PercentSplitStrategy** — values are basis points (10000 = 100%). Validate sum == 10000. last absorbs cent-remainder.

### Records (immutable)

```java
public record User(String id, String name, String email) {}
public record Split(String userId, long amountCents) {}
public record Settlement(String debtorId, String creditorId, long amountCents) {}
public record Expense(String id, String paidById, long totalAmountCents,
                      List<Split> splits, String description, Instant createdAt) {
    public Expense { splits = List.copyOf(splits); }   // defensive copy
}
```

### The principle to verbalize — Strategy + Information Expert
> "Strategy lets the split-type algorithm vary at runtime — each impl owns its own validation rules. Balance-graph invariants (no opposing positives, normalized representation) live on ExpenseManager because that's where the graph state lives — Information Expert. Records are immutable everywhere; mutation only happens to the manager's three maps under the synchronized lock."

---

## STEP 4 — Implementation (~17 min)

### Open by asking
> "Real Java or pseudo-code? I'll do `addExpense` first (it shows the Strategy delegation + balance update), then the `addOwed` normalization, then the greedy simplification algorithm, then dry-run the chain-debt collapse scenario."

### 4.1 `addExpense` — Strategy delegation + balance update

```java
public synchronized Expense addExpense(String paidById, long totalAmountCents,
                                       SplitType splitType,
                                       Map<String, Long> participantInputs,
                                       String description) {
    if (totalAmountCents <= 0)       throw new IllegalArgumentException("amount must be > 0");
    if (!users.containsKey(paidById)) throw new IllegalArgumentException("unknown payer");
    for (String uid : participantInputs.keySet()) {
        if (!users.containsKey(uid))  throw new IllegalArgumentException("unknown participant " + uid);
    }

    SplitStrategy strategy = strategies.get(splitType);
    List<Split> splits = strategy.calculate(totalAmountCents, participantInputs);   // ← may throw

    Expense expense = new Expense(UUID.randomUUID().toString(), paidById,
            totalAmountCents, splits, description, clock.instant());
    expenses.add(expense);

    // Every non-payer participant owes the payer their share.
    for (Split s : splits) {
        if (!s.userId().equals(paidById)) {
            addOwed(paidById, s.userId(), s.amountCents());
        }
    }
    return expense;
}
```

**Three callouts to deliver out loud:**

1. *"Strategy delegation. The `Map<SplitType, SplitStrategy>` lookup means new split types are one new class + one map entry; ExpenseManager doesn't grow. Validation lives on each Strategy because the validation rules differ per split type."*

2. *"Validation BEFORE mutation. If the strategy throws (exact sum mismatch, bad bps), the expense is never recorded, the balance graph never mutates. All-or-nothing."*

3. *"Only non-payers owe. If Alice pays $30 and is herself a participant, her own share ($10) doesn't generate a self-debt. The `s.userId().equals(paidById)` guard."*

### 4.2 `addOwed` — pairwise balance normalization (the tricky bit)

```java
private void addOwed(String creditorId, String debtorId, long amount) {
    // Net any existing opposing balance first — never store both directions positive.
    long opposing = getStored(debtorId, creditorId);      // amount creditor previously owed debtor
    if (opposing >= amount) {
        setStored(debtorId, creditorId, opposing - amount);
    } else {
        // Wipe opposing, push remainder into the forward direction.
        setStored(debtorId, creditorId, 0);
        setStored(creditorId, debtorId, getStored(creditorId, debtorId) + (amount - opposing));
    }
}
```

> **Senior callout:** *"This is the normalization that keeps the balance graph clean. Without it you can have `balances[A][B] = 1000` AND `balances[B][A] = 800` simultaneously — same net effect but more storage and confused `getBalance` results. By always cancelling the opposing direction first, the invariant `balances[X][Y] > 0 implies balances[Y][X] == 0` always holds."*

### 4.3 `getBalance` — signed pairwise query

```java
public synchronized long getBalance(String u1, String u2) {
    return getStored(u1, u2) - getStored(u2, u1);
}
```

Positive: u1 is owed by u2. Negative: u1 owes u2. Zero: settled. **One subtract.**

### 4.4 `simplifyDebts` — the greedy algorithm

```java
public synchronized List<Settlement> simplifyDebts() {
    // Step 1: net balance per user
    Map<String, Long> net = new HashMap<>();
    for (String userId : users.keySet()) {
        long n = getNetBalance(userId);
        if (n != 0) net.put(userId, n);
    }

    // Step 2: max-heap of creditors, min-heap of debtors (most-negative first)
    PriorityQueue<String> creditors = new PriorityQueue<>((a,b) -> Long.compare(net.get(b), net.get(a)));
    PriorityQueue<String> debtors   = new PriorityQueue<>((a,b) -> Long.compare(net.get(a), net.get(b)));
    for (Map.Entry<String, Long> e : net.entrySet()) {
        if      (e.getValue() > 0) creditors.offer(e.getKey());
        else if (e.getValue() < 0) debtors.offer(e.getKey());
    }

    // Step 3: greedy match
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

> **Senior callouts on the algorithm:**
> - *"Greedy, not optimal. The truly optimal minimum-transactions problem is NP-hard (subset-sum reduction). Greedy produces at most N-1 settlements which is the theoretical upper bound; typical inputs collapse to far fewer."*
> - *"Two priority queues are O(N log N) for the sort + O(N log N) for the match — total O(N log N) where N is the number of users with non-zero net balance."*
> - *"Settled users (net == 0) are skipped entirely — they don't appear in the queues. A circular debt like A→B→C→A fully cancels and emits ZERO settlements."*

### 4.5 Verification — dry-run the chain-debt collapse

```
Setup: 4 users A, B, C, D.

Expense 1: B pays $10 for A          → A owes B 1000
Expense 2: C pays $10 for B          → B owes C 1000
Expense 3: D pays $10 for C          → C owes D 1000

Balance graph (normalized):
   balances[B][A] = 1000
   balances[C][B] = 1000
   balances[D][C] = 1000

Net balances (sum of stored credits − sum of stored debits):
   net[A] = 0 − 1000 = -1000
   net[B] = 1000 − 1000 = 0     ← settled — skip
   net[C] = 1000 − 1000 = 0     ← settled — skip
   net[D] = 1000 − 0 = +1000

Heaps:
   creditors = [D@+1000]
   debtors   = [A@−1000]

Greedy match:
   pop D (+1000), pop A (-1000), settle min(1000, 1000) = 1000
   emit Settlement(debtorId=A, creditorId=D, amountCents=1000)
   newC = 0, newD = 0 → neither pushed back; both queues empty.

Result: 1 settlement: A pays D 1000.

vs. naïve "settle every pairwise debt": 3 settlements (A→B, B→C, C→D).
Win: 3× fewer transactions, same net outcome.
```

### 4.6 Verification — circular debt fully cancels

```
Setup: A→B 10, B→C 10, C→A 10 (three separate expenses).

Net balances after all three:
   net[A] = +1000 (paid for C) − 1000 (owes B) = 0
   net[B] = +1000 (paid for A) − 1000 (owes C) = 0
   net[C] = +1000 (paid for B) − 1000 (owes A) = 0

Heaps: both empty (no non-zero nets).
Settlements: 0.    ← circular debt naturally collapses to nothing.
```

> **This is the killer dry-run** — shows the algorithm correctly detects fully-cancelled cycles and emits zero settlements.

---

## STEP 5 — Extensibility (~8 min)

### 5.1 "Notify users when an expense is added or simplified"

> **Problem in current design:** *"Users only see their balance when they explicitly query. They don't get nudges."*
>
> **Pattern as the fix:** *"Observer. Add `ExpenseListener` interface with `onExpenseAdded(Expense)` and `onSettlementsComputed(List<Settlement>)`. ExpenseManager publishes events; SMS/email/push subscribers register independently. Same fire-outside-lock pattern as Inventory Management."*

### 5.2 "Multi-currency expenses"

> **Problem in current design:** *"`long cents` assumes one currency. A trip to Europe with mixed USD + EUR expenses breaks the additivity."*
>
> **Pattern as the fix:** *"Money becomes a record `Money(long minorUnits, Currency currency)`. Expenses store original currency. For balance queries, convert via an injected `FxRateProvider` to a 'home currency' chosen by the user. FX rates are a Strategy — historical (rate at expense time) vs. spot (rate at query time) are two implementations."*

### 5.3 "Groups with admins + per-group balance views"

> **Problem in current design:** *"All expenses live in one global ledger. Real apps scope expenses to groups (Trip-NYC, Roommates-2026)."*
>
> **Pattern as the fix:** *"Promote Group to an entity. Each Group owns its own ledger + its own pairwise balance graph. Members can belong to multiple groups. ExpenseManager becomes `GroupManager` that routes by groupId."*

### 5.4 "Truly optimal simplification"

> **Problem in current design:** *"Greedy gives ≤ N-1 settlements but isn't always optimal (NP-hard in general). For e.g. {A:+30, B:+30, C:-30, D:-30}, greedy emits 3 settlements when 2 suffice."*
>
> **Pattern as the fix:** *"Reduce to subset-sum: find a subset of users whose net balances sum to zero and settle them internally before greedy. For interview scope, mention this exists; building a proper solver is more than a Step-5 sketch."*

### 5.5 Other "what-if" answers

| Follow-up                                  | Answer                                                                                              |
| ------------------------------------------ | --------------------------------------------------------------------------------------------------- |
| "Persist across restart"                   | Inject `ExpenseRepository`; write on every add; replay on boot to reconstruct balances.            |
| "Audit log — who recorded what when"       | Already in `Expense.createdAt`. Add `recordedBy` field if expenses can be entered by someone other than the payer. |
| "Concurrent adds — finer-grained locking"  | Per-user lock pair with `compareTo`-ordered acquisition (same pattern as Inventory transfer). Worth it only at high contention. |
| "Settle a partial amount mid-trip"         | `recordSettlement(debtor, creditor, amount)` — treats a payment as an inverse expense; updates the balance graph the same way. |
| "Receipts / attachments"                   | Extend Expense with `List<Attachment>`. Attachment is a separate immutable record.                 |

---

## Design Patterns — Hello Interview's canonical 8

> **The base names ONE pattern** (Strategy) plus two principles (Facade, Information Expert). Strategy is justified by R3's three explicit split types — same one-sentence test as Logger and Rate Limiter.

### How this maps to Splitwise specifically

**Already in the BASE design:**

- **Strategy (#1)** ⭐ — `SplitStrategy` interface; three impls; per-type registry. Justified by R3. Name it in Step 2.
- **Facade (#8)** — `ExpenseManager` is the only class application code touches.
- **Information Expert** (GRASP) — Strategy impls own their own validation. Balance-graph normalization lives on the manager because the graph lives there.
- **Immutability** (principle) — User, Expense, Split, Settlement are all records.
- **Dependency Injection** (principle) — `Clock` injected for testable timestamps.

**Reach for on Step-5 follow-ups:**

| Follow-up                                  | Pattern (HI's 8)             | Your line                                                                                            |
| ------------------------------------------ | ---------------------------- | ---------------------------------------------------------------------------------------------------- |
| "Notify on expense / settlement"           | **Observer (#2)**            | *"ExpenseManager publishes events; SMS/email/push subscribe independently."*                       |
| "Multi-currency"                           | **Strategy (#1)** ⭐         | *"`FxRateProvider` interface; `HistoricalRate` vs `SpotRate` impls."*                              |
| "Different simplification algorithms"      | **Strategy (#1)** ⭐         | *"`SimplificationStrategy` interface; `Greedy` (default) vs `Optimal` (NP-hard solver) impls."*    |
| "Rate-limit add-expense per user"          | (Decorator, like Rate Limiter) | Wrap the manager's addExpense behind a rate-limited decorator.                                    |
| "Persist"                                  | (Repository — not HI's 8)    | Inject `ExpenseRepository`; write on every add.                                                    |

**Patterns to refuse:**

- **Singleton on ExpenseManager** — kills tests; DI a single instance instead.
- **`BalanceSheet` as a class** — speculative ceremony.
- **`Group` class for v1** — no group-level invariants in the requirements.
- **`Money` class for v1** — `long` cents + single currency is enough.

### One sentence to say at the end of Step 3

> *"The base design names Strategy by name — three split-type implementations registered in a map keyed by SplitType. Facade and Information Expert are the principles. Observer, Repository, and FX-rate Strategy land in Step 5 if the corresponding follow-ups come up."*

---

## Interview deep-dives — the questions you'll definitely get asked

### 1. Complexity (Big-O)

Let `U` = users, `P` = participants per expense, `E` = expenses recorded, `N` = users with non-zero net balance.

| Operation              | Time                                              | Space                | Notes                                                                              |
| ---------------------- | ------------------------------------------------- | -------------------- | ---------------------------------------------------------------------------------- |
| `addExpense`           | **`O(P)`** for the strategy + balance updates     | O(P) per call        | Strategy validation + P balance-graph updates                                      |
| `getBalance(u1, u2)`   | **`O(1)`**                                        | O(1)                 | Two map lookups                                                                    |
| `getNetBalance(u)`     | **`O(U)`**                                        | O(1)                 | Could be `O(1)` by maintaining a `net` map alongside `balances`                    |
| `simplifyDebts`        | **`O(U + N log N)`**                              | O(N)                 | One pass to build nets + heap operations                                          |
| Storage — balances     | -                                                 | **`O(U²)`** worst    | Dense in worst case; sparse in practice                                            |
| Storage — expenses     | -                                                 | **`O(E)`**           | Full ledger retained                                                               |

> **Senior callout:** *"getNetBalance is O(U) today; if it became hot I'd maintain a `Map<String, Long> net` alongside the pairwise balances, updated in `addOwed`. Two structures to keep consistent vs. cheap recomputation — a classic tradeoff. At realistic group sizes (< 50 users) the O(U) scan is fine."*

### 2. Concurrency / thread-safety

| Approach                                | When to use                                  | Cost                                                              |
| --------------------------------------- | -------------------------------------------- | ----------------------------------------------------------------- |
| **Coarse-grained `synchronized(this)`** ⭐ | **Default.** Correct + simple                | All operations serialize globally — fine for an in-process app    |
| Per-user lock pair with `compareTo` order | Each `addExpense` touches only 2 users    | Need ordered acquisition to avoid deadlock; significant added complexity |
| Optimistic CAS on each balance cell      | Extreme throughput, careful retry           | Hardest to get right; only after profiling                       |

> **The deadlock parallel:** *"Same pattern as Inventory Management's transfer — pairwise locks need ordered acquisition. If we ever moved to fine-grained, we'd `compareTo`-order the two user-ids and lock that way. For a single-user app at typical scale, the coarse lock is correct and trivial."*

### 3. Testing — what to write tests for

| Test category                | Cases to cover                                                                                              |
| ---------------------------- | ----------------------------------------------------------------------------------------------------------- |
| Each split type              | EQUAL with rounding (10 / 3); EXACT validates sum; PERCENT validates bps == 10000                          |
| Pairwise consistency         | After 3 expenses A→B B→A B→A, `getBalance` returns the correct signed value                                |
| Balance graph normalization  | Opposing balances cancel — assert `balances[A][B] > 0` implies `balances[B][A] == 0`                       |
| Simplification — chain       | A→B→C→D chain collapses to 1 settlement                                                                    |
| Simplification — cycle       | Circular debt fully cancels (0 settlements)                                                                |
| Simplification — invariants  | Sum of all settlements' debit amounts == sum of credit amounts; net of every user ends at 0                |
| Validation                   | unknown user, EXACT sum mismatch, PERCENT bps mismatch, negative amount → exception                       |

```java
@Test
void chain_collapses_to_one_settlement() {
    ExpenseManager m = new ExpenseManager();
    m.addUser(new User("A", "A", null));
    m.addUser(new User("B", "B", null));
    m.addUser(new User("C", "C", null));
    m.addUser(new User("D", "D", null));
    m.addExpense("B", 1000L, SplitType.EQUAL, Map.of("A", 0L), "");
    m.addExpense("C", 1000L, SplitType.EQUAL, Map.of("B", 0L), "");
    m.addExpense("D", 1000L, SplitType.EQUAL, Map.of("C", 0L), "");

    List<Settlement> s = m.simplifyDebts();
    assertEquals(1, s.size());
    assertEquals("A", s.get(0).debtorId());
    assertEquals("D", s.get(0).creditorId());
    assertEquals(1000L, s.get(0).amountCents());
}
```

### 4. SOLID mapping

| Letter                       | Where it shows up                                                                                          |
| ---------------------------- | ---------------------------------------------------------------------------------------------------------- |
| **S** Single Responsibility  | Manager = orchestration + graph state. Each Strategy = one split algorithm. Records = data only. |
| **O** Open/Closed            | New split type = new Strategy + one map entry. Manager unchanged. Same for FX-rate Strategy / Observer / Repository extensions. |
| **L** Liskov Substitution    | Any SplitStrategy substitutable behind the interface — same input/output contract, same exception expectations. |
| **I** Interface Segregation  | `SplitStrategy` is one method. No `validate()` + `calculate()` + `describe()` mixed in. |
| **D** Dependency Inversion   | Manager depends on `SplitStrategy` interface, not on `EqualSplitStrategy` directly. Clock injected for time. |

### 5. "Summarize your design in 30 seconds"

> *"One orchestrator + facade — `ExpenseManager` — and four immutable records (User, Expense, Split, Settlement). Strategy in the base for three split types (EQUAL, EXACT, PERCENT), justified by R3's explicit enumeration. Money is `long` cents everywhere — never floats; PERCENT uses basis points so 33.33% is `3333` with integer arithmetic and the last participant absorbs the rounding remainder so totals are exact. The pairwise balance graph is `Map<String, Map<String, Long>>`, normalized so we never store both `balances[A][B] > 0` AND `balances[B][A] > 0` — `addOwed` cancels opposing balances first. The senior signal is the greedy debt simplification: compute net balance per user, push creditors into a max-heap and debtors into a min-heap, greedy-match until both empty. Produces ≤ N-1 settlements — typically way fewer. Drives a chain A→B→C→D to a single A pays D settlement; collapses circular debt to zero. Extensions: Observer for notifications, FX-rate Strategy for multi-currency, Repository for persistence, Group as a first-class entity once it gains real behavior."*

That's ~55 seconds. Hits: Strategy + cents discipline + balance normalization + greedy simplification + the dry-run insight.

---

## Closing soundbites (memorize these)

- **Opening:** *"Splitwise has two correctness traps — money as float and naïve debt graphs that grow forever. Let me handle both upfront."*
- **Why `long` cents:** *"Floats can't represent 0.10 exactly; equal splits would drift over hundreds of transactions."*
- **Why basis points for PERCENT:** *"33.33% as 3333 bps. Integer arithmetic, no float drift, last participant absorbs the remainder so sums are exact."*
- **Why balance-graph normalization:** *"Never store both directions positive. `addOwed` cancels opposing balances first. Keeps `getBalance` clean and the storage minimal."*
- **Why greedy simplification:** *"Optimal is NP-hard. Greedy gives at most N-1 settlements — typically way fewer. Tradeoff is honest signal; named-and-tradeoff is the senior framing."*
- **On the algorithm:** *"Net balance per user; max-heap of creditors and min-heap of debtors; greedy match. Settled users (net=0) are skipped — that's why circular debt produces zero settlements."*
- **On Strategy in the base:** *"Three explicit split types in the requirements means Strategy passes the one-sentence test in the base — not Step 5."*

---

## Top mistakes that lose points

- **Money as `double` / `float`** — instant lose. Use `long` cents.
- **Percent as `double` percentage** — drift; use basis-point `long`.
- **Distributing rounding equally** instead of dumping the remainder on the last participant — the sum doesn't equal the total; pennies vanish/appear.
- **Storing both `balances[A][B] > 0` AND `balances[B][A] > 0`** — graph un-normalized; `getBalance` returns nonsense unless you remember to subtract both sides.
- **Self-debt on the payer** — forgetting the `userId != paidById` guard creates a balance from a user to themselves.
- **Naïve "pay everyone you owe" instead of simplification** — N expense gives O(N) settlements; greedy gives ≤ N-1 total system-wide.
- **Not skipping zero-net users in simplification** — circular debt would emit useless `(X pays X, 0)` entries.
- **Validation AFTER mutation** — a bad EXACT-sum input partly updates the graph before throwing. Validate first.
- **Forgetting concurrency** — all manager mutations need `synchronized`.
- **Promoting `Money`, `Group`, `BalanceSheet`, `Currency` to classes for v1** — speculative ceremony when records + maps are sufficient.

---

## Files in this folder (your reference implementation)

| File                                              | What it shows                                                                            |
| ------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| `model/User.java`                                 | Immutable record                                                                          |
| `model/Split.java`                                | Computed split (userId + amountCents)                                                    |
| `model/Settlement.java`                           | Debtor → creditor instruction                                                            |
| `model/Expense.java`                              | Immutable ledger entry with defensive `List.copyOf` on splits                            |
| `splittype/SplitType.java`                        | Enum — EQUAL / EXACT / PERCENT                                                           |
| `splittype/SplitStrategy.java`                    | Strategy interface                                                                       |
| `splittype/EqualSplitStrategy.java`               | floor + remainder-on-last; sums to total exactly                                          |
| `splittype/ExactSplitStrategy.java`               | Validates sum == total; rejects otherwise                                                |
| `splittype/PercentSplitStrategy.java`             | Basis points; validates bps sum == 10000; last absorbs cent-remainder                    |
| `ExpenseManager.java`                             | **The hot class** — Strategy registry, balance-graph normalization, greedy simplification |
| `SplitwiseDriver.java`                            | 6 scenarios — each split type, chain collapse, circular cancel, validation               |

Run from the project root:

```bash
mvn -q compile exec:java \
  -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.splitwise.SplitwiseDriver
```
