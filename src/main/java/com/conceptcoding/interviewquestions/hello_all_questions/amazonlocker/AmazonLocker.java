package com.conceptcoding.interviewquestions.hello_all_questions.amazonlocker;

import com.conceptcoding.interviewquestions.hello_all_questions.amazonlocker.model.AccessToken;
import com.conceptcoding.interviewquestions.hello_all_questions.amazonlocker.model.Compartment;
import com.conceptcoding.interviewquestions.hello_all_questions.amazonlocker.model.Size;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;

public class AmazonLocker {

    private static final Duration TOKEN_TTL = Duration.ofDays(7);
    private static final int CODE_GEN_MAX_ATTEMPTS = 10;

    private final List<Compartment> compartments;
    private final Map<String, AccessToken> tokensByCode;
    private final Clock clock;
    private final Random random;

    public AmazonLocker(List<Compartment> compartments) {
        this(compartments, Clock.systemDefaultZone(), new Random());
    }

    public AmazonLocker(List<Compartment> compartments, Clock clock, Random random) {
        this.compartments = compartments;
        this.tokensByCode = new HashMap<>();
        this.clock = clock;
        this.random = random;
    }

    // Order matters: open hardware FIRST so the driver can physically deposit,
    // THEN mark occupied + mint token. Token is the proof-of-deposit returned to caller.
    public String depositPackage(Size size) {
        Compartment compartment = findAvailable(size);
        if (compartment == null) {
            throw new NoSuchElementException("No available compartment of size " + size);
        }

        compartment.open();
        compartment.markOccupied();

        String code = generateUniqueCode();
        Instant expiresAt = clock.instant().plus(TOKEN_TTL);
        AccessToken token = new AccessToken(code, expiresAt, compartment);
        tokensByCode.put(code, token);
        return code;
    }

    // Three distinct error types so the caller can tell apart:
    //   - malformed input (IllegalArgument), unknown code (NoSuchElement), expired (IllegalState).
    // O(1) — token references its compartment, no scan.
    public void pickup(String tokenCode) {
        if (tokenCode == null || tokenCode.isEmpty()) {
            throw new IllegalArgumentException("Invalid access token code");
        }
        AccessToken token = tokensByCode.get(tokenCode);
        if (token == null) {
            throw new NoSuchElementException("Invalid access token code");
        }
        if (token.isExpired(clock)) {
            throw new IllegalStateException("Access token has expired");
        }

        Compartment compartment = token.getCompartment();
        compartment.open();
        compartment.markFree();
        tokensByCode.remove(tokenCode);
    }

    // Staff reclaim = the deferred completion of a pickup that never happened.
    // Must mirror pickup's cleanup: free the compartment AND drop the token, else the map leaks.
    public void openExpiredCompartments() {
        Iterator<Map.Entry<String, AccessToken>> it = tokensByCode.entrySet().iterator();
        while (it.hasNext()) {
            AccessToken token = it.next().getValue();
            if (token.isExpired(clock)) {
                Compartment c = token.getCompartment();
                c.open();
                c.markFree();
                it.remove();
            }
        }
    }

    private Compartment findAvailable(Size size) {
        for (Compartment c : compartments) {
            if (c.getSize() == size && c.isAvailable()) {
                return c;
            }
        }
        return null;
    }

    // 6-digit codes collide ~1 in 1M; bounded retry against the live map prevents
    // issuing a code that already points to someone else's compartment.
    private String generateUniqueCode() {
        for (int attempts = 0; attempts < CODE_GEN_MAX_ATTEMPTS; attempts++) {
            String code = String.format("%06d", random.nextInt(1_000_000));
            if (!tokensByCode.containsKey(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Could not generate a unique access token code");
    }
}
