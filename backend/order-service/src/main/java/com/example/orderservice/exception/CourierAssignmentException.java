package com.example.orderservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Wraps a failure reported by courier-service while reserving a courier,
 * preserving the original HTTP status so the dispatcher sees the real reason
 * (courier not found vs. courier not available) instead of a generic error.
 */
public class CourierAssignmentException extends RuntimeException {

    private final HttpStatus status;

    public CourierAssignmentException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
