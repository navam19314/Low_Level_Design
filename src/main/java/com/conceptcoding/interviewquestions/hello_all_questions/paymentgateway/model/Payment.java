package com.conceptcoding.interviewquestions.hello_all_questions.paymentgateway.model;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Mutable payment record — owns its own state machine. The status field is the
 * ONLY mutable bit on a Payment; everything else is set in the constructor.
 *
 * <p>State transitions are guarded by {@link #transitionTo} which consults the
 * static {@code ALLOWED_TRANSITIONS} map. Invalid transitions (e.g., SUCCESS →
 * PENDING) throw immediately, so a Payment's status can never silently regress.
 */
public class Payment {

    // Adjacency list of valid status transitions. EnumMap is the right Map for enum keys.
    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(PaymentStatus.class);
    static {
        ALLOWED_TRANSITIONS.put(PaymentStatus.PENDING,        EnumSet.of(PaymentStatus.PROCESSING, PaymentStatus.FAILED));
        ALLOWED_TRANSITIONS.put(PaymentStatus.PROCESSING,     EnumSet.of(PaymentStatus.SUCCESS,    PaymentStatus.FAILED));
        ALLOWED_TRANSITIONS.put(PaymentStatus.SUCCESS,        EnumSet.of(PaymentStatus.REFUND_PENDING));
        ALLOWED_TRANSITIONS.put(PaymentStatus.REFUND_PENDING, EnumSet.of(PaymentStatus.REFUNDED,   PaymentStatus.SUCCESS));
        ALLOWED_TRANSITIONS.put(PaymentStatus.FAILED,         EnumSet.noneOf(PaymentStatus.class));   // terminal
        ALLOWED_TRANSITIONS.put(PaymentStatus.REFUNDED,       EnumSet.noneOf(PaymentStatus.class));   // terminal
    }

    private final String id;
    private final String idempotencyKey;
    private final String customerId;
    private final long amountCents;
    private final String currency;
    private final PaymentMethod method;
    private final Instant createdAt;

    private PaymentStatus status;
    private Instant lastUpdatedAt;
    private String  errorCode;
    private String  errorMessage;

    public Payment(String id, PaymentRequest request, Instant now) {
        this.id             = id;
        this.idempotencyKey = request.idempotencyKey();
        this.customerId     = request.customerId();
        this.amountCents    = request.amountCents();
        this.currency       = request.currency();
        this.method         = request.method();
        this.createdAt      = now;
        this.lastUpdatedAt  = now;
        this.status         = PaymentStatus.PENDING;
    }

    public String        getId()             { return id; }
    public String        getIdempotencyKey() { return idempotencyKey; }
    public String        getCustomerId()     { return customerId; }
    public long          getAmountCents()    { return amountCents; }
    public String        getCurrency()       { return currency; }
    public PaymentMethod getMethod()         { return method; }
    public PaymentStatus getStatus()         { return status; }
    public Instant       getCreatedAt()      { return createdAt; }
    public Instant       getLastUpdatedAt()  { return lastUpdatedAt; }
    public String        getErrorCode()      { return errorCode; }
    public String        getErrorMessage()   { return errorMessage; }

    /** Guarded state transition — throws if the move isn't in the allowed adjacency. */
    public void transitionTo(PaymentStatus next, Instant now) {
        if (!ALLOWED_TRANSITIONS.get(status).contains(next)) {
            throw new IllegalStateException(
                    "Invalid transition " + status + " → " + next + " for payment " + id);
        }
        this.status = next;
        this.lastUpdatedAt = now;
    }

    public void markFailure(String code, String message, Instant now) {
        transitionTo(PaymentStatus.FAILED, now);
        this.errorCode = code;
        this.errorMessage = message;
    }
}
