package com.conceptcoding.interviewquestions.hello_all_questions.pipelines.notification.sender;

// A failure that might succeed if we try again: timeout, provider 503, rate limited.
// Anything else (invalid phone number, unsubscribed email) is permanent: retrying is pointless.
public class TransientDeliveryException extends RuntimeException {
    public TransientDeliveryException(String message) { super(message); }
}
