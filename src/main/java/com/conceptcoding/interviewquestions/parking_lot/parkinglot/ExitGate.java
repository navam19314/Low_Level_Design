package com.conceptcoding.interviewquestions.parking_lot.parkinglot;

import com.conceptcoding.interviewquestions.parking_lot.Ticket;
import com.conceptcoding.interviewquestions.parking_lot.payment.Payment;
import com.conceptcoding.interviewquestions.parking_lot.pricing.CostComputation;

public class ExitGate {

    private final CostComputation costComputation;

    public ExitGate(CostComputation costComputation) {
        this.costComputation = costComputation;
    }

    public void completeExit(ParkingBuilding building,
                             Ticket ticket,
                             Payment payment) {
        // Step 1: Calculate parking cost
        double amount = calculatePrice(ticket);

        // Step 2: Process payment
        boolean paymentSuccess = payment.pay(amount);
        if (!paymentSuccess) {
            throw new RuntimeException("Payment failed. Exit denied.");
        }

        // Step 3: Release parking spot
        building.release(ticket);
        System.out.println("Exit successful. Gate opened.");
    }

    private double calculatePrice(Ticket ticket) {
        return costComputation.compute(ticket);
    }
}

