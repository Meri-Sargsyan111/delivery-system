package com.example.apigateway.exception;

import java.time.LocalDateTime;

/**
 * Mirrors the ErrorResponse shape every other service returns (timestamp/status/error/
 * message/path), so a failure surfaced at the gateway layer (downstream timeout,
 * connection refused, no matching route) looks the same to clients as one surfaced by
 * an individual service's own GlobalExceptionHandler.
 */
public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path
) {}