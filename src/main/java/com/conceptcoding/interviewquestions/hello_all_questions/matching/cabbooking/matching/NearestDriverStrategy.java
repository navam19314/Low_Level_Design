package com.conceptcoding.interviewquestions.hello_all_questions.matching.cabbooking.matching;

import com.conceptcoding.interviewquestions.hello_all_questions.matching.cabbooking.model.Driver;
import com.conceptcoding.interviewquestions.hello_all_questions.matching.cabbooking.model.Location;

import java.util.List;

public class NearestDriverStrategy implements DriverMatchingStrategy {

    @Override
    public Driver match(Location pickup, List<Driver> availableDrivers) {
        Driver nearest = null;
        for (Driver d : availableDrivers) {
            if (nearest == null || d.getLocation().distanceTo(pickup) < nearest.getLocation().distanceTo(pickup)) {
                nearest = d;                                   // closest so far
            }
        }
        return nearest;                                        // null = no driver available
    }
}
