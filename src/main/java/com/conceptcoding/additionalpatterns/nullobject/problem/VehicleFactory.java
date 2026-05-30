package com.conceptcoding.additionalpatterns.nullobject.problem;

import com.conceptcoding.additionalpatterns.nullobject.Bike;
import com.conceptcoding.additionalpatterns.nullobject.Car;
import com.conceptcoding.additionalpatterns.nullobject.Vehicle;

public class VehicleFactory {

    public static Vehicle getVehicle(String type) {
        if (type.equals("car")) {
            return new Car("Toyota", "Red", 5, 60, true);
        } else if (type.equals("bike")) {
            return new Bike("Yamaha", "Black", 60);
        } else {
            return null; // THE PROBLEM
        }
    }
}
