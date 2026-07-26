package com.example.trackingservice.routing;

/** Thrown by GeocodingClient for interactive REST calls after retries are exhausted. */
public class GeocodingUnavailableException extends RuntimeException {

    public GeocodingUnavailableException(String message) {
        super(message);
    }
}
