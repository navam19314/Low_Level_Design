package com.conceptcoding.interviewquestions.carrental;

import com.conceptcoding.interviewquestions.carrental.Bill.Bill;
import com.conceptcoding.interviewquestions.carrental.Bill.DailyBillingStrategy;
import com.conceptcoding.interviewquestions.carrental.payment.Payment;
import com.conceptcoding.interviewquestions.carrental.payment.UPIPaymentStrategy;
import com.conceptcoding.interviewquestions.carrental.product.Vehicle;
import com.conceptcoding.interviewquestions.carrental.product.VehicleType;
import com.conceptcoding.interviewquestions.carrental.reservation.Reservation;
import com.conceptcoding.interviewquestions.carrental.reservation.ReservationType;

import java.time.LocalDate;

public class Demo {

    public static void main(String[] args) throws Exception {

        System.out.println("\n===== LLD: Car Rental System Demo =====");

        VehicleRentalSystem rentalSystem = new VehicleRentalSystem();

        // ---------------------------------------------------------
        // 1. Create Stores in System
        // ---------------------------------------------------------

        // Create store 1
        Location store1Location = new Location(45, "Area1", "City1", "State1", "India", 12345);
        Store store1 = new Store(1001, store1Location);
        rentalSystem.addStore(store1);

        // ---------------------------------------------------------
        // 2. Create Users in System
        // ---------------------------------------------------------

        // Create users
        User user1 = new User(801, "SJ", "DL2022GDG556690");
        User user2 = new User(802, "DJ", "DL2017DHW9090765231");
        rentalSystem.addUser(user1);
        rentalSystem.addUser(user2);


        // ---------------------------------------------------------
        // 3. Add vehicles to store inventory
        // ---------------------------------------------------------
        Vehicle vehicle1 = new Vehicle(1, "DL1234", VehicleType.FOUR_WHEELER);
        vehicle1.setDailyRentalCost(1100);

        Vehicle vehicle2 = new Vehicle(2, "DL5678", VehicleType.FOUR_WHEELER);
        vehicle2.setDailyRentalCost(1400);

        store1.getInventory().addVehicle(vehicle1);
        store1.getInventory().addVehicle(vehicle2);


        // ---------------------------------------------------------
        // 4. User selects store and searches vehicles
        // ---------------------------------------------------------
        Store selectedStore = rentalSystem.getStore(1001);

        LocalDate fromDate = LocalDate.of(2025, 12, 5);
        LocalDate toDate = LocalDate.of(2025, 12, 7);

        System.out.println("\nAvailable vehicles from " + fromDate + " to " + toDate + ":");

        for (Vehicle vehicle : selectedStore.getVehicles(VehicleType.FOUR_WHEELER, fromDate, toDate)) {
            System.out.println(" - " + vehicle.getVehicleID() + ": " + vehicle.getVehicleType());
        }


        // ---------------------------------------------------------
        // 5. User creates reservation
        // ---------------------------------------------------------
        System.out.println("\nCreating reservation...");

        Reservation reservation = selectedStore.createReservation(
                1,  // vehicle ID
                user1,
                fromDate,
                toDate,
                ReservationType.DAILY
        );

        System.out.println("Reservation created with ID: " + reservation.getReservationId());


        // ---------------------------------------------------------
        // 6. User starts the trip
        // ---------------------------------------------------------
        System.out.println("\nStarting trip...");
        selectedStore.startTrip(reservation.getReservationId());


        // ---------------------------------------------------------
        // 7. User submits the vehicle
        // ---------------------------------------------------------
        System.out.println("Submitting vehicle...");
        selectedStore.submitVehicle(reservation.getReservationId());


        // ---------------------------------------------------------
        // 8. System generates the bill
        // ---------------------------------------------------------
        System.out.println("\nGenerating bill...");
        Bill bill = selectedStore.generateBill(reservation.getReservationId(),
                new DailyBillingStrategy(selectedStore.getInventory()));

        System.out.println("Bill ID: " + bill.getBillId());
        System.out.println("Bill Amount: " + bill.getTotalBillAmount());


        // ---------------------------------------------------------
        // 9. User makes payment
        // ---------------------------------------------------------
        System.out.println("\nProcessing Payment...");

        Payment payment = selectedStore.makePayment(
                bill,
                new UPIPaymentStrategy(),
                bill.getTotalBillAmount()
        );

        System.out.println("\n===== PAYMENT RECEIPT =====");
        System.out.println("Payment ID: " + payment.getPaymentId());
        System.out.println("Paid Amount: " + payment.getAmountPaid());
        System.out.println("Payment Mode: " + payment.getPaymentMode());
        System.out.println("Payment Date: " + payment.getPaymentDate());
        System.out.println("============================");
    }
}