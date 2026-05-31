package com.conceptcoding.interviewquestions.hello_all_questions.jobscheduler.policy;

import com.conceptcoding.interviewquestions.hello_all_questions.jobscheduler.model.Job;

import java.util.Comparator;

/** First-in-first-out — oldest createdAt wins. Ignores priority and scheduledAt. */
public class FifoPolicy implements SchedulingPolicy {

    @Override
    public Comparator<Job> comparator() {
        return Comparator.comparing(Job::getCreatedAt);
    }
}
