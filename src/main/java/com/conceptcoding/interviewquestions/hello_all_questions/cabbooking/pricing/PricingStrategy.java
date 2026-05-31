package com.conceptcoding.interviewquestions.hello_all_questions.cabbooking.pricing;

import com.conceptcoding.interviewquestions.hello_all_questions.cabbooking.model.Location;

/**
 * Strategy seam for "what does this trip cost?".
 *
 * <p>Returns {@code long cents} — never doubles for money. Source / destination
 * are supplied as Locations (not just distance) so policies like region-aware
 * pricing or "premium-zone airport surcharge" can implement themselves.
 *
 * <p>The {@code surgeMultiplierBasisPoints} is passed at call time, not stored
 * on the strategy, because surge changes minute-to-minute based on demand.
 * 10_000 basis points = 1.0× (no surge); 15_000 = 1.5×; 20_000 = 2.0×.
 */
public interface PricingStrategy {
    /**
     * @param surgeMultiplierBasisPoints 10000 = 1.0×, 15000 = 1.5×, etc. Use 10000 for no surge.
     */
    long calculateFareCents(Location source, Location destination, int surgeMultiplierBasisPoints);
}
