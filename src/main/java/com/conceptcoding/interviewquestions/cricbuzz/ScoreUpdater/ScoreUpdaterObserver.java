package com.conceptcoding.interviewquestions.cricbuzz.ScoreUpdater;


import com.conceptcoding.interviewquestions.cricbuzz.Inning.BallDetails;

public interface ScoreUpdaterObserver {

    public void update(BallDetails ballDetails);
}
