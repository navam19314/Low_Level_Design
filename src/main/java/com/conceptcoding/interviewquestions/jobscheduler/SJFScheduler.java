package com.conceptcoding.interviewquestions.jobscheduler;

import java.util.Comparator;

public class SJFScheduler implements SchedulingStrategy {

    @Override
    public Comparator<Job> getComparator() {
        return Comparator.comparingInt(Job::getDuration)
                .thenComparingInt(j -> j.getPriority().ordinal());
    }
}
