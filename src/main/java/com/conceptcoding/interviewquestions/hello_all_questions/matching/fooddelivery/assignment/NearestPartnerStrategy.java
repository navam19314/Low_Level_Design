package com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.assignment;

import com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model.DeliveryPartner;
import com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model.Location;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class NearestPartnerStrategy implements PartnerAssignmentStrategy {

    @Override
    public List<DeliveryPartner> rank(List<DeliveryPartner> partners, Location pickup) {
        List<DeliveryPartner> free = new ArrayList<>();
        for (DeliveryPartner p : partners) {
            if (p.isAvailable()) free.add(p);
        }
        free.sort(Comparator.comparingDouble(p -> p.getLocation().distanceTo(pickup)));
        return free;
    }
}
