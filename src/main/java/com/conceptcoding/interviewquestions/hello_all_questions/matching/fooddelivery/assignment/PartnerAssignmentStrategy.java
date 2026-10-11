package com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.assignment;

import com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model.DeliveryPartner;
import com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model.Location;

import java.util.List;

// Strategy: which free rider gets this order? Nearest today; best-rated or fewest-deliveries tomorrow.
public interface PartnerAssignmentStrategy {
    // returns the chosen rider, or null if none is free
    DeliveryPartner choose(List<DeliveryPartner> freePartners, Location pickup);
}
