package com.conceptcoding.interviewquestions.atm.ATMStates;

import com.conceptcoding.interviewquestions.atm.ATMRoomComponents.ATM;
import com.conceptcoding.interviewquestions.atm.ATMRoomComponents.Card;

public class IdleState extends ATMState {

    @Override
    public void insertCard(ATM atm, Card card) {
        System.out.println("Card is inserted");
        atm.setCurrentATMState(new HasCardState());
    }
}