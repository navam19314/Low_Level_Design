package com.conceptcoding.interviewquestions.cricbuzz.Team;

import com.conceptcoding.interviewquestions.cricbuzz.Inning.BallDetails;
import com.conceptcoding.interviewquestions.cricbuzz.Inning.OverDetails;
import com.conceptcoding.interviewquestions.cricbuzz.Team.Player.PlayerDetails;

public class Wicket {

    public WicketType wicketType;
    public PlayerDetails takenBy;
    public OverDetails overDetail;
    public BallDetails ballDetail;

    public Wicket(WicketType wicketType, PlayerDetails takenBy, OverDetails overDetail, BallDetails ballDetail) {

        this.wicketType = wicketType;
        this.takenBy = takenBy;
        this.overDetail = overDetail;
        this.ballDetail = ballDetail;
    }
}
