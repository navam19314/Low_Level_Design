package com.conceptcoding.interviewquestions.hello_all_questions.jobscheduler.listener;

import com.conceptcoding.interviewquestions.hello_all_questions.jobscheduler.model.Job;

/**
 * Observer interface for job lifecycle events. The scheduler invokes these
 * AFTER the relevant state transition. Listeners are stored in a CopyOnWriteArrayList
 * so iteration is lock-free; a throwing listener is logged and skipped.
 */
public interface JobListener {

    /** Called when a job starts running (or restarts on retry). */
    default void onStarted(Job job)                                { }

    /** Called when a job completes successfully. */
    default void onCompleted(Job job)                              { }

    /** Called when a job fails an attempt (may or may not retry). */
    default void onFailed(Job job, Throwable error, boolean willRetry) { }

    /** Called when a job is cancelled before completion. */
    default void onCancelled(Job job)                              { }
}
