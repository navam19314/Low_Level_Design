package com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot.pricing;

import com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot.model.VehicleType;

import java.time.Duration;

// Strategy: how much does a stay cost? Hourly today; first-hour-free, daily cap,
// weekend rates tomorrow, each a new class.
public interface PricingStrategy {
    long fee(VehicleType vehicle, Duration parked);    // whole rupees
}
