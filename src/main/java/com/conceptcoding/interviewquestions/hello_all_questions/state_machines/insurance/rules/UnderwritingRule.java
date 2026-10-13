package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.rules;

import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.model.Application;

// Strategy: each eligibility rule is one small class.
// A new rule from the actuaries = a new class added to the list. Nothing else changes.
public interface UnderwritingRule {
    String check(Application app);          // null = OK, otherwise the reason to reject
}
