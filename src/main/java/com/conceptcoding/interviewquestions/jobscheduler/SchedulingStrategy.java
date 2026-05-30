package com.conceptcoding.interviewquestions.jobscheduler;

import java.util.Comparator;

public interface SchedulingStrategy {

    Comparator<Job> getComparator();

    // Override in strategies that need deadline filtering (e.g. EDF)
    default boolean isValid(Job job) {
        return true;
    }
}
