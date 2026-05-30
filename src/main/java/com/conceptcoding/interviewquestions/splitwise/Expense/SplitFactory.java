package com.conceptcoding.interviewquestions.splitwise.Expense;

import com.conceptcoding.interviewquestions.splitwise.Expense.Split.EqualExpenseSplit;
import com.conceptcoding.interviewquestions.splitwise.Expense.Split.ExpenseSplit;
import com.conceptcoding.interviewquestions.splitwise.Expense.Split.PercentageExpenseSplit;
import com.conceptcoding.interviewquestions.splitwise.Expense.Split.UnequalExpenseSplit;

public class SplitFactory {

    public static ExpenseSplit getSplitObject(ExpenseSplitType splitType) {

        switch (splitType) {
            case EQUAL:
                return new EqualExpenseSplit();
            case UNEQUAL:
                return new UnequalExpenseSplit();
            case PERCENTAGE:
                return new PercentageExpenseSplit();
            default:
                return null;
        }
    }
}
