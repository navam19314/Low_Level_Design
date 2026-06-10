package com.conceptcoding.interviewquestions.hello_all_questions.parkinglot;

import com.conceptcoding.interviewquestions.hello_all_questions.parkinglot.model.ParkingSpot;
import com.conceptcoding.interviewquestions.hello_all_questions.parkinglot.model.SpotType;
import com.conceptcoding.interviewquestions.hello_all_questions.parkinglot.model.Ticket;
import com.conceptcoding.interviewquestions.hello_all_questions.parkinglot.model.VehicleType;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

public class ParkingLot {

    private final List<ParkingSpot> spots;
    private final Map<String, Ticket> activeTickets;
    private final Set<String> occupiedSpotIds;
    private final long hourlyRateCents;

    public ParkingLot(List<ParkingSpot> spots, long hourlyRateCents) {
        this.spots = spots;
        this.hourlyRateCents = hourlyRateCents;
        this.activeTickets = new HashMap<>();
        this.occupiedSpotIds = new HashSet<>();
    }

    // Find a compatible spot, mark it occupied, mint a ticket.
    // entryTime stamped with LocalDateTime.now() at the moment of entry.
    public Ticket enter(VehicleType vehicleType) {
        ParkingSpot spot = findAvailableSpot(vehicleType);
        if (spot == null) {
            throw new NoSuchElementException("No available spot for " + vehicleType);
        }

        String ticketId = UUID.randomUUID().toString();
        Ticket ticket = new Ticket(ticketId, spot.getId(), vehicleType, LocalDateTime.now());
        occupiedSpotIds.add(spot.getId());
        activeTickets.put(ticketId, ticket);
        return ticket;
    }

    // Computes fee using LocalDateTime.now() as exit time.
    // Removing the ticket from the map prevents a second exit on the same ticket.
    public long exit(String ticketId) {
        if (ticketId == null || ticketId.isEmpty()) {
            throw new IllegalArgumentException("Invalid ticket id");
        }
        Ticket ticket = activeTickets.get(ticketId);
        if (ticket == null) {
            throw new NoSuchElementException("Ticket not found or already used");
        }

        LocalDateTime exitTime = LocalDateTime.now();
        long fee = computeFee(ticket.getEntryTime(), exitTime);
        occupiedSpotIds.remove(ticket.getSpotId());
        activeTickets.remove(ticketId);
        return fee;
    }

    // First-fit scan — first compatible free spot wins.
    private ParkingSpot findAvailableSpot(VehicleType vehicleType) {
        SpotType required = mapToSpotType(vehicleType);
        for (ParkingSpot spot : spots) {
            if (spot.getSpotType() == required && !occupiedSpotIds.contains(spot.getId())) {
                return spot;
            }
        }
        return null;
    }

    private SpotType mapToSpotType(VehicleType vehicleType) {
        switch (vehicleType) {
            case MOTORCYCLE: return SpotType.MOTORCYCLE;
            case CAR:        return SpotType.CAR;
            case LARGE:      return SpotType.LARGE;
            default: throw new IllegalArgumentException("Unknown vehicle type: " + vehicleType);
        }
    }

    // Any partial hour rounds UP — minimum charge is 1 hour.
    private long computeFee(LocalDateTime entryTime, LocalDateTime exitTime) {
        long minutes = Duration.between(entryTime, exitTime).toMinutes();
        long hours = (minutes + 59) / 60;   // ceiling division
        if (hours == 0) hours = 1;           // minimum 1-hour charge
        return hours * hourlyRateCents;
    }
}
