package com.conceptcoding.interviewquestions.couponappln;


import com.conceptcoding.interviewquestions.couponappln.coupons.PercentageCouponDecorator;
import com.conceptcoding.interviewquestions.couponappln.coupons.TypeCouponDecorator;
import com.conceptcoding.interviewquestions.couponappln.product.Product;

import java.util.ArrayList;
import java.util.List;

public class ShoppingCart {

    List<Product> productList;

    public ShoppingCart() {
        productList = new ArrayList<>();
    }

    public void addToCart(Product product) {
        // Decorate the Product with applicable coupons
        Product productWithEligibleDiscount =
                new TypeCouponDecorator(new PercentageCouponDecorator(product, 20), 10);
        productList.add(productWithEligibleDiscount);
    }

    public double getTotalPrice() {
        double totalPrice = 0;

        // productList contains decorated products
        for (Product product : productList) {
            totalPrice += product.getPrice(); // getPrice() will return the price after applying the coupons
        }

        return totalPrice;
    }
}