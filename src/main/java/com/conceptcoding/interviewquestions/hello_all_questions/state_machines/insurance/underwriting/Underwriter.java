package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.underwriting;

import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.application.Application;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.model.Quote;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.model.UnderwritingDecision;

import java.util.ArrayList;
import java.util.List;

// Runs EVERY rule (not stop-at-first) so a rejection lists all the reasons at once,
// then either rejects or prices the policy with the summed loadings.
public class Underwriter {

    private final List<UnderwritingRule> rules;
    private final PremiumCalculator premiumCalculator;

    public Underwriter(List<UnderwritingRule> rules, PremiumCalculator premiumCalculator) {
        this.rules = new ArrayList<>(rules);
        this.premiumCalculator = premiumCalculator;
    }

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

        Quote quote = premiumCalculator.calculate(app, totalLoading, loadingReasons);
        return UnderwritingDecision.approve(quote);
    }
}
