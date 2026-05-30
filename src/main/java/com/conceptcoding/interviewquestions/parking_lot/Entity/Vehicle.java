package com.conceptcoding.interviewquestions.parking_lot.Entity;

import com.conceptcoding.interviewquestions.parking_lot.enums.VehicleType;

public class Vehicle {

    String vehicleNumber;
    VehicleType vehicleType;

    public Vehicle(String vehicleNumber, VehicleType vehicleType) {
        this.vehicleNumber = vehicleNumber;
        this.vehicleType = vehicleType;
    }

    public String getVehicleNumber() {
        return vehicleNumber;
    }

    public VehicleType getVehicleType() {
        return vehicleType;
    }
}


