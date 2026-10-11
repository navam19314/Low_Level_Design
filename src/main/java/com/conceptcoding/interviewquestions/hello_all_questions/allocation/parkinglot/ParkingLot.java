package com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot;

import com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot.allocation.SpotAllocationStrategy;
import com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot.model.ParkingSpot;
import com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot.model.Ticket;
import com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot.model.VehicleType;
import com.conceptcoding.interviewquestions.hello_all_questions.allocation.parkinglot.pricing.PricingStrategy;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

// The service the entry and exit gates call.
//
// Thread safety: enter() and exit() are synchronized, one lock for the whole lot.
// Simple and correct: two gates can never get the same spot. If the interviewer asks for more
// throughput, the follow-up replaces this with an atomic claim (see the walkthrough, Extensibility #6).
public class ParkingLot {

    private final List<ParkingSpot> spots;
    private final Set<String> occupiedSpotIds = new HashSet<>();        // the ONE place "taken" lives
    private final Map<String, Ticket> activeTickets = new HashMap<>();  // ticketId → ticket
    private final SpotAllocationStrategy allocationStrategy;
    private final PricingStrategy pricingStrategy;
    private final Clock clock;
    private int nextTicketNumber = 1;

    public ParkingLot(List<ParkingSpot> spots, SpotAllocationStrategy allocationStrategy,
                      PricingStrategy pricingStrategy, Clock clock) {
        this.spots = new ArrayList<>(spots);
        this.allocationStrategy = allocationStrategy;
        this.pricingStrategy = pricingStrategy;
        this.clock = clock;
    }

    public synchronized Ticket enter(VehicleType vehicle) {
        ParkingSpot spot = allocationStrategy.choose(freeSpots(), vehicle);
        if (spot == null) {
            throw new IllegalStateException("No spot available for " + vehicle);
        }
        occupiedSpotIds.add(spot.getId());
        Ticket ticket = new Ticket("T-" + nextTicketNumber++, spot.getId(), vehicle, clock.instant());
        activeTickets.put(ticket.getId(), ticket);
        return ticket;
    }

    // returns the fee in rupees.
    // Atomic: the fee is worked out FIRST. If pricing fails, nothing has changed yet,
    // so the ticket is still valid (no "spot freed but nobody charged").
    public synchronized long exit(String ticketId) {
        Ticket ticket = activeTickets.get(ticketId);
        if (ticket == null) throw new NoSuchElementException("Ticket not found or already used: " + ticketId);
        Duration parked = Duration.between(ticket.getEntryTime(), clock.instant());
        long fee = pricingStrategy.fee(ticket.getVehicleType(), parked);     // may throw: no state changed yet
        activeTickets.remove(ticketId);                                     // removed = can't exit twice
        occupiedSpotIds.remove(ticket.getSpotId());
        return fee;
    }

//    // for the "spots free" display board
//    public synchronized int freeSpotsFor(VehicleType vehicle) {
//        int free = 0;
//        for (ParkingSpot s : freeSpots()) if (s.getType().canFit(vehicle)) free++;
//        return free;
//    }

    private List<ParkingSpot> freeSpots() {
        List<ParkingSpot> free = new ArrayList<>();
        for (ParkingSpot s : spots) if (!occupiedSpotIds.contains(s.getId())) free.add(s);
        return free;
    }
}
