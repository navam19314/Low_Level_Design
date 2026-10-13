# Insurance Application (term life, Ethos-style)

> 30–40 min round. **Ethos's core product**: expect questions on the status flow and the rules. Phases: **Requirements (5) → Entities (3) → Class Design (10–15) → Implementation (10) → Extensibility (5)**.

---

## 0. The problem in plain words (read first, don't say it in the interview)

**Picture buying term insurance online.** You fill in your details (age, smoker?, how much cover you want, your income) and press Submit. The insurer runs its checks: too old? Cover too high for your income? You're either **approved** with a yearly price (the premium) or **rejected** with the reasons. If approved, your **policy is issued**.

**The whole system is 3 actions:**
```
Apply   →  apply(details)    → a DRAFT application
Submit  →  submit(app)       → run ALL the rules → APPROVED + premium, or REJECTED + reasons
Issue   →  issuePolicy(app)  → only if APPROVED, and only once
```

**Real world → code**
| In the real world | In the code |
|---|---|
| Your form | `Application`: your details + status + premium or reasons |
| Draft → Approved / Rejected → Issued | `ApplicationStatus` with `canMoveTo` |
| The insurer's checks ("max age 60") | `UnderwritingRule`: one class per rule |
| The final contract | `Policy` |

**Only 2 things are tricky:**
1. **What's allowed depends on the status.** You can't issue a rejected application, and you can't issue twice. Every change goes through `moveTo`, which checks `canMoveTo`.
2. **The rules keep changing,** so each rule is its own class in a list (Strategy). Run **all** of them, so a rejected person hears every reason at once.

**Picture this run before you start coding** (rules: age 18–60, cover ≤ 20 × income; premium: ₹1 per ₹1,000 of cover, smokers pay double):
```
Asha  28, ₹1 crore cover, ₹10 lakh income → APPROVED ₹10,000/yr → issue → POL-APP-1 → issue again: refused
Meera smoker, ₹1 crore                    → APPROVED ₹20,000/yr
Ravi  65, ₹5 crore, ₹10 lakh income        → REJECTED [age outside 18-60, cover > 20x income]
50 clicks on "Issue" at once              → exactly 1 policy
```

---

## 1. Requirements (~5 min)

**Ask:** which details? Which rules? How is the price calculated? Can it be issued twice? Double clicks?

```
1. apply(name, age, smoker, coverage, income) → a DRAFT application
2. submit → run every rule → APPROVED with a yearly premium, or REJECTED with all the reasons
3. issuePolicy → only when APPROVED, only once
4. Safe with double clicks (no two policies)

Out of scope: a multi-step form, price loadings, manual review, payment, emails
```
If the brief says **"use the State pattern"**, write Extension 1 instead of the enum.

---

## 2. Entities & Relationships (~3 min)

```
InsuranceService
   ├── Application (many)    details, status, premium / reasons
   ├── UnderwritingRule (list)
   └── creates Policy
```
- **Underwriting runs inside `submit`,** so the statuses are just DRAFT → APPROVED / REJECTED → ISSUED.
- **No Applicant or Quote class:** the details and the premium live on `Application`.

---

## 3. Class Design (~10 min)

```
InsuranceService    apply(...) → Application · submit(id) → status · issuePolicy(id) → Policy

Application         id, name, age, smoker, coverage, annualIncome, status, annualPremium, rejectionReasons    moveTo(next)
ApplicationStatus   DRAFT, APPROVED, REJECTED, ISSUED      canMoveTo(next)
Policy              policyNumber, applicationId, annualPremium

UnderwritingRule    check(app) → null (OK) or the reason     ← AgeRule, CoverageToIncomeRule
```
- **Thread safety:** every service method is `synchronized`.

### Design patterns: the problem → the fix (how to say it)
**Strategy for the rules.**
*Problem:* the actuaries add rules every month. *Without it:* one long `submit()` full of `if`s, edited every time. *With it:* each rule is a small class, and `submit` loops over the list.
**Say:** *"A new rule is a new class added to the list. `submit` never changes."*

**Enum + `canMoveTo` for the status.**
*Problem:* what's allowed depends on the status. *Without it:* `if (status == ...)` checks scattered everywhere, and one forgotten check means a rejected person gets a policy. *With it:* one list of legal moves, and every change goes through `moveTo`.
**Say:** *"While states only limit the next move, an enum is enough. If each state gets its own behaviour, I'd use the State pattern."*

**Not used:** Chain of Responsibility (it stops at the first match, but we want **all** the reasons) and Singleton (it's hard to test).

### ACID (if asked)
**Atomic:** the rules run *before* the status changes, so it's approved or rejected in one step. **Consistent:** APPROVED always has a premium, and a policy is issued at most once. **Isolated:** `synchronized`. **Durable:** not in memory; *"in production, a DB transaction."*

---

## 4. Implementation (~10 min)

**Write it in this order:** `ApplicationStatus` → `Application` → the rule interface and one rule → `InsuranceService`.

```java
public enum ApplicationStatus {
    DRAFT, APPROVED, REJECTED, ISSUED;

    public boolean canMoveTo(ApplicationStatus next) {
        switch (this) {
            case DRAFT:    return next == APPROVED || next == REJECTED;
            case APPROVED: return next == ISSUED;
            default:       return false;                 // REJECTED, ISSUED are final
        }
    }
}

public class Application {
    private final String id, name;
    private final int age;
    private final boolean smoker;
    private final long coverage, annualIncome;
    private ApplicationStatus status = ApplicationStatus.DRAFT;
    private long annualPremium;
    private List<String> rejectionReasons = new ArrayList<>();

    public void moveTo(ApplicationStatus next) {
        if (!status.canMoveTo(next)) {
            throw new IllegalStateException("Application " + id + ": cannot go from " + status + " to " + next);
        }
        status = next;
    }
    // constructor, getters, setAnnualPremium, setRejectionReasons
}

public class Policy { private final String policyNumber, applicationId; private final long annualPremium; /* ctor + getters */ }
```

```java
public interface UnderwritingRule {
    String check(Application app);                       // null = OK, otherwise the reason
}

public class AgeRule implements UnderwritingRule {
    private final int minAge, maxAge;
    // constructor

    public String check(Application app) {
        if (app.getAge() < minAge || app.getAge() > maxAge) return "Age " + app.getAge() + " is outside " + minAge + "-" + maxAge;
        return null;
    }
}
// CoverageToIncomeRule: the same shape. coverage > income × 20 → "Cover is more than 20x income"
```

```java
public class InsuranceService {
    private final Map<String, Application> applications = new HashMap<>();
    private final List<UnderwritingRule> rules;
    private int nextApplicationNumber = 1;

    // constructor takes the rules

    public synchronized Application apply(String name, int age, boolean smoker, long coverage, long annualIncome) {
        Application app = new Application("APP-" + nextApplicationNumber++, name, age, smoker, coverage, annualIncome);
        applications.put(app.getId(), app);
        return app;
    }

    public synchronized ApplicationStatus submit(String applicationId) {
        Application app = findApplication(applicationId);
        List<String> reasons = new ArrayList<>();
        for (UnderwritingRule rule : rules) {                // run EVERY rule
            String reason = rule.check(app);
            if (reason != null) reasons.add(reason);
        }
        if (reasons.isEmpty()) {
            app.moveTo(ApplicationStatus.APPROVED);          // throws if already submitted
            app.setAnnualPremium(premium(app));
        } else {
            app.moveTo(ApplicationStatus.REJECTED);
            app.setRejectionReasons(reasons);
        }
        return app.getStatus();
    }

    public synchronized Policy issuePolicy(String applicationId) {
        Application app = findApplication(applicationId);
        app.moveTo(ApplicationStatus.ISSUED);                // throws unless APPROVED, so only once
        return new Policy("POL-" + app.getId(), app.getId(), app.getAnnualPremium());
    }

    private long premium(Application app) {                 // ₹1 per ₹1,000 of cover; smokers ×2
        long premium = app.getCoverage() / 1000;
        return app.isSmoker() ? premium * 2 : premium;
    }

    private Application findApplication(String applicationId) {
        Application app = applications.get(applicationId);
        if (app == null) throw new NoSuchElementException("Unknown application " + applicationId);
        return app;
    }
}
```

**Verify out loud:** walk through the run from section 0. For the double click, say: *"The first click moves APPROVED → ISSUED. The second sees ISSUED, and ISSUED → ISSUED isn't allowed, so it throws. That's one policy."*

---

## 5. Extensibility (~5 min)

Name **which class changes**, write the few new lines, and don't redesign the rest.

**Quick revision:**

| # | Interviewer asks | One-line answer |
|---|---|---|
| 1 | "Use the State pattern" | A class per status; each allows only its own actions |
| 2 | "A multi-step form" | The details become answers in a map; each question has a type that validates it |
| 3 | "Smokers / high BMI pay more" | A rule can **load** the price, not just reject |
| 4 | "Some cases need a human" | New status UNDER_REVIEW; a person approves or rejects later |
| 5 | "Email the user, keep an audit trail" | Observer: listeners hear every status change |
| 6 | "Millions of users, the lock is slow" | Lock one application, not the whole service |

---

### 1. "Use the State pattern"
**Idea:** each status is a class, and every action is **refused by default**. A state overrides only what's legal in it.
```java
public interface ApplicationState {
    ApplicationStatus status();
    default void submit(Application app)  { throw notAllowed("submit"); }
    default Policy issue(Application app) { throw notAllowed("issue"); }

    private IllegalStateException notAllowed(String action) {
        return new IllegalStateException("Cannot " + action + " when " + status());
    }
}

public class DraftState implements ApplicationState {       // only submit is allowed
    public ApplicationStatus status() { return ApplicationStatus.DRAFT; }
    public void submit(Application app) { /* run rules → app.setState(new ApprovedState()) or new FinalState(REJECTED) */ }
}

public class ApprovedState implements ApplicationState {    // only issue is allowed
    public ApplicationStatus status() { return ApplicationStatus.APPROVED; }
    public Policy issue(Application app) {
        app.setState(new FinalState(ApplicationStatus.ISSUED));
        return new Policy("POL-" + app.getId(), app.getId(), app.getAnnualPremium());
    }
}

public class FinalState implements ApplicationState {       // REJECTED, ISSUED: nothing is allowed
    private final ApplicationStatus status;
    public FinalState(ApplicationStatus status) { this.status = status; }
    public ApplicationStatus status() { return status; }
}
// Application: private ApplicationState state = new DraftState();   submit() { state.submit(this); }
```
**Say:** *"Use it when each state has its own behaviour. A new state is a new class."*

---

### 2. "A multi-step form (Personal → Health)"
**Idea:** the details become **answers in a map**, so a new question needs no new field. Each question's type validates its own answer.
```java
public enum QuestionType {
    NUMBER, YES_NO;
    public boolean isValid(String value) {
        if (this == NUMBER) return value.matches("\\d+");
        return value.equalsIgnoreCase("yes") || value.equalsIgnoreCase("no");
    }
}

// Application: Map<String, String> answers instead of the fields
// InsuranceService
public synchronized void answer(String applicationId, String questionId, String value) {
    Application app = findApplication(applicationId);
    if (app.getStatus() != ApplicationStatus.DRAFT) throw new IllegalStateException("Already submitted");
    if (!questions.get(questionId).isValid(value)) throw new IllegalArgumentException("Bad answer: " + value);
    app.putAnswer(questionId, value);
}
```
**Say:** *"Questions become data, so product can add one without changing the code."*

---

### 3. "Smokers and high BMI pay more"
**Idea:** a rule returns one of: **OK**, **charge X% more**, or **reject**. The extra percentages add up.
```java
public class RuleResult {
    final boolean rejected;
    final int extraPercent;
    final String reason;
    public static RuleResult ok()                       { return new RuleResult(false, 0, null); }
    public static RuleResult extra(int pct, String why) { return new RuleResult(false, pct, why); }
    public static RuleResult reject(String why)         { return new RuleResult(true, 0, why); }
}

// submit(): in the loop
//   if (r.rejected) reasons.add(r.reason); else totalExtra += r.extraPercent;
// premium = base * (100 + totalExtra) / 100
```
**Check:** BMI 31 → +25% → ₹10,000 becomes ₹12,500.

---

### 4. "Some cases need a human underwriter"
**Idea:** a new status, UNDER_REVIEW. A rule can say "refer" (e.g. cover above ₹5 crore), and a person decides later.
```java
// ApplicationStatus: + UNDER_REVIEW
//   DRAFT        → APPROVED, REJECTED, UNDER_REVIEW
//   UNDER_REVIEW → APPROVED, REJECTED

public synchronized void reviewDecision(String applicationId, boolean approve) {
    Application app = findApplication(applicationId);
    if (approve) {
        app.moveTo(ApplicationStatus.APPROVED);          // throws unless UNDER_REVIEW
        app.setAnnualPremium(premium(app));
    } else {
        app.moveTo(ApplicationStatus.REJECTED);
    }
}
```
**Say:** *"Only the status list and one method change. A reject from any rule still wins over a refer."*

---

### 5. "Email the user, update the CRM, keep an audit trail"
**Idea:** the service just announces "status changed". Email, CRM and audit are each a listener (Observer).
```java
public interface StatusListener {
    void statusChanged(Application app, ApplicationStatus from, ApplicationStatus to);
}

// InsuranceService
private final List<StatusListener> listeners = new ArrayList<>();
public synchronized void addListener(StatusListener l) { listeners.add(l); }

// after each moveTo(...) in submit / issuePolicy:
for (StatusListener l : listeners) l.statusChanged(app, from, app.getStatus());

// service.addListener(new EmailListener());  service.addListener(new AuditLog());
```
**Say:** *"A new channel is a new listener. Emails are slow, so in production a listener just puts the event on a queue."*

---

### 6. "Millions of users, the single lock is slow"
**First say:** *"Each call holds the lock for microseconds. I'd measure first."*

**Idea:** applications are independent, so lock **one application**, not the whole service.
```java
private final Map<String, Application> applications = new ConcurrentHashMap<>();

public Policy issuePolicy(String applicationId) {            // no synchronized on the method
    Application app = findApplication(applicationId);
    synchronized (app) {                                      // only this application waits
        app.moveTo(ApplicationStatus.ISSUED);
        return new Policy("POL-" + app.getId(), app.getId(), app.getAnnualPremium());
    }
}
```
**Say:** *"A double click still gives one policy, but different users never wait for each other. Across servers: `UPDATE ... SET status = 'ISSUED' WHERE id = ? AND status = 'APPROVED'`, where 0 rows updated means it was already issued."*

---

```bash
mvn -q compile exec:java -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.InsuranceDriver
```
