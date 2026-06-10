package com.conceptcoding.interviewquestions.hello_all_questions.parkinglot;

import com.conceptcoding.interviewquestions.hello_all_questions.parkinglot.model.ParkingSpot;
import com.conceptcoding.interviewquestions.hello_all_questions.parkinglot.model.SpotType;
import com.conceptcoding.interviewquestions.hello_all_questions.parkinglot.model.Ticket;
import com.conceptcoding.interviewquestions.hello_all_questions.parkinglot.model.VehicleType;

import java.util.ArrayList;
import java.util.List;

public class ParkingLotDriver {

    public static void main(String[] args) {
        List<ParkingSpot> spots = new ArrayList<>();
        spots.add(new ParkingSpot("M1", SpotType.MOTORCYCLE));
        spots.add(new ParkingSpot("C1", SpotType.CAR));
        spots.add(new ParkingSpot("C2", SpotType.CAR));
        spots.add(new ParkingSpot("L1", SpotType.LARGE));

        // hourly rate: 500 cents = ₹5/hr
        ParkingLot lot = new ParkingLot(spots, 500);

        // --- Happy path: car enters and exits immediately (minimum 1h charge) ---
        System.out.println("--- Car parks and exits (minimum 1h charge) ---");
        Ticket t1 = lot.enter(VehicleType.CAR);
        System.out.println("Entry time : " + t1.getEntryTime());
        System.out.println("Spot       : " + t1.getSpotId());
        long fee = lot.exit(t1.getId());
        System.out.println("Fee        : " + fee + " cents (expect >= 500)");

        // --- Double exit rejected ---
        System.out.println("\n--- Double exit rejected ---");
        try {
            lot.exit(t1.getId());
        } catch (RuntimeException e) {
            System.out.println("Rejected: " + e.getMessage());
        }

        // --- Lot full for a vehicle type ---
        System.out.println("\n--- Lot full for CAR ---");
        lot.enter(VehicleType.CAR);
        lot.enter(VehicleType.CAR);
        try {
            lot.enter(VehicleType.CAR);   // no CAR spots left
        } catch (RuntimeException e) {
            System.out.println("Rejected: " + e.getMessage());
        }

        // --- Motorcycle — instant exit, minimum 1h charge ---
        System.out.println("\n--- Motorcycle instant exit ---");
        Ticket t2 = lot.enter(VehicleType.MOTORCYCLE);
        long motoFee = lot.exit(t2.getId());
        System.out.println("Fee: " + motoFee + " cents (expect 500)");

        // --- Invalid ticket ---
        System.out.println("\n--- Invalid ticket ---");
        try {
            lot.exit("does-not-exist");
        } catch (RuntimeException e) {
            System.out.println("Rejected: " + e.getMessage());
        }
        try {
            lot.exit("");
        } catch (RuntimeException e) {
            System.out.println("Rejected: " + e.getMessage());
        }
    }
}
