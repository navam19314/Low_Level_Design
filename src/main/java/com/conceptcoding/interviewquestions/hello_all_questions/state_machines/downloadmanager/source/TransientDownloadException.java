package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.downloadmanager.source;

// Network blip, timeout, server 503: worth retrying from the same offset.
public class TransientDownloadException extends RuntimeException {
    public TransientDownloadException(String message) { super(message); }
}
