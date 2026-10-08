package com.conceptcoding.interviewquestions.hello_all_questions.policies.paymentgateway.processor;

import com.conceptcoding.interviewquestions.hello_all_questions.policies.paymentgateway.model.PaymentMethod;
import com.conceptcoding.interviewquestions.hello_all_questions.policies.paymentgateway.model.PaymentRequest;

// Simulated card processor. Real impl would wrap Stripe / Razorpay / PayU SDK.
// Deterministic for tests: requests over ₹1,000 (100,000 paise) are "rejected by issuer".
public class CardProcessor implements PaymentProcessor {

    private static final long ISSUER_LIMIT_PAISE = 100_000L;

    @Override
    public boolean supports(PaymentMethod method) {
        return method == PaymentMethod.CARD;
    }

    @Override
    public ProcessorResponse process(PaymentRequest request) {
        if (request.amountPaise() > ISSUER_LIMIT_PAISE) {
            return ProcessorResponse.fail("CARD_DECLINED", "amount exceeds issuer limit");
        }
        return ProcessorResponse.ok();
    }
}
