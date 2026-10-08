package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.insurance.underwriting;

// What ONE rule says: fine / fine but charge more / decline.
public class RuleResult {

    private final boolean declined;
    private final int loadingPercent;     // extra premium % (0 when no loading)
    private final String reason;          // null when the rule has nothing to say

    private RuleResult(boolean declined, int loadingPercent, String reason) {
        this.declined = declined;
        this.loadingPercent = loadingPercent;
        this.reason = reason;
    }

    public static RuleResult accept()                         { return new RuleResult(false, 0, null); }
    public static RuleResult load(int percent, String reason) { return new RuleResult(false, percent, reason); }
    public static RuleResult decline(String reason)           { return new RuleResult(true, 0, reason); }

    public boolean isDeclined()        { return declined; }
    public int     getLoadingPercent() { return loadingPercent; }
    public String  getReason()         { return reason; }
}
