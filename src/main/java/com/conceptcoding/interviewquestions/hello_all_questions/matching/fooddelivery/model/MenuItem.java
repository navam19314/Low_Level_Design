package com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model;

public class MenuItem {

    private final String id;
    private final String name;
    private long price;                      // whole rupees
    private boolean available = true;

    public MenuItem(String id, String name, long price) {
        this.id = id;
        this.name = name;
        this.price = price;
    }

    public String  getId()       { return id; }
    public String  getName()     { return name; }
    public long    getPrice()    { return price; }
    public boolean isAvailable() { return available; }
    public void    setPrice(long price)            { this.price = price; }
    public void    setAvailable(boolean available) { this.available = available; }
}
