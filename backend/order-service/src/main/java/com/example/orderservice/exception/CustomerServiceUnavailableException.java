package com.example.orderservice.exception;

/**
 * auth-service is unreachable or too slow to resolve the customer for a new order (see
 * AuthServiceClient) - mapped to 503 with a clean, user-facing message rather than letting
 * the underlying I/O exception (raw socket/timeout message, internal hostnames) reach the
 * client, and rather than letting the request thread hang until api-gateway's own
 * response-timeout trips first.
 */
public class CustomerServiceUnavailableException extends RuntimeException {

    public CustomerServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}