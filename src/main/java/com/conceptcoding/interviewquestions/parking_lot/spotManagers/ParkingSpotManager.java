package com.conceptcoding.interviewquestions.parking_lot.spotManagers;

import com.conceptcoding.interviewquestions.parking_lot.Entity.ParkingSpot;
import com.conceptcoding.interviewquestions.parking_lot.LookupStrategy.ParkingSpotLookupStrategy;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public abstract class ParkingSpotManager {

    protected final List<ParkingSpot> spots;
    protected final ParkingSpotLookupStrategy strategy;
    private final ReentrantLock lock = new ReentrantLock(true);

    protected ParkingSpotManager(List<ParkingSpot> spots,
                                 ParkingSpotLookupStrategy strategy) {
        this.spots = spots;
        this.strategy = strategy;
    }

    public ParkingSpot park() {
        lock.lock();
        try {
            // Step 1: Select a free spot using strategy
            ParkingSpot spot = strategy.selectSpot(spots);
            if (spot == null) {
                return null; // No free spot available
            }

            // Step 2: Occupy the selected spot
            spot.occupySpot();
            return spot;
        } finally {
            lock.unlock();
        }
    }

    public void unPark(ParkingSpot spot) {
        lock.lock();
        try {
            spot.releaseSpot();
        } finally {
            lock.unlock();
        }
    }

    public boolean hasFreeSpot() {
        lock.lock();
        try {
            // Check if any spot is free
            for (ParkingSpot spot : spots) {
                if (spot.isSpotFree()) {
                    return true;
                }
            }
            return false;
        } finally {
            lock.unlock();
        }
    }
}


