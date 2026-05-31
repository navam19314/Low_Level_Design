package com.conceptcoding.interviewquestions.hello_all_questions.splitwise.model;

/**
 * One user's share of an expense — computed result of applying a SplitStrategy.
 * amountCents = how much this user OWES to whoever paid.
 */
public record Split(String userId, long amountCents) {}
