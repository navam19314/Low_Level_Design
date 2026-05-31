package com.conceptcoding.interviewquestions.hello_all_questions.parkinglot;

import com.conceptcoding.interviewquestions.hello_all_questions.parkinglot.model.ParkingSpot;
import com.conceptcoding.interviewquestions.hello_all_questions.parkinglot.model.SpotType;
import com.conceptcoding.interviewquestions.hello_all_questions.parkinglot.model.Ticket;
import com.conceptcoding.interviewquestions.hello_all_questions.parkinglot.model.VehicleType;

import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/**
 * Single-threaded by contract. The base design assumes one caller drives enter/exit at a time.
 *
 * <p>For multi-gate concurrency the cheapest correct fix is method-level synchronization:
 * mark both {@code enter} and {@code exit} {@code synchronized} — operations are short and
 * contention is low. For higher throughput, replace the HashSet/HashMap with
 * {@code ConcurrentHashMap.newKeySet()} + {@code ConcurrentHashMap}, and use the atomic
 * {@code Set.add(spotId)} return value as the "claim" so two threads can't grab the same spot.
 *
 * <p>Allocation policy is currently hardcoded to first-fit (linear scan, first compatible
 * free spot wins). If the interviewer asks for variants (best-fit, random distribution,
 * proximity-to-entrance), extract a {@code SpotLookupStrategy} interface and inject — see
 * Step 5 of INTERVIEW_WALKTHROUGH.md for the refactor sketch.
 */
public class ParkingLot {

    private static final long MILLIS_PER_HOUR = 60L * 60L * 1000L;

    private final List<ParkingSpot> spots;
    private final Map<String, Ticket> activeTickets;
    private final Set<String> occupiedSpotIds;
    private final long hourlyRateCents;
    private final Clock clock;

    public ParkingLot(List<ParkingSpot> spots, long hourlyRateCents) {
        this(spots, hourlyRateCents, Clock.systemUTC());
    }

    public ParkingLot(List<ParkingSpot> spots, long hourlyRateCents, Clock clock) {
        this.spots = spots;
        this.hourlyRateCents = hourlyRateCents;
        this.clock = clock;
        this.activeTickets = new HashMap<>();
        this.occupiedSpotIds = new HashSet<>();
    }

    // Find a compatible spot, mark it occupied, mint a ticket. All-or-nothing:
    // if no spot exists we throw before mutating any state.
    public Ticket enter(VehicleType vehicleType) {
        ParkingSpot spot = findAvailableSpot(vehicleType);
        if (spot == null) {
            throw new NoSuchElementException("No available spot for vehicle type " + vehicleType);
        }

        occupiedSpotIds.add(spot.getId());
        String ticketId = UUID.randomUUID().toString();
        Ticket ticket = new Ticket(ticketId, spot.getId(), vehicleType, clock.millis());
        activeTickets.put(ticketId, ticket);
        return ticket;
    }

    // Returns the fee in cents. Removing the ticket from the map is what
    // prevents a second exit with the same ticket — same trick as Locker.pickup.
    public long exit(String ticketId) {
        if (ticketId == null || ticketId.isEmpty()) {
            throw new IllegalArgumentException("Invalid ticket id");
        }
        Ticket ticket = activeTickets.get(ticketId);
        if (ticket == null) {
            throw new NoSuchElementException("Ticket not found or already used");
        }

        long fee = computeFee(ticket.getEntryTimeMs(), clock.millis());
        occupiedSpotIds.remove(ticket.getSpotId());
        activeTickets.remove(ticketId);
        return fee;
    }

    // First-fit linear scan — simplest correct allocation. If the requirements
    // grow to "support multiple lookup policies", extract a SpotLookupStrategy.
    private ParkingSpot findAvailableSpot(VehicleType vehicleType) {
        SpotType required = mapVehicleTypeToSpotType(vehicleType);
        for (ParkingSpot spot : spots) {
            if (spot.getSpotType() == required && !occupiedSpotIds.contains(spot.getId())) {
                return spot;
            }
        }
        return null;
    }

    private SpotType mapVehicleTypeToSpotType(VehicleType vehicleType) {
        switch (vehicleType) {
            case MOTORCYCLE: return SpotType.MOTORCYCLE;
            case CAR:        return SpotType.CAR;
            case LARGE:      return SpotType.LARGE;
            default: throw new IllegalArgumentException("Unknown vehicle type " + vehicleType);
        }
    }

    // Any partial hour rounds UP to the next full hour — covered by the modulo check.
    private long computeFee(long entryTimeMs, long exitTimeMs) {
        long durationMs = exitTimeMs - entryTimeMs;
        long hours = durationMs / MILLIS_PER_HOUR;
        if (durationMs % MILLIS_PER_HOUR > 0) {
            hours++;
        }
        if (hours == 0) {
            hours = 1; // minimum 1-hour charge even for instant exit
        }
        return hours * hourlyRateCents;
    }
}
