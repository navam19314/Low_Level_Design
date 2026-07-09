package com.conceptcoding.interviewquestions.hello_all_questions.vendingmachine.model;

// Immutable product. slot ("A1", "B3") is the user-facing selector on the machine.
public class Product {

    private final String slot;
    private final String name;
    private final int    price;

    public Product(String slot, String name, int price) {
        this.slot  = slot;
        this.name  = name;
        this.price = price;
    }

    public String getSlot()  { return slot; }
    public String getName()  { return name; }
    public int    getPrice() { return price; }
}
