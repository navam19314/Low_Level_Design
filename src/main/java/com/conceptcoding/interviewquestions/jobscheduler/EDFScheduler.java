package com.conceptcoding.interviewquestions.jobscheduler;

import java.util.Comparator;

public class EDFScheduler implements SchedulingStrategy {

    private final int currentTime;

    public EDFScheduler(int currentTime) {
        this.currentTime = currentTime;
    }

    @Override
    public Comparator<Job> getComparator() {
        return Comparator.comparingInt(Job::getDeadline);
    }

    @Override
    public boolean isValid(Job job) {
        return job.getDeadline() > currentTime;
    }
}
