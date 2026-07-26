package com.example.paymentservice.exception;

/**
 * Thrown by OrderServiceClient after exhausting its inline retries - the caller
 * (PaymentServiceImpl) must catch this, release the order-creation claim, and leave the
 * payment for the reconciliation sweep rather than letting this propagate as a 5xx that
 * would make a webhook caller (e.g. Stripe) retry indefinitely for a payment that already
 * succeeded.
 */
public class OrderCreationException extends RuntimeException {

    public OrderCreationException(String message, Throwable cause) {
        super(message, cause);
    }
}
