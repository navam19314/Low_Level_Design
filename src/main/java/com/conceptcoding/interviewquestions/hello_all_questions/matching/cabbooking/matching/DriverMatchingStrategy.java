package com.conceptcoding.interviewquestions.hello_all_questions.matching.cabbooking.matching;

import com.conceptcoding.interviewquestions.hello_all_questions.matching.cabbooking.model.Driver;
import com.conceptcoding.interviewquestions.hello_all_questions.matching.cabbooking.model.Location;

import java.util.List;

// Strategy: which available driver gets this ride? Nearest today; best-rated or least-busy tomorrow.
public interface DriverMatchingStrategy {
    // returns the chosen driver, or null if there is none
    Driver match(Location pickup, List<Driver> availableDrivers);
}
