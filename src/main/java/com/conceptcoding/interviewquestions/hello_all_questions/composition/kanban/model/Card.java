package com.conceptcoding.interviewquestions.hello_all_questions.composition.kanban.model;

// A task on the board. Changed only through Board (package-private setters),
// so every change happens under the board's lock.
public class Card {

    private final String id;
    private String title;
    private String description;
    private String assigneeId;          // null = unassigned
    private final String createdBy;

    public Card(String id, String title, String description, String createdBy) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.createdBy = createdBy;
    }

    void setTitle(String title)             { this.title = title; }
    void setDescription(String description) { this.description = description; }
    void setAssigneeId(String assigneeId)   { this.assigneeId = assigneeId; }

    public String getId()          { return id; }
    public String getTitle()       { return title; }
    public String getDescription() { return description; }
    public String getAssigneeId()  { return assigneeId; }
    public String getCreatedBy()   { return createdBy; }

    @Override
    public String toString() { return title + (assigneeId == null ? "" : " @" + assigneeId); }
}
