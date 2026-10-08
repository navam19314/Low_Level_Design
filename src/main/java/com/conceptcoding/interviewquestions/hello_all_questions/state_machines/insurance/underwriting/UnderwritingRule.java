package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.underwriting;

import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.application.Application;

// Strategy: each eligibility / risk rule is one small class.
// New rule from the actuaries = a new class added to the list. Nothing else changes.
public interface UnderwritingRule {
    RuleResult evaluate(Application application);
}
