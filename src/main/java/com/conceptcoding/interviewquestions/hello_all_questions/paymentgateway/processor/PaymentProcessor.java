package com.conceptcoding.interviewquestions.hello_all_questions.paymentgateway.processor;

import com.conceptcoding.interviewquestions.hello_all_questions.paymentgateway.model.PaymentMethod;
import com.conceptcoding.interviewquestions.hello_all_questions.paymentgateway.model.PaymentRequest;

/**
 * Strategy interface — one impl per payment method. The gateway routes each
 * request to the matching processor based on {@code supports()}.
 *
 * <p>Implementations represent the I/O boundary with an external payment network
 * (Stripe-like for cards, NPCI for UPI, etc.). Real implementations would be
 * Adapters around vendor SDKs; here we simulate them for the smoke test.
 */
public interface PaymentProcessor {

    /** Whether this processor can handle the given method. */
    boolean supports(PaymentMethod method);

    /**
     * Process the payment. Returns a {@link ProcessorResponse} indicating success
     * or a specific failure reason. MUST NOT throw on normal failures — the gateway
     * relies on the response to drive the state machine.
     */
    ProcessorResponse process(PaymentRequest request);

    /** Process result returned by a processor. Immutable. */
    record ProcessorResponse(boolean success, String errorCode, String errorMessage) {
        public static ProcessorResponse ok()                                  { return new ProcessorResponse(true,  null, null); }
        public static ProcessorResponse fail(String code, String message)     { return new ProcessorResponse(false, code, message); }
    }
}
