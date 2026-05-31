package com.conceptcoding.interviewquestions.hello_all_questions.paymentgateway.processor;

import com.conceptcoding.interviewquestions.hello_all_questions.paymentgateway.model.PaymentMethod;
import com.conceptcoding.interviewquestions.hello_all_questions.paymentgateway.model.PaymentRequest;

/** Simulated net-banking processor. */
public class NetBankingProcessor implements PaymentProcessor {

    @Override
    public boolean supports(PaymentMethod method) {
        return method == PaymentMethod.NETBANKING;
    }

    @Override
    public ProcessorResponse process(PaymentRequest request) {
        return ProcessorResponse.ok();
    }
}
