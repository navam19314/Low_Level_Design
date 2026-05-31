package com.conceptcoding.interviewquestions.hello_all_questions.paymentgateway.processor;

import com.conceptcoding.interviewquestions.hello_all_questions.paymentgateway.model.PaymentMethod;
import com.conceptcoding.interviewquestions.hello_all_questions.paymentgateway.model.PaymentRequest;

/**
 * Simulated card processor. Real impl would wrap Stripe / Razorpay / PayU SDK.
 * Deterministic for tests: requests over 100k cents are "rejected by issuer".
 */
public class CardProcessor implements PaymentProcessor {

    private static final long ISSUER_LIMIT_CENTS = 100_000L;

    @Override
    public boolean supports(PaymentMethod method) {
        return method == PaymentMethod.CARD;
    }

    @Override
    public ProcessorResponse process(PaymentRequest request) {
        if (request.amountCents() > ISSUER_LIMIT_CENTS) {
            return ProcessorResponse.fail("CARD_DECLINED", "amount exceeds issuer limit");
        }
        return ProcessorResponse.ok();
    }
}
