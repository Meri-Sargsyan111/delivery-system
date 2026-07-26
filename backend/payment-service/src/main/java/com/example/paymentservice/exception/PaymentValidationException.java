package com.example.paymentservice.exception;

/**
 * Thrown for amount/currency mismatches between what was expected and what the provider
 * actually confirmed, and other integrity violations that must never be silently
 * accepted - see PaymentServiceImpl's verify/webhook handling.
 */
public class PaymentValidationException extends RuntimeException {

    public PaymentValidationException(String message) {
        super(message);
    }
}
