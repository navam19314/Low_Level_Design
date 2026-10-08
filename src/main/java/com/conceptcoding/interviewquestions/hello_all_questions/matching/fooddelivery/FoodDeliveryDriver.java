package com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery;

import com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.assignment.NearestPartnerStrategy;
import com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model.DeliveryPartner;
import com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model.Location;
import com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model.MenuItem;
import com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model.Order;
import com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model.Restaurant;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class FoodDeliveryDriver {

    public static void main(String[] args) throws Exception {
        FoodDeliveryService app = new FoodDeliveryService(new NearestPartnerStrategy());

        Restaurant meghana = new Restaurant("R1", "Meghana Foods", new Location(0, 0));
        MenuItem biryani = new MenuItem("I1", "Chicken Biryani", 320);
        meghana.addItem(biryani);
        meghana.addItem(new MenuItem("I2", "Raita", 40));
        Restaurant truffles = new Restaurant("R2", "Truffles", new Location(5, 5));
        truffles.addItem(new MenuItem("I3", "Burger", 250));
        app.addRestaurant(meghana);
        app.addRestaurant(truffles);

        DeliveryPartner near = new DeliveryPartner("P1", "Ravi (1 km)", new Location(1, 0));
        DeliveryPartner far  = new DeliveryPartner("P2", "Asha (8 km)", new Location(8, 0));
        app.addPartner(near);
        app.addPartner(far);

        System.out.println("=== Search 'meg' ===");
        app.searchRestaurants("meg").forEach(r -> System.out.println("  " + r.getName()));

        System.out.println("\n=== Cart: one restaurant only ===");
        app.addToCart("alice", "R1", "I1", 2);
        app.addToCart("alice", "R1", "I2", 1);
        try { app.addToCart("alice", "R2", "I3", 1); }
        catch (IllegalStateException e) { System.out.println("  Rejected: " + e.getMessage()); }

        System.out.println("\n=== Place order: prices are copied into the order ===");
        Order o1 = app.placeOrder("alice", new Location(2, 2));
        biryani.setPrice(999);                                   // menu price changes AFTER ordering
        System.out.println("  " + o1.getId() + " " + o1.getItems() + " total ₹" + o1.getTotal()
                + "  (expect ₹680, not affected by new price)");
        System.out.println("  cart empty after order? " + app.getCart("alice").isEmpty());

        System.out.println("\n=== Illegal transition is rejected ===");
        try { app.deliver(o1.getId()); }
        catch (IllegalStateException e) { System.out.println("  Rejected: " + e.getMessage()); }

        System.out.println("\n=== Accept → nearest free rider assigned ===");
        app.acceptOrder(o1.getId());
        System.out.println("  rider = " + o1.getPartnerId() + "  (expect P1, the nearest)");

        System.out.println("\n=== Second order while Ravi is busy → next nearest ===");
        app.addToCart("bob", "R1", "I2", 3);
        Order o2 = app.placeOrder("bob", new Location(1, 1));
        app.acceptOrder(o2.getId());
        System.out.println("  rider = " + o2.getPartnerId() + "  (expect P2)");

        System.out.println("\n=== Third order: no rider free → waits, assigned when one frees up ===");
        app.addToCart("carol", "R2", "I3", 1);
        Order o3 = app.placeOrder("carol", new Location(6, 6));
        app.acceptOrder(o3.getId());
        System.out.println("  rider now = " + o3.getPartnerId() + "  (expect null)");
        app.markReady(o1.getId());
        app.pickUp(o1.getId());
        app.deliver(o1.getId());
        System.out.println("  " + o1.getId() + " " + o1.getStatus() + "; " + o3.getId() + " rider = "
                + o3.getPartnerId() + "  (expect P1, freed by the delivery)");

        System.out.println("\n=== Cancel frees the rider; cancel after pickup is rejected ===");
        app.cancel(o2.getId());
        System.out.println("  " + o2.getId() + " " + o2.getStatus() + ", Asha free? " + far.isAvailable());
        app.markReady(o3.getId());
        app.pickUp(o3.getId());
        try { app.cancel(o3.getId()); }
        catch (IllegalStateException e) { System.out.println("  Rejected: " + e.getMessage()); }

        concurrentAssignment();
    }

    // 20 orders accepted at the same instant, only 5 riders: exactly 5 orders get a rider
    // and no rider is given two orders.
    private static void concurrentAssignment() throws Exception {
        System.out.println("\n=== Concurrency: 20 orders accepted at once, 5 riders ===");
        FoodDeliveryService app = new FoodDeliveryService(new NearestPartnerStrategy());
        Restaurant r = new Restaurant("R1", "Cloud Kitchen", new Location(0, 0));
        r.addItem(new MenuItem("I1", "Thali", 150));
        app.addRestaurant(r);
        for (int i = 1; i <= 5; i++) app.addPartner(new DeliveryPartner("P" + i, "Rider " + i, new Location(i, 0)));

        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            app.addToCart("user" + i, "R1", "I1", 1);
            orders.add(app.placeOrder("user" + i, new Location(1, 1)));
        }

        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        for (Order o : orders) {
            pool.submit(() -> {
                start.await();
                app.acceptOrder(o.getId());
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        int assigned = 0;
        Set<String> ridersUsed = new HashSet<>();
        for (Order o : orders) {
            if (o.getPartnerId() != null) {
                assigned++;
                ridersUsed.add(o.getPartnerId());
            }
        }
        System.out.println("  orders with a rider = " + assigned + "  (expect 5)");
        System.out.println("  distinct riders     = " + ridersUsed.size() + "  (expect 5)");
        System.out.println(assigned == 5 && ridersUsed.size() == 5 ? "  no rider double-assigned ✓" : "  RACE ✗");
    }
}
