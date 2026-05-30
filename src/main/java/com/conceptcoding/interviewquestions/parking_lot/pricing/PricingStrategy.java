package com.conceptcoding.interviewquestions.parking_lot.pricing;

import com.conceptcoding.interviewquestions.parking_lot.Ticket;

public interface PricingStrategy {

    double calculate(Ticket ticket);
}

