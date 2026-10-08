package com.conceptcoding.interviewquestions.hello_all_questions.policies.splitwise.model;

import java.util.LinkedHashSet;
import java.util.Set;

// A trip / flat / office lunch circle. Expenses in a group may only involve its members.
public class Group {

    private final String id;
    private final String name;
    private final Set<String> memberIds = new LinkedHashSet<>();

    public Group(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public void    addMember(String userId)    { memberIds.add(userId); }
    public boolean isMember(String userId)     { return memberIds.contains(userId); }
    public Set<String> getMemberIds()          { return new LinkedHashSet<>(memberIds); }
    public String  getId()                     { return id; }
    public String  getName()                   { return name; }
}
