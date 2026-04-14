package com.work.mautonlaundry.exceptions;

public class PaymentGatewayTimeoutException extends RuntimeException {

    public PaymentGatewayTimeoutException(String message) {
        super(message);
    }

    public PaymentGatewayTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
