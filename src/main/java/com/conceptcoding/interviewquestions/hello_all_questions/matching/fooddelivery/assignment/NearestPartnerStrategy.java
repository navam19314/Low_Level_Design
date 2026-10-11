package com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.assignment;

import com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model.DeliveryPartner;
import com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model.Location;

import java.util.List;

public class NearestPartnerStrategy implements PartnerAssignmentStrategy {

    @Override
    public DeliveryPartner choose(List<DeliveryPartner> freePartners, Location pickup) {
        DeliveryPartner nearest = null;
        for (DeliveryPartner p : freePartners) {
            if (nearest == null || p.getLocation().distanceTo(pickup) < nearest.getLocation().distanceTo(pickup)) {
                nearest = p;                                   // closest so far
            }
        }
        return nearest;                                        // null = nobody free
    }
}
