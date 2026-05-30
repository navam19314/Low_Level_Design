package com.conceptcoding.interviewquestions.parking_lot.pricing;

import com.conceptcoding.interviewquestions.parking_lot.Ticket;

public class FixedPricingStrategy implements PricingStrategy {

    @Override
    public double calculate(Ticket ticket) {
        return 100;
    }
}

