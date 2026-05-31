package com.conceptcoding.interviewquestions.hello_all_questions.jobscheduler.policy;

import com.conceptcoding.interviewquestions.hello_all_questions.jobscheduler.model.Job;

import java.util.Comparator;

/**
 * Higher numeric priority runs first; ties broken by earlier createdAt (FIFO
 * within a priority level). Reverse on priority because larger priority should
 * compare "smaller" for PriorityQueue (which is a min-heap).
 */
public class PriorityFirstPolicy implements SchedulingPolicy {

    @Override
    public Comparator<Job> comparator() {
        return Comparator
                .comparingInt(Job::getPriority).reversed()        // highest priority first
                .thenComparing(Job::getCreatedAt);                // tiebreak: oldest first
    }
}
