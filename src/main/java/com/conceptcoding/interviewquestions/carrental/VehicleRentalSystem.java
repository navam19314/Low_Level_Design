package com.conceptcoding.interviewquestions.carrental;

import java.util.ArrayList;
import java.util.List;

/*
we can also create, StoreManager class which takes
Care of managing List of Stores, and this VehicleRentalSystem has StoreManager

similarly we can also create UserManager, which takes
care of managing list of Users, and this VehicleRentalSystem has UserManager

for now for simplicity i am putting list of stores and list of Users in VehicleRentalSystem class.
 */
public class VehicleRentalSystem {

    List<Store> storeList;
    List<User> userList;

    public VehicleRentalSystem() {
        storeList = new ArrayList<>();
        userList = new ArrayList<>();
    }

    public Store getStore(int storeId) {
        // Find store by ID
        for (Store store : storeList) {
            if (store.getStoreId() == storeId) {
                return store;
            }
        }
        throw new RuntimeException("Store not found with ID: " + storeId);
    }

    public User getUser(int userId) {
        // Find user by ID
        for (User user : userList) {
            if (user.getUserId() == userId) {
                return user;
            }
        }
        throw new RuntimeException("User not found with ID: " + userId);
    }

    public void addStore(Store store) {
        storeList.add(store);
    }

    public void addUser(User user) {
        userList.add(user);
    }

    public void removeStore(int storeId) {
        // Remove store by ID
        storeList.removeIf(store -> store.getStoreId() == storeId);
    }

    public void removeUser(int userId) {
        // Remove user by ID
        userList.removeIf(user -> user.getUserId() == userId);
    }

}