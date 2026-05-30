package com.conceptcoding.interviewquestions.parking_lot.parkinglot;

import com.conceptcoding.interviewquestions.parking_lot.Entity.Vehicle;
import com.conceptcoding.interviewquestions.parking_lot.Entity.ParkingSpot;
import com.conceptcoding.interviewquestions.parking_lot.Ticket;
import com.conceptcoding.interviewquestions.parking_lot.enums.VehicleType;
import com.conceptcoding.interviewquestions.parking_lot.pricing.CostComputation;

import java.util.List;

public class ParkingBuilding {

    private final List<ParkingLevel> levels;

    public ParkingBuilding(List<ParkingLevel> levels,
                           CostComputation costComputation) {
        this.levels = levels;
    }

    Ticket allocate(Vehicle vehicle) {
        // Search for available spot across all levels
        for (ParkingLevel level : levels) {
            VehicleType vehicleType = vehicle.getVehicleType();
            
            if (level.hasAvailability(vehicleType)) {
                ParkingSpot spot = level.park(vehicleType);
                if (spot != null) {
                    Ticket ticket = new Ticket(vehicle, level, spot);
                    System.out.println("Parking allocated at level: " + level.getLevelNumber() +
                                     " spot: " + spot.getSpotId());
                    return ticket;
                }
            }
        }
        throw new RuntimeException("Parking Full");
    }

    void release(Ticket ticket) {
        ParkingLevel level = ticket.getLevel();
        VehicleType vehicleType = ticket.getVehicle().getVehicleType();
        level.unPark(vehicleType, ticket.getSpot());
    }
}


