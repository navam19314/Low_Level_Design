package com.conceptcoding.interviewquestions.hello_all_questions.matching.cabbooking.pricing;

import com.conceptcoding.interviewquestions.hello_all_questions.matching.cabbooking.model.Location;

// Fare = baseFare + (distanceKm × perKm), then apply surge multiplier. Amounts in rupees.
//
// Math done in long throughout. Distance is the only double in the chain
// and gets multiplied by an int (rupees-per-km) — we round to the nearest
// rupee at the end via Math.round.
public class DistanceBasedPricing implements PricingStrategy {

    private final long baseFare;   // rupees
    private final long perKm;      // rupees per km

    public DistanceBasedPricing(long baseFare, long perKm) {
        this.baseFare = baseFare;
        this.perKm    = perKm;
    }

    @Override
    public long calculateFare(Location src, Location dst, int surgeMultiplierBasisPoints) {
        double km            = src.distanceKm(dst);
        long   distanceFare  = Math.round(km * perKm);
        long   subtotal      = baseFare + distanceFare;
        // Apply surge: subtotal * (basisPoints / 10_000)
        return subtotal * surgeMultiplierBasisPoints / 10_000;          // integer math: no doubles for money
    }
}
