package com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model;

// One dish. Price and availability can change while the app is live
// (restaurant runs out of biryani), so both are volatile: every thread sees the latest value.
public class MenuItem {

    private final String id;
    private final String name;
    private volatile long price;                 // whole rupees, never double
    private volatile boolean available = true;

    public MenuItem(String id, String name, long price) {
        this.id = id;
        this.name = name;
        this.price = price;
    }

    public String  getId()        { return id; }
    public String  getName()      { return name; }
    public long    getPrice()     { return price; }
    public boolean isAvailable()  { return available; }

    public void setPrice(long price)            { this.price = price; }
    public void setAvailable(boolean available) { this.available = available; }
}
