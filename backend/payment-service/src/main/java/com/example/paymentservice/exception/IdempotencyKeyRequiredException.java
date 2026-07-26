package com.example.paymentservice.exception;

/** Thrown when POST /payments is called without the required Idempotency-Key header. */
public class IdempotencyKeyRequiredException extends RuntimeException {

    public IdempotencyKeyRequiredException(String message) {
        super(message);
    }
}
