package com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot.pricing;

import com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot.model.VehicleType;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

// Rate per started hour, per vehicle type. 2h10m → 3 hours. Minimum 1 hour.
public class HourlyPricing implements PricingStrategy {

    private final Map<VehicleType, Long> ratePerHour;

    public HourlyPricing(Map<VehicleType, Long> ratePerHour) {
        this.ratePerHour = new EnumMap<>(ratePerHour);
    }

    @Override
    public long fee(VehicleType vehicle, Duration parked) {
        long minutes = parked.toMinutes();
        long hours = Math.max(1, (minutes + 59) / 60);    // round UP to whole hours
        return hours * ratePerHour.get(vehicle);
    }
}
