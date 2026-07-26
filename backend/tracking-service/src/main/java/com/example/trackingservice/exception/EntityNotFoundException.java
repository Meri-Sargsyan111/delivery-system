package com.example.trackingservice.exception;

/** Thrown when a live-tracking lookup (GET .../live, GET .../route) has no TrackingState row yet. */
public class EntityNotFoundException extends RuntimeException {

    public EntityNotFoundException(String message) {
        super(message);
    }
}
