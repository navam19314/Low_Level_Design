package com.conceptcoding.interviewquestions.hello_all_questions.splitwise.model;

// A single "debtor pays creditor X cents" instruction from simplifyDebts()
public class Settlement {

    private final String debtorId;
    private final String creditorId;
    private final long   amountCents;

    public Settlement(String debtorId, String creditorId, long amountCents) {
        this.debtorId    = debtorId;
        this.creditorId  = creditorId;
        this.amountCents = amountCents;
    }

    public String getDebtorId()    { return debtorId; }
    public String getCreditorId()  { return creditorId; }
    public long   getAmountCents() { return amountCents; }
}
