package com.conceptcoding.interviewquestions.hello_all_questions.pipelines.jobscheduler.policy;

import com.conceptcoding.interviewquestions.hello_all_questions.pipelines.jobscheduler.model.Job;

import java.util.Comparator;

// Strategy interface — provides the Comparator that orders jobs in the
// ready queue. Different policies (priority-first, FIFO, earliest-deadline-first)
// are different Comparators. The scheduler is parameterized by one and
// builds its PriorityBlockingQueue with that comparator.
//
// The comparator must define a TOTAL order — ties must be resolved (typically
// by createdAt) so the queue's ordering is deterministic. Otherwise threads can
// race to the "front" non-deterministically.
public interface SchedulingPolicy {

    //  Comparator placing the NEXT-TO-RUN job FIRST (smallest in PriorityQueue terms).
    Comparator<Job> comparator();
}
