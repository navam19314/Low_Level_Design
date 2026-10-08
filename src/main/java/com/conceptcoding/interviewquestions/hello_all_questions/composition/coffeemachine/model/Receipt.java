package com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.model;

public class Receipt {

    private final String orderId;
    private final String description;
    private final long price;              // rupees

    public Receipt(String orderId, String description, long price) {
        this.orderId = orderId;
        this.description = description;
        this.price = price;
    }

    public String getOrderId()     { return orderId; }
    public String getDescription() { return description; }
    public long   getPrice()       { return price; }

    @Override
    public String toString() { return orderId + ": " + description + " = ₹" + price; }
}
