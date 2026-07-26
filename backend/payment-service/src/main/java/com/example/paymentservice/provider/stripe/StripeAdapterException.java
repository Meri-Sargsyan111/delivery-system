package com.example.paymentservice.provider.stripe;

/** Wraps a StripeException so callers don't need a Stripe SDK import to handle provider failures. */
public class StripeAdapterException extends RuntimeException {

    public StripeAdapterException(String message, Throwable cause) {
        super(message, cause);
    }
}
