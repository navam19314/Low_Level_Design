package com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine;

import com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.addon.Caramel;
import com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.addon.ExtraShot;
import com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.addon.LargeSize;
import com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.addon.Milk;
import com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.beverage.Beverage;
import com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.beverage.Espresso;
import com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.beverage.Latte;
import com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.model.Ingredient;
import com.conceptcoding.interviewquestions.hello_all_questions.composition.coffeemachine.model.Inventory;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class CoffeeMachineDriver {

    public static void main(String[] args) throws Exception {
        CoffeeMachine machine = new CoffeeMachine(fullInventory());

        System.out.println("=== Plain and decorated ===");
        System.out.println("  " + machine.brew(new Espresso()) + "  (expect ₹100)");
        Beverage fancy = new Caramel(new ExtraShot(new Latte()));
        System.out.println("  " + machine.brew(fancy) + "  (expect 150 + 40 + 35 = ₹225)");
        System.out.println("  recipe " + fancy.recipe());

        System.out.println("\n=== Order matters for Large ===");
        Beverage largeMilky = new LargeSize(new Milk(new Espresso()));
        Beverage milkyLarge = new Milk(new LargeSize(new Espresso()));
        System.out.println("  " + largeMilky.description() + " ₹" + largeMilky.cost() + " " + largeMilky.recipe());
        System.out.println("  " + milkyLarge.description() + " ₹" + milkyLarge.cost() + " " + milkyLarge.recipe());

        System.out.println("\n=== Menu: build from names (what the touchscreen sends) ===");
        Menu menu = new Menu();
        System.out.println("  " + machine.brew(menu.build("americano", List.of("extra_shot", "extra_shot"))));
        tryIt("unknown add-on", () -> menu.build("latte", List.of("whisky")));
        tryIt("unknown drink", () -> menu.build("frappe", List.of()));

        System.out.println("\n=== Running out: all-or-nothing ===");
        Inventory small = new Inventory();
        small.refill(Ingredient.COFFEE_BEANS, 100);
        small.refill(Ingredient.WATER, 1000);
        small.refill(Ingredient.MILK, 120);
        CoffeeMachine m2 = new CoffeeMachine(small);
        System.out.println("  can make a latte? " + m2.canMake(new Latte()) + " (needs 150 ml milk, have 120)");
        tryIt("brew a latte", () -> m2.brew(new Latte()));
        System.out.println("  beans untouched by the failed order: " + small.get(Ingredient.COFFEE_BEANS) + " (expect 100)");
        System.out.println("  needs refill (< 200): " + m2.needsRefill(200));
        small.refill(Ingredient.MILK, 500);
        System.out.println("  after refill: " + m2.brew(new Latte()));

        concurrentOrders();
    }

    // 20 people order a latte at once; there's milk for exactly 10. 10 get coffee, milk never goes negative.
    private static void concurrentOrders() throws Exception {
        System.out.println("\n=== Concurrency: 20 latte orders, milk for 10 ===");
        Inventory inv = new Inventory();
        inv.refill(Ingredient.COFFEE_BEANS, 10_000);
        inv.refill(Ingredient.WATER, 10_000);
        inv.refill(Ingredient.MILK, 1500);                    // 10 × 150 ml
        CoffeeMachine machine = new CoffeeMachine(inv);
        AtomicInteger served = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        for (int i = 0; i < 20; i++) {
            pool.submit(() -> {
                start.await();
                try {
                    machine.brew(new Latte());
                    served.incrementAndGet();
                } catch (IllegalStateException e) {
                    // out of milk
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
        System.out.println("  served = " + served.get() + " (expect 10), milk left = " + inv.get(Ingredient.MILK) + " (expect 0)");
    }

    private static Inventory fullInventory() {
        Inventory inv = new Inventory();
        for (Ingredient i : Ingredient.values()) inv.refill(i, 5000);
        return inv;
    }

    private static void tryIt(String label, Runnable action) {
        try {
            action.run();
            System.out.println("  " + label + ": ALLOWED");
        } catch (RuntimeException e) {
            System.out.println("  " + label + ": rejected → " + e.getMessage());
        }
    }
}
