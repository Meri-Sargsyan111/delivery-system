package com.example.aiservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Wraps a failure talking to the OpenAI API (network error, auth failure, rate limit,
 * unexpected response shape) so the controller can return a clean error to the caller
 * instead of leaking OpenAI's own response body.
 */
public class AiServiceException extends RuntimeException {

    private final HttpStatus status;

    public AiServiceException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
