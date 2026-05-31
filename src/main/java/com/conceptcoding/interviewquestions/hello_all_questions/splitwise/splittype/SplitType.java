package com.conceptcoding.interviewquestions.hello_all_questions.splitwise.splittype;

public enum SplitType {
    EQUAL,    // values in input map ignored — divide equally among participants
    EXACT,    // values are amounts in cents; MUST sum to total
    PERCENT   // values are basis points (10000 = 100%); MUST sum to 10000
}
