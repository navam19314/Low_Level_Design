package com.conceptcoding.interviewquestions.parking_lot;

import com.conceptcoding.interviewquestions.parking_lot.Entity.ParkingSpot;
import com.conceptcoding.interviewquestions.parking_lot.Entity.Vehicle;
import com.conceptcoding.interviewquestions.parking_lot.LookupStrategy.ParkingSpotLookupStrategy;
import com.conceptcoding.interviewquestions.parking_lot.LookupStrategy.RandomLookupStrategy;
import com.conceptcoding.interviewquestions.parking_lot.enums.VehicleType;
import com.conceptcoding.interviewquestions.parking_lot.parkinglot.*;
import com.conceptcoding.interviewquestions.parking_lot.payment.CashPayment;
import com.conceptcoding.interviewquestions.parking_lot.payment.UPIPayment;
import com.conceptcoding.interviewquestions.parking_lot.pricing.CostComputation;
import com.conceptcoding.interviewquestions.parking_lot.pricing.FixedPricingStrategy;
import com.conceptcoding.interviewquestions.parking_lot.spotManagers.FourWheelerSpotManager;
import com.conceptcoding.interviewquestions.parking_lot.spotManagers.ParkingSpotManager;
import com.conceptcoding.interviewquestions.parking_lot.spotManagers.TwoWheelerSpotManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ParkingLotClient {

    public static void main(String[] args) {
        // Step 1: Create lookup strategy for spot selection
        ParkingSpotLookupStrategy strategy = new RandomLookupStrategy();

        // Step 2: Create Level 1 with spot managers
        Map<VehicleType, ParkingSpotManager> levelOneManagers = new HashMap<>();
        levelOneManagers.put(VehicleType.TWO_WHEELER,
                new TwoWheelerSpotManager(
                        List.of(new ParkingSpot("L1-S1"), new ParkingSpot("L1-S2")),
                        strategy));
        levelOneManagers.put(VehicleType.FOUR_WHEELER,
                new FourWheelerSpotManager(
                        List.of(new ParkingSpot("L1-S3")),
                        strategy));
        ParkingLevel level1 = new ParkingLevel(1, levelOneManagers);

        // Step 3: Create Level 2 with spot managers
        Map<VehicleType, ParkingSpotManager> levelTwoManagers = new HashMap<>();
        levelTwoManagers.put(VehicleType.TWO_WHEELER,
                new TwoWheelerSpotManager(
                        List.of(new ParkingSpot("L2-S1")),
                        strategy));
        levelTwoManagers.put(VehicleType.FOUR_WHEELER,
                new FourWheelerSpotManager(
                        List.of(new ParkingSpot("L2-S2"), new ParkingSpot("L2-S3")),
                        strategy));
        ParkingLevel level2 = new ParkingLevel(2, levelTwoManagers);

        // Step 4: Create parking building with levels
        ParkingBuilding parkingBuilding = new ParkingBuilding(
                List.of(level1, level2),
                new CostComputation(new FixedPricingStrategy()));

        // Step 5: Create parking lot with building, entrance and exit gates
        ParkingLot parkingLot = new ParkingLot(
                parkingBuilding,
                new EntranceGate(),
                new ExitGate(new CostComputation(new FixedPricingStrategy())));

        // Step 6: Test parking lot with vehicles
        Vehicle bike = new Vehicle("BIKE-101", VehicleType.TWO_WHEELER);
        Vehicle car = new Vehicle("CAR-201", VehicleType.FOUR_WHEELER);

        Ticket bikeTicket = parkingLot.vehicleArrives(bike);
        Ticket carTicket = parkingLot.vehicleArrives(car);

        parkingLot.vehicleExits(bikeTicket, new CashPayment());
        parkingLot.vehicleExits(carTicket, new UPIPayment());
    }
}
