package com.conceptcoding.interviewquestions.parking_lot.parkinglot;

import com.conceptcoding.interviewquestions.parking_lot.Entity.Vehicle;
import com.conceptcoding.interviewquestions.parking_lot.Ticket;

public class EntranceGate {

    public Ticket enter(ParkingBuilding building, Vehicle vehicle) {
        return building.allocate(vehicle);
    }
}


