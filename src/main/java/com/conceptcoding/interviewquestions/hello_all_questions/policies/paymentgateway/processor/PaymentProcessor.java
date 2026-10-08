package com.conceptcoding.interviewquestions.hello_all_questions.policies.paymentgateway.processor;

import com.conceptcoding.interviewquestions.hello_all_questions.policies.paymentgateway.model.PaymentMethod;
import com.conceptcoding.interviewquestions.hello_all_questions.policies.paymentgateway.model.PaymentRequest;

// Strategy: one implementation per payment method. The gateway routes a request to the
// processor whose supports() says yes. Real ones are Adapters around vendor SDKs
// (Razorpay / Stripe for cards, NPCI for UPI).
public interface PaymentProcessor {

    boolean supports(PaymentMethod method);

    // normal failures (declined, insufficient funds) come back as a response, not an exception
    ProcessorResponse process(PaymentRequest request);

    final class ProcessorResponse {
        private final boolean success;
        private final String errorCode;
        private final String errorMessage;

        private ProcessorResponse(boolean success, String errorCode, String errorMessage) {
            this.success = success;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
        }

        public static ProcessorResponse ok()                              { return new ProcessorResponse(true, null, null); }
        public static ProcessorResponse fail(String code, String message) { return new ProcessorResponse(false, code, message); }

        public boolean success()      { return success; }
        public String  errorCode()    { return errorCode; }
        public String  errorMessage() { return errorMessage; }
    }
}
