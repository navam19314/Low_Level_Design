package com.conceptcoding.interviewquestions.hello_all_questions.splitwise.model;

// A single "debtor pays creditor X rupees" instruction from simplifyDebts()
public class Settlement {

    private final String debtorId;
    private final String creditorId;
    private final long   amount;   // rupees

    public Settlement(String debtorId, String creditorId, long amount) {
        this.debtorId   = debtorId;
        this.creditorId = creditorId;
        this.amount     = amount;
    }

    public String getDebtorId()   { return debtorId; }
    public String getCreditorId() { return creditorId; }
    public long   getAmount()     { return amount; }
}
