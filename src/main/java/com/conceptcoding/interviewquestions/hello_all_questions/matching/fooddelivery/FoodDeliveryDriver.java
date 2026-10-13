package com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery;

import com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.assignment.NearestPartnerStrategy;
import com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model.DeliveryPartner;
import com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model.Location;
import com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model.MenuItem;
import com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model.Order;
import com.conceptcoding.interviewquestions.hello_all_questions.matching.fooddelivery.model.Restaurant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class FoodDeliveryDriver {

    static final Location HOME = new Location(2, 2);

    public static void main(String[] args) throws Exception {
        FoodDeliveryService app = new FoodDeliveryService(new NearestPartnerStrategy());
        Restaurant meghana = new Restaurant("R1", "Meghana Foods", new Location(0, 0));
        MenuItem biryani = new MenuItem("I1", "Biryani", 320);
        MenuItem raita = new MenuItem("I2", "Raita", 40);
        meghana.addItem(biryani);
        meghana.addItem(raita);
        app.addRestaurant(meghana);
        DeliveryPartner ravi = new DeliveryPartner("P1", "Ravi", new Location(1, 0));    // 1 km from the restaurant
        DeliveryPartner asha = new DeliveryPartner("P2", "Asha", new Location(8, 0));    // 8 km
        app.addPartner(ravi);
        app.addPartner(asha);

        System.out.println("=== Place order: prices are copied into the order ===");
        Order o1 = app.placeOrder("alice", "R1", items("I1", 2, "I2", 1), HOME);
        biryani.setPrice(999);                                   // the menu changes AFTER ordering
        System.out.println("  " + o1.getId() + " " + o1.getItems() + " total ₹" + o1.getTotal() + " (expect ₹680)");

        System.out.println("\n=== Restaurant accepts → nearest free rider ===");
        tryIt("deliver before accepting", () -> app.deliver(o1.getId()));
        app.acceptOrder(o1.getId());
        System.out.println("  " + o1.getStatus() + ", rider " + o1.getPartner().getName() + " (expect ACCEPTED, Ravi)");

        System.out.println("\n=== Second order while Ravi is busy → Asha ===");
        Order o2 = app.placeOrder("bob", "R1", items("I2", 3), HOME);
        app.acceptOrder(o2.getId());
        System.out.println("  rider " + o2.getPartner().getName() + " (expect Asha)");

        System.out.println("\n=== Third order, no rider free → stays PLACED ===");
        Order o3 = app.placeOrder("carol", "R1", items("I2", 1), HOME);
        tryIt("accept with no rider free", () -> app.acceptOrder(o3.getId()));
        System.out.println("  " + o3.getStatus() + ", rider " + o3.getPartner() + " (expect PLACED, null)");

        System.out.println("\n=== Pick up → deliver → rider free again, at the customer's door ===");
        app.pickUp(o1.getId());
        tryIt("cancel after pickup", () -> app.cancel(o1.getId()));
        app.deliver(o1.getId());
        System.out.println("  " + o1.getStatus() + ", Ravi free? " + ravi.isAvailable() + ", at " + ravi.getLocation() + " (expect DELIVERED, true, (2.0,2.0))");

        System.out.println("\n=== Cancel before pickup frees the rider ===");
        app.cancel(o2.getId());
        System.out.println("  " + o2.getStatus() + ", Asha free? " + asha.isAvailable() + " (expect CANCELLED, true)");
        app.acceptOrder(o3.getId());
        System.out.println("  carol accepted, rider " + o3.getPartner().getName() + " (expect Ravi: 2.8 km away now, Asha 8 km)");

        System.out.println("\n=== Rejections ===");
        raita.setAvailable(false);
        tryIt("order an unavailable item", () -> app.placeOrder("dan", "R1", items("I2", 1), HOME));
        tryIt("empty order", () -> app.placeOrder("dan", "R1", items(), HOME));
        tryIt("quantity 0", () -> app.placeOrder("dan", "R1", items("I1", 0), HOME));
        tryIt("unknown item", () -> app.placeOrder("dan", "R1", items("I9", 1), HOME));
        tryIt("accept twice", () -> app.acceptOrder(o3.getId()));

        concurrentAccepts();
    }

    // 20 restaurants accept at the same instant, 5 riders: exactly 5 orders get a rider, all different.
    private static void concurrentAccepts() throws Exception {
        System.out.println("\n=== Concurrency: 20 accepts, 5 riders ===");
        FoodDeliveryService app = new FoodDeliveryService(new NearestPartnerStrategy());
        Restaurant r = new Restaurant("R1", "Meghana Foods", new Location(0, 0));
        r.addItem(new MenuItem("I1", "Biryani", 320));
        app.addRestaurant(r);
        for (int i = 0; i < 5; i++) app.addPartner(new DeliveryPartner("P" + i, "Rider " + i, new Location(i, 0)));
        List<Order> orders = new ArrayList<>();
        for (int i = 0; i < 20; i++) orders.add(app.placeOrder("u" + i, "R1", items("I1", 1), HOME));

        List<String> riders = Collections.synchronizedList(new ArrayList<>());
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch go = new CountDownLatch(1);
        for (Order o : orders) {
            pool.submit(() -> {
                go.await();
                try { app.acceptOrder(o.getId()); riders.add(o.getPartner().getId()); }
                catch (IllegalStateException e) { /* no rider free */ }
                return null;
            });
        }
        go.countDown();
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
        Set<String> distinct = new HashSet<>(riders);
        System.out.println("  assigned = " + riders.size() + " (expect 5), distinct riders = " + distinct.size() + " (expect 5)");
    }

    // items("I1", 2, "I2", 1) → {I1=2, I2=1}, in that order
    private static Map<String, Integer> items(Object... pairs) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) m.put((String) pairs[i], (Integer) pairs[i + 1]);
        return m;
    }

    private static void tryIt(String label, Runnable action) {
        try {
            action.run();
            System.out.println("  " + label + ": ALLOWED ✗");
        } catch (RuntimeException e) {
            System.out.println("  " + label + ": rejected → " + e.getMessage());
        }
    }
}
