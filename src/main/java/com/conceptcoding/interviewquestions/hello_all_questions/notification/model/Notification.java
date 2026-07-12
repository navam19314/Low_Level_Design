package com.conceptcoding.interviewquestions.hello_all_questions.notification.model;

// Immutable payload sent across all channels. Channel-specific rendering
// (HTML vs plain text vs short SMS) is each Sender's job, not the Notification's —
// that's how one payload reaches every channel without N×M renderer classes.
public class Notification {

    private final String id;
    private final String recipientId;
    private final String subject;
    private final String body;

    public Notification(String id, String recipientId, String subject, String body) {
        if (id == null || recipientId == null || subject == null || body == null) {
            throw new IllegalArgumentException("id, recipientId, subject, body are required");
        }
        this.id          = id;
        this.recipientId = recipientId;
        this.subject     = subject;
        this.body        = body;
    }

    public String getId()          { return id; }
    public String getRecipientId() { return recipientId; }
    public String getSubject()     { return subject; }
    public String getBody()        { return body; }
}
