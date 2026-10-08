package com.conceptcoding.interviewquestions.hello_all_questions.allocation.amazonlocker;

import com.conceptcoding.interviewquestions.hello_all_questions.allocation.amazonlocker.model.AccessToken;
import com.conceptcoding.interviewquestions.hello_all_questions.allocation.amazonlocker.model.Compartment;
import com.conceptcoding.interviewquestions.hello_all_questions.allocation.amazonlocker.model.Size;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

// One locker bank (a wall of compartments at a store).
//
// Concurrency without a global lock:
//   compartment.tryOccupy()              atomic: two drivers never get the same door
//   tokensByCode.putIfAbsent(code, ...)  atomic: two parcels never get the same code
//   tokensByCode.remove(code, token)     atomic: a code opens the door exactly once
public class AmazonLocker {

    private static final Duration TOKEN_TTL = Duration.ofDays(3);
    private static final int CODE_GEN_MAX_ATTEMPTS = 10;

    private final List<Compartment> compartments;
    private final Map<String, AccessToken> tokensByCode = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Random random;

    public AmazonLocker(List<Compartment> compartments) {
        // SecureRandom: pickup codes are like passwords; java.util.Random is predictable
        this(compartments, Clock.systemUTC(), new SecureRandom());
    }

    public AmazonLocker(List<Compartment> compartments, Clock clock, Random random) {
        this.compartments = new ArrayList<>(compartments);
        this.clock = clock;
        this.random = random;
    }

    // Delivery driver arrives with a parcel → returns the 6-digit pickup code for the customer.
    public String depositPackage(Size parcelSize) {
        Compartment compartment = claimSmallestFitting(parcelSize);
        if (compartment == null) throw new NoSuchElementException("No compartment free for a " + parcelSize + " parcel");

        Instant expiresAt = clock.instant().plus(TOKEN_TTL);
        for (int attempt = 0; attempt < CODE_GEN_MAX_ATTEMPTS; attempt++) {
            String code = String.format("%06d", random.nextInt(1_000_000));
            if (tokensByCode.putIfAbsent(code, new AccessToken(code, expiresAt, compartment)) == null) {
                compartment.open();                     // driver puts the parcel in
                return code;
            }
        }
        compartment.markFree();                         // undo the claim: we couldn't issue a code
        throw new IllegalStateException("Could not generate a unique pickup code");
    }

    // Customer types the code → the door opens once.
    public void pickup(String code) {
        if (code == null || code.isEmpty()) throw new IllegalArgumentException("Invalid pickup code");
        AccessToken token = tokensByCode.get(code);
        if (token == null) throw new NoSuchElementException("Invalid pickup code");
        if (token.isExpired(clock)) throw new IllegalStateException("Pickup code has expired");
        if (!tokensByCode.remove(code, token)) {        // someone used it a moment ago
            throw new NoSuchElementException("Invalid pickup code");
        }
        Compartment c = token.getCompartment();
        c.open();
        c.markFree();
    }

    // Staff run this daily: open compartments whose parcel was never collected (returned to sender).
    // Mirrors pickup's cleanup: free the compartment AND drop the token, or the map leaks.
    public int openExpiredCompartments() {
        int reclaimed = 0;
        for (AccessToken token : tokensByCode.values()) {
            if (token.isExpired(clock) && tokensByCode.remove(token.getCode(), token)) {
                token.getCompartment().open();
                token.getCompartment().markFree();
                reclaimed++;
            }
        }
        return reclaimed;
    }

    public int freeCompartmentsFor(Size parcelSize) {
        int free = 0;
        for (Compartment c : compartments) if (c.getSize().canHold(parcelSize) && c.isAvailable()) free++;
        return free;
    }

    // Smallest compartment that fits, so large ones stay free for large parcels.
    // tryOccupy() may fail if another driver grabbed it a moment earlier: just try the next one.
    private Compartment claimSmallestFitting(Size parcelSize) {
        List<Compartment> candidates = new ArrayList<>();
        for (Compartment c : compartments) {
            if (c.getSize().canHold(parcelSize) && c.isAvailable()) candidates.add(c);
        }
        candidates.sort(Comparator.comparingInt(c -> c.getSize().getRank()));
        for (Compartment c : candidates) {
            if (c.tryOccupy()) return c;
        }
        return null;
    }
}
