package com.conceptcoding.interviewquestions.hello_all_questions.splitwise.model;

/**
 * A single "pay X to Y" instruction emitted by debt simplification.
 * After all settlements are executed, every user's net balance is 0.
 */
public record Settlement(String debtorId, String creditorId, long amountCents) {}
