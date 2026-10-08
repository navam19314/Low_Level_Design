package com.conceptcoding.interviewquestions.hello_all_questions.pipelines.notification.event;

import java.util.HashMap;
import java.util.Map;

// Something that happened in the business ("ORDER_SHIPPED for user-42, orderId=A17").
// Publishers describe WHAT happened; they never say how anyone should be notified.
public class Event {

    private final String type;
    private final String userId;
    private final Map<String, String> data;

    public Event(String type, String userId, Map<String, String> data) {
        this.type = type;
        this.userId = userId;
        this.data = new HashMap<>(data);
    }

    public String getType()                { return type; }
    public String getUserId()              { return userId; }
    public Map<String, String> getData()   { return new HashMap<>(data); }
}
