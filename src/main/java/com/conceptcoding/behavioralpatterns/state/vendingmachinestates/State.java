package com.conceptcoding.behavioralpatterns.state.vendingmachinestates;

import com.conceptcoding.behavioralpatterns.state.context.Item;
import com.conceptcoding.behavioralpatterns.state.context.VendingMachine;

import java.util.List;


public abstract class State {

    public void clickOnInsertCoinButton(VendingMachine machine) throws Exception {
         // by default nothing happens
    }

    public void clickOnStartProductSelectionButton(VendingMachine machine) throws Exception {
        // by default nothing happens
    }

    public void insertCoin(VendingMachine machine, Coin coin) throws Exception {
        // by default nothing happens
    }

    public void chooseProduct(VendingMachine machine, int codeNumber) throws Exception {
        // by default nothing happens
    }

    public int getChange(int returnChangeMoney) throws Exception {
        // by default nothing happens
        return 0;
    }

    public Item dispenseProduct(VendingMachine machine, int codeNumber) throws Exception {
        // by default nothing happens
        return null;
    }

    public List<Coin> refundFullMoney(VendingMachine machine) throws Exception {
        // by default nothing happens
        return null;
    }

    public void updateInventory(VendingMachine machine, Item item, int codeNumber) throws Exception {
        // by default nothing happens
    }
}


