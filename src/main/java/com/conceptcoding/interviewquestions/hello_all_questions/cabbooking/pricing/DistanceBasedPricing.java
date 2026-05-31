package com.conceptcoding.interviewquestions.hello_all_questions.cabbooking.pricing;

import com.conceptcoding.interviewquestions.hello_all_questions.cabbooking.model.Location;

/**
 * Fare = baseFare + (distanceKm × perKmCents), then apply surge multiplier.
 *
 * <p>Math done in {@code long} throughout. Distance is the only double in
 * the chain and gets multiplied by an int (cents-per-km) — we round to the
 * nearest cent at the end via Math.round.
 */
public class DistanceBasedPricing implements PricingStrategy {

    private final long baseFareCents;
    private final long perKmCents;

    public DistanceBasedPricing(long baseFareCents, long perKmCents) {
        this.baseFareCents = baseFareCents;
        this.perKmCents    = perKmCents;
    }

    @Override
    public long calculateFareCents(Location src, Location dst, int surgeMultiplierBasisPoints) {
        double km            = src.distanceKm(dst);
        long   distanceCents = Math.round(km * perKmCents);
        long   subtotalCents = baseFareCents + distanceCents;
        // Apply surge: subtotal * (basisPoints / 10_000)
        return Math.round(subtotalCents * (surgeMultiplierBasisPoints / 10_000.0));
    }
}
