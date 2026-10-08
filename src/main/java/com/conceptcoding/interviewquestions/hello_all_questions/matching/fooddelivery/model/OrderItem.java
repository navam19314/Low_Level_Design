package com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model;

// A line on the order. Name and price are COPIED from the menu at order time,
// so a later price change on the menu never changes what the customer was charged.
public class OrderItem {

    private final String itemId;
    private final String name;
    private final long unitPrice;
    private final int quantity;

    public OrderItem(String itemId, String name, long unitPrice, int quantity) {
        this.itemId = itemId;
        this.name = name;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    public long subtotal() { return unitPrice * quantity; }

    public String getItemId()    { return itemId; }
    public String getName()      { return name; }
    public long   getUnitPrice() { return unitPrice; }
    public int    getQuantity()  { return quantity; }

    @Override
    public String toString() { return quantity + " x " + name + " @ ₹" + unitPrice; }
}
