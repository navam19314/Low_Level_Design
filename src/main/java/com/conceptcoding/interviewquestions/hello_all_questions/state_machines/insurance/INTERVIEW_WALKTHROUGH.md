# Insurance Policy Application Flow

> **Why this one:** it's Ethos's core product (online term-life insurance). Expect the interviewer to know the domain well and to push on the state flow and the rules.
> **The brief:** applicant, multi-step questionnaire, eligibility rules, quote generation, policy issuance. **Strategy** for underwriting rules, **State** for application status: `DRAFT → SUBMITTED → UNDERWRITING → APPROVED / REJECTED → ISSUED`.
>
> **The crux (what's really being tested):**
> 1. **State pattern done properly:** each state allows only its own actions, and an illegal action is impossible to forget to check.
> 2. **Rules as pluggable strategies:** a new eligibility rule is a new class, with no edits to existing code.
> 3. **Questions as data:** the questionnaire can change without changing `Application`.
>
> **Families:** F3 State machine + F5 Swappable policy. See [foundations](../../00_AMAZON_LLD_FOUNDATIONS.md).

---

## 1. Plain-language picture

### What actually happens when you buy term insurance online
```
1. You start an application                       → DRAFT
2. You fill a form over several screens:
   Personal (age, income) → Coverage (₹1 crore, 30 yrs) → Health (height, weight, smoker)
3. You press Submit; answers are frozen            → SUBMITTED
4. The insurer checks your risk ("underwriting")   → UNDERWRITING
5a. OK: they offer a price (a quote)               → APPROVED
5b. Not OK: refused, with reasons                  → REJECTED
6. You accept the quote and the policy is created  → ISSUED
```

### The application is a file moving between desks
Think of an insurance office. Your paper file moves from desk to desk:
- At the **draft desk** you may write on the form. You can't get a policy here.
- At the **submitted desk** nobody writes on it; it waits for an underwriter.
- At the **underwriting desk** an underwriter decides: approve with a price, or reject.
- At the **approved desk** the clerk can print your policy. Only here.

Each desk knows **only what it is allowed to do**. If you ask the draft desk for a policy, it says "not here". That's the **State pattern**: one class per desk, and each class implements only its own actions. Everything else is refused by default.

### Underwriting is a panel of checkers
Each checker looks at one thing and gives a verdict:
```
Age checker      : 18–65?                     → OK / DECLINE
Income checker   : cover ≤ 20 × income?       → OK / DECLINE
BMI checker      : BMI ≤ 35? 30–35 costs more → OK / +25% / DECLINE
Smoker checker   : smoker costs more          → OK / +50%
```
**All** checkers run, so a rejection lists every reason. If nobody declines, the "+X%" loadings are added up and applied to the base price. A new rule from the actuaries is just a new checker on the panel: that's **Strategy**.

### The price
```
base premium  = cover ÷ 1,000 × rate for your age band     ₹1 crore, age 28 → ₹10,000 / year
final premium = base × (100 + total loading %) ÷ 100        smoker + BMI 31 → +75% → ₹17,500 / year
```

---

## 2. From story to design

| # | Decision | Rejected | Why |
|---|---|---|---|
| D1 | **State pattern**: `ApplicationState` interface, one class per state | an enum + `if (status == ...)` in every method | Here each state **does different work** (draft validates answers, approved creates a policy). With ifs, every new action needs a check in every method; one missed check = a policy issued from DRAFT. |
| D2 | Interface methods are `default` and **throw**; each state overrides only its legal actions | every state implementing every method | Final states (REJECTED, ISSUED) need zero code, and "not allowed" can't be forgotten. |
| D3 | `Application` (the *context*) just delegates: `submit() { state.submit(this); }` | the service deciding what's legal | Rules about *when* live in one place: the state classes. |
| D4 | One `FinalState(status)` class for REJECTED and ISSUED | two identical empty classes | Both allow nothing. |
| D5 | Each underwriting rule is a **Strategy** (`UnderwritingRule`) | one big `evaluate()` method full of ifs | New rule = new class. Each rule is testable on its own. |
| D6 | A rule returns **accept / load X% / decline**, and the `Underwriter` runs **all** rules | stop at the first decline | The applicant (and regulators) get every reason at once. |
| D7 | Questions are **data** (`Questionnaire` → steps → `Question{id, type}`); answers are `Map<questionId, String>` | fields like `age`, `smoker` on `Application` | Product teams add questions all the time. Rules read answers by id (`getNumber("age")`). |
| D8 | `QuestionType.isValid()` validates answers | validation in the service | Each type knows its own rule (NUMBER parses, YES_NO is yes/no). |
| D9 | Money as `long` rupees; rate in **paise** per ₹1,000 | `double` | No rounding errors in prices. |
| D10 | `Application` methods are `synchronized` | no locking | A double-clicked "Issue policy" button must not issue two policies. |
| D11 | Out of base: manual review (REFERRED), quote expiry, conditional questions, payment, audit | building all | Follow-ups (§5). |

### Class shape
```
InsuranceService                       ← the service the web app calls
  startApplication · answer · nextStep · submit · underwrite · issuePolicy

Application                            ← State-pattern CONTEXT
  id, applicantName, Questionnaire, Map<questionId, answer>
  ApplicationState state, Quote, rejectionReasons, Policy
  answer() submit() startUnderwriting() recordDecision() issue()  → all delegate to state

«interface» ApplicationState           ← every action throws by default
  ├── DraftState         answer(), submit()
  ├── SubmittedState     startUnderwriting()
  ├── UnderwritingState  recordDecision()  → APPROVED or REJECTED
  ├── ApprovedState      issue()           → ISSUED
  └── FinalState         (nothing)         REJECTED, ISSUED

«interface» UnderwritingRule → RuleResult{accept | load % | decline}
  ├── AgeRule  ├── CoverageToIncomeRule  ├── BmiRule  └── SmokerRule
Underwriter (runs all rules) + PremiumCalculator → UnderwritingDecision{approve(Quote) | reject(reasons)}

Questionnaire → QuestionnaireStep → Question{id, text, QuestionType}
Quote { coverage, basePremium, loading %, annualPremium }     Policy { number, holder, cover, premium }
```

---

## 3. Patterns that earn their place

| Pattern | In the base? | Problem it solves | Without it |
|---|---|---|---|
| **State** (`ApplicationState`) | **Yes** | each status allows different actions and does different work | `if (status == DRAFT) ... else if ...` repeated in 5 methods; adding a status edits all of them |
| **Strategy** (`UnderwritingRule`) | **Yes** | many independent eligibility/risk rules that change often | one giant method; every rule change risks breaking the others |
| **Factory** (products) | No | term life vs health: different questionnaire + rules per product | Q8 |
| **Observer** (status events) | No | email the applicant, update CRM on each status change | Q12 |

**Why State here but an enum in Food Delivery?** Say this; interviewers love the contrast.
> *"In food delivery each status only limits the next move. That's bookkeeping, so an enum with `canMoveTo` is enough. Here each state does different **work**: draft validates answers, underwriting records a decision, approved builds a policy. So each state gets its own class."*

**Tempting but wrong here:**
- **Chain of Responsibility for rules:** in a chain, a handler can stop the request. We want **every** rule to run and all reasons collected. That's a list of strategies.
- **Builder for `Application`:** answers arrive one at a time over several screens. That's the multi-step flow, not object construction.
- **Singleton service:** create once and inject.

---

## 4. The 45-minute run

### Clarify (min 0–5)
> **You:** Flow: the applicant fills a multi-step form, submits, underwriting runs rules, then it's either approved with a quote or rejected; on acceptance a policy is issued. Correct?
> **Interviewer:** Yes.
> **You:** Can the applicant save and come back between steps, and edit answers before submitting?
> **Interviewer:** Yes. After submit, answers are locked.
> **You:** Do rules only accept or decline, or can they also raise the price?
> **Interviewer:** Both: some rules decline, some add a loading.
> **You:** On rejection, do we show all the reasons or just the first?
> **Interviewer:** All.
> **You:** Is underwriting automatic, or can a human review it?
> **Interviewer:** Automatic for now.
> **You:** Payment and quote expiry are out of scope?
> **Interviewer:** For now.

```
In scope:  multi-step questionnaire (validate answers, find next incomplete step)
           submit (all answered) → underwrite (all rules; loadings or decline) → quote
           issue policy from an approved quote · illegal actions refused · thread-safe
Out:       manual review, quote expiry, conditional questions, payment, multiple products
```

### Timeline
| Min | Do |
|---|---|
| 5–10 | Class shape. Say: *"State pattern for status, because each state does different work. Strategy for rules."* |
| 10–20 | Code step 1: **`ApplicationState`** + the 5 state classes. This is what they asked to see. |
| 20–25 | Code step 2: `Application` (delegation + answers map). |
| 25–33 | Code step 3: `UnderwritingRule`, `RuleResult`, 2 rules (`AgeRule`, `SmokerRule`), `Underwriter`, `PremiumCalculator`. Say *"BMI and income rules follow the same shape."* |
| 33–36 | Code step 4: `InsuranceService` + the thin questionnaire classes. |
| 36–40 | Dry run: one approval with loadings, plus an illegal action. |
| 40–45 | Follow-ups. |

### The code you write, in this order

**Step 1: the State pattern** ([application/](application/))
```java
public enum ApplicationStatus {
    DRAFT, SUBMITTED, UNDERWRITING, APPROVED, REJECTED, ISSUED
}

// Every action is refused by default; each state overrides ONLY what's legal in it.
public interface ApplicationState {

    ApplicationStatus status();

    default void answer(Application app, String questionId, String value) { throw notAllowed("answer questions"); }
    default void submit(Application app)                                  { throw notAllowed("submit"); }
    default void startUnderwriting(Application app)                       { throw notAllowed("start underwriting"); }
    default void recordDecision(Application app, UnderwritingDecision d)  { throw notAllowed("record a decision"); }
    default Policy issue(Application app)                                 { throw notAllowed("issue a policy"); }

    private IllegalStateException notAllowed(String action) {            // Java 9+: private interface method
        return new IllegalStateException("Cannot " + action + " when the application is " + status());
    }
}

public class DraftState implements ApplicationState {
    public ApplicationStatus status() { return ApplicationStatus.DRAFT; }

    @Override
    public void answer(Application app, String questionId, String value) {
        Question q = app.getQuestionnaire().getQuestion(questionId);       // throws if unknown
        if (!q.getType().isValid(value)) {
            throw new IllegalArgumentException("Invalid answer for '" + q.getText() + "': " + value);
        }
        app.putAnswer(questionId, value.trim());
    }

    @Override
    public void submit(Application app) {
        List<String> missing = app.getMissingQuestionIds();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Cannot submit, unanswered questions: " + missing);
        }
        app.setState(new SubmittedState());
    }
}

public class SubmittedState implements ApplicationState {
    public ApplicationStatus status() { return ApplicationStatus.SUBMITTED; }

    @Override
    public void startUnderwriting(Application app) { app.setState(new UnderwritingState()); }
}

public class UnderwritingState implements ApplicationState {
    public ApplicationStatus status() { return ApplicationStatus.UNDERWRITING; }

    @Override
    public void recordDecision(Application app, UnderwritingDecision decision) {
        if (decision.isApproved()) {
            app.setQuote(decision.getQuote());
            app.setState(new ApprovedState());
        } else {
            app.setRejectionReasons(decision.getRejectionReasons());
            app.setState(new FinalState(ApplicationStatus.REJECTED));
        }
    }
}

public class ApprovedState implements ApplicationState {
    public ApplicationStatus status() { return ApplicationStatus.APPROVED; }

    @Override
    public Policy issue(Application app) {
        Quote quote = app.getQuote();
        Policy policy = new Policy("POL-" + app.getId(), app.getId(), app.getApplicantName(),
                                   quote.getCoverage(), quote.getAnnualPremium(), Instant.now());
        app.setPolicy(policy);
        app.setState(new FinalState(ApplicationStatus.ISSUED));
        return policy;
    }
}

// REJECTED and ISSUED: overrides nothing, so every action throws
public class FinalState implements ApplicationState {
    private final ApplicationStatus status;
    public FinalState(ApplicationStatus status) { this.status = status; }
    public ApplicationStatus status() { return status; }
}
```
**Shape to remember:** interface with throwing defaults → each state overrides its 1–2 legal actions → the action calls `app.setState(next)`.

**Step 2: the context** ([application/Application.java](application/Application.java))
```java
public class Application {

    private final String id;
    private final String applicantName;
    private final Questionnaire questionnaire;
    private final Map<String, String> answers = new LinkedHashMap<>();   // questionId → answer
    private ApplicationState state = new DraftState();
    private Quote quote;
    private List<String> rejectionReasons = new ArrayList<>();
    private Policy policy;

    public Application(String id, String applicantName, Questionnaire questionnaire) {
        this.id = id;
        this.applicantName = applicantName;
        this.questionnaire = questionnaire;
    }

    // actions just delegate; synchronized so a double click can't issue twice
    public synchronized void answer(String questionId, String value) { state.answer(this, questionId, value); }
    public synchronized void submit()                                { state.submit(this); }
    public synchronized void startUnderwriting()                     { state.startUnderwriting(this); }
    public synchronized void recordDecision(UnderwritingDecision d)  { state.recordDecision(this, d); }
    public synchronized Policy issue()                               { return state.issue(this); }
    public synchronized ApplicationStatus getStatus()                { return state.status(); }

    // multi-step form: first step with an unanswered question, or null when complete
    public synchronized QuestionnaireStep getNextIncompleteStep() {
        for (QuestionnaireStep step : questionnaire.getSteps()) {
            for (Question q : step.getQuestions()) {
                if (!answers.containsKey(q.getId())) return step;
            }
        }
        return null;
    }

    public synchronized List<String> getMissingQuestionIds() {
        List<String> missing = new ArrayList<>();
        for (Question q : questionnaire.getAllQuestions()) {
            if (!answers.containsKey(q.getId())) missing.add(q.getId());
        }
        return missing;
    }

    // typed reads for the rules
    public synchronized long getNumber(String questionId)    { return Long.parseLong(answers.get(questionId)); }
    public synchronized boolean getYesNo(String questionId)  { return "yes".equalsIgnoreCase(answers.get(questionId)); }
    public synchronized String getText(String questionId)    { return answers.get(questionId); }

    // package-private: only the state classes call these
    void setState(ApplicationState state)           { this.state = state; }
    void putAnswer(String questionId, String value) { answers.put(questionId, value); }
    void setQuote(Quote quote)                      { this.quote = quote; }
    void setRejectionReasons(List<String> reasons)  { this.rejectionReasons = new ArrayList<>(reasons); }
    void setPolicy(Policy policy)                   { this.policy = policy; }
    // + getters
}
```
**Package-private setters** are why the states live in the same package as `Application`: only states can change the status, and the service can't bypass them.

**Step 3: rules as strategies** ([underwriting/](underwriting/))
```java
public class RuleResult {
    private final boolean declined;
    private final int loadingPercent;
    private final String reason;

    private RuleResult(boolean declined, int loadingPercent, String reason) {
        this.declined = declined;
        this.loadingPercent = loadingPercent;
        this.reason = reason;
    }

    public static RuleResult accept()                         { return new RuleResult(false, 0, null); }
    public static RuleResult load(int percent, String reason) { return new RuleResult(false, percent, reason); }
    public static RuleResult decline(String reason)           { return new RuleResult(true, 0, reason); }
    // + getters
}

public interface UnderwritingRule {
    RuleResult evaluate(Application application);
}

public class AgeRule implements UnderwritingRule {
    private final int minAge;
    private final int maxAge;
    public AgeRule(int minAge, int maxAge) { this.minAge = minAge; this.maxAge = maxAge; }

    @Override
    public RuleResult evaluate(Application app) {
        long age = app.getNumber("age");
        if (age < minAge || age > maxAge) {
            return RuleResult.decline("Age " + age + " is outside " + minAge + "-" + maxAge);
        }
        return RuleResult.accept();
    }
}

public class SmokerRule implements UnderwritingRule {
    @Override
    public RuleResult evaluate(Application app) {
        return app.getYesNo("smoker") ? RuleResult.load(50, "smoker +50%") : RuleResult.accept();
    }
}
// BmiRule (>35 decline, 30–35 +25%) and CoverageToIncomeRule (cover > 20× income → decline): same shape

public class Underwriter {
    private final List<UnderwritingRule> rules;
    private final PremiumCalculator premiumCalculator;

    public Underwriter(List<UnderwritingRule> rules, PremiumCalculator premiumCalculator) {
        this.rules = new ArrayList<>(rules);
        this.premiumCalculator = premiumCalculator;
    }

    // run ALL rules so a rejection lists every reason
    public UnderwritingDecision evaluate(Application app) {
        List<String> declineReasons = new ArrayList<>();
        List<String> loadingReasons = new ArrayList<>();
        int totalLoading = 0;
        for (UnderwritingRule rule : rules) {
            RuleResult result = rule.evaluate(app);
            if (result.isDeclined()) {
                declineReasons.add(result.getReason());
            } else if (result.getLoadingPercent() > 0) {
                totalLoading += result.getLoadingPercent();
                loadingReasons.add(result.getReason());
            }
        }
        if (!declineReasons.isEmpty()) return UnderwritingDecision.reject(declineReasons);
        return UnderwritingDecision.approve(premiumCalculator.calculate(app, totalLoading, loadingReasons));
    }
}

public class PremiumCalculator {
    // rate in paise per ₹1,000 of cover per year: integer math only
    public Quote calculate(Application app, int loadingPercent, List<String> loadingReasons) {
        long age = app.getNumber("age");
        long coverage = app.getNumber("coverage");
        long ratePaisePer1000 = age < 30 ? 100 : age < 45 ? 200 : 500;
        long basePremium = coverage / 1000 * ratePaisePer1000 / 100;     // ₹1 crore at 28 → ₹10,000/yr
        long annualPremium = basePremium * (100 + loadingPercent) / 100;
        return new Quote(coverage, basePremium, loadingPercent, annualPremium, loadingReasons);
    }
}
```

**Step 4: the service + thin questionnaire classes** ([InsuranceService.java](InsuranceService.java), [model/](model/))
```java
public class InsuranceService {
    private final Map<String, Application> applications = new ConcurrentHashMap<>();
    private final Questionnaire questionnaire;
    private final Underwriter underwriter;
    private final AtomicLong seq = new AtomicLong();

    public InsuranceService(Questionnaire questionnaire, Underwriter underwriter) {
        this.questionnaire = questionnaire;
        this.underwriter = underwriter;
    }

    public Application startApplication(String applicantName) {
        Application app = new Application("APP-" + seq.incrementAndGet(), applicantName, questionnaire);
        applications.put(app.getId(), app);
        return app;
    }

    public void answer(String id, String questionId, String value) { get(id).answer(questionId, value); }
    public QuestionnaireStep nextStep(String id)                    { return get(id).getNextIncompleteStep(); }
    public void submit(String id)                                   { get(id).submit(); }
    public Policy issuePolicy(String id)                            { return get(id).issue(); }

    // startUnderwriting() fails if it already started, so the rules run once.
    // While UNDERWRITING no answer can change, so the rules read a stable application.
    public ApplicationStatus underwrite(String id) {
        Application app = get(id);
        app.startUnderwriting();
        UnderwritingDecision decision = underwriter.evaluate(app);
        app.recordDecision(decision);
        return app.getStatus();
    }
    // + get(id) → NoSuchElementException if missing
}

public enum QuestionType {
    NUMBER, YES_NO, TEXT;
    public boolean isValid(String value) {
        if (value == null || value.isBlank()) return false;
        switch (this) {
            case NUMBER:
                try { return Long.parseLong(value.trim()) >= 0; } catch (NumberFormatException e) { return false; }
            case YES_NO:
                return value.equalsIgnoreCase("yes") || value.equalsIgnoreCase("no");
            default:
                return true;
        }
    }
}
// Question{id, text, type} · QuestionnaireStep{name, List<Question>} · Questionnaire{steps + getQuestion(id)}
// Quote{coverage, basePremium, loadingPercent, annualPremium} · Policy{number, holder, cover, premium, issuedAt}
// UnderwritingDecision{approve(quote) | reject(reasons)}
```

### Dry run
```
Rahul: age 28, ₹1 crore cover, 170 cm / 90 kg, smoker.
answer(...) ×7      DraftState.answer validates each, stores it
submit()            DraftState.submit: nothing missing → SUBMITTED
underwrite()        SubmittedState.startUnderwriting → UNDERWRITING
                    AgeRule accept · IncomeRule accept · BmiRule 31.1 → +25% · SmokerRule → +50%
                    no declines → loading 75% → base ₹10,000 × 1.75 = ₹17,500/yr
                    UnderwritingState.recordDecision → APPROVED
answer("smoker","no")  ApprovedState has no answer() → default throws:
                    "Cannot answer questions when the application is APPROVED"
issue()             ApprovedState.issue → Policy POL-APP-2 → ISSUED
issue() again       FinalState → "Cannot issue a policy when the application is ISSUED"
```
The driver also fires 10 simultaneous `issue` clicks: exactly 1 policy, 9 refused.

---

## 5. Follow-ups: answer out loud first, then open

<details>
<summary><b>Q1. Why not just an enum with a switch?</b></summary>

An enum is right when states only limit **which move is next** (Food Delivery). Here states differ in **what they do**:
- DRAFT validates and stores answers, and checks completeness on submit.
- UNDERWRITING turns a decision into a quote or reasons.
- APPROVED builds a policy.

With an enum, each of the 5 actions needs its own `switch (status)` with "not allowed" branches: 5 × 6 = 30 cases, any of which can be wrong. With State, a new status is **one new class**; a new action is one `default` method plus overrides only where it's legal. Illegal actions throw automatically.
</details>

<details>
<summary><b>Q2. "Some cases need a human underwriter." Add manual review.</b></summary>

A third rule outcome, **refer**, and a new **REFERRED** state. Nothing existing breaks.
```java
// RuleResult: one more outcome
public static RuleResult refer(String reason) {
    RuleResult r = new RuleResult(false, 0, reason);
    r.referred = true;
    return r;
}

// Underwriter.evaluate: declines win, then referrals, then approve
if (result.isDeclined()) {
    declineReasons.add(result.getReason());
} else if (result.isReferred()) {
    referReasons.add(result.getReason());
} else if (result.getLoadingPercent() > 0) { ... }
...
if (!declineReasons.isEmpty()) return UnderwritingDecision.reject(declineReasons);
if (!referReasons.isEmpty())   return UnderwritingDecision.refer(referReasons);

// UnderwritingState.recordDecision: one new branch
if (decision.isReferred()) {
    app.setRejectionReasons(decision.getRejectionReasons());   // reasons for the reviewer
    app.setState(new ReferredState());
} else if (decision.isApproved()) { ... }

// REFERRED: the human records a decision exactly like the automatic underwriter does
public class ReferredState extends UnderwritingState {
    @Override
    public ApplicationStatus status() { return ApplicationStatus.REFERRED; }
}
```
The reviewer's tool calls `app.recordDecision(approve(quote))` or `reject(reasons)`. Flow: `UNDERWRITING → REFERRED → APPROVED/REJECTED`. Example rule: "family history of heart disease → refer for medical review".
</details>

<details>
<summary><b>Q3. The actuaries add a new rule: some occupations are declined or cost more.</b></summary>

A new class added to the rules list. **No existing class changes** (Open/Closed):
```java
public class OccupationRule implements UnderwritingRule {
    private static final Set<String> DECLINED = Set.of("deep-sea diver", "stunt performer");
    private static final Set<String> LOADED = Set.of("pilot", "miner");

    @Override
    public RuleResult evaluate(Application app) {
        String job = app.getText("occupation").toLowerCase();
        if (DECLINED.contains(job)) return RuleResult.decline("Occupation not covered: " + job);
        if (LOADED.contains(job)) return RuleResult.load(30, job + " +30%");
        return RuleResult.accept();
    }
}
// wiring: new Underwriter(List.of(..., new OccupationRule()), calculator)
// + one new Question("occupation", "Your occupation", QuestionType.TEXT) in the questionnaire
```
**Bonus:** rule thresholds (ages 18–65, 20× income) are constructor parameters, so they can come from config without a code change.
</details>

<details>
<summary><b>Q4. Conditional questions: ask "cigarettes per day" only if smoker = yes.</b></summary>

A question can say which answer makes it visible. "Missing" and "next step" only count **visible** questions.
```java
// Question: optional visibility condition
private final String showIfQuestionId;   // null = always shown
private final String showIfAnswer;

public Question(String id, String text, QuestionType type, String showIfQuestionId, String showIfAnswer) { ... }

public boolean isVisible(Map<String, String> answers) {
    return showIfQuestionId == null || showIfAnswer.equalsIgnoreCase(answers.get(showIfQuestionId));
}

// Application.getMissingQuestionIds() and getNextIncompleteStep(): skip hidden questions
if (q.isVisible(answers) && !answers.containsKey(q.getId())) missing.add(q.getId());

// usage
new Question("cigarettesPerDay", "How many per day?", QuestionType.NUMBER, "smoker", "yes");
```
A non-smoker can submit without it; a smoker can't. Still data, not code: the form changes and `Application` doesn't.
</details>

<details>
<summary><b>Q5. A quote is valid for 30 days only.</b></summary>

`Quote` gets `validUntil`. `ApprovedState.issue()` checks it and moves to a new **EXPIRED** final state:
```java
Quote quote = app.getQuote();
if (Instant.now().isAfter(quote.getValidUntil())) {           // prices change: old quotes die
    app.setState(new FinalState(ApplicationStatus.EXPIRED));
    throw new IllegalStateException("Quote expired on " + quote.getValidUntil() + ". Please re-apply.");
}
```
That's **lazy expiry** (checked when someone tries to use it), like the rate limiter's refill. Add a nightly job only if the business wants expired apps to show as EXPIRED in reports before anyone touches them. Inject a `Clock` instead of `Instant.now()` to test it.
</details>

<details>
<summary><b>Q6. The applicant wants to edit answers after submitting.</b></summary>

A `withdraw()` action, legal only in SUBMITTED (before underwriting starts):
```java
// ApplicationState
default void withdraw(Application app) { throw notAllowed("withdraw"); }

// SubmittedState
@Override
public void withdraw(Application app) { app.setState(new DraftState()); }   // answers kept, editable again
```
Once underwriting has started, `withdraw` throws automatically: UnderwritingState doesn't override it. That's the State pattern paying off: a new action costs one default method and one override.
</details>

<details>
<summary><b>Q7. Underwriting calls external services (medical records, credit bureau) and can take hours.</b></summary>

That's exactly why UNDERWRITING is its own state. Run it **asynchronously**, so `submit` returns immediately:
```java
private final ExecutorService underwritingPool = Executors.newFixedThreadPool(4);

public void submit(String id) {
    get(id).submit();
    underwritingPool.submit(() -> {
        try {
            underwrite(id);
        } catch (Exception e) {
            // external service failed: send to a human instead of leaving it stuck in UNDERWRITING
            get(id).recordDecision(UnderwritingDecision.refer(List.of("System error: " + e.getMessage())));
        }
    });
}
```
In production: a queue (SQS/Kafka) + workers, retries with backoff for the external calls, and an email to the applicant when the decision is ready (Q12).
</details>

<details>
<summary><b>Q8. Ethos sells several products (term life, whole life): different questions and rules.</b></summary>

A `Product` bundles the questionnaire and underwriter. The service picks one by product id (a simple registry, i.e. Factory):
```java
public class Product {
    private final String id;
    private final Questionnaire questionnaire;
    private final Underwriter underwriter;
    // constructor + getters
}

private final Map<String, Product> products = new ConcurrentHashMap<>();

public Application startApplication(String productId, String applicantName) {
    Product product = products.get(productId);
    if (product == null) throw new NoSuchElementException("Unknown product: " + productId);
    Application app = new Application(nextId(), applicantName, product.getQuestionnaire());
    app.setProductId(productId);        // new field on Application; underwrite() uses its product's underwriter
    applications.put(app.getId(), app);
    return app;
}
```
The state classes don't change at all: the flow is the same for every product.
</details>

<details>
<summary><b>Q9. Regulators need an audit trail of every status change.</b></summary>

Every change already goes through **one** method, `Application.setState()`, so the audit lives there:
```java
private final List<String> history = new ArrayList<>();

void setState(ApplicationState next) {
    history.add(Instant.now() + " " + state.status() + " -> " + next.status());
    this.state = next;
}
```
That's another payoff of "only states can change the status". In production it's an append-only table: `(applicationId, from, to, at, actor, reason)`. Also store the **answers snapshot and rule results** used for the decision, since regulators ask *why* someone was declined.
</details>

<details>
<summary><b>Q10. Collect the first premium before issuing the policy.</b></summary>

Charge inside the application's lock, then issue. The application id is the **idempotency key**, so a retry can't charge twice:
```java
public Policy issuePolicy(String id, PaymentMethod payment) {
    Application app = get(id);
    synchronized (app) {                                         // same lock as Application's own methods
        if (app.getStatus() != ApplicationStatus.APPROVED) {
            throw new IllegalStateException("Cannot issue when " + app.getStatus());
        }
        if (!payment.charge(app.getApplicantName(), app.getQuote().getAnnualPremium(), app.getId())) {
            throw new IllegalStateException("Payment failed");   // stays APPROVED: the user can retry
        }
        return app.issue();
    }
}
```
A cleaner alternative is a new **PAYMENT_PENDING** state between APPROVED and ISSUED, if payment is asynchronous (UPI mandates, bank redirects).
</details>

<details>
<summary><b>Q11. The applicant leaves at step 2 and comes back tomorrow.</b></summary>

Answers are saved on every `answer()` call; persist them behind an `ApplicationRepository` interface (in-memory now, DB later). `getNextIncompleteStep()` already tells the UI where to resume. Expire stale DRAFTs after, say, 30 days.
</details>

<details>
<summary><b>Q12. Email the applicant on approval or rejection; update the CRM.</b></summary>

**Observer** on status changes. Hook it into `setState()` (the single place status changes), or have the service publish after each action. Listeners are wrapped in try/catch so a failed email doesn't undo an approval. In production, publish events to a queue.
</details>

<details>
<summary><b>Q13. How would you know it's working in production?</b></summary>

- **Metrics:** funnel (started → submitted → approved → issued) and **drop-off per questionnaire step**, approval/decline rate **per rule**, time in UNDERWRITING, referral rate.
- **Alarms:** applications stuck in UNDERWRITING > N hours; a sudden jump in one rule's decline rate (a bad config or a bug); policies issued without payment (should be 0).
- **Logs/audit:** the audit trail (Q9) with the rule results behind every decision.
</details>

<details>
<summary><b>Q14. How do you test it?</b></summary>

- **Each rule in isolation:** build an `Application` with fixed answers, call `rule.evaluate()`, check accept/load/decline. Rules are small, so tests are tiny.
- **The state table:** for each state, call every action and assert only the legal ones work (the driver's "illegal actions" section).
- **Pricing:** known inputs → exact rupees (₹10,000 and ₹17,500 cases).
- **Concurrency:** 10 threads call `issuePolicy` on one approved application at once → exactly 1 policy (in the driver).
</details>

---

## 6. Traps that cost points
1. `if (status == ...)` in every service method instead of states owning their actions.
2. A public `setStatus()` the service can call: it bypasses every rule. Keep the setter package-private.
3. Stopping at the first failed rule: the applicant gets one reason, fixes it, and gets rejected again for another.
4. `age`, `smoker`, `bmi` as fields on `Application`: every new question becomes a code change.
5. `double` for premiums.
6. Chain of Responsibility for rules, when all rules must run.
7. Forgetting that answers must be frozen after submit, or rules could read changing data.

---

## 7. Recall check (next day, no peeking)
1. Draw the state diagram, and list which action each state allows.
2. Why do the interface methods have throwing `default`s? What does that buy you?
3. Why are `Application`'s setters package-private?
4. State here vs enum in Food Delivery: say the one-sentence difference.
5. What does a rule return, and why does the `Underwriter` run all of them?
6. Compute the premium: age 40, ₹50 lakh cover, smoker.
7. Add manual review: what changes, and what doesn't?

<details><summary>Answer to 6</summary>

Rate for 30–44 = 200 paise per ₹1,000 → 5,000,000 / 1,000 × 200 / 100 = ₹10,000 base. Smoker +50% → **₹15,000/yr**.
</details>

**Rebuild in 10 minutes:** `ApplicationState` with throwing defaults · `DraftState` (answer, submit) · `SubmittedState` · `UnderwritingState` · `ApprovedState` (issue) · `FinalState` · `Application` delegating · `UnderwritingRule` + `RuleResult` · `Underwriter` running all rules.

---

**Files:** `InsuranceService` · `application/` (`Application`, `ApplicationStatus`, `ApplicationState`, 5 states) · `underwriting/` (`UnderwritingRule`, `RuleResult`, 4 rules, `Underwriter`, `PremiumCalculator`) · `model/` (`Questionnaire`, `QuestionnaireStep`, `Question`, `QuestionType`, `Quote`, `Policy`, `UnderwritingDecision`) · `InsuranceDriver` (step-by-step flow, loadings, multi-reason rejection, 7 illegal actions, 10-thread issue test)
```bash
mvn -q compile exec:java -Dexec.mainClass=com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.InsuranceDriver
```
