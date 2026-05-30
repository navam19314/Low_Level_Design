package com.conceptcoding.interviewquestions.carrental.payment;

import com.conceptcoding.interviewquestions.carrental.Bill.Bill;

public interface PaymentStrategy {

    Payment processPayment(Bill bill, double paymentAmount);
}

