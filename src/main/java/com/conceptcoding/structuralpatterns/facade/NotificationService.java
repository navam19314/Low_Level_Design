package com.conceptcoding.structuralpatterns.facade;

// Subsystem D: Notification
public class NotificationService {
    public void sendConfirmation(String productId) {
        System.out.println("Sending order confirmation for product: " + productId);
    }
}