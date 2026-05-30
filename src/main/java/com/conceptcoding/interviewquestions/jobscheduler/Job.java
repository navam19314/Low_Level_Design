package com.conceptcoding.interviewquestions.jobscheduler;

import com.conceptcoding.interviewquestions.jobscheduler.enums.Priority;
import com.conceptcoding.interviewquestions.jobscheduler.enums.UserType;

public class Job {

    private final String name;
    private final int duration;
    private final Priority priority;
    private final int deadline;
    private final UserType userType;
    private final int arrivalOrder;   // used by FCFS

    public Job(String name, int duration, Priority priority, int deadline, UserType userType, int arrivalOrder) {
        this.name = name;
        this.duration = duration;
        this.priority = priority;
        this.deadline = deadline;
        this.userType = userType;
        this.arrivalOrder = arrivalOrder;
    }

    public String getName()        { return name; }
    public int getDuration()       { return duration; }
    public Priority getPriority()  { return priority; }
    public int getDeadline()       { return deadline; }
    public UserType getUserType()  { return userType; }
    public int getArrivalOrder()   { return arrivalOrder; }

    @Override
    public String toString() {
        return name + "(dur=" + duration + ", pri=" + priority + ", dl=" + deadline + ", user=" + userType + ")";
    }
}
