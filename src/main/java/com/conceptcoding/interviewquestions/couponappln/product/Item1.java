package com.conceptcoding.interviewquestions.couponappln.product;

public class Item1 extends Product {

    public Item1(String name, double price, ProductType type) {
        super(name, price, type);
    }

    @Override
    public double getPrice() {
        return originalPrice;
    }
}
