package com.example.trackingservice.routing;

/** Thrown by RoutingClient for interactive REST calls after retries are exhausted. */
public class RoutingUnavailableException extends RuntimeException {

    public RoutingUnavailableException(String message) {
        super(message);
    }
}
