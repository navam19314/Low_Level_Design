package com.conceptcoding.interviewquestions.hello_all_questions.paymentgateway.processor;

import com.conceptcoding.interviewquestions.hello_all_questions.paymentgateway.model.PaymentMethod;
import com.conceptcoding.interviewquestions.hello_all_questions.paymentgateway.model.PaymentRequest;

/** Simulated UPI processor (NPCI-style). Always succeeds in this demo. */
public class UpiProcessor implements PaymentProcessor {

    @Override
    public boolean supports(PaymentMethod method) {
        return method == PaymentMethod.UPI;
    }

    @Override
    public ProcessorResponse process(PaymentRequest request) {
        return ProcessorResponse.ok();
    }
}
