package com.conceptcoding.interviewquestions.hello_all_questions.jobscheduler.policy;

import com.conceptcoding.interviewquestions.hello_all_questions.jobscheduler.model.Job;

import java.util.Comparator;

/**
 * Earliest-deadline-first — the job whose {@code scheduledAt} is earliest goes
 * to the front. Ties broken by createdAt to keep ordering deterministic.
 *
 * <p>For real EDF you'd also store an explicit deadline separate from "schedule
 * to run at"; here we conflate them since the problem only models one timestamp.
 */
public class EarliestDeadlineFirstPolicy implements SchedulingPolicy {

    @Override
    public Comparator<Job> comparator() {
        return Comparator
                .comparing(Job::getScheduledAt)
                .thenComparing(Job::getCreatedAt);
    }
}
