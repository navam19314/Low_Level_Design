package com.conceptcoding.interviewquestions.hello_all_questions.pipelines.notification.sender;

// Wraps Thread.sleep so tests can retry instantly and check the waits.
public interface Sleeper {
    void sleep(long millis) throws InterruptedException;

    Sleeper REAL = Thread::sleep;
}
