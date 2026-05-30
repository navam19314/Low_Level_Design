package com.conceptcoding.interviewquestions.jobscheduler;

import java.util.Comparator;

public class FCFSScheduler implements SchedulingStrategy {

    @Override
    public Comparator<Job> getComparator() {
        return Comparator.comparingInt(Job::getArrivalOrder);
    }
}
