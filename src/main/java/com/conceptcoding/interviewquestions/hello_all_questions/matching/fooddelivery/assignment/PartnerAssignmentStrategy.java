package com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.assignment;

import com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model.DeliveryPartner;
import com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model.Location;

import java.util.List;

// Strategy: HOW we pick a rider (nearest, best rated, fewest deliveries today...).
// Returns candidates best-first. The service then tries to claim them in that order,
// because the best rider may get grabbed by another order a moment earlier.
public interface PartnerAssignmentStrategy {
    List<DeliveryPartner> rank(List<DeliveryPartner> partners, Location pickup);
}
