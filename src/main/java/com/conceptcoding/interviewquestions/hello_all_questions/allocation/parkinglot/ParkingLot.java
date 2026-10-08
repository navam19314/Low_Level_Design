package com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot;

import com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot.allocation.SpotAllocationStrategy;
import com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot.model.ParkingSpot;
import com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot.model.Ticket;
import com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot.model.Vehicle;
import com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot.pricing.PricingStrategy;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

// The service the entry and exit gates call.
//
// Concurrency without a global lock:
//   occupiedSpotIds.add(id)        is atomic: of two cars racing for spot C1, exactly one gets true
//   platesInside.putIfAbsent(...)  is atomic: the same car can't enter twice
//   activeTickets.remove(id)       is atomic: a ticket can only be exited once
public class ParkingLot {

    private final List<ParkingSpot> spots;
    private final Set<String> occupiedSpotIds = ConcurrentHashMap.newKeySet();
    private final Map<String, Ticket> activeTickets = new ConcurrentHashMap<>();   // ticketId → ticket
    private final Map<String, String> platesInside = new ConcurrentHashMap<>();    // plate → ticketId
    private final SpotAllocationStrategy allocationStrategy;
    private final PricingStrategy pricingStrategy;
    private final Clock clock;
    private final AtomicLong ticketSeq = new AtomicLong();

    public ParkingLot(List<ParkingSpot> spots, SpotAllocationStrategy allocationStrategy,
                      PricingStrategy pricingStrategy, Clock clock) {
        this.spots = new ArrayList<>(spots);
        this.allocationStrategy = allocationStrategy;
        this.pricingStrategy = pricingStrategy;
        this.clock = clock;
    }

    public Ticket enter(Vehicle vehicle) {
        String ticketId = "T-" + ticketSeq.incrementAndGet();
        if (platesInside.putIfAbsent(vehicle.getLicensePlate(), ticketId) != null) {
            throw new IllegalStateException(vehicle.getLicensePlate() + " is already inside");
        }

        List<ParkingSpot> candidates = new ArrayList<>();
        for (ParkingSpot spot : spots) {
            if (spot.getType().canFit(vehicle.getType()) && !occupiedSpotIds.contains(spot.getId())) {
                candidates.add(spot);
            }
        }
        for (ParkingSpot spot : allocationStrategy.rank(candidates, vehicle.getType())) {
            if (occupiedSpotIds.add(spot.getId())) {         // atomic claim; false = someone beat us to it
                Ticket ticket = new Ticket(ticketId, spot.getId(), vehicle, clock.instant());
                activeTickets.put(ticketId, ticket);
                return ticket;
            }
        }
        platesInside.remove(vehicle.getLicensePlate());       // undo: the car didn't get in
        throw new IllegalStateException("No spot available for " + vehicle.getType());
    }

    // returns the fee in rupees
    public long exit(String ticketId) {
        Ticket ticket = activeTickets.remove(ticketId);       // atomic: a second exit finds nothing
        if (ticket == null) throw new NoSuchElementException("Ticket not found or already used: " + ticketId);

        Duration parked = Duration.between(ticket.getEntryTime(), clock.instant());
        long fee = pricingStrategy.fee(ticket.getVehicle().getType(), parked);
        occupiedSpotIds.remove(ticket.getSpotId());
        platesInside.remove(ticket.getVehicle().getLicensePlate());
        return fee;
    }

    // for the "spots free" display board at the entrance
    public int freeSpotsFor(Vehicle vehicle) {
        int free = 0;
        for (ParkingSpot spot : spots) {
            if (spot.getType().canFit(vehicle.getType()) && !occupiedSpotIds.contains(spot.getId())) free++;
        }
        return free;
    }
}
