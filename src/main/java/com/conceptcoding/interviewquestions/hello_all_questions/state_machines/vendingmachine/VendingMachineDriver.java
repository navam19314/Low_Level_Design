package com.conceptcoding.interviewquestions.hello_all_questions.state_machines.vendingmachine;

import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.vendingmachine.model.Coin;
import com.conceptcoding.interviewquestions.hello_all_questions.state_machines.vendingmachine.model.Product;

public class VendingMachineDriver {

    public static void main(String[] args) {
        scenarioHappyPath();
        scenarioInsufficientBalance();
        scenarioCancelRefunds();
        scenarioOutOfStock();
        scenarioInvalidTransitionsFromNoCoin();
        scenarioExactChangeNoRefund();
        scenarioSoldOutAndRestock();
    }

    // ---- 1. Happy path: TEN + FIVE = ₹15 → Soda ₹15 dispensed, no change ----
    private static void scenarioHappyPath() {
        System.out.println("=== Scenario 1: happy path ===");
        VendingMachine vm = primedMachine();
        vm.insertCoin(Coin.TEN);
        vm.insertCoin(Coin.FIVE);
        vm.selectProduct("A1");
        System.out.println();
    }

    // ---- 2. Insufficient balance — select before enough coins ----
    private static void scenarioInsufficientBalance() {
        System.out.println("=== Scenario 2: insufficient balance ===");
        VendingMachine vm = primedMachine();
        vm.insertCoin(Coin.FIVE);      // ₹5, need ₹15
        try {
            vm.selectProduct("A1");
        } catch (IllegalStateException e) {
            System.out.println("  rejected: " + e.getMessage());
        }
        System.out.println();
    }

    // ---- 3. Cancel returns the deposited balance ----
    private static void scenarioCancelRefunds() {
        System.out.println("=== Scenario 3: cancel returns balance ===");
        VendingMachine vm = primedMachine();
        vm.insertCoin(Coin.TEN);
        vm.insertCoin(Coin.TEN);
        vm.cancel();                    // refund ₹20
        System.out.println();
    }

    // ---- 4. One slot out of stock, others still sell ----
    private static void scenarioOutOfStock() {
        System.out.println("=== Scenario 4: one slot out of stock ===");
        VendingMachine vm = new VendingMachine();
        vm.stockProduct(new Product("A1", "Soda", 15), 1);    // only one Soda
        vm.stockProduct(new Product("B2", "Chips", 10), 3);
        vm.insertCoin(Coin.TEN); vm.insertCoin(Coin.FIVE);
        vm.selectProduct("A1");                                // the last Soda
        vm.insertCoin(Coin.TEN); vm.insertCoin(Coin.FIVE);
        try {
            vm.selectProduct("A1");
        } catch (IllegalStateException e) {
            System.out.println("  rejected: " + e.getMessage());
        }
        vm.selectProduct("B2");                                // pick something else: ₹5 change
        System.out.println();
    }

    // ---- 5. Invalid transitions from NoCoin ----
    private static void scenarioInvalidTransitionsFromNoCoin() {
        System.out.println("=== Scenario 5: invalid actions from NoCoin state ===");
        VendingMachine vm = primedMachine();
        try { vm.selectProduct("A1"); } catch (IllegalStateException e) { System.out.println("  selectProduct: " + e.getMessage()); }
        try { vm.dispense();           } catch (IllegalStateException e) { System.out.println("  dispense:      " + e.getMessage()); }
        vm.cancel();
        System.out.println();
    }

    // ---- 6. Exact change — no refund printed ----
    private static void scenarioExactChangeNoRefund() {
        System.out.println("=== Scenario 6: exact change → no refund printed ===");
        VendingMachine vm = primedMachine();
        vm.insertCoin(Coin.TEN); vm.insertCoin(Coin.FIVE);   // exactly ₹15
        vm.selectProduct("A1");
        System.out.println();
    }

    // ---- 7. Whole machine sold out → coins refused; restock → back in business ----
    private static void scenarioSoldOutAndRestock() {
        System.out.println("=== Scenario 7: sold out, then restocked ===");
        VendingMachine vm = new VendingMachine();
        vm.stockProduct(new Product("A1", "Soda", 15), 1);
        vm.insertCoin(Coin.TEN); vm.insertCoin(Coin.FIVE);
        vm.selectProduct("A1");                                // last item in the machine
        System.out.println("  state: " + vm.getState().getClass().getSimpleName() + " (expect SoldOutState)");
        try {
            vm.insertCoin(Coin.TEN);
        } catch (IllegalStateException e) {
            System.out.println("  rejected: " + e.getMessage());
        }
        vm.stockProduct(new Product("A1", "Soda", 15), 5);
        System.out.println("  after restock: " + vm.getState().getClass().getSimpleName() + " (expect NoCoinState)");
        vm.insertCoin(Coin.TWENTY);
        vm.selectProduct("A1");
        System.out.println();
    }

    // ----- helper -----
    private static VendingMachine primedMachine() {
        VendingMachine vm = new VendingMachine();
        vm.stockProduct(new Product("A1", "Soda",  15), 5);
        vm.stockProduct(new Product("B2", "Chips", 10), 5);
        vm.stockProduct(new Product("C3", "Candy",  5), 5);
        return vm;
    }
}
