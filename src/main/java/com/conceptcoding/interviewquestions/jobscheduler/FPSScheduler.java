package com.conceptcoding.interviewquestions.jobscheduler;

import java.util.Comparator;

public class FPSScheduler implements SchedulingStrategy {

    @Override
    public Comparator<Job> getComparator() {
        return Comparator.comparing(Job::getPriority)
                .thenComparing(Job::getUserType)
                .thenComparingInt(Job::getDuration);
    }
}
