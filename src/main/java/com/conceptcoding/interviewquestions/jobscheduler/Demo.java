package com.conceptcoding.interviewquestions.jobscheduler;

import com.conceptcoding.interviewquestions.jobscheduler.enums.Priority;
import com.conceptcoding.interviewquestions.jobscheduler.enums.UserType;

public class Demo {

    public static void main(String[] args) throws InterruptedException {

        // 3 worker threads, FPS scheduling
        Scheduler scheduler = new Scheduler(3, new FPSScheduler());

        scheduler.addJob(new Job("Job1", 5, Priority.P2, 200, UserType.USER,  1));
        scheduler.addJob(new Job("Job2", 2, Priority.P0, 300, UserType.ROOT,  2));
        scheduler.addJob(new Job("Job3", 8, Priority.P1, 400, UserType.ADMIN, 3));
        scheduler.addJob(new Job("Job4", 1, Priority.P0, 250, UserType.ADMIN, 4));
        scheduler.addJob(new Job("Job5", 3, Priority.P1, 350, UserType.USER,  5));

        Thread.sleep(1000);

        // Switch to EDF — jobs with deadline <= 250 will be skipped
        System.out.println("\n--- Switching to EDF (currentTime=250) ---\n");
        scheduler.setStrategy(new EDFScheduler(250));

        scheduler.addJob(new Job("Job6", 2, Priority.P1, 200, UserType.USER,  6));  // expired — skipped
        scheduler.addJob(new Job("Job7", 4, Priority.P0, 400, UserType.ROOT,  7));  // valid
        scheduler.addJob(new Job("Job8", 1, Priority.P2, 300, UserType.ADMIN, 8));  // valid

        Thread.sleep(1000);
        scheduler.shutdown();
    }
}
